-- ================================================================================
-- POBLACIÓN DEMO - CheTurno
-- ================================================================================
-- Deja la base "viva" a escala de todo el país: los 3 centros del juego de datos
-- original más 50 hospitales y sanatorios reales de las 23 provincias y CABA
-- (nombre, dirección, coordenadas y teléfono de © colaboradores de OpenStreetMap,
-- licencia ODbL), unos 140 médicos en 10 especialidades (uno por centro con la
-- agenda abierta a reservas, más los cuatro con login), ~1800 pacientes, turnos
-- pasados (historial) y futuros (lo que viene), auditoría, encuestas de
-- satisfacción, lista de espera y notificaciones. Los médicos y pacientes son
-- ficticios. Las cuentas para entrar están en staging/CREDENCIALES.md.
--
-- Uso (siempre con psql):
--   psql -v ON_ERROR_STOP=1 -q -f staging/poblacion_demo.sql
--   cd testing && npm run db:demo            (usa PGHOST/PGUSER/... del entorno)
--
-- Propiedades:
--   * Re-ejecutable: cada corrida borra los datos de dominio generados y los
--     vuelve a crear con fechas relativas al día de hoy (hora de Argentina).
--   * Autosuficiente: funciona sobre una base vacía (solo el esquema que crea
--     Hibernate), sobre staging/userinit.sql o sobre una corrida anterior.
--   * Nunca borra ni modifica filas de `users`: solo agrega las cuentas demo que
--     falten. Tampoco toca `configuracion`.
--   * Conserva los pacientes y médicos que tienen cuenta de login.
--   * Segura con el backend levantado: no baja ninguna secuencia (Hibernate
--     reserva ids de a 50 en memoria) y corre en una sola transacción.
--   * Agrega índices de apoyo (CREATE INDEX IF NOT EXISTS), ver sección 2.1.
--
-- Mails: el backend manda recordatorios y avisos de cancelación automática a los
-- turnos sin confirmar. Los pacientes generados usan @example.com (dominio
-- reservado, no entrega) y los turnos de las próximas 96 hs ya nacen confirmados
-- o cancelados; los PROGRAMADO quedan de 5 días en adelante.
--
-- Qué borra: turnos, auditoría de turnos, encuestas respondidas e invitaciones,
-- notificaciones, deep links, lista de espera, días excepcionales, esquemas de
-- turno, disponibilidades, staff médico, relaciones médico/centro-especialidad,
-- y los médicos y pacientes sin cuenta de login.
--
-- El tamaño está pensado para el servidor de producción (1 GB de RAM): varias
-- pantallas traen todos los turnos o generan todos los horarios en cada carga.
-- Parámetros opcionales:  psql -v semanas_historia=8 -f staging/poblacion_demo.sql
-- ================================================================================

\set ON_ERROR_STOP on

\if :{?semanas_historia}
\else
\set semanas_historia 13
\endif
\if :{?semanas_historia_destacados}
\else
\set semanas_historia_destacados 39
\endif
\if :{?dias_futuro}
\else
\set dias_futuro 27
\endif
\if :{?max_turnos_auditados}
\else
\set max_turnos_auditados 500
\endif
\if :{?max_encuestas}
\else
\set max_encuestas 360
\endif
\if :{?pacientes}
\else
\set pacientes 1800
\endif
\if :{?agendas_por_centro}
\else
\set agendas_por_centro 1
\endif

BEGIN;

SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires';
SET LOCAL client_min_messages = warning;

-- =====================================
-- 0. Funciones auxiliares y parámetros
-- =====================================

-- Uniforme [0,1) determinístico a partir de una clave.
CREATE FUNCTION pg_temp.u(k text) RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$
  SELECT ('x' || substr(md5(k), 1, 7))::bit(28)::int / 268435456.0
$$;

CREATE FUNCTION pg_temp.sin_acentos(t text) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT replace(lower(translate(t, 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunAEIOUUN')), ' ', '')
$$;

-- Reserva p_n ids por encima de todo lo que la secuencia ya entregó y de lo que
-- hay en la tabla. Devuelve la base: los ids reservados son base+1 .. base+p_n.
-- La secuencia nunca retrocede, así un backend en ejecución no repite ids.
-- p_piso: id mínimo a superar (para no pisar ids que se van a reutilizar).
CREATE FUNCTION pg_temp.reservar(p_seq text, p_tabla text, p_n bigint, p_piso bigint DEFAULT 0) RETURNS bigint
LANGUAGE plpgsql AS $$
DECLARE
  v_max bigint;
  v_last bigint;
  v_called boolean;
  v_base bigint;
BEGIN
  EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', p_tabla) INTO v_max;
  EXECUTE format('SELECT last_value, is_called FROM %I', p_seq) INTO v_last, v_called;
  IF NOT v_called THEN
    v_last := 0;
  END IF;
  v_base := GREATEST(v_max, v_last, p_piso);
  IF p_n > 0 THEN
    PERFORM setval(p_seq::regclass, v_base + p_n, true);
  END IF;
  RETURN v_base;
END $$;

CREATE TEMP TABLE d_param ON COMMIT DROP AS
SELECT current_date                          AS hoy,
       localtimestamp(0)                     AS ahora,
       localtime(0)                          AS hora_actual,
       :semanas_historia::int                AS semanas_historia,
       :semanas_historia_destacados::int     AS semanas_destacados,
       :dias_futuro::int                     AS dias_futuro,
       :max_turnos_auditados::int            AS max_turnos_auditados,
       :max_encuestas::int                   AS max_encuestas,
       :pacientes::int                       AS pacientes,
       :agendas_por_centro::int              AS agendas_por_centro,
       -- BCrypt de "password" (el mismo hash que staging/userinit.sql)
       '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi'::text AS hash_password;

-- =====================================
-- 1. Catálogo de la demo
-- =====================================

-- Centros. Los tres primeros son los del juego de datos original (ficticios, en
-- Trelew). El resto son instituciones reales de todo el país: nombre, dirección,
-- localidad, provincia, coordenadas y teléfono salen de OpenStreetMap.
-- n_medicos = médicos sin login que se generan para el centro.
CREATE TEMP TABLE d_centro (
  orden int, slug text, nombre text, direccion text, localidad text, provincia text,
  telefono text, latitud double precision, longitud double precision,
  n_consultorios int, n_medicos int, perfil text, semilla boolean
) ON COMMIT DROP;
INSERT INTO d_centro VALUES
  (1, 'santamaria', 'Clínica Santa María', 'Av. San Martín 123', 'Trelew', 'Chubut', '+5492804501111', -43.2489, -65.3051, 4, 2, 'general', true),
  (2, 'delsur', 'Clínica del Sur', 'Calle Belgrano 456', 'Trelew', 'Chubut', '+5492804502222', -43.2525, -65.3089, 4, 4, 'general', true),
  (3, 'delsol', 'Consultorios del Sol', 'Av. Fontana 789', 'Trelew', 'Chubut', '+5492804503333', -43.2456, -65.3012, 4, 4, 'general', true),
  (4, 'italiano', 'Hospital Italiano de Buenos Aires', 'Potosí 4060', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4959-0200', -34.607859, -58.423678, 2, 3, 'general', false),
  (5, 'clinicas', 'Hospital de Clínicas José de San Martín', 'Avenida Córdoba 2351', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 5950-8000', -34.598964, -58.400040, 2, 3, 'general', false),
  (6, 'garrahan', 'Hospital de Pediatría Garrahan', 'Avenida Pichincha 1890', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4122-6000', -34.630973, -58.393727, 2, 3, 'pediatrico', false),
  (7, 'aleman', 'Hospital Alemán', 'Avenida Pueyrredón 1640', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4827 7000', -34.591817, -58.401967, 2, 3, 'general', false),
  (8, 'britanico', 'Hospital Británico de Buenos Aires', 'Solís 2186', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', 'No informado', -34.633375, -58.388614, 2, 3, 'general', false),
  (9, 'guemes', 'Sanatorio Güemes', 'Francisco Acuña de Figueroa 1340', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4959 8200', -34.597332, -58.422201, 2, 3, 'general', false),
  (10, 'fernandez', 'Hospital General de Agudos Juan A. Fernández', 'Cerviño 3356', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4808 2600', -34.581369, -58.406944, 2, 3, 'general', false),
  (11, 'favaloro', 'Hospital Universitario Fundación Favaloro', 'Avenida Belgrano 1746', 'Buenos Aires', 'Ciudad Autónoma de Buenos Aires', '+54 11 4378-1200', -34.614368, -58.391194, 2, 3, 'cardio', false),
  (12, 'posadas', 'Hospital Nacional Profesor Alejandro Posadas', 'Avenida Presidente Arturo Umberto Illia', 'El Palomar', 'Buenos Aires', '+54 11 44699300', -34.629036, -58.574893, 2, 2, 'general', false),
  (13, 'austral', 'Hospital Universitario Austral', 'Avenida Juan Domingo Perón 1500', 'Pilar', 'Buenos Aires', '+54 2322 482000', -34.456293, -58.864211, 2, 2, 'general', false),
  (14, 'ludovica', 'Hospital de Niños Sor María Ludovica', 'Calle 14 1631', 'La Plata', 'Buenos Aires', '+54 221 453 5901', -34.932335, -57.943055, 2, 3, 'pediatrico', false),
  (15, 'comunidadmdp', 'Hospital Privado de Comunidad', 'Córdoba', 'Mar del Plata', 'Buenos Aires', '+54 223 499 0000', -38.021798, -57.565631, 2, 3, 'general', false),
  (16, 'penna', 'Hospital Interzonal Dr. José Penna', 'Láinez 2401', 'Bahía Blanca', 'Buenos Aires', '+54 291 459 3600', -38.731090, -62.228073, 2, 2, 'general', false),
  (17, 'privadocba', 'Hospital Privado Universitario de Córdoba', 'Obispo Trejo y Sanabria 320', 'Córdoba', 'Córdoba', 'No informado', -31.419293, -64.187036, 2, 3, 'general', false),
  (18, 'allende', 'Sanatorio Allende', 'Avenida Hipólito Yrigoyen 384', 'Córdoba', 'Córdoba', '+54 351 4269212', -31.424619, -64.187336, 2, 3, 'general', false),
  (19, 'clinicascba', 'Hospital Nacional de Clínicas', 'Santa Rosa 1564', 'Córdoba', 'Córdoba', 'No informado', -31.405357, -64.203678, 2, 3, 'general', false),
  (20, 'trinidad', 'Hospital de Niños de la Santísima Trinidad', 'Bajada del Pucará 787', 'Córdoba', 'Córdoba', '+54 351 458 6400', -31.427495, -64.168420, 2, 3, 'pediatrico', false),
  (21, 'centenario', 'Hospital Provincial del Centenario', 'Justo José de Urquiza 3101', 'Rosario', 'Santa Fe', '+54 341 472-4643', -32.938542, -60.664743, 2, 3, 'general', false),
  (22, 'parque', 'Sanatorio Parque', 'Bulevar Nicasio Oroño 860', 'Rosario', 'Santa Fe', '+54 341 527-8463', -32.944263, -60.653745, 2, 3, 'general', false),
  (23, 'heca', 'Hospital de Emergencias Dr. Clemente Álvarez', 'Avenida Carlos Pellegrini 3205', 'Rosario', 'Santa Fe', '+54 341 480-8111', -32.952843, -60.670510, 2, 3, 'general', false),
  (24, 'cullen', 'Hospital Dr. José María Cullen', 'Avenida Gobernador Freyre 2150', 'Santa Fe', 'Santa Fe', 'No informado', -31.648747, -60.717990, 2, 3, 'general', false),
  (25, 'centralmza', 'Hospital Central de Mendoza', 'Leandro Nicéforo Alem 500', 'Mendoza', 'Mendoza', '54 0261 4297100', -32.892124, -68.832926, 2, 3, 'general', false),
  (26, 'espanolmza', 'Hospital Español de Mendoza', 'Juan de Garay 965', 'Godoy Cruz', 'Mendoza', '+54 261 449 0300', -32.915122, -68.844127, 2, 2, 'general', false),
  (27, 'rawsonsj', 'Hospital Dr. Guillermo Rawson', 'Avenida Rawson 494 (S)', 'San Juan', 'San Juan', '+54 264 4222272', -31.539413, -68.513234, 2, 2, 'general', false),
  (28, 'carrillosl', 'Hospital Central Dr. Ramón Carrillo', 'Autopista de las Serranías Puntanas 782', 'San Luis', 'San Luis', 'No informado', -33.307017, -66.283643, 2, 2, 'general', false),
  (29, 'padilla', 'Hospital Ángel C. Padilla', 'Alberdi 550', 'San Miguel de Tucumán', 'Tucumán', 'No informado', -26.836479, -65.215133, 2, 3, 'general', false),
  (30, 'sanbernardo', 'Hospital San Bernardo', 'Mariano Boedo', 'Salta', 'Salta', 'No informado', -24.792105, -65.399500, 2, 3, 'general', false),
  (31, 'soria', 'Hospital Pablo Soria', 'General Paz', 'San Salvador de Jujuy', 'Jujuy', 'No informado', -24.182635, -65.312111, 2, 2, 'general', false),
  (32, 'carrillosgo', 'Hospital Regional Dr. Ramón Carrillo', 'Avenida Belgrano Sur 2273', 'Santiago del Estero', 'Santiago del Estero', 'No informado', -27.805570, -64.251598, 2, 2, 'general', false),
  (33, 'verabarros', 'Hospital Regional Dr. Enrique Vera Barros', 'Tupac Amaru', 'La Rioja', 'La Rioja', 'No informado', -29.413592, -66.835001, 2, 2, 'general', false),
  (34, 'perrando', 'Hospital Dr. Julio C. Perrando', 'Avenida 9 de Julio 1100', 'Resistencia', 'Chaco', '+54 362 4425050', -27.458109, -58.975683, 2, 2, 'general', false),
  (35, 'madariaga', 'Hospital Escuela de Agudos Dr. Ramón Madariaga', 'Avenida Cabred 1120', 'Posadas', 'Misiones', '+54 376 4443700', -27.384485, -55.889502, 2, 2, 'general', false),
  (36, 'hacformosa', 'Hospital de Alta Complejidad Pte. Juan Domingo Perón', 'Avenida Néstor Kirchner', 'Formosa', 'Formosa', 'No informado', -26.181530, -58.188430, 2, 2, 'general', false),
  (37, 'sanmartinparana', 'Hospital San Martín', 'Padre Corona', 'Paraná', 'Entre Ríos', 'No informado', -31.731757, -60.506294, 2, 2, 'general', false),
  (38, 'bariloche', 'Hospital Zonal Bariloche Dr. Ramón Carrillo', 'Avenida Perito Moreno 601', 'San Carlos de Bariloche', 'Río Negro', '+54 294 442-6100', -41.136206, -71.300101, 2, 2, 'general', false),
  (39, 'zatti', 'Hospital Artémides Zatti', 'Rivadavia 391', 'Viedma', 'Río Negro', '02920 425434', -40.814555, -62.989939, 2, 2, 'general', false),
  (40, 'margara', 'Hospital Zonal de Trelew Dr. Adolfo Margara', '28 de Julio', 'Trelew', 'Chubut', 'No informado', -43.255489, -65.310766, 2, 2, 'general', false),
  (41, 'santateresita', 'Hospital Subzonal Santa Teresita', 'Carlos Pellegrini', 'Rawson', 'Chubut', 'No informado', -43.296156, -65.107690, 2, 2, 'general', false),
  (42, 'isola', 'Hospital Zonal Dr. Andrés Ísola', 'Roberto Gómez 299', 'Puerto Madryn', 'Chubut', 'No informado', -42.758805, -65.041730, 2, 2, 'general', false),
  (43, 'esquel', 'Hospital Zonal de Esquel', '25 de Mayo', 'Esquel', 'Chubut', 'No informado', -42.917915, -71.315747, 2, 2, 'general', false),
  (44, 'riogallegos', 'Hospital Regional Río Gallegos', 'José Ingenieros 98', 'Río Gallegos', 'Santa Cruz', '+54 2966 42 0025', -51.628433, -69.220286, 2, 2, 'general', false),
  (45, 'ushuaia', 'Hospital Regional Ushuaia Gobernador Ernesto Campos', '12 de Octubre 65', 'Ushuaia', 'Tierra del Fuego', 'No informado', -54.813674, -68.323228, 2, 2, 'general', false),
  (46, 'riogrande', 'Hospital Regional Río Grande', 'Florentino Ameghino 709', 'Río Grande', 'Tierra del Fuego', '+54 2964 422086', -53.782124, -67.698993, 2, 2, 'general', false),
  (47, 'elcruce', 'Hospital El Cruce Néstor Kirchner', 'Avenida Calchaquí 5401', 'Florencio Varela', 'Buenos Aires', '+54 11 42109000', -34.772439, -58.271199, 2, 2, 'general', false),
  (48, 'sanmartinlp', 'Hospital General San Martín de La Plata', 'Avenida 1 1791', 'La Plata', 'Buenos Aires', '+54 221 421 1195', -34.922752, -57.922981, 2, 3, 'general', false),
  (49, 'alende', 'Hospital Interzonal Dr. Oscar Alende', 'Avenida Juan B. Justo 6701', 'Mar del Plata', 'Buenos Aires', '+54 223 4770262', -37.994614, -57.601492, 2, 3, 'general', false),
  (50, 'bautista', 'Hospital Interzonal San Juan Bautista', 'Julio Herrera', 'San Fernando del Valle de Catamarca', 'Catamarca', '+54 383 443-7660', -28.461761, -65.790485, 2, 2, 'general', false),
  (51, 'molas', 'Hospital Dr. Lucio Molas', 'Avenida Pilcomayo 700', 'Santa Rosa', 'La Pampa', '+54 295 445-5000', -36.598028, -64.291861, 2, 2, 'general', false),
  (52, 'castrorendon', 'Hospital Provincial Neuquén Dr. Eduardo Castro Rendón', 'Buenos Aires 450', 'Neuquén', 'Neuquén', '+54 299 449-0800', -38.950203, -68.056911, 2, 3, 'general', false),
  (53, 'escuelactes', 'Hospital Escuela General José Francisco de San Martín', 'Avenida 3 de Abril', 'Corrientes', 'Corrientes', '+54 379 442 7058', -27.475072, -58.835856, 2, 2, 'general', false);

CREATE TEMP TABLE d_esp (orden int, nombre text, descripcion text, intervalo int) ON COMMIT DROP;
INSERT INTO d_esp VALUES
  ( 1, 'Cardiología',    'Especialidad médica que se ocupa del corazón y sistema cardiovascular', 30),
  ( 2, 'Pediatría',      'Especialidad médica dedicada al cuidado de niños y adolescentes', 30),
  ( 3, 'Traumatología',  'Especialidad médica que trata lesiones del sistema músculo-esquelético', 30),
  ( 4, 'Clínica Médica', 'Atención integral del adulto, prevención y seguimiento de enfermedades crónicas', 30),
  ( 5, 'Dermatología',   'Diagnóstico y tratamiento de enfermedades de la piel', 30),
  ( 6, 'Ginecología',    'Salud del aparato reproductor femenino y controles periódicos', 30),
  ( 7, 'Oftalmología',   'Diagnóstico y tratamiento de enfermedades de los ojos', 30),
  ( 8, 'Neurología',     'Diagnóstico y tratamiento de enfermedades del sistema nervioso', 40),
  ( 9, 'Nutrición',      'Planes de alimentación y seguimiento nutricional', 40),
  (10, 'Kinesiología',   'Rehabilitación física y tratamiento del movimiento', 40);

CREATE TEMP TABLE d_os (orden int, nombre text, codigo text, descripcion text, hasta double precision) ON COMMIT DROP;
-- "hasta" es el acumulado de la distribución de pacientes (el resto queda sin obra social).
INSERT INTO d_os VALUES
  (1, 'OSDE',          'OSDE001',   'Obra Social de los Empleados de Comercio', 0.18),
  (2, 'Swiss Medical', 'SWISS001',  'Swiss Medical Group',                      0.28),
  (3, 'Galeno',        'GALENO001', 'Galeno Argentina',                         0.36),
  (4, 'PAMI',          'PAMI001',   'Programa de Atención Médica Integral',     0.52),
  (5, 'SEROS',         'SEROS001',  'Instituto de Seguridad Social y Seguros del Chubut', 0.72),
  (6, 'Medifé',        'MEDIFE001', 'Medifé Asociación Civil',                  0.78),
  (7, 'Sancor Salud',  'SANCOR001', 'Sancor Salud Grupo de Medicina Privada',   0.86),
  (8, 'OMINT',         'OMINT001',  'OMINT S.A. de Servicios',                  0.90);

-- Nombres y apellidos para generar médicos y pacientes ficticios.
CREATE TEMP TABLE d_nombre (n text) ON COMMIT DROP;
INSERT INTO d_nombre VALUES
  ('Juan'),('María'),('Carlos'),('Ana'),('Luis'),('Laura'),('José'),('Marta'),('Pedro'),('Lucía'),
  ('Miguel'),('Sofía'),('Jorge'),('Valentina'),('Diego'),('Camila'),('Martín'),('Florencia'),('Pablo'),('Julieta'),
  ('Sergio'),('Carolina'),('Fernando'),('Agustina'),('Ricardo'),('Micaela'),('Gustavo'),('Rocío'),('Daniel'),('Milagros'),
  ('Alejandro'),('Paula'),('Roberto'),('Daniela'),('Eduardo'),('Victoria'),('Marcelo'),('Natalia'),('Oscar'),('Gabriela'),
  ('Raúl'),('Silvia'),('Hugo'),('Patricia'),('Rubén'),('Claudia'),('Néstor'),('Mónica'),('Héctor'),('Graciela'),
  ('Matías'),('Brenda'),('Nicolás'),('Antonella'),('Facundo'),('Candela'),('Lucas'),('Abril'),('Tomás'),('Morena'),
  ('Franco'),('Lourdes'),('Emiliano'),('Pilar'),('Santiago'),('Guadalupe'),('Joaquín'),('Malena'),('Ezequiel'),('Aldana'),
  ('Cristian'),('Yamila'),('Maximiliano'),('Noelia'),('Leandro'),('Soledad'),('Gonzalo'),('Romina'),('Ramiro'),('Vanesa');
CREATE TEMP TABLE d_apellido (a text) ON COMMIT DROP;
INSERT INTO d_apellido VALUES
  ('González'),('Rodríguez'),('Gómez'),('Fernández'),('López'),('Díaz'),('Martínez'),('Pérez'),('García'),('Sánchez'),
  ('Romero'),('Sosa'),('Torres'),('Álvarez'),('Ruiz'),('Ramírez'),('Flores'),('Benítez'),('Acosta'),('Medina'),
  ('Herrera'),('Suárez'),('Aguirre'),('Giménez'),('Gutiérrez'),('Pereyra'),('Rojas'),('Molina'),('Castro'),('Ortiz'),
  ('Silva'),('Núñez'),('Luna'),('Juárez'),('Cabrera'),('Ríos'),('Morales'),('Godoy'),('Moreno'),('Ferreyra'),
  ('Domínguez'),('Carrizo'),('Peralta'),('Castillo'),('Ledesma'),('Quiroga'),('Vega'),('Vera'),('Muñoz'),('Ojeda'),
  ('Ponce'),('Villalba'),('Cardozo'),('Navarro'),('Coronel'),('Vázquez'),('Ramos'),('Vargas'),('Cáceres'),('Arias'),
  ('Figueroa'),('Córdoba'),('Correa'),('Maldonado'),('Paz'),('Rivero'),('Miranda'),('Mansilla'),('Farías'),('Roldán'),
  ('Méndez'),('Guzmán'),('Agüero'),('Hernández'),('Lucero'),('Cruz'),('Páez'),('Escobar'),('Mendoza'),('Barrios'),
  ('Bustos'),('Ávila'),('Ayala'),('Blanco'),('Soria'),('Maidana'),('Acuña'),('Leiva'),('Duarte'),('Moyano'),
  ('Campos'),('Soto'),('Chávez'),('Franco'),('Williams'),('Jones'),('Evans'),('Roberts'),('Davies'),('Thomas');

-- Médicos. Los cuatro con login (password "password") atienden en los centros
-- originales; el resto se genera: n_medicos por centro, con nombres ficticios.
CREATE TEMP TABLE d_medico (
  orden int, nombre text, apellido text, esp int, email text, dni bigint,
  telefono text, matricula text, login boolean, centro_orden int, pos int
) ON COMMIT DROP;
INSERT INTO d_medico (orden, nombre, apellido, esp, email, dni, telefono, matricula, login) VALUES
  (1, 'Dr. Juan', 'Pérez',   1, 'medico@turnero.com',               33333333, '+5492804333333', 'MP-12345', true),
  (2, 'María',    'García',  2, 'medico.pediatria@turnero.com',     33333334, '+5492804333334', 'MP-12346', true),
  (3, 'Ricardo',  'Benítez', 3, 'medico.traumatologia@turnero.com', 33333335, '+5492804333335', 'MP-12347', true),
  (4, 'Andrea',   'Suárez',  4, 'medico.clinica@turnero.com',       33333336, '+5492804333336', 'MP-12348', true);

WITH puestos AS (
  SELECT c.orden AS centro_orden, c.perfil, k.pos,
         row_number() OVER (ORDER BY c.orden, k.pos) AS rn
    FROM d_centro c
    CROSS JOIN LATERAL generate_series(1, c.n_medicos) AS k(pos)
), combos AS (
  SELECT n AS nombre, a AS apellido, row_number() OVER (ORDER BY md5('med|' || n || '|' || a)) AS rn
    FROM d_nombre CROSS JOIN d_apellido
   WHERE (n, a) NOT IN (('Juan', 'Pérez'), ('María', 'García'), ('Ricardo', 'Benítez'), ('Andrea', 'Suárez'))
)
INSERT INTO d_medico (orden, nombre, apellido, esp, login, centro_orden, pos)
SELECT 4 + p.rn, co.nombre, co.apellido,
       CASE p.perfil
         WHEN 'pediatrico' THEN CASE WHEN p.pos <= 2 THEN 2 ELSE 3 END
         WHEN 'cardio'     THEN CASE WHEN p.pos <= 2 THEN 1 ELSE 4 END
         ELSE 1 + (p.centro_orden + p.pos * 3) % 10
       END,
       false, p.centro_orden, p.pos
  FROM puestos p JOIN combos co ON co.rn = p.rn;

UPDATE d_medico
   SET email     = pg_temp.sin_acentos(nombre || '.' || apellido) || '@example.com',
       dni       = 16000000 + orden * 100003,
       telefono  = '+549280' || (4700000 + orden),
       matricula = 'MP-' || (20000 + orden * 37)
 WHERE NOT login;

-- Bloques semanales. Los médicos con login usan un patrón de varios días y
-- bloques de 3 horas; el resto atiende un bloque de 2 horas por semana (c0..c9).
--   B = martes y jueves 08-11        C = lunes y miércoles 14-17
--   D = martes y jueves 14-17        E = lunes, miércoles y viernes 08-11
CREATE TEMP TABLE d_bloque (patron text, dow int, dia text, hora_inicio time) ON COMMIT DROP;
INSERT INTO d_bloque VALUES
  ('B', 2, 'MARTES', '08:00'), ('B', 4, 'JUEVES',    '08:00'),
  ('C', 1, 'LUNES',  '14:00'), ('C', 3, 'MIERCOLES', '14:00'),
  ('D', 2, 'MARTES', '14:00'), ('D', 4, 'JUEVES',    '14:00'),
  ('E', 1, 'LUNES',  '08:00'), ('E', 3, 'MIERCOLES', '08:00'), ('E', 5, 'VIERNES', '08:00'),
  ('c0', 1, 'LUNES',  '08:00'), ('c1', 2, 'MARTES', '08:00'), ('c2', 3, 'MIERCOLES', '08:00'),
  ('c3', 4, 'JUEVES', '08:00'), ('c4', 5, 'VIERNES', '08:00'),
  ('c5', 1, 'LUNES',  '14:00'), ('c6', 2, 'MARTES', '14:00'), ('c7', 3, 'MIERCOLES', '14:00'),
  ('c8', 4, 'JUEVES', '14:00'), ('c9', 5, 'VIERNES', '14:00');

-- Plan de atención: qué médico atiende en qué centro, consultorio y bloques.
-- Los médicos con login usan el consultorio 1 de los centros originales; los
-- generados van al consultorio 2 en esos centros y al 1 en el resto.
-- con_agenda: si además de su disponibilidad tiene un esquema de turnos abierto,
-- o sea, horarios que los pacientes pueden reservar. La pantalla de turnos genera
-- en cada carga todos los horarios de todos los esquemas y su costo crece con la
-- cantidad de esquemas, así que por defecto se abre la agenda de un médico por
-- centro (-v agendas_por_centro=N para abrir más). El resto conserva su
-- disponibilidad y sus turnos.
CREATE TEMP TABLE d_plan (
  medico_orden int, centro_orden int, patron text, cons_pos int, minutos_bloque int, hora_offset int,
  con_agenda boolean
) ON COMMIT DROP;
INSERT INTO d_plan
SELECT m.orden, c.orden, v.patron, 1, 180, 0, true
  FROM (VALUES
    ('medico@turnero.com', 'santamaria', 'E'), ('medico@turnero.com', 'delsur', 'B'),
    ('medico@turnero.com', 'delsol', 'C'), ('medico.pediatria@turnero.com', 'santamaria', 'B'),
    ('medico.traumatologia@turnero.com', 'santamaria', 'C'), ('medico.clinica@turnero.com', 'santamaria', 'D')
  ) AS v(email, centro, patron)
  JOIN d_medico m ON m.email = v.email
  JOIN d_centro c ON c.slug = v.centro;
INSERT INTO d_plan
SELECT m.orden, m.centro_orden, 'c' || ((m.pos - 1 + m.centro_orden) % 10),
       CASE WHEN c.semilla THEN 2 ELSE 1 END, 120, m.orden % 3,
       m.pos <= (SELECT agendas_por_centro FROM d_param)
  FROM d_medico m JOIN d_centro c ON c.orden = m.centro_orden
 WHERE NOT m.login;

-- Cuentas de login de la demo (password "password"). Solo se crean las que falten.
CREATE TEMP TABLE d_user (
  email text, nombre text, apellido text, dni bigint, telefono text, role text, centro text
) ON COMMIT DROP;
INSERT INTO d_user VALUES
  ('superadmin@turnero.com',           'Super',    'Administrador', 10000000, '+5492804100000', 'SUPERADMIN',    NULL),
  ('admin.santamaria@turnero.com',     'Carlos',   'Rodríguez',     11111111, '+5492804111111', 'ADMINISTRADOR', 'santamaria'),
  ('admin.delsur@turnero.com',         'Laura',    'Fernández',     11111112, '+5492804111112', 'ADMINISTRADOR', 'delsur'),
  ('admin.delsol@turnero.com',         'Roberto',  'Sánchez',       11111113, '+5492804111113', 'ADMINISTRADOR', 'delsol'),
  ('operador1.santamaria@turnero.com', 'Ana',      'Martínez',      22222221, '+5492804222221', 'OPERADOR',      'santamaria'),
  ('operador2.santamaria@turnero.com', 'Pedro',    'López',         22222222, '+5492804222222', 'OPERADOR',      'santamaria'),
  ('operador1.delsur@turnero.com',     'Marta',    'Gómez',         22222223, '+5492804222223', 'OPERADOR',      'delsur'),
  ('operador2.delsur@turnero.com',     'Jorge',    'Díaz',          22222224, '+5492804222224', 'OPERADOR',      'delsur'),
  ('operador1.delsol@turnero.com',     'Silvia',   'Torres',        22222225, '+5492804222225', 'OPERADOR',      'delsol'),
  ('operador2.delsol@turnero.com',     'Miguel',   'Castro',        22222226, '+5492804222226', 'OPERADOR',      'delsol'),
  ('paciente1.santamaria@turnero.com', 'María',    'González',      44444441, '+5492804444441', 'PACIENTE',      'santamaria'),
  ('paciente2.santamaria@turnero.com', 'José',     'Ramírez',       44444442, '+5492804444442', 'PACIENTE',      'santamaria'),
  ('paciente1.delsur@turnero.com',     'Lucía',    'Morales',       44444443, '+5492804444443', 'PACIENTE',      'delsur'),
  ('paciente2.delsur@turnero.com',     'Fernando', 'Silva',         44444444, '+5492804444444', 'PACIENTE',      'delsur'),
  ('paciente1.delsol@turnero.com',     'Sofía',    'Vargas',        44444445, '+5492804444445', 'PACIENTE',      'delsol'),
  ('paciente2.delsol@turnero.com',     'Diego',    'Ortiz',         44444446, '+5492804444446', 'PACIENTE',      'delsol');
INSERT INTO d_user
SELECT email, nombre, apellido, dni, telefono, 'MEDICO', NULL FROM d_medico WHERE login;

-- Datos personales de los pacientes con login (para crear la ficha si falta).
CREATE TEMP TABLE d_pac_login (email text, fecha_nacimiento date, obra_social text) ON COMMIT DROP;
INSERT INTO d_pac_login VALUES
  ('paciente1.santamaria@turnero.com', '1985-03-15', 'OSDE001'),
  ('paciente2.santamaria@turnero.com', '1990-07-22', 'OSDE001'),
  ('paciente1.delsur@turnero.com',     '1988-11-30', 'SWISS001'),
  ('paciente2.delsur@turnero.com',     '1992-05-18', 'SWISS001'),
  ('paciente1.delsol@turnero.com',     '1995-01-12', 'GALENO001'),
  ('paciente2.delsol@turnero.com',     '1987-09-25', 'GALENO001');

-- =====================================
-- 2. Limpieza de los datos de dominio
-- =====================================

-- Para que el panel de un médico con sesión abierta no quede apuntando a un
-- staff que ya no existe, se reutiliza el id de cada par (médico, centro).
CREATE TEMP TABLE d_staff_previo ON COMMIT DROP AS
SELECT lower(m.email) AS medico_email, c.nombre AS centro_nombre, MIN(sm.id) AS id
  FROM staff_medico sm
  JOIN medico m ON m.id = sm.medico_id
  JOIN centro_atencion c ON c.id = sm.centro_atencion_id
 GROUP BY 1, 2;

-- Los JSON de auditoría viven como large objects: se liberan antes de borrar las filas.
DO $$
BEGIN
  PERFORM lo_unlink(m.oid)
     FROM pg_largeobject_metadata m
    WHERE m.oid IN (
          SELECT old_values FROM audit_log WHERE entity_type = 'TURNO' OR turno_id IS NOT NULL
          UNION
          SELECT new_values FROM audit_log WHERE entity_type = 'TURNO' OR turno_id IS NOT NULL);
END $$;

DELETE FROM encuesta_respuesta;
DELETE FROM encuesta_invitacion;
DELETE FROM audit_log WHERE entity_type = 'TURNO' OR turno_id IS NOT NULL;
DELETE FROM notificaciones;
DELETE FROM deep_link_token;
DELETE FROM lista_espera;
DELETE FROM configuracion_excepcional;
DELETE FROM turno;
DELETE FROM esquema_turno_horarios;
DELETE FROM esquema_turno;
DELETE FROM disponibilidad_medico_horarios;
DELETE FROM disponibilidad_medico;
DELETE FROM staff_medico;
DELETE FROM medico_especialidad;
DELETE FROM centro_especialidad;

DELETE FROM preferencia_horaria ph
 WHERE NOT EXISTS (SELECT 1 FROM paciente p JOIN users u ON lower(u.email) = lower(p.email)
                    WHERE p.id = ph.paciente_id);
DELETE FROM paciente p
 WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower(p.email));
DELETE FROM medico m
 WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = lower(m.email))
   AND NOT EXISTS (SELECT 1 FROM d_medico d WHERE d.email = lower(m.email));

-- =====================================
-- 2.1 Índices de apoyo
-- =====================================
-- Hibernate no indexa las claves foráneas. Sin estos índices la agenda recorre
-- toda la tabla de turnos por cada horario que muestra (decenas de segundos con
-- esta cantidad de datos). No cambian el comportamiento, solo los tiempos.

CREATE INDEX IF NOT EXISTS idx_turno_staff_fecha_hora ON turno (staff_medico_id, fecha, hora_inicio);
CREATE INDEX IF NOT EXISTS idx_turno_paciente ON turno (paciente_id);
CREATE INDEX IF NOT EXISTS idx_turno_centro_fecha ON turno (centro_atencion_id, fecha);
CREATE INDEX IF NOT EXISTS idx_turno_fecha ON turno (fecha);
CREATE INDEX IF NOT EXISTS idx_audit_turno ON audit_log (turno_id);
CREATE INDEX IF NOT EXISTS idx_encuesta_respuesta_turno ON encuesta_respuesta (turno_id);
CREATE INDEX IF NOT EXISTS idx_encuesta_invitacion_turno ON encuesta_invitacion (turno_id);
CREATE INDEX IF NOT EXISTS idx_notificaciones_paciente ON notificaciones (paciente_id);

-- =====================================
-- 3. Obras sociales, especialidades, centros y consultorios
-- =====================================

WITH faltan AS (
  SELECT d.*, row_number() OVER (ORDER BY d.orden) AS rn
    FROM d_os d
   WHERE NOT EXISTS (SELECT 1 FROM obra_social o WHERE o.codigo = d.codigo)
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('obra_social_seq', 'obra_social', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO obra_social (id, nombre, codigo, descripcion)
SELECT base.b + f.rn, f.nombre, f.codigo, f.descripcion FROM faltan f, base;

WITH faltan AS (
  SELECT d.*, row_number() OVER (ORDER BY d.orden) AS rn
    FROM d_esp d
   WHERE NOT EXISTS (SELECT 1 FROM especialidad e WHERE lower(e.nombre) = lower(d.nombre))
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('especialidad_seq', 'especialidad', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO especialidad (id, nombre, descripcion)
SELECT base.b + f.rn, f.nombre, f.descripcion FROM faltan f, base;

WITH faltan AS (
  SELECT d.*, row_number() OVER (ORDER BY d.orden) AS rn
    FROM d_centro d
   WHERE NOT EXISTS (SELECT 1 FROM centro_atencion c WHERE c.nombre = d.nombre)
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('centro_atencion_seq', 'centro_atencion', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO centro_atencion (id, nombre, direccion, localidad, provincia, telefono, latitud, longitud)
SELECT base.b + f.rn, f.nombre, f.direccion, f.localidad, f.provincia, f.telefono, f.latitud, f.longitud
  FROM faltan f, base;

ALTER TABLE d_centro ADD COLUMN id int;
ALTER TABLE d_esp ADD COLUMN id int;
UPDATE d_centro d SET id = c.id FROM centro_atencion c WHERE c.nombre = d.nombre;
UPDATE d_esp d
   SET id = (SELECT MIN(e.id) FROM especialidad e WHERE lower(e.nombre) = lower(d.nombre));

-- Consultorios: se completan hasta n_consultorios por centro.
INSERT INTO consultorio (numero, nombre, centro_atencion_id)
SELECT n.numero,
       CASE d.slug
         WHEN 'santamaria' THEN 'Consultorio ' || chr(64 + n.numero)
         WHEN 'delsur'     THEN 'Consultorio Sur ' || n.numero
         WHEN 'delsol'     THEN 'Consultorio Sol ' || n.numero
         ELSE 'Consultorio ' || n.numero
       END,
       d.id
  FROM d_centro d
  CROSS JOIN LATERAL generate_series(1, d.n_consultorios) AS n(numero)
 WHERE NOT EXISTS (SELECT 1 FROM consultorio c
                    WHERE c.centro_atencion_id = d.id AND c.numero = n.numero);

CREATE TEMP TABLE d_cons ON COMMIT DROP AS
SELECT *
  FROM (SELECT c.id, c.centro_atencion_id AS centro_id, c.nombre,
               row_number() OVER (PARTITION BY c.centro_atencion_id ORDER BY c.numero, c.id) AS pos
          FROM consultorio c
          JOIN d_centro d ON d.id = c.centro_atencion_id) x
 WHERE x.pos <= (SELECT d.n_consultorios FROM d_centro d WHERE d.id = x.centro_id);

DELETE FROM consultorio_horarios WHERE consultorio_id IN (SELECT id FROM d_cons);
INSERT INTO consultorio_horarios (consultorio_id, dia_semana, hora_apertura, hora_cierre, activo)
SELECT c.id, dia.d, '08:00', '18:00', true
  FROM d_cons c
  CROSS JOIN (VALUES ('LUNES'), ('MARTES'), ('MIERCOLES'), ('JUEVES'), ('VIERNES')) AS dia(d);

-- =====================================
-- 4. Encuesta base (la crea el backend al arrancar; se repone si falta)
-- =====================================

WITH tipos (tipo, texto, orden) AS (VALUES
  ('NPS',           'En una escala del 0 al 10, ¿qué tan probable es que recomiendes nuestro centro de salud a un amigo o familiar?', 1),
  ('CSAT',          '¿Cómo calificarías tu satisfacción general con la atención recibida hoy?', 2),
  ('RATING_ESPERA', '¿Cómo evaluarías el tiempo que tuviste que esperar para ser atendido?', 3),
  ('RATING_TRATO',  '¿Cómo calificarías el trato y la amabilidad del profesional que te atendió?', 4),
  ('TEXTO_LIBRE',   '¿Tenés alguna sugerencia o comentario adicional para ayudarnos a mejorar?', 5)
), faltan AS (
  SELECT t.*, row_number() OVER (ORDER BY t.orden) AS rn
    FROM tipos t
   WHERE NOT EXISTS (SELECT 1 FROM pregunta p WHERE p.tipo = t.tipo)
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('pregunta_seq', 'pregunta', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO pregunta (id, texto_pregunta, tipo)
SELECT base.b + f.rn, f.texto, f.tipo FROM faltan f, base;

WITH base AS MATERIALIZED (
  SELECT pg_temp.reservar('encuesta_plantilla_seq', 'encuesta_plantilla', 1) AS b
   WHERE NOT EXISTS (SELECT 1 FROM encuesta_plantilla WHERE nombre = 'Encuesta de Satisfacción General (Base)')
), nueva AS (
  INSERT INTO encuesta_plantilla (id, nombre, centro_atencion_id, especialidad_id)
  SELECT base.b + 1, 'Encuesta de Satisfacción General (Base)', NULL, NULL FROM base
  RETURNING id
)
INSERT INTO plantilla_pregunta (plantilla_id, pregunta_id)
SELECT nueva.id, p.id
  FROM nueva
  JOIN (SELECT MIN(id) AS id FROM pregunta GROUP BY tipo) p ON true;

CREATE TEMP TABLE d_pregunta ON COMMIT DROP AS
SELECT tipo, MIN(id) AS id FROM pregunta GROUP BY tipo;

-- =====================================
-- 5. Cuentas de login
-- =====================================

INSERT INTO users (nombre, apellido, dni, email, telefono, hashed_password, role, enabled,
                   account_non_expired, account_non_locked, credentials_non_expired,
                   email_verified, centro_atencion_id)
SELECT d.nombre, d.apellido, d.dni, d.email, d.telefono, p.hash_password, d.role, true,
       true, true, true, true,
       CASE WHEN d.role IN ('ADMINISTRADOR', 'OPERADOR') THEN c.id END
  FROM d_user d
  CROSS JOIN d_param p
  LEFT JOIN d_centro c ON c.slug = d.centro
 WHERE NOT EXISTS (SELECT 1 FROM users u
                    WHERE lower(u.email) = lower(d.email) OR u.dni = d.dni OR u.telefono = d.telefono);

INSERT INTO operador (nombre, apellido, dni, email, telefono, activo, centro_atencion_id)
SELECT d.nombre, d.apellido, d.dni, d.email, d.telefono, true, c.id
  FROM d_user d
  JOIN d_centro c ON c.slug = d.centro
 WHERE d.role = 'OPERADOR'
   AND NOT EXISTS (SELECT 1 FROM operador o
                    WHERE lower(o.email) = lower(d.email) OR o.dni = d.dni OR o.telefono = d.telefono);

-- =====================================
-- 6. Médicos
-- =====================================

WITH faltan AS (
  SELECT d.*, row_number() OVER (ORDER BY d.orden) AS rn
    FROM d_medico d
   WHERE NOT EXISTS (SELECT 1 FROM medico m WHERE lower(m.email) = d.email)
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('medico_seq', 'medico', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO medico (id, nombre, apellido, dni, email, telefono, matricula)
SELECT base.b + f.rn, f.nombre, f.apellido, f.dni, f.email, f.telefono, f.matricula
  FROM faltan f, base;

ALTER TABLE d_medico ADD COLUMN id int;
UPDATE d_medico d SET id = m.id FROM medico m WHERE lower(m.email) = d.email;

INSERT INTO medico_especialidad (medico_id, especialidad_id)
SELECT d.id, e.id FROM d_medico d JOIN d_esp e ON e.orden = d.esp;

-- =====================================
-- 7. Pacientes
-- =====================================

-- Fichas de los pacientes con login que no la tengan.
WITH faltan AS (
  SELECT u.nombre, u.apellido, u.dni, u.email, u.telefono, l.fecha_nacimiento, os.id AS obra_social_id,
         row_number() OVER (ORDER BY u.email) AS rn
    FROM d_pac_login l
    JOIN users u ON lower(u.email) = l.email
    LEFT JOIN obra_social os ON os.codigo = l.obra_social
   WHERE NOT EXISTS (SELECT 1 FROM paciente p
                      WHERE lower(p.email) = l.email OR p.dni = u.dni OR p.telefono = u.telefono)
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('paciente_seq', 'paciente', (SELECT count(*) FROM faltan)) AS b
)
INSERT INTO paciente (id, nombre, apellido, dni, email, telefono, fecha_nacimiento, profile_completed, obra_social_id)
SELECT base.b + f.rn, f.nombre, f.apellido, f.dni, f.email, f.telefono, f.fecha_nacimiento, true, f.obra_social_id
  FROM faltan f, base;

-- Pacientes generados: cada centro tiene su propio padrón, proporcional a la
-- cantidad de horarios que ofrece por semana (mínimo 15).
CREATE TEMP TABLE d_pac_n ON COMMIT DROP AS
WITH peso AS (
  SELECT pl.centro_orden,
         SUM(pl.minutos_bloque / e.intervalo * (SELECT count(*) FROM d_bloque b WHERE b.patron = pl.patron)) AS slots
    FROM d_plan pl
    JOIN d_medico m ON m.orden = pl.medico_orden
    JOIN d_esp e ON e.orden = m.esp
   GROUP BY pl.centro_orden
)
SELECT p.centro_orden,
       GREATEST(15, round(p.slots * (SELECT pacientes FROM d_param) / (SELECT SUM(slots) FROM peso)))::int AS n
  FROM peso p;

CREATE TEMP TABLE d_pac ON COMMIT DROP AS
WITH lugares AS (
  SELECT n.centro_orden, i.idx, row_number() OVER (ORDER BY n.centro_orden, i.idx) AS rk
    FROM d_pac_n n
    CROSS JOIN LATERAL generate_series(0, n.n - 1) AS i(idx)
), combos AS (
  SELECT n AS nombre, a AS apellido, row_number() OVER (ORDER BY md5(n || '|' || a)) AS rk
    FROM d_nombre CROSS JOIN d_apellido
)
SELECT l.rk::int AS rk, c.nombre, c.apellido, l.centro_orden, l.idx, NULL::int AS id
  FROM lugares l JOIN combos c ON c.rk = l.rk;

UPDATE d_pac d
   SET id = b.base + d.rk
  FROM (SELECT pg_temp.reservar('paciente_seq', 'paciente', (SELECT count(*) FROM d_pac)) AS base) b;

INSERT INTO paciente (id, nombre, apellido, dni, email, telefono, fecha_nacimiento, profile_completed, obra_social_id)
SELECT d.id, d.nombre, d.apellido,
       18000000 + d.rk * 12347,
       pg_temp.sin_acentos(d.nombre || '.' || d.apellido) || '@example.com',
       '+549280' || (4800000 + d.rk),
       date '1942-01-01' + floor(pg_temp.u('nac' || d.rk) * 27500)::int,
       true,
       (SELECT o.id
          FROM d_os x JOIN obra_social o ON o.codigo = x.codigo
         WHERE pg_temp.u('os' || d.rk) < x.hasta
         ORDER BY x.hasta LIMIT 1)
  FROM d_pac d;

CREATE INDEX ON d_pac (centro_orden, idx);

-- =====================================
-- 8. Staff médico, disponibilidades y esquemas de turno
-- =====================================

CREATE TEMP TABLE d_staff ON COMMIT DROP AS
SELECT m.orden AS medico_orden, m.id AS medico_id, m.email AS medico_email, m.login AS destacado,
       c.orden AS centro_orden, c.id AS centro_id, c.slug AS centro_slug,
       e.id AS especialidad_id, e.intervalo, pl.patron, pl.minutos_bloque, pl.hora_offset, pl.cons_pos, pl.con_agenda,
       row_number() OVER (ORDER BY m.orden, c.orden) AS rn,
       sp.id AS id_previo,
       NULL::int AS staff_id, NULL::int AS consultorio_id, NULL::int AS disponibilidad_id, NULL::int AS esquema_id,
       CASE WHEN m.orden = 1 AND c.orden = 1 THEN 0.84
            ELSE 0.58 + 0.26 * pg_temp.u('occ' || m.email || c.slug) END AS ocupacion
  FROM d_plan pl
  JOIN d_medico m ON m.orden = pl.medico_orden
  JOIN d_centro c ON c.orden = pl.centro_orden
  JOIN d_esp e ON e.orden = m.esp
  LEFT JOIN d_staff_previo sp ON sp.medico_email = m.email AND sp.centro_nombre = c.nombre;

UPDATE d_staff s SET consultorio_id = c.id
  FROM d_cons c WHERE c.centro_id = s.centro_id AND c.pos = s.cons_pos;

CREATE TEMP TABLE d_staff_bloque ON COMMIT DROP AS
SELECT s.rn, s.medico_id, s.consultorio_id, b.dow, b.dia,
       (b.hora_inicio + s.hora_offset * interval '1 hour')::time AS hora_inicio,
       (b.hora_inicio + s.hora_offset * interval '1 hour' + s.minutos_bloque * interval '1 minute')::time AS hora_fin
  FROM d_staff s JOIN d_bloque b ON b.patron = s.patron;

DO $$
BEGIN
  IF (SELECT count(*) FROM d_staff) <> (SELECT count(*) FROM d_plan) THEN
    RAISE EXCEPTION 'd_plan referencia un médico, centro o especialidad inexistente';
  END IF;
  IF EXISTS (SELECT 1 FROM d_staff WHERE consultorio_id IS NULL) THEN
    RAISE EXCEPTION 'Hay médicos asignados a un consultorio que el centro no tiene';
  END IF;
  IF EXISTS (SELECT 1 FROM d_staff_bloque x JOIN d_staff_bloque y
               ON x.rn < y.rn AND x.dow = y.dow
              AND x.hora_inicio < y.hora_fin AND y.hora_inicio < x.hora_fin
              AND (x.consultorio_id = y.consultorio_id OR x.medico_id = y.medico_id)) THEN
    RAISE EXCEPTION 'Dos bloques se pisan en el mismo consultorio o para el mismo médico';
  END IF;
END $$;

UPDATE d_staff s
   SET staff_id = COALESCE(s.id_previo, b.base + n.rn_nuevo)
  FROM (SELECT pg_temp.reservar('staff_medico_seq', 'staff_medico',
                                (SELECT count(*) FROM d_staff WHERE id_previo IS NULL),
                                (SELECT COALESCE(MAX(id_previo), 0) FROM d_staff)) AS base) b,
       (SELECT rn, row_number() OVER (ORDER BY rn) AS rn_nuevo FROM d_staff WHERE id_previo IS NULL
        UNION ALL
        SELECT rn, 0 FROM d_staff WHERE id_previo IS NOT NULL) n
 WHERE n.rn = s.rn;

UPDATE d_staff s SET disponibilidad_id = b.base + s.rn
  FROM (SELECT pg_temp.reservar('disponibilidad_medico_seq', 'disponibilidad_medico',
                                (SELECT count(*) FROM d_staff)) AS base) b;
UPDATE d_staff s SET esquema_id = b.base + s.rn
  FROM (SELECT pg_temp.reservar('esquema_turno_seq', 'esquema_turno',
                                (SELECT count(*) FROM d_staff)) AS base) b;

INSERT INTO staff_medico (id, porcentaje, centro_atencion_id, consultorio_id, especialidad_id, medico_id)
SELECT staff_id, NULL, centro_id, consultorio_id, especialidad_id, medico_id FROM d_staff;

INSERT INTO centro_especialidad (centro_id, especialidad_id)
SELECT DISTINCT centro_id, especialidad_id FROM d_staff;

INSERT INTO disponibilidad_medico (id, especialidad_id, staff_medico_id)
SELECT disponibilidad_id, especialidad_id, staff_id FROM d_staff ORDER BY rn;
INSERT INTO disponibilidad_medico_horarios (disponibilidad_medico_id, dia, hora_inicio, hora_fin)
SELECT s.disponibilidad_id, b.dia, b.hora_inicio, b.hora_fin
  FROM d_staff s JOIN d_staff_bloque b ON b.rn = s.rn;

INSERT INTO esquema_turno (id, intervalo, centro_atencion_id, consultorio_id, disponibilidad_medico_id, staff_medico_id)
SELECT esquema_id, intervalo, centro_id, consultorio_id, disponibilidad_id, staff_id FROM d_staff WHERE con_agenda ORDER BY rn;
INSERT INTO esquema_turno_horarios (esquema_turno_id, dia, hora_inicio, hora_fin)
SELECT s.esquema_id, b.dia, b.hora_inicio, b.hora_fin
  FROM d_staff s JOIN d_staff_bloque b ON b.rn = s.rn
 WHERE s.con_agenda;

-- =====================================
-- 9. Turnos (pasado, hoy y futuro)
-- =====================================

CREATE TEMP TABLE d_turno ON COMMIT DROP AS
WITH slots AS (
  SELECT s.staff_id, s.medico_id, s.centro_id, s.centro_orden, s.especialidad_id, s.consultorio_id,
         s.ocupacion, p.hoy, p.hora_actual, dd.d AS dias,
         p.hoy + dd.d AS fecha,
         (b.hora_inicio + (k.k * s.intervalo) * interval '1 minute')::time AS hora_inicio,
         (b.hora_inicio + ((k.k + 1) * s.intervalo) * interval '1 minute')::time AS hora_fin
    FROM d_staff s
    CROSS JOIN d_param p
    JOIN d_staff_bloque b ON b.rn = s.rn
    CROSS JOIN LATERAL generate_series(
           -7 * CASE WHEN s.destacado THEN p.semanas_destacados ELSE p.semanas_historia END,
           p.dias_futuro) AS dd(d)
    CROSS JOIN LATERAL generate_series(
           0, s.minutos_bloque / s.intervalo - 1) AS k(k)
   WHERE extract(isodow FROM p.hoy + dd.d) = b.dow
), ocupados AS (
  SELECT s.*,
         pg_temp.u('e' || s.staff_id || s.fecha || s.hora_inicio) AS r
    FROM slots s
   WHERE pg_temp.u('o' || s.staff_id || s.fecha || s.hora_inicio) <
         s.ocupacion * CASE
           WHEN s.dias < 0   THEN GREATEST(0.55, 1 + s.dias * 0.0025)   -- crece hacia el presente
           WHEN s.dias <= 7  THEN 0.90
           WHEN s.dias <= 14 THEN 0.62
           WHEN s.dias <= 21 THEN 0.38
           ELSE 0.20
         END
), con_estado AS (
  SELECT o.*,
         CASE
           WHEN o.dias < 0 OR (o.dias = 0 AND o.hora_fin <= o.hora_actual) THEN
             CASE WHEN o.r < 0.77 THEN 'COMPLETO' WHEN o.r < 0.86 THEN 'AUSENTE' ELSE 'CANCELADO' END
           -- Dentro de las próximas 48 hs el sistema cancela solo lo que siga PROGRAMADO.
           -- y a los 2 días de anticipación también lo REAGENDADO: lo cercano queda confirmado.
           WHEN o.dias <= 4 THEN
             CASE WHEN o.r < 0.89 THEN 'CONFIRMADO' ELSE 'CANCELADO' END
           ELSE
             CASE WHEN o.r < 0.55 THEN 'CONFIRMADO' WHEN o.r < 0.85 THEN 'PROGRAMADO'
                  WHEN o.r < 0.89 THEN 'REAGENDADO' ELSE 'CANCELADO' END
         END AS estado,
         row_number() OVER (PARTITION BY o.centro_id, o.fecha, o.hora_inicio ORDER BY o.staff_id) AS rn_hora,
         row_number() OVER (ORDER BY o.fecha, o.hora_inicio, o.staff_id) AS rn
    FROM ocupados o
)
SELECT c.*, NULL::int AS id, NULL::int AS paciente_id FROM con_estado c;

UPDATE d_turno t SET id = b.base + t.rn
  FROM (SELECT pg_temp.reservar('turno_seq', 'turno', (SELECT count(*) FROM d_turno)) AS base) b;

-- Cada turno toma un paciente del padrón de su centro; dentro de una misma fecha
-- y hora cada paciente aparece una sola vez.
UPDATE d_turno t SET paciente_id = dp.id
  FROM d_pac dp, d_pac_n n
 WHERE n.centro_orden = t.centro_orden
   AND dp.centro_orden = t.centro_orden
   AND dp.idx = (floor(pg_temp.u('p' || t.centro_orden || t.fecha || t.hora_inicio) * n.n)::int
                 + t.rn_hora::int) % n.n;

-- Los pacientes con login reciben historial y próximos turnos en su centro:
-- uno cada ~9 días, repartidos entre los dos pacientes de cada centro.
CREATE TEMP TABLE d_pac_demo ON COMMIT DROP AS
SELECT pa.id AS paciente_id, lower(pa.email) AS email, c.id AS centro_id,
       row_number() OVER (PARTITION BY c.id ORDER BY pa.email) - 1 AS k
  FROM d_user du
  JOIN paciente pa ON lower(pa.email) = du.email
  JOIN d_centro c ON c.slug = du.centro
 WHERE du.role = 'PACIENTE';

WITH candidatos AS (
  SELECT DISTINCT ON (pd.paciente_id, floor((t.dias + 1000) / 9.0))
         t.id, pd.paciente_id
    FROM d_pac_demo pd
    JOIN d_turno t ON t.centro_id = pd.centro_id
   WHERE (t.id % 2) = pd.k
     AND t.dias >= -91
   ORDER BY pd.paciente_id, floor((t.dias + 1000) / 9.0), md5(t.id::text || pd.email)
)
UPDATE d_turno t SET paciente_id = c.paciente_id
  FROM candidatos c WHERE c.id = t.id;

-- Sus turnos futuros quedan confirmados: el backend manda recordatorios y avisos
-- de cancelación por mail a los turnos sin confirmar, y estas cuentas usan un
-- dominio (@turnero.com) que no es del proyecto.
UPDATE d_turno t
   SET estado = 'CONFIRMADO'
  FROM d_pac_demo pd
 WHERE pd.paciente_id = t.paciente_id
   AND (t.dias > 0 OR (t.dias = 0 AND t.hora_fin > t.hora_actual))
   AND (t.estado IN ('PROGRAMADO', 'REAGENDADO')
        OR t.id = (SELECT x.id FROM d_turno x
                    WHERE x.paciente_id = t.paciente_id AND x.dias > 0
                    ORDER BY x.fecha, x.hora_inicio LIMIT 1));

INSERT INTO turno (id, estado, fecha, hora_inicio, hora_fin, asistio, observaciones,
                   centro_atencion_id, consultorio_id, especialidad_id, medico_id, paciente_id, staff_medico_id)
SELECT t.id, t.estado, t.fecha, t.hora_inicio, t.hora_fin,
       CASE t.estado WHEN 'COMPLETO' THEN true WHEN 'AUSENTE' THEN false END,
       CASE WHEN t.estado = 'COMPLETO' AND pg_temp.u('obs' || t.id) < 0.28 THEN
         (ARRAY['Control anual sin novedades.', 'Se solicita laboratorio de rutina.',
                'Paciente en seguimiento. Próximo control en 3 meses.', 'Se indica tratamiento y reposo.',
                'Se deriva a estudios complementarios.', 'Evolución favorable.',
                'Se ajusta medicación.', 'Primera consulta. Se abre historia clínica.',
                'Trae estudios solicitados. Sin hallazgos relevantes.', 'Se renueva receta.']
         )[1 + floor(pg_temp.u('obs2' || t.id) * 10)::int]
       END,
       t.centro_id, t.consultorio_id, t.especialidad_id, t.medico_id, t.paciente_id, t.staff_id
  FROM d_turno t;

-- =====================================
-- 10. Auditoría de turnos
-- =====================================
-- El tablero de calidad recorre toda la auditoría y carga cada turno por
-- separado, así que se audita una muestra de turnos recientes y próximos (con su
-- recorrido completo) más todos los turnos de los pacientes con login.
-- =====================================

CREATE TEMP TABLE d_aud ON COMMIT DROP AS
SELECT t.id, t.estado, t.fecha, t.hora_inicio, t.hora_fin, t.dias,
       lower(pa.email) AS paciente_email,
       COALESCE((SELECT lower(u.email) FROM users u
                  WHERE u.role = 'OPERADOR' AND u.centro_atencion_id = t.centro_id
                  ORDER BY u.id LIMIT 1), 'superadmin@turnero.com') AS operador_email,
       pg_temp.u('a1' || t.id) AS r1, pg_temp.u('a2' || t.id) AS r2, pg_temp.u('a3' || t.id) AS r3,
       -- Alta del turno: entre 2 y 20 días antes (los futuros, en los últimos 10 días).
       CASE WHEN t.dias > 0
            THEN p.ahora - (0.2 + pg_temp.u('a4' || t.id) * 9.5) * interval '1 day'
            ELSE (t.fecha + t.hora_inicio) - (2 + pg_temp.u('a4' || t.id) * 18) * interval '1 day'
       END AS creado,
       LEAST(p.ahora, t.fecha + t.hora_inicio) AS tope,
       p.ahora
  FROM d_turno t
  JOIN paciente pa ON pa.id = t.paciente_id
  CROSS JOIN d_param p
 WHERE t.id IN (
         (SELECT x.id FROM d_turno x WHERE x.dias BETWEEN -5 AND 10
           ORDER BY md5('aud' || x.id) LIMIT (SELECT max_turnos_auditados FROM d_param))
         UNION
         SELECT x.id FROM d_turno x JOIN d_pac_demo pd ON pd.paciente_id = x.paciente_id);

INSERT INTO audit_log (action, entity_id, entity_type, estado_anterior, estado_nuevo,
                       performed_at, performed_by, reason, turno_id)
SELECT 'CREATE', a.id, 'TURNO', NULL, 'PROGRAMADO', date_trunc('second', a.creado),
       CASE WHEN a.r1 < 0.6 THEN a.paciente_email ELSE a.operador_email END, NULL, a.id
  FROM d_aud a
UNION ALL
SELECT 'CONFIRM', a.id, 'TURNO', 'PROGRAMADO', 'CONFIRMADO',
       date_trunc('second', a.creado + (a.tope - a.creado) * (0.25 + 0.5 * a.r2)),
       CASE WHEN a.r2 < 0.75 THEN a.paciente_email ELSE a.operador_email END, NULL, a.id
  FROM d_aud a WHERE a.estado IN ('CONFIRMADO', 'COMPLETO', 'AUSENTE')
UNION ALL
SELECT 'COMPLETE', a.id, 'TURNO', 'CONFIRMADO', 'COMPLETO',
       date_trunc('second', LEAST(a.ahora, a.fecha + a.hora_fin + (2 + a.r3 * 30) * interval '1 minute')),
       a.operador_email, NULL, a.id
  FROM d_aud a WHERE a.estado = 'COMPLETO'
UNION ALL
SELECT 'UPDATE_STATUS', a.id, 'TURNO', 'CONFIRMADO', 'AUSENTE',
       date_trunc('second', LEAST(a.ahora, a.fecha + a.hora_fin + (15 + a.r3 * 30) * interval '1 minute')),
       a.operador_email, 'El paciente no se presentó', a.id
  FROM d_aud a WHERE a.estado = 'AUSENTE'
UNION ALL
SELECT 'CANCEL', a.id, 'TURNO', CASE WHEN a.r2 < 0.5 THEN 'PROGRAMADO' ELSE 'CONFIRMADO' END, 'CANCELADO',
       date_trunc('second', a.creado + (a.tope - a.creado) * (0.3 + 0.6 * a.r3)),
       CASE WHEN a.r1 < 0.65 THEN a.paciente_email ELSE a.operador_email END,
       (ARRAY['El paciente no puede asistir', 'Viaje imprevisto', 'Motivos laborales',
              'El paciente se encuentra enfermo', 'Solicita otro profesional',
              'Reprogramación de agenda del profesional']
       )[1 + floor(a.r3 * 6)::int], a.id
  FROM d_aud a WHERE a.estado = 'CANCELADO'
UNION ALL
SELECT 'RESCHEDULE', a.id, 'TURNO', 'PROGRAMADO', 'REAGENDADO',
       date_trunc('second', a.creado + (a.tope - a.creado) * (0.3 + 0.6 * a.r3)),
       CASE WHEN a.r1 < 0.5 THEN a.paciente_email ELSE a.operador_email END,
       (ARRAY['Reprogramado a pedido del paciente', 'Cambio de horario del profesional',
              'Superposición con otro compromiso del paciente']
       )[1 + floor(a.r3 * 3)::int], a.id
  FROM d_aud a WHERE a.estado = 'REAGENDADO'
ORDER BY 6;

-- =====================================
-- 11. Encuestas de satisfacción
-- =====================================

CREATE TEMP TABLE d_enc ON COMMIT DROP AS
SELECT x.*, row_number() OVER (ORDER BY x.id) AS rn
  FROM (SELECT t.id, t.paciente_id, t.centro_id, t.fecha, t.hora_fin, p.ahora,
               -- Calidad percibida: cada centro tiene su propio nivel.
               CASE WHEN pg_temp.u('q' || t.id) < 0.66 + 0.26 * pg_temp.u('qc' || t.centro_id) THEN 'alta'
                    WHEN pg_temp.u('q' || t.id) < 0.90 + 0.08 * pg_temp.u('qc' || t.centro_id) THEN 'media'
                    ELSE 'baja' END AS calidad,
               pg_temp.u('n' || t.id) AS r1, pg_temp.u('c' || t.id) AS r2,
               pg_temp.u('w' || t.id) AS r3, pg_temp.u('t' || t.id) AS r4,
               pg_temp.u('x' || t.id) AS r5
          FROM d_turno t CROSS JOIN d_param p
         WHERE t.estado = 'COMPLETO' AND t.dias BETWEEN -75 AND -1
         ORDER BY md5('enc' || t.id)
         LIMIT (SELECT max_encuestas FROM d_param)) x;

ALTER TABLE d_enc ADD COLUMN respondida timestamp;
UPDATE d_enc SET respondida =
  date_trunc('second', LEAST(ahora, fecha + hora_fin + (2 + r5 * 58) * interval '1 hour'));

WITH base AS MATERIALIZED (
  SELECT pg_temp.reservar('encuesta_respuesta_seq', 'encuesta_respuesta', (SELECT count(*) * 5 FROM d_enc)) AS b
), resp AS (
  SELECT e.rn, 1 AS n, 'NPS' AS tipo, e.id, e.paciente_id, e.respondida,
         CASE e.calidad WHEN 'alta' THEN 9 + floor(e.r1 * 2)::int
                        WHEN 'media' THEN 6 + floor(e.r1 * 3)::int
                        ELSE 2 + floor(e.r1 * 4)::int END AS valor, NULL::text AS texto
    FROM d_enc e
  UNION ALL
  SELECT e.rn, 2, 'CSAT', e.id, e.paciente_id, e.respondida,
         CASE e.calidad WHEN 'alta' THEN 4 + floor(e.r2 * 2)::int
                        WHEN 'media' THEN 3 + floor(e.r2 * 2)::int
                        ELSE 2 + floor(e.r2 * 2)::int END, NULL
    FROM d_enc e
  UNION ALL
  SELECT e.rn, 3, 'RATING_ESPERA', e.id, e.paciente_id, e.respondida,
         CASE e.calidad WHEN 'alta' THEN 3 + floor(e.r3 * 3)::int
                        WHEN 'media' THEN 3 + floor(e.r3 * 2)::int
                        ELSE 1 + floor(e.r3 * 2)::int END, NULL
    FROM d_enc e
  UNION ALL
  SELECT e.rn, 4, 'RATING_TRATO', e.id, e.paciente_id, e.respondida,
         CASE e.calidad WHEN 'alta' THEN 5
                        WHEN 'media' THEN 4 + floor(e.r4 * 2)::int
                        ELSE 3 + floor(e.r4 * 2)::int END, NULL
    FROM d_enc e
  UNION ALL
  SELECT e.rn, 5, 'TEXTO_LIBRE', e.id, e.paciente_id, e.respondida, NULL,
         CASE e.calidad
           WHEN 'alta' THEN (ARRAY[
             'Excelente atención, muy amable el profesional.', 'Me atendieron puntual y me explicaron todo con claridad.',
             'Muy conforme con la atención recibida.', 'El médico se tomó el tiempo para escuchar todas mis dudas.',
             'Muy buena experiencia, sacar el turno por la web fue muy simple.', 'Todo perfecto, gracias.',
             'Instalaciones limpias y personal muy cordial.', 'Recomendable, volvería a atenderme acá.'
           ])[1 + floor(e.r4 * 8)::int]
           WHEN 'media' THEN (ARRAY[
             'Buena atención, aunque tuve que esperar un poco.', 'La consulta estuvo bien pero fue algo corta.',
             'Bien en general. Faltaría más señalización para encontrar el consultorio.',
             'El profesional muy bien, la espera en recepción se hizo larga.',
             'Correcto. Sería bueno recibir un recordatorio el mismo día.',
             'Conforme, pero costó conseguir turno con poca anticipación.'
           ])[1 + floor(e.r4 * 6)::int]
           ELSE (ARRAY[
             'Esperé más de una hora para ser atendido.', 'La atención fue apurada, no me explicaron el tratamiento.',
             'Me cambiaron el consultorio y nadie avisó.', 'Poca predisposición en la recepción.',
             'El turno se atrasó mucho y no dieron explicaciones.', 'No quedé conforme con la consulta.'
           ])[1 + floor(e.r4 * 6)::int]
         END
    FROM d_enc e
   WHERE e.r5 < CASE e.calidad WHEN 'alta' THEN 0.10 WHEN 'media' THEN 0.22 ELSE 0.60 END
)
INSERT INTO encuesta_respuesta (id, fecha_creacion, valor_numerico, valor_texto, paciente_id, pregunta_id, turno_id)
SELECT base.b + (r.rn - 1) * 5 + r.n, r.respondida, r.valor, r.texto, r.paciente_id, q.id, r.id
  FROM resp r
  JOIN d_pregunta q ON q.tipo = r.tipo
  CROSS JOIN base;

-- Invitaciones: las respondidas quedan COMPLETADA y otras tantas ENVIADA sin
-- respuesta. Ninguna queda PENDIENTE, así el backend no intenta enviarlas.
CREATE TEMP TABLE d_inv ON COMMIT DROP AS
SELECT x.*, row_number() OVER (ORDER BY x.turno_id) AS rn
  FROM (SELECT e.id AS turno_id, e.paciente_id, 'COMPLETADA' AS estado, e.fecha + e.hora_fin AS creada
          FROM d_enc e
        UNION ALL
        (SELECT t.id, t.paciente_id, 'ENVIADA', t.fecha + t.hora_fin
           FROM d_turno t
          WHERE t.estado = 'COMPLETO' AND t.dias BETWEEN -30 AND -1
            AND NOT EXISTS (SELECT 1 FROM d_enc e WHERE e.id = t.id)
          ORDER BY md5('inv' || t.id)
          LIMIT (SELECT max_encuestas / 2 FROM d_param))) x;

INSERT INTO encuesta_invitacion (id, estado, fecha_creacion, fecha_envio, fecha_proximo_reintento,
                                 intentos_envio, intervalo_reintento_horas, max_intentos_reintento,
                                 ultimo_error, paciente_id, turno_id)
SELECT b.base + i.rn, i.estado, i.creada, i.creada + interval '2 hours', NULL, 1, 24, 2, NULL,
       i.paciente_id, i.turno_id
  FROM d_inv i,
       (SELECT pg_temp.reservar('encuesta_invitacion_seq', 'encuesta_invitacion',
                                (SELECT count(*) FROM d_inv)) AS base) b;

-- =====================================
-- 12. Lista de espera
-- =====================================

WITH items AS (
  SELECT g.i, s.centro_id, s.especialidad_id, s.medico_id, s.centro_orden,
         pg_temp.u('le1' || g.i) AS r1, pg_temp.u('le2' || g.i) AS r2,
         pg_temp.u('le3' || g.i) AS r3, pg_temp.u('le4' || g.i) AS r4
    FROM generate_series(1, 90) AS g(i)
    JOIN d_staff s ON s.rn = 1 + floor(pg_temp.u('le0' || g.i) * (SELECT count(*) FROM d_staff))::int
), base AS MATERIALIZED (
  SELECT pg_temp.reservar('lista_espera_seq', 'lista_espera', 90) AS b
)
INSERT INTO lista_espera (id, estado, fecha_deseada_desde, fecha_deseada_hasta, fecha_solicitud,
                          urgencia_medica, centro_atencion_id, especialidad_id, medico_preferido_id, paciente_id)
SELECT base.b + it.i,
       CASE WHEN it.r1 < 0.72 THEN 'PENDIENTE' WHEN it.r1 < 0.86 THEN 'RESUELTA' ELSE 'CUBIERTA' END,
       p.hoy + 1 + floor(it.r2 * 10)::int,
       p.hoy + 12 + floor(it.r2 * 10)::int + floor(it.r3 * 20)::int,
       date_trunc('minute', p.ahora - (0.1 + it.r3 * 34) * interval '1 day'),
       CASE WHEN it.r4 < 0.34 THEN 'BAJA' WHEN it.r4 < 0.70 THEN 'MEDIA'
            WHEN it.r4 < 0.90 THEN 'ALTA' ELSE 'URGENTE' END,
       it.centro_id,
       it.especialidad_id,
       CASE WHEN it.r3 < 0.35 THEN it.medico_id END,
       dp.id
  FROM items it
  CROSS JOIN d_param p
  CROSS JOIN base
  JOIN d_pac_n n ON n.centro_orden = it.centro_orden
  JOIN d_pac dp ON dp.centro_orden = it.centro_orden AND dp.idx = (it.i * 13) % n.n;

-- =====================================
-- 13. Notificaciones de los pacientes con login
-- =====================================

INSERT INTO notificaciones (fecha_creacion, fecha_leida, leida, mensaje, paciente_id, tipo, titulo, turno_id, usuario_creador)
SELECT n.creada,
       CASE WHEN n.leida THEN n.creada + interval '3 hours' END,
       n.leida, n.mensaje, n.paciente_id, n.tipo, n.titulo, n.turno_id, 'SISTEMA'
  FROM (
    SELECT t.paciente_id, t.id AS turno_id, x.tipo, x.titulo,
           format(x.plantilla, to_char(t.fecha, 'DD/MM/YYYY'), to_char(t.hora_inicio, 'HH24:MI'),
                  m.nombre || ' ' || m.apellido, c.nombre) AS mensaje,
           date_trunc('minute', LEAST(p.ahora - interval '10 minutes',
                                      CASE WHEN t.dias > 0 THEN p.ahora - (1 + pg_temp.u('nt' || t.id || x.tipo) * 6) * interval '1 day'
                                           ELSE (t.fecha + t.hora_fin) + x.despues END)) AS creada,
           (t.dias < -3 OR pg_temp.u('nl' || t.id || x.tipo) < 0.4) AS leida
      FROM d_turno t
      JOIN d_pac_demo pd ON pd.paciente_id = t.paciente_id
      JOIN medico m ON m.id = t.medico_id
      JOIN centro_atencion c ON c.id = t.centro_id
      CROSS JOIN d_param p
      JOIN (VALUES
        ('CONFIRMADO', 1, 'CONFIRMACION',       'Turno confirmado',
         'Tu turno del %s a las %s con %s en %s fue confirmado.', interval '0'),
        ('PROGRAMADO', 1, 'NUEVO_TURNO',        'Nuevo turno solicitado',
         'Registramos tu turno del %s a las %s con %s en %s. Recordá confirmarlo.', interval '0'),
        ('REAGENDADO', 1, 'REAGENDAMIENTO',     'Turno reagendado',
         'Tu turno fue reagendado para el %s a las %s con %s en %s.', interval '0'),
        ('CANCELADO',  0, 'CANCELACION',        'Turno cancelado',
         'Se canceló tu turno del %s a las %s con %s en %s.', interval '-2 days'),
        ('COMPLETO',   0, 'ENCUESTA_PENDIENTE', 'Contanos cómo te fue',
         'Tu opinión sobre el turno del %s a las %s con %s en %s nos ayuda a mejorar.', interval '2 hours')
      ) AS x(estado, futuro, tipo, titulo, plantilla, despues)
        ON x.estado = t.estado
       AND ((x.futuro = 1 AND t.dias > 0) OR (x.futuro = 0 AND t.dias BETWEEN -45 AND -1))
  ) n
 ORDER BY n.creada;

-- =====================================
-- 14. Verificaciones
-- =====================================

DO $$
DECLARE
  v_faltan text;
BEGIN
  SELECT string_agg(d.email, ', ') INTO v_faltan
    FROM d_user d
   WHERE NOT EXISTS (SELECT 1 FROM users u WHERE lower(u.email) = d.email AND u.role = d.role);
  IF v_faltan IS NOT NULL THEN
    RAISE EXCEPTION 'No se pudieron asegurar estas cuentas (email, DNI o teléfono en uso por otra): %', v_faltan;
  END IF;
  IF EXISTS (SELECT 1 FROM d_turno WHERE paciente_id IS NULL) THEN
    RAISE EXCEPTION 'Quedaron turnos sin paciente';
  END IF;
  IF EXISTS (SELECT 1 FROM turno GROUP BY paciente_id, fecha, hora_inicio HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Un paciente quedó con dos turnos a la misma hora';
  END IF;
  IF EXISTS (SELECT 1 FROM turno WHERE estado <> 'CANCELADO'
              GROUP BY staff_medico_id, fecha, hora_inicio HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Un médico quedó con dos turnos a la misma hora';
  END IF;
END $$;

-- Sin estadísticas actualizadas el planificador no usa los índices de 2.1.
ANALYZE turno;
ANALYZE audit_log;
ANALYZE paciente;
ANALYZE staff_medico;
ANALYZE esquema_turno;
ANALYZE esquema_turno_horarios;
ANALYZE disponibilidad_medico;
ANALYZE encuesta_respuesta;
ANALYZE encuesta_invitacion;
ANALYZE notificaciones;
ANALYZE lista_espera;

COMMIT;

-- =====================================
-- Resumen
-- =====================================
\pset footer off
SELECT 'centros' AS dato, count(*) AS cantidad FROM centro_atencion
UNION ALL SELECT 'consultorios', count(*) FROM consultorio
UNION ALL SELECT 'especialidades', count(*) FROM especialidad
UNION ALL SELECT 'obras sociales', count(*) FROM obra_social
UNION ALL SELECT 'médicos', count(*) FROM medico
UNION ALL SELECT 'staff médico (médico en un centro)', count(*) FROM staff_medico
UNION ALL SELECT 'esquemas de turno', count(*) FROM esquema_turno
UNION ALL SELECT 'pacientes', count(*) FROM paciente
UNION ALL SELECT 'turnos', count(*) FROM turno
UNION ALL SELECT 'turnos pasados', count(*) FROM turno WHERE fecha < (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
UNION ALL SELECT 'turnos de hoy', count(*) FROM turno WHERE fecha = (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
UNION ALL SELECT 'turnos futuros', count(*) FROM turno WHERE fecha > (now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
UNION ALL SELECT 'auditoría de turnos', count(*) FROM audit_log WHERE entity_type = 'TURNO'
UNION ALL SELECT 'encuestas respondidas', count(DISTINCT turno_id) FROM encuesta_respuesta
UNION ALL SELECT 'lista de espera', count(*) FROM lista_espera
UNION ALL SELECT 'notificaciones', count(*) FROM notificaciones
UNION ALL SELECT 'usuarios', count(*) FROM users;

SELECT estado, count(*) AS turnos, min(fecha) AS desde, max(fecha) AS hasta
  FROM turno GROUP BY estado ORDER BY 2 DESC;

-- Datos demo de historia clínica para los pacientes del médico con id 1 (Dr. Juan Pérez).
-- Idempotente: no duplica consultas, diagnósticos ni perfiles si se corre dos veces.
-- Uso: psql -U <usuario> -d turnerodb -v ON_ERROR_STOP=1 < poblar-hce-demo.sql
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE pj ON COMMIT DROP AS
  SELECT DISTINCT paciente_id AS pid FROM turno WHERE medico_id = 1 AND estado <> 'CANCELADO';

CREATE TEMP TABLE tpl (n int, code text, motivo text, anamnesis text, examen text, plan text, indicaciones text) ON COMMIT DROP;
INSERT INTO tpl VALUES
 (0,'I10','Control de presión arterial','Paciente hipertenso en seguimiento. Refiere buena adherencia al tratamiento, sin cefalea ni mareos. Trae registros domiciliarios dentro de rango.','Buen estado general. Ruidos cardíacos normofonéticos, sin soplos. Pulsos periféricos presentes y simétricos. Sin edemas.','Se mantiene esquema antihipertensivo. Se solicita laboratorio de rutina con función renal e ionograma.','Continuar con medicación habitual. Dieta hiposódica. Control de presión en domicilio tres veces por semana.'),
 (1,'I11','Disnea de esfuerzo','Refiere disnea a esfuerzos moderados de un mes de evolución. Niega dolor precordial, palpitaciones y síncope. Antecedente de hipertensión de larga data.','Ritmo regular. Choque de punta desplazado. Sin ingurgitación yugular. Murmullo vesicular conservado, sin rales.','Se solicita ecocardiograma Doppler y electrocardiograma. Se ajusta tratamiento antihipertensivo.','Restricción de sodio. Actividad física suave. Consultar por guardia ante disnea de reposo o edemas.'),
 (2,'I25','Control de cardiopatía isquémica','Paciente con enfermedad coronaria crónica estable. Sin angor en los últimos meses. Tolera caminatas de treinta minutos sin síntomas.','Ruidos cardíacos normales. Sin signos de insuficiencia cardíaca. Tensión arterial controlada.','Se mantiene antiagregación y estatina. Se solicita ergometría de control.','No suspender la medicación. Mantener actividad aeróbica regular. Control en tres meses con estudios.'),
 (3,'I50','Seguimiento de insuficiencia cardíaca','Paciente con insuficiencia cardíaca en clase funcional II. Refiere leve aumento de edemas vespertinos. Sin ortopnea ni disnea paroxística nocturna.','Edema maleolar leve bilateral. Sin rales crepitantes. Ritmo regular, sin tercer ruido.','Se ajusta dosis de diurético. Se solicita laboratorio con función renal y péptido natriurético.','Control diario de peso. Restricción hídrica a un litro y medio. Consultar si aumenta más de dos kilos en tres días.'),
 (4,'E78','Control de dislipidemia','Concurre con laboratorio: colesterol total y LDL por encima del objetivo. Sin síntomas. Refiere dieta irregular.','Examen cardiovascular sin particularidades. Índice de masa corporal en rango de sobrepeso.','Se inicia tratamiento con estatina. Nuevo perfil lipídico y hepatograma en tres meses.','Dieta baja en grasas saturadas. Actividad física ciento cincuenta minutos por semana.'),
 (5,'R42','Mareos y palpitaciones','Refiere episodios de mareo y palpitaciones de corta duración, sin pérdida de conocimiento. Los relaciona con situaciones de estrés.','Ritmo regular al momento del examen. Sin soplos. Tensión arterial sin cambios ortostáticos.','Se solicita Holter de veinticuatro horas y laboratorio con perfil tiroideo.','Reducir cafeína. Registrar los episodios con fecha y duración. Consultar por guardia ante síncope.'),
 (6,'Z00','Chequeo cardiovascular','Consulta para control de salud. Asintomático. Sin antecedentes cardiovasculares personales de relevancia.','Examen físico normal. Ruidos cardíacos normofonéticos. Pulsos conservados.','Se solicita electrocardiograma y laboratorio de rutina. Riesgo cardiovascular global bajo.','Mantener hábitos saludables. Control anual.'),
 (7,'E11','Control de riesgo cardiovascular en diabetes','Paciente con diabetes tipo 2 en tratamiento. Refiere glucemias en ayunas aceptables. Sin dolor precordial ni claudicación.','Pulsos pedios presentes. Sin lesiones en pies. Examen cardiovascular normal.','Se solicita hemoglobina glicosilada, microalbuminuria y perfil lipídico. Se refuerza prevención cardiovascular.','Continuar con medicación. Plan alimentario indicado por nutrición. Control oftalmológico anual.'),
 (8,'E66','Evaluación previa a actividad física','Paciente con obesidad que desea iniciar actividad física. Sin síntomas cardiovasculares. Sedentario.','Índice de masa corporal elevado. Tensión arterial en límite superior. Resto del examen normal.','Se solicita ergometría y electrocardiograma. Apto condicional hasta resultado de estudios.','Iniciar con caminatas progresivas. Derivación a nutrición.'),
 (9,'I10','Ajuste de medicación antihipertensiva','Refiere registros domiciliarios elevados en las últimas dos semanas. Cefalea occipital ocasional. Buena adherencia.','Tensión arterial elevada en consultorio en dos tomas. Fondo de ojo no evaluado. Sin signos de daño de órgano blanco al examen.','Se aumenta dosis de antihipertensivo y se agrega segundo fármaco. Control en cuatro semanas.','Tomar la medicación siempre en el mismo horario. Traer registro de presión a la próxima consulta.');

-- 1) Una consulta cerrada por cada turno atendido (COMPLETO) con Juan Pérez
INSERT INTO hce_consulta (creada_en, actualizada_en, cerrada_en, estado, motivo_consulta, anamnesis, examen_fisico, plan, indicaciones,
                          frecuencia_cardiaca, tension_sistolica, tension_diastolica, peso_kg, talla_cm, imc, temperatura, proximo_control,
                          centro_atencion_id, especialidad_id, medico_id, paciente_id, turno_id)
SELECT ts, ts + interval '20 minutes', ts + interval '20 minutes', 'CERRADA', tpl.motivo, tpl.anamnesis, tpl.examen, tpl.plan, tpl.indicaciones,
       60 + (t.id * 13) % 35, 110 + (t.id * 17) % 45, 65 + (t.id * 11) % 30,
       v.peso, v.talla, round((v.peso / ((v.talla / 100.0) ^ 2))::numeric, 1), 36.0 + ((t.id * 3) % 10) / 10.0,
       CASE WHEN t.id % 3 <> 0 THEN t.fecha + 90 END,
       t.centro_atencion_id, t.especialidad_id, 1, t.paciente_id, t.id
FROM turno t
JOIN tpl ON tpl.n = (t.id * 7 + t.paciente_id) % 10
CROSS JOIN LATERAL (SELECT (t.fecha + t.hora_inicio) AS ts,
                           (55 + (t.paciente_id * 7) % 45 + (t.id % 3))::float8 AS peso,
                           (150 + (t.paciente_id * 3) % 40)::float8 AS talla) v
WHERE t.medico_id = 1 AND t.estado = 'COMPLETO'
  AND NOT EXISTS (SELECT 1 FROM hce_consulta c WHERE c.turno_id = t.id);

-- 2) Completar hasta 3 consultas por paciente con consultas anteriores sin turno asociado
INSERT INTO hce_consulta (creada_en, actualizada_en, cerrada_en, estado, motivo_consulta, anamnesis, examen_fisico, plan, indicaciones,
                          frecuencia_cardiaca, tension_sistolica, tension_diastolica, peso_kg, talla_cm, imc, temperatura, proximo_control,
                          centro_atencion_id, especialidad_id, medico_id, paciente_id, turno_id)
SELECT ts, ts + interval '20 minutes', ts + interval '20 minutes', 'CERRADA', tpl.motivo, tpl.anamnesis, tpl.examen, tpl.plan, tpl.indicaciones,
       62 + (f.pid * 5 + k * 9) % 30, 112 + (f.pid * 3 + k * 13) % 40, 66 + (f.pid * 7 + k * 5) % 28,
       v.peso, v.talla, round((v.peso / ((v.talla / 100.0) ^ 2))::numeric, 1), 36.0 + ((f.pid + k) % 10) / 10.0,
       NULL, f.centro, 1, 1, f.pid, NULL
FROM (SELECT pj.pid,
             (SELECT min(t.centro_atencion_id) FROM turno t WHERE t.medico_id = 1 AND t.paciente_id = pj.pid) AS centro,
             (SELECT count(*) FROM hce_consulta c WHERE c.paciente_id = pj.pid AND c.medico_id = 1) AS n
      FROM pj) f
CROSS JOIN LATERAL generate_series(1, 3 - f.n::int) AS k
JOIN tpl ON tpl.n = (f.pid + k * 3) % 10
CROSS JOIN LATERAL (SELECT timestamp '2025-03-03 09:00' + ((f.pid * 11) % 150) * interval '1 day' + k * interval '47 days'
                             + ((f.pid + k) % 14) * interval '30 minutes' AS ts,
                           (55 + (f.pid * 7) % 45)::float8 AS peso,
                           (150 + (f.pid * 3) % 40)::float8 AS talla) v
WHERE f.n < 3;

-- 3) Un diagnóstico por consulta
INSERT INTO hce_diagnostico (code, display, system_uri, tipo, consulta_id)
SELECT cie.code, cie.display, cie.system_uri, CASE WHEN c.id % 4 = 0 THEN 'PRESUNTIVO' ELSE 'DEFINITIVO' END, c.id
FROM hce_consulta c
JOIN tpl ON tpl.motivo = c.motivo_consulta
JOIN hce_cie10 cie ON cie.code = tpl.code
WHERE c.medico_id = 1 AND c.paciente_id IN (SELECT pid FROM pj)
  AND NOT EXISTS (SELECT 1 FROM hce_diagnostico d WHERE d.consulta_id = c.id);

-- 4) Perfil clínico
INSERT INTO hce_perfil (actualizado_en, actualizado_por, antecedentes_familiares, antecedentes_personales, antecedentes_quirurgicos,
                        comparte_entre_centros, grupo_sanguineo, habitos, paciente_id)
SELECT now(), 'medico@turnero.com',
       (ARRAY['Padre con hipertensión arterial.','Madre con diabetes tipo 2.','Padre con infarto de miocardio a los 58 años.','Sin antecedentes familiares de relevancia.','Hermano con dislipidemia.','Madre hipertensa, abuelo materno con accidente cerebrovascular.'])[1 + pid % 6],
       (ARRAY['Hipertensión arterial diagnosticada hace 5 años.','Dislipidemia en tratamiento.','Sin antecedentes patológicos de relevancia.','Diabetes tipo 2 e hipertensión arterial.','Hipotiroidismo en tratamiento.','Ex tabaquista. Hipertensión arterial.','Sobrepeso. Sin otras patologías conocidas.'])[1 + pid % 7],
       (ARRAY['Sin cirugías previas.','Apendicectomía en la juventud.','Colecistectomía laparoscópica.','Sin cirugías previas.','Hernioplastia inguinal.'])[1 + pid % 5],
       false,
       (ARRAY['O_POSITIVO','A_POSITIVO','B_POSITIVO','O_NEGATIVO','A_NEGATIVO','AB_POSITIVO','O_POSITIVO','A_POSITIVO'])[1 + pid % 8],
       (ARRAY['No fuma. Actividad física dos veces por semana.','Sedentario. No fuma. Alcohol ocasional.','Tabaquista de 10 cigarrillos por día.','Camina a diario. Dieta equilibrada.','Ex tabaquista hace 3 años. Sedentario.'])[1 + pid % 5],
       pid
FROM pj
ON CONFLICT (paciente_id) DO UPDATE
  SET actualizado_en = EXCLUDED.actualizado_en, actualizado_por = EXCLUDED.actualizado_por,
      antecedentes_familiares = EXCLUDED.antecedentes_familiares, antecedentes_personales = EXCLUDED.antecedentes_personales,
      antecedentes_quirurgicos = EXCLUDED.antecedentes_quirurgicos, grupo_sanguineo = EXCLUDED.grupo_sanguineo, habitos = EXCLUDED.habitos
  WHERE hce_perfil.antecedentes_personales IS NULL AND hce_perfil.habitos IS NULL;

-- 5) Alergias (1 de cada 3), medicación (2 de cada 3) y vacunas (todos)
INSERT INTO hce_alergia (activa, criticidad, reaccion, sustancia, perfil_id)
SELECT true, (ARRAY['ALTA','BAJA','BAJA','NO_EVALUADA'])[1 + pid % 4],
       (ARRAY['Rash cutáneo generalizado','Broncoespasmo','Urticaria','Edema de labios'])[1 + pid % 4],
       (ARRAY['Penicilina','Aspirina','Contraste yodado','Dipirona'])[1 + (pid / 3) % 4], p.id
FROM pj JOIN hce_perfil p ON p.paciente_id = pj.pid
WHERE pid % 3 = 0 AND NOT EXISTS (SELECT 1 FROM hce_alergia a WHERE a.perfil_id = p.id);

INSERT INTO hce_medicacion (activa, dosis, farmaco, frecuencia, perfil_id)
SELECT true, m.dosis, m.farmaco, m.frecuencia, p.id
FROM pj JOIN hce_perfil p ON p.paciente_id = pj.pid
JOIN (VALUES (0,'Enalapril','10 mg','Cada 12 horas'), (1,'Losartán','50 mg','Una vez por día'), (2,'Atorvastatina','20 mg','Una vez por día, a la noche'),
             (3,'Amlodipina','5 mg','Una vez por día'), (4,'Bisoprolol','2,5 mg','Una vez por día'), (5,'Aspirina','100 mg','Una vez por día')) AS m(n, farmaco, dosis, frecuencia)
  ON m.n = pid % 6 OR (pid % 4 = 0 AND m.n = (pid + 2) % 6)
WHERE pid % 3 <> 1 AND NOT EXISTS (SELECT 1 FROM hce_medicacion x WHERE x.perfil_id = p.id);

INSERT INTO hce_vacuna (dosis, fecha_aplicacion, lote, nombre, perfil_id)
SELECT v.dosis, v.fecha + (pid % 40), NULL, v.nombre, p.id
FROM pj JOIN hce_perfil p ON p.paciente_id = pj.pid
JOIN (VALUES (0,'Antigripal','Dosis anual', date '2026-04-06'), (1,'COVID-19','Refuerzo', date '2025-10-06'), (2,'Antineumocócica','Dosis única', date '2025-05-12')) AS v(n, nombre, dosis, fecha)
  ON v.n = 0 OR (v.n = 1 AND pid % 2 = 0) OR (v.n = 2 AND pid % 5 = 0)
WHERE NOT EXISTS (SELECT 1 FROM hce_vacuna x WHERE x.perfil_id = p.id);

-- Verificación antes de confirmar
SELECT 'pacientes de Juan Pérez' AS dato, count(*) FROM pj
UNION ALL SELECT 'consultas', count(*) FROM hce_consulta WHERE medico_id = 1
UNION ALL SELECT 'diagnósticos', count(*) FROM hce_diagnostico
UNION ALL SELECT 'perfiles con datos', count(*) FROM hce_perfil WHERE antecedentes_personales IS NOT NULL
UNION ALL SELECT 'alergias', count(*) FROM hce_alergia
UNION ALL SELECT 'medicaciones', count(*) FROM hce_medicacion
UNION ALL SELECT 'vacunas', count(*) FROM hce_vacuna;
SELECT min(n) AS min_consultas, max(n) AS max_consultas, count(*) FILTER (WHERE n < 2) AS pacientes_con_menos_de_2
FROM (SELECT pj.pid, (SELECT count(*) FROM hce_consulta c WHERE c.paciente_id = pj.pid AND c.medico_id = 1) n FROM pj) x;

COMMIT;

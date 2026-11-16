/**
 * Reset y seed de la base contra los scripts de staging/.
 * Usa psql para respetar bloques DO $$ de los .sql existentes.
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { dbConfig } = require('./config');

const STAGING_DIR = path.resolve(__dirname, '../../staging');

function psqlEnv() {
  return {
    ...process.env,
    PGHOST: dbConfig.host,
    PGPORT: String(dbConfig.port),
    PGUSER: dbConfig.user,
    PGPASSWORD: dbConfig.password,
    PGDATABASE: dbConfig.database
  };
}

function psql(args, input) {
  return execFileSync('psql', ['-v', 'ON_ERROR_STOP=1', '-q', ...args], {
    env: psqlEnv(),
    input,
    encoding: 'utf8'
  });
}

function runSql(sql) {
  return psql(['-f', '-'], sql);
}

function runFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`No existe el script SQL: ${filePath}`);
  }
  return psql(['-f', filePath]);
}

/**
 * Vacía todas las tablas de public y reinicia las secuencias.
 * Lo usa el arranque de la suite y el reseed previo a los tests multi-tenant.
 */
function truncateAll() {
  runSql(`
DO $$
DECLARE
  stmt text;
  seq record;
BEGIN
  SELECT 'TRUNCATE TABLE ' || string_agg(format('%I.%I', schemaname, tablename), ', ')
         || ' RESTART IDENTITY CASCADE'
    INTO stmt
    FROM pg_tables
   WHERE schemaname = 'public';
  IF stmt IS NOT NULL THEN
    EXECUTE stmt;
  END IF;

  -- GenerationType.AUTO usa el optimizer pooled: el próximo nextval
  -- tiene que ser el tope del bloque (el increment, típicamente 50).
  -- Reiniciar en 1 produce ids negativos.
  FOR seq IN
    SELECT sequencename, increment_by
      FROM pg_sequences
     WHERE schemaname = 'public'
  LOOP
    EXECUTE format(
      'SELECT setval(%L, %s, false)',
      seq.sequencename,
      GREATEST(seq.increment_by, 1)
    );
  END LOOP;
END $$;
`);
}

const DOMAIN_TABLES = [
  'lista_espera',
  'notificaciones',
  'encuesta_respuesta',
  'encuesta_invitacion',
  'encuesta_plantilla_pregunta',
  'encuesta_plantilla',
  'pregunta',
  'preferencia_horaria',
  'turno',
  'esquema_turno_horarios',
  'esquema_turno',
  'disponibilidad_medico_horarios',
  'disponibilidad_medico',
  'staff_medico',
  'medico_especialidad',
  'centro_especialidad',
  'consultorio_horarios',
  'consultorio',
  'medico',
  'paciente',
  'operador',
  'administrador',
  'especialidad',
  'obra_social',
  'centro_atencion',
  'audit_log',
  'configuracion_excepcional',
  'configuracion',
  'deep_link_token'
];

function deleteExistingTables(tableNames) {
  const list = tableNames.map((name) => `'${name}'`).join(', ');
  runSql(`
DO $$
DECLARE
  r record;
BEGIN
  PERFORM set_config('session_replication_role', 'replica', true);
  FOR r IN
    SELECT tablename
      FROM pg_tables
     WHERE schemaname = 'public'
       AND tablename IN (${list})
  LOOP
    EXECUTE format('DELETE FROM %I', r.tablename);
  END LOOP;
  PERFORM set_config('session_replication_role', 'origin', true);
END $$;
`);
}

/**
 * Borra datos de dominio (centros, médicos, turnos, etc.) y conserva users.
 * Reemplaza el endpoint inexistente DELETE /deleteAll.
 */
function resetDomain() {
  deleteExistingTables(DOMAIN_TABLES);
  const deleteAll = path.join(STAGING_DIR, 'deleteAll.sql');
  if (fs.existsSync(deleteAll)) {
    runFile(deleteAll);
  }
}

/**
 * Vacía especialidades y las filas que las referencian.
 * Reemplaza el endpoint inexistente DELETE /especialidades/reset.
 */
function resetEspecialidades() {
  deleteExistingTables([
    'medico_especialidad',
    'centro_especialidad',
    'staff_medico',
    'especialidad'
  ]);
}

const SUPERADMIN_SQL = `
INSERT INTO users (
  nombre, apellido, dni, email, telefono,
  hashed_password, role, enabled, account_non_expired,
  account_non_locked, credentials_non_expired, email_verified,
  centro_atencion_id
) VALUES (
  'Super', 'Administrador', 10000000, 'superadmin@turnero.com', '+5492804100000',
  '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
  'SUPERADMIN',
  true, true, true, true, true,
  NULL
)
ON CONFLICT (email) DO NOTHING;
`;

function ensureSuperAdmin() {
  runSql(SUPERADMIN_SQL);
}

/**
 * Carga el juego de datos multi-tenant de staging/userinit.sql.
 */
/**
 * Hibernate pooled (allocationSize 50) interpreta el nextval como el tope
 * del bloque. Después de inserts con id explícito, el próximo bloque tiene
 * que empezar por encima del MAX(id); si no, el alta choca con la PK.
 */
function alignPooledSequences() {
  runSql(`
DO $$
DECLARE
  seq record;
  max_id bigint;
  step bigint;
  next_hi bigint;
  table_name text;
BEGIN
  FOR seq IN
    SELECT sequencename, increment_by AS step_size
      FROM pg_sequences
     WHERE schemaname = 'public'
       AND increment_by > 1
  LOOP
    step := seq.step_size;
    table_name := regexp_replace(seq.sequencename, '_seq$', '');
    BEGIN
      EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', table_name) INTO max_id;
    EXCEPTION WHEN undefined_table OR undefined_column THEN
      CONTINUE;
    END;
    -- El bloque de un nextval H es (H - increment, H]. Tiene que empezar
    -- después del mayor id ya insertado.
    next_hi := (((max_id + step - 1) / step) + 1) * step;
    PERFORM setval(seq.sequencename::regclass, next_hi, false);
  END LOOP;
END $$;
`);
}

function seedMultiTenant() {
  truncateAll();
  runFile(path.join(STAGING_DIR, 'userinit.sql'));
  ensureSuperAdmin();
  alignPooledSequences();
}

function prepareLegacyBaseline() {
  truncateAll();
  ensureSuperAdmin();
}

module.exports = {
  runSql,
  runFile,
  truncateAll,
  resetDomain,
  resetEspecialidades,
  ensureSuperAdmin,
  seedMultiTenant,
  prepareLegacyBaseline
};

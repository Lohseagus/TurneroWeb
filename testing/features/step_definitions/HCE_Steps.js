const { After, Given, When, Then } = require('@cucumber/cucumber');
const assert = require('assert');
const { POST, GET, PUT, login } = require('./MT_Helpers');
const { runSql } = require('../../support/db');

const TURNO_ID = 900001;
const MEDICO_OTRO_ID = 900002;
const STAFF_OTRO_ID = 900003;
const PASSWORD_HASH = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi';

function sqlValor(salida) {
  const match = String(salida).match(/(\d+)/);
  if (!match) {
    throw new Error(`Sin id en la salida SQL: ${salida}`);
  }
  return Number(match[1]);
}

function consultorioCentro1() {
  return sqlValor(runSql('SELECT id FROM consultorio WHERE centro_atencion_id = 1 ORDER BY id LIMIT 1;'));
}

function prepararTurnoYMedicoAjeno() {
  const consultorioId = consultorioCentro1();
  runSql(`
    INSERT INTO turno (id, fecha, hora_inicio, hora_fin, estado, paciente_id, staff_medico_id, medico_id, consultorio_id, especialidad_id, centro_atencion_id)
    VALUES (${TURNO_ID}, CURRENT_DATE, '10:00', '10:30', 'CONFIRMADO', 1, 1, 1, ${consultorioId}, 1, 1)
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO users (nombre, apellido, dni, email, telefono, hashed_password, role, enabled, account_non_expired, account_non_locked, credentials_non_expired, email_verified)
    VALUES ('Dra. Ana', 'López', 33333999, 'hce.otro.centro@turnero.com', '+5492804333999', '${PASSWORD_HASH}', 'MEDICO', true, true, true, true, true)
    ON CONFLICT (email) DO NOTHING;

    INSERT INTO medico (id, nombre, apellido, dni, email, telefono, matricula)
    VALUES (${MEDICO_OTRO_ID}, 'Dra. Ana', 'López', 33333999, 'hce.otro.centro@turnero.com', '+5492804333999', 'MP-HCE-2')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO staff_medico (id, centro_atencion_id, medico_id, especialidad_id)
    VALUES (${STAFF_OTRO_ID}, 2, ${MEDICO_OTRO_ID}, 1)
    ON CONFLICT (id) DO NOTHING;
  `);
}

function limpiarHistoria() {
  runSql(`
    DELETE FROM hce_receta_item;
    DELETE FROM hce_receta;
    DELETE FROM hce_orden_estudio_item;
    DELETE FROM hce_orden_estudio;
    DELETE FROM hce_enmienda;
    DELETE FROM hce_diagnostico;
    DELETE FROM hce_adjunto;
    DELETE FROM hce_acceso;
    DELETE FROM hce_consulta;
    DELETE FROM hce_alergia;
    DELETE FROM hce_medicacion;
    DELETE FROM hce_vacuna;
    DELETE FROM hce_perfil;
    DELETE FROM turno WHERE id = ${TURNO_ID};
    DELETE FROM staff_medico WHERE id = ${STAFF_OTRO_ID};
    DELETE FROM medico WHERE id = ${MEDICO_OTRO_ID};
    DELETE FROM users WHERE email = 'hce.otro.centro@turnero.com';
  `);
}

function guardar(world, result) {
  world.statusCode = result.statusCode;
  world.httpStatusCode = result.httpStatusCode;
  world.response = result.response;
}

After({ tags: '@hce' }, function () {
  limpiarHistoria();
});

Given('que preparo el turno de historia clínica del centro 1', function () {
  prepararTurnoYMedicoAjeno();
  this.turnoId = TURNO_ID;
  this.pacienteId = 1;
});

Given('que preparo una consulta cerrada de historia clínica en el centro 1', function () {
  prepararTurnoYMedicoAjeno();
  const sesion = login('medico@turnero.com', 'password');
  assert.ok(sesion.success, 'El médico de prueba no pudo ingresar');
  const creada = POST('/hce/consultas', {
    turnoId: TURNO_ID,
    motivoConsulta: 'Cefalea',
    anamnesis: 'Inicio hace dos días'
  }, sesion.accessToken);
  assert.strictEqual(creada.statusCode, 201, JSON.stringify(creada.response));
  const cerrada = POST(`/hce/consultas/${creada.response.data.id}/cerrar`, {}, sesion.accessToken);
  assert.strictEqual(cerrada.statusCode, 200, JSON.stringify(cerrada.response));
  this.consultaId = creada.response.data.id;
  this.pacienteId = creada.response.data.pacienteId;
  this.turnoId = TURNO_ID;
});

When('creo una consulta de historia clínica para ese turno con motivo {string}', function (motivo) {
  const result = POST('/hce/consultas', {
    turnoId: this.turnoId,
    motivoConsulta: motivo,
    anamnesis: 'Sin antecedentes de alarma',
    tensionSistolica: 120,
    tensionDiastolica: 80,
    pesoKg: 70,
    tallaCm: 170
  }, this.accessToken);
  guardar(this, result);
  if (result.response && result.response.data) {
    this.consultaId = result.response.data.id;
    this.pacienteId = result.response.data.pacienteId;
  }
});

When('cierro la consulta de historia clínica', function () {
  const result = POST(`/hce/consultas/${this.consultaId}/cerrar`, {}, this.accessToken);
  guardar(this, result);
});

When('intento modificar la consulta de historia clínica cerrada', function () {
  const result = PUT(`/hce/consultas/${this.consultaId}`, {
    motivoConsulta: 'Texto reescrito'
  }, this.accessToken);
  guardar(this, result);
});

When('agrego una enmienda de historia clínica con motivo {string}', function (motivo) {
  const result = POST(`/hce/consultas/${this.consultaId}/enmiendas`, {
    motivo,
    texto: 'La dosis correcta es 50 mg'
  }, this.accessToken);
  guardar(this, result);
});

When('consulto la consulta de historia clínica', function () {
  const result = GET(`/hce/consultas/${this.consultaId}`, this.accessToken);
  guardar(this, result);
});

When('consulto los metadatos de historia clínica del paciente de prueba', function () {
  const result = GET(`/hce/pacientes/${this.pacienteId}/metadata`, this.accessToken);
  guardar(this, result);
});

When('consulto mi ficha de médico', function () {
  const result = GET('/medicos/me', this.accessToken);
  guardar(this, result);
});

When('consulto mi historia clínica', function () {
  const result = GET('/hce/mi', this.accessToken);
  guardar(this, result);
});

When('descargo el PDF de mi historia clínica', function () {
  const result = GET('/hce/mi/pdf', this.accessToken);
  guardar(this, result);
});

Then('la consulta de historia clínica tiene una enmienda', function () {
  const enmiendas = this.response.data && this.response.data.enmiendas;
  assert.ok(Array.isArray(enmiendas) && enmiendas.length >= 1, 'La consulta cerrada debería mostrar la enmienda');
  assert.strictEqual(enmiendas[0].motivo, 'Error de transcripción');
});

Then('la ficha de médico tiene id', function () {
  const id = this.response && this.response.data && this.response.data.id;
  assert.ok(Number.isInteger(id) && id > 0, `La ficha no trajo un id: ${JSON.stringify(this.response)}`);
});

Then('recibo un PDF de historia clínica', function () {
  assert.strictEqual(this.statusCode, 200);
  const cuerpo = this.response.raw || '';
  assert.ok(cuerpo.startsWith('%PDF'), 'La descarga debería ser un PDF');
});

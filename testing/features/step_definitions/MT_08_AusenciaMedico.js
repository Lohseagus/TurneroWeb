const { When, Then } = require('@cucumber/cucumber');
const assert = require('assert');
const { GET, POST, login, ENDPOINTS } = require('./MT_Helpers');
const { runSql } = require('../../support/db');

const DIAS = ['DOMINGO', 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO'];

function dormir(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function dentroDe(dias) {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() + dias);
  const dia = String(fecha.getDate()).padStart(2, '0');
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const anio = fecha.getFullYear();
  return {
    iso: `${anio}-${mes}-${dia}`,
    backend: `${dia}-${mes}-${anio}`,
    semana: DIAS[fecha.getDay()]
  };
}

function normalizarFecha(valor) {
  if (Array.isArray(valor)) {
    const [anio, mes, dia] = valor;
    return `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
  }
  const texto = String(valor);
  const partes = texto.split('-');
  if (partes[0].length === 4) {
    return texto.slice(0, 10);
  }
  return `${partes[2]}-${partes[1]}-${partes[0]}`;
}

function asegurarEsquema(staffId, consultorioId, dia, token) {
  const horarios = [{ dia, horaInicio: '08:00:00', horaFin: '18:00:00' }];
  const disponibilidad = POST(ENDPOINTS.DISPONIBILIDADES_MEDICO, {
    staffMedicoId: staffId,
    especialidadId: 1,
    horarios
  }, token);
  let disponibilidadMedicoId = disponibilidad.response?.data?.id;
  if (!disponibilidadMedicoId) {
    const existentes = GET(`${ENDPOINTS.DISPONIBILIDADES_MEDICO}/staffMedico/${staffId}`, token);
    disponibilidadMedicoId = existentes.response?.data?.[0]?.id;
  }
  return POST(ENDPOINTS.ESQUEMA_TURNO, {
    staffMedicoId: staffId,
    consultorioId,
    centroId: 1,
    disponibilidadMedicoId,
    intervalo: 30,
    horarios
  }, token);
}

function tokenDeAusencia(turnoId) {
  const sql = `COPY (SELECT token FROM deep_link_token WHERE turno_id = ${Number(turnoId)} AND tipo = 'AUSENCIA' ORDER BY id DESC LIMIT 1) TO STDOUT;`;
  for (let intento = 0; intento < 12; intento += 1) {
    try {
      const token = runSql(sql).trim();
      if (token) {
        return token;
      }
    } catch (error) {
      if (intento === 11) {
        throw error;
      }
    }
    dormir(1000);
  }
  return '';
}

When('el médico marca una ausencia que cancela un turno futuro del paciente', function () {
  const superadmin = login('superadmin@turnero.com', 'password');
  assert.strictEqual(superadmin.success, true, 'El superadmin no pudo entrar');

  const consultorios = GET(`${ENDPOINTS.CONSULTORIOS}/centrosAtencion/1/consultorios`, superadmin.accessToken);
  assert.strictEqual(consultorios.statusCode, 200, JSON.stringify(consultorios.response));
  const consultorioId = consultorios.response.data[0].id;
  const fecha = dentroDe(14);

  const esquema = asegurarEsquema(1, consultorioId, fecha.semana, superadmin.accessToken);
  const turno = POST(ENDPOINTS.TURNOS, {
    pacienteId: 1,
    consultorioId,
    staffMedicoId: 1,
    fecha: fecha.iso,
    horaInicio: '10:00:00',
    horaFin: '10:30:00',
    estado: 'PROGRAMADO'
  }, superadmin.accessToken);
  assert.strictEqual(turno.statusCode, 200,
    `No se pudo crear el turno (${fecha.iso}, esquema ${esquema.statusCode}): ${JSON.stringify(turno.response)}`);

  const medico = login('medico@turnero.com', 'password');
  assert.strictEqual(medico.success, true, 'El médico no pudo entrar');
  const ausencia = POST('/agenda/ausencias', {
    staffMedicoId: 1,
    centroAtencionId: 1,
    fechaInicio: fecha.iso,
    fechaFin: fecha.iso,
    motivo: 'congreso'
  }, medico.accessToken);
  assert.strictEqual(ausencia.statusCode, 200, JSON.stringify(ausencia.response));
  assert.ok((ausencia.response.data.cancelados || []).some((item) => item.id === turno.response.data.id),
    JSON.stringify(ausencia.response));

  this.ausencia = {
    turnoId: turno.response.data.id,
    fechaCancelada: fecha.iso,
    staffMedicoId: 1,
    tokenMedico: medico.accessToken
  };
});

When('el paciente abre el enlace de esa cancelación', function () {
  const token = tokenDeAusencia(this.ausencia.turnoId);
  assert.ok(token, `No apareció el enlace de ausencia del turno ${this.ausencia.turnoId}`);
  const validacion = POST('/deep-links/validate', { token }, null);
  assert.strictEqual(validacion.statusCode, 200, JSON.stringify(validacion.response));
  assert.strictEqual(validacion.response.data.context.tipo, 'AUSENCIA');
  assert.strictEqual(validacion.response.data.context.ausencia, true);
  assert.strictEqual(validacion.response.data.context.turnoId, this.ausencia.turnoId);
  this.ausencia.tokenPaciente = validacion.response.data.tokens.accessToken;
});

When('elige un horario libre del mismo médico', function () {
  const slots = GET(`/agenda/slots-disponibles/${this.ausencia.staffMedicoId}?semanas=4`, this.ausencia.tokenPaciente);
  assert.strictEqual(slots.statusCode, 200, JSON.stringify(slots.response));
  const libre = (slots.response.data || []).find((slot) => normalizarFecha(slot.fecha) !== this.ausencia.fechaCancelada);
  assert.ok(libre, `No quedó un horario libre fuera de la ausencia: ${JSON.stringify(slots.response.data)}`);
  const reserva = POST('/turno/asignar', {
    fecha: libre.fecha,
    horaInicio: libre.horaInicio,
    horaFin: libre.horaFin,
    pacienteId: 1,
    staffMedicoId: libre.staffMedicoId,
    consultorioId: libre.consultorioId,
    centroId: libre.centroId,
    estado: 'PROGRAMADO'
  }, this.ausencia.tokenPaciente);
  assert.strictEqual(reserva.statusCode, 200, JSON.stringify(reserva.response));
  this.ausencia.turnoNuevoId = reserva.response.data.id;
});

Then('queda reservado un turno nuevo y el anterior figura cancelado', function () {
  const superadmin = login('superadmin@turnero.com', 'password');
  const nuevo = GET(`${ENDPOINTS.TURNOS}/${this.ausencia.turnoNuevoId}`, superadmin.accessToken);
  const anterior = GET(`${ENDPOINTS.TURNOS}/${this.ausencia.turnoId}`, superadmin.accessToken);
  assert.strictEqual(nuevo.statusCode, 200, JSON.stringify(nuevo.response));
  assert.strictEqual(anterior.statusCode, 200, JSON.stringify(anterior.response));
  assert.strictEqual(nuevo.response.data.estado, 'PROGRAMADO');
  assert.strictEqual(anterior.response.data.estado, 'CANCELADO');
  assert.notStrictEqual(this.ausencia.turnoNuevoId, this.ausencia.turnoId);
});

const { When, Then, Given, BeforeAll } = require('@cucumber/cucumber');
const assert = require('assert');
const request = require('../../support/request');

function especialidadesResueltas(especialidades) {
  const nombres = String(especialidades || '')
    .split(',')
    .map((esp) => esp.trim())
    .filter(Boolean);
  let catalogo = [];
  try {
    const res = request('GET', 'http://backend:8080/especialidades');
    catalogo = JSON.parse(res.getBody('utf8')).data || [];
  } catch (error) {
    catalogo = [];
  }
  return nombres.map((nombre) => {
    const encontrada = catalogo.find(
      (esp) => esp.nombre && esp.nombre.toLowerCase() === nombre.toLowerCase()
    );
    return encontrada
      ? { id: encontrada.id, nombre: encontrada.nombre }
      : { nombre };
  });
}

function payloadMedico(nombre, apellido, dni, matricula, especialidades) {
  const especialidadesArray = especialidadesResueltas(especialidades);
  const dniTexto = dni == null ? '' : String(dni);
  const digitos = dniTexto.replace(/\D/g, '') || String(Date.now()).slice(-8);
  return {
    nombre,
    apellido,
    dni: dniTexto,
    matricula,
    // El alta actual persiste email y teléfono (Persona) y, si hay usuario
    // autenticado, también la cuenta. Los escenarios no los declaran.
    email: `m${digitos}@medicos.test`,
    telefono: `549${digitos}`.slice(0, 15),
    especialidades: especialidadesArray,
    especialidadIds: especialidadesArray.map((esp) => esp.id).filter((id) => id != null)
  };
}

function postMedico(medico) {
  try {
    const res = request('POST', 'http://backend:8080/medicos/create-by-admin', { json: medico });
    return JSON.parse(res.getBody('utf8'));
  } catch (error) {
    try {
      return error.response
        ? JSON.parse(error.response.body.toString('utf8'))
        : { status_code: 500, status_text: 'Error interno' };
    } catch (parseErr) {
      return { status_code: 500, status_text: 'Error interno' };
    }
  }
}

Given('que existen {int} medicos registrados en el sistema', function (cantidad) {
    // Trae la lista actual de médicos y guarda la cantidad real en el contexto
    const res = request('GET', 'http://backend:8080/medicos');
    const data = JSON.parse(res.getBody('utf8'));
    this.medicos = data.data;
    this.cantidadMedicos = this.medicos.length;

});

When(
  'el administrador crea un médico con {string}, {string}, {string}, {string}, {string}',
  function (nombre, apellido, dni, matricula, especialidades) {
    this.response = postMedico(payloadMedico(nombre, apellido, dni, matricula, especialidades));
  }
);

When(
  'el administrador crea un médico con {string}, {string}, {string}, {string} y las especialidades {string}',
  function (nombre, apellido, dni, matricula, especialidades) {
    this.response = postMedico(payloadMedico(nombre, apellido, dni, matricula, especialidades));
  }
);

When('un usuario del sistema solicita la lista de medicos', function () {
  try {
    const res = request('GET', 'http://backend:8080/medicos');
    this.response = JSON.parse(res.getBody('utf8'));
  } catch (error) {
    this.response = error.response
      ? JSON.parse(error.response.body.toString('utf8'))
      : { status_code: 500, status_text: 'Error interno' };
  }
});


Then(
  'el sistema responde con {int} y {string} para medicos',
  function (expectedStatus, expectedText) {
    assert.strictEqual(this.response.status_code, expectedStatus, `Status esperado: ${expectedStatus}, recibido: ${this.response.status_code}`);
    assert.ok(
      this.response.status_text.trim().toLowerCase().includes(expectedText.trim().toLowerCase()),
      `Esperado status_text que contenga "${expectedText}", pero fue "${this.response.status_text}"`
    );
  }
);


Then('el sistema responde con un JSON:', function (docString) {
  const expected = JSON.parse(docString);
  // Solo validamos status_code y status_text, y que data sea array
  assert.strictEqual(this.response.status_code, expected.status_code, 'status_code incorrecto');
  assert.ok(
    this.response.status_text.trim().toLowerCase().includes(expected.status_text.trim().toLowerCase()),
    `status_text incorrecto: esperado "${expected.status_text}", recibido "${this.response.status_text}"`
  );
  assert.ok(Array.isArray(this.response.data), 'data no es un array');
});  // Si querés comparar los médicos, podés hacer un assert.deepStrictEqual aquí

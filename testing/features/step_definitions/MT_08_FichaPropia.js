const { When, Then, After } = require('@cucumber/cucumber');
const assert = require('assert');
const { GET, POST, DELETE } = require('./MT_Helpers');

function guardar(mundo, result) {
  mundo.statusCode = result.statusCode;
  mundo.response = result.response;
  mundo.httpStatusCode = result.httpStatusCode;
}

After({ tags: '@ficha-propia' }, function () {
  if (!this.fichaPropiaId || !this.accessToken) {
    return;
  }
  DELETE(`/pacientes/${this.fichaPropiaId}`, this.accessToken);
  this.fichaPropiaId = null;
});

When('consulto mi ficha de paciente', function () {
  guardar(this, GET('/pacientes/me', this.accessToken));
});

Then('no tengo ficha de paciente', function () {
  assert.strictEqual(this.statusCode, 404, JSON.stringify(this.response));
});

When('intento crear la ficha de {string}', function (email) {
  guardar(this, POST('/pacientes/me', {
    id: 1,
    email,
    nombre: 'Intruso',
    apellido: 'Ajeno',
    dni: 44444441,
    telefono: '+5492804444441'
  }, this.accessToken));
});

Then('la ficha ajena queda rechazada', function () {
  assert.strictEqual(this.statusCode, 409, JSON.stringify(this.response));
  const texto = (this.response && this.response.status_text) || '';
  assert.ok(texto.includes('propia ficha'), texto);
});

Then('el paciente con DNI {int} sigue siendo {string}', function (dni, nombre) {
  const result = GET(`/pacientes/dni/${dni}`, this.accessToken);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  assert.strictEqual(result.response.data.nombre, nombre);
  assert.notStrictEqual(result.response.data.email, 'medico@turnero.com');
});

When('creo mi ficha de paciente', function () {
  const result = POST('/pacientes/me', {}, this.accessToken);
  guardar(this, result);
  if (result.response && result.response.data && result.response.data.id) {
    this.fichaPropiaId = result.response.data.id;
  }
});

When('vuelvo a crear mi ficha de paciente', function () {
  guardar(this, POST('/pacientes/me', {}, this.accessToken));
});

Then('mi ficha de paciente queda con el email {string} y el DNI {int}', function (email, dni) {
  assert.strictEqual(this.statusCode, 201, JSON.stringify(this.response));
  assert.strictEqual(this.response.data.email, email);
  assert.strictEqual(Number(this.response.data.dni), dni);
  assert.ok(this.fichaPropiaId, 'No quedó el id de la ficha creada');
});

Then('se vincula la misma ficha sin duplicarla', function () {
  assert.strictEqual(this.statusCode, 200, JSON.stringify(this.response));
  assert.strictEqual(this.response.data.id, this.fichaPropiaId);
  const texto = this.response.status_text || '';
  assert.ok(texto.includes('vinculó'), texto);
});

When('borro la ficha de paciente que acabo de crear', function () {
  assert.ok(this.fichaPropiaId, 'No hay ficha para borrar');
  const result = DELETE(`/pacientes/${this.fichaPropiaId}`, this.accessToken);
  assert.ok(result.statusCode === 200 || result.statusCode === 204, JSON.stringify(result.response));
  this.fichaPropiaId = null;
});

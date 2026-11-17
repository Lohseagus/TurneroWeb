const { After, Given, When, Then } = require('@cucumber/cucumber');
const assert = require('assert');
const { POST, GET, PUT } = require('./MT_Helpers');
const { runSql } = require('../../support/db');

function guardar(world, result) {
  world.statusCode = result.statusCode;
  world.httpStatusCode = result.httpStatusCode;
  world.response = result.response;
}

function limpiarRecetas() {
  runSql(`
    DELETE FROM hce_receta_item;
    DELETE FROM hce_receta;
    DELETE FROM hce_orden_estudio_item;
    DELETE FROM hce_orden_estudio;
  `);
}

After({ tags: '@recetas' }, function () {
  limpiarRecetas();
});

Given('que el paciente de la receta tiene sexo y fecha de nacimiento', function () {
  runSql(`UPDATE paciente SET sexo = 'F', fecha_nacimiento = DATE '1990-05-12' WHERE id = ${this.pacienteId || 1};`);
});

When('creo una consulta de historia clínica con diagnóstico CIE-10', function () {
  const result = POST('/hce/consultas', {
    turnoId: this.turnoId,
    motivoConsulta: 'Cefalea',
    anamnesis: 'Inicio hace dos días',
    diagnosticos: [{
      systemUri: 'http://hl7.org/fhir/sid/icd-10',
      code: 'R51',
      display: 'Cefalea',
      tipo: 'PRESUNTIVO'
    }]
  }, this.accessToken);
  guardar(this, result);
  if (result.response && result.response.data) {
    this.consultaId = result.response.data.id;
    this.pacienteId = result.response.data.pacienteId;
  }
});

When('creo una receta de ibuprofeno para esa consulta', function () {
  const result = POST('/recetas', {
    consultaId: this.consultaId,
    tipo: 'COMUN',
    diagnosticoCode: 'R51',
    sexo: 'F',
    fechaNacimiento: '1990-05-12',
    items: [{
      nombreGenerico: 'ibuprofeno',
      concentracion: '400 mg',
      formaFarmaceutica: 'comprimidos',
      cantidad: 2,
      dosis: '1 comprimido',
      frecuencia: 'cada 8 horas',
      via: 'oral'
    }]
  }, this.accessToken);
  guardar(this, result);
  if (result.response && result.response.data) {
    this.recetaId = result.response.data.id;
  }
});

When('emito la receta electrónica', function () {
  const result = POST(`/recetas/${this.recetaId}/emitir`, {}, this.accessToken);
  guardar(this, result);
  if (result.response && result.response.data) {
    this.recetaId = result.response.data.id;
    this.codigoReceta = result.response.data.codigoVerificacion;
  }
});

When('intento modificar la receta emitida', function () {
  const result = PUT(`/recetas/${this.recetaId}`, {
    consultaId: this.consultaId,
    tipo: 'COMUN',
    diagnosticoCode: 'R51',
    items: [{
      nombreGenerico: 'paracetamol',
      concentracion: '500 mg',
      formaFarmaceutica: 'comprimidos',
      cantidad: 1,
      dosis: '1 comprimido',
      frecuencia: 'cada 8 horas'
    }]
  }, this.accessToken);
  guardar(this, result);
});

When('verifico la receta sin autenticarme', function () {
  const result = GET(`/recetas/verificar/${this.codigoReceta}`, null);
  guardar(this, result);
});

When('descargo el PDF de la receta', function () {
  const result = GET(`/recetas/${this.recetaId}/pdf`, this.accessToken);
  guardar(this, result);
});

When('anulo la receta sin motivo', function () {
  const result = POST(`/recetas/${this.recetaId}/anular`, {}, this.accessToken);
  guardar(this, result);
});

When('anulo la receta con motivo {string}', function (motivo) {
  const result = POST(`/recetas/${this.recetaId}/anular`, { motivo }, this.accessToken);
  guardar(this, result);
});

Then('la receta emitida tiene identificador y huella', function () {
  const data = this.response.data;
  assert.ok(data.identificador && data.identificador.startsWith('CTR'), 'Falta el identificador local');
  assert.strictEqual(data.cuir || null, null);
  assert.strictEqual((data.hashContenido || '').length, 64);
  assert.strictEqual((data.codigoVerificacion || '').length, 64);
  assert.strictEqual(data.estado, 'EMITIDA');
  assert.strictEqual(data.integridad, true);
});

Then('la verificación pública muestra el medicamento y no el diagnóstico', function () {
  const data = this.response.data;
  assert.strictEqual(data.estado, 'EMITIDA');
  assert.strictEqual(data.vigente, true);
  assert.strictEqual(data.integridad, true);
  assert.ok(data.items && data.items[0].nombre.toLowerCase().includes('ibuprofeno'));
  assert.ok(data.pacienteIniciales);
  assert.ok(data.dniParcial && data.dniParcial.includes('*'));
  assert.strictEqual(data.diagnosticoCode, undefined);
  assert.strictEqual(data.pacienteNombre, undefined);
  const texto = JSON.stringify(data);
  assert.ok(!texto.includes('Cefalea'));
  assert.ok(!texto.includes('anamnesis'));
});

Then('recibo un PDF de receta', function () {
  assert.strictEqual(this.statusCode, 200);
  const cuerpo = this.response.raw || '';
  assert.ok(cuerpo.startsWith('%PDF'), 'La descarga debería ser un PDF');
});

Then('la receta figura anulada', function () {
  assert.strictEqual(this.response.data.estado, 'ANULADA');
  assert.strictEqual(this.response.data.motivoAnulacion, 'Error de dosis');
  assert.ok(this.response.data.anuladoPor);
  assert.ok(this.response.data.anuladoEn);
});

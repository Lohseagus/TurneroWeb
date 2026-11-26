const { When, Then, After } = require('@cucumber/cucumber');
const assert = require('assert');
const { GET, POST, PUT, login } = require('./MT_Helpers');
const { SUPERADMIN } = require('../../support/auth');

const PRECIOS = {
  PROFESIONAL: { precioBaseMensual: 25000, precioPorProfesional: 0, precioPorSedeExtra: null, sedesIncluidas: 1 },
  CONSULTORIO: { precioBaseMensual: 0, precioPorProfesional: 18000, precioPorSedeExtra: null, sedesIncluidas: 1 },
  CENTRO: { precioBaseMensual: 200000, precioPorProfesional: 0, precioPorSedeExtra: null, sedesIncluidas: 1 },
  RED: { precioBaseMensual: 350000, precioPorProfesional: 0, precioPorSedeExtra: 120000, sedesIncluidas: 3 }
};

function numero(valor) {
  return Number(valor);
}

function planDe(catalogo, codigo) {
  return (catalogo.planes || []).find((plan) => plan.codigo === codigo);
}

function loginSuperadmin() {
  const sesion = login(SUPERADMIN.email, SUPERADMIN.password);
  assert.ok(sesion.success, `No pude ingresar como superadministrador: ${JSON.stringify(sesion.response)}`);
  return sesion.accessToken;
}

function alta(planCodigo) {
  const n = Date.now();
  const body = {
    planCodigo,
    ciclo: 'MENSUAL',
    profesionalesContratados: planCodigo === 'CONSULTORIO' ? 2 : 1,
    addons: [],
    organizacionNombre: `Org SaaS ${n}`,
    centroNombre: `Centro SaaS ${n}`,
    direccion: `Calle SaaS ${n}`,
    localidad: 'CABA',
    provincia: 'Buenos Aires',
    telefonoCentro: String(4100000000 + (n % 1000000)),
    latitud: -34.6037,
    longitud: -58.3816,
    adminNombre: 'Ada',
    adminApellido: 'Prueba',
    adminEmail: `saas${n}@example.com`,
    adminPassword: 'Prueba123',
    adminDni: 30000000 + (n % 60000000),
    adminTelefono: String(1100000000 + (n % 800000000))
  };
  const result = POST('/api/saas/registro', body);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  return { body, data: result.response.data };
}

function restaurarCatalogo(token) {
  const admin = GET('/api/saas/admin/planes', token);
  if (admin.statusCode !== 200 || !admin.response.data) {
    return;
  }
  const catalogo = admin.response.data;
  for (const plan of catalogo.planes || []) {
    const canonico = PRECIOS[plan.codigo];
    if (!canonico) continue;
    plan.precioBaseMensual = canonico.precioBaseMensual;
    plan.precioPorProfesional = canonico.precioPorProfesional;
    plan.precioPorSedeExtra = canonico.precioPorSedeExtra;
    plan.sedesIncluidas = canonico.sedesIncluidas;
    plan.descuentoAnualPorcentaje = null;
    PUT(`/api/saas/admin/planes/${plan.id}`, plan, token);
  }
  const config = catalogo.configuracion;
  config.diasTrial = 30;
  config.ivaPorcentaje = 21;
  config.descuentoAnualPorcentaje = 15;
  config.moneda = 'ARS';
  PUT('/api/saas/admin/configuracion', config, token);
}

When('consulto el catálogo público de planes SaaS', function () {
  const result = GET('/api/saas/planes');
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  this.catalogoSaas = result.response.data;
});

Then('el catálogo incluye el plan {string} con precio base {int}', function (codigo, precio) {
  const plan = planDe(this.catalogoSaas, codigo);
  assert.ok(plan, `No está el plan ${codigo}`);
  assert.strictEqual(numero(plan.precioBaseMensual), precio);
});

Then('el catálogo incluye el plan {string} con precio por profesional {int}', function (codigo, precio) {
  const plan = planDe(this.catalogoSaas, codigo);
  assert.ok(plan, `No está el plan ${codigo}`);
  assert.strictEqual(numero(plan.precioPorProfesional), precio);
  assert.strictEqual(plan.minProfesionales, 2);
  assert.strictEqual(plan.maxProfesionales, 10);
});

Then('el catálogo incluye el plan {string} con precio base {int}, {int} sedes incluidas y extra {int}', function (codigo, base, sedes, extra) {
  const plan = planDe(this.catalogoSaas, codigo);
  assert.ok(plan, `No está el plan ${codigo}`);
  assert.strictEqual(numero(plan.precioBaseMensual), base);
  assert.strictEqual(plan.sedesIncluidas, sedes);
  assert.strictEqual(numero(plan.precioPorSedeExtra), extra);
});

Then('la prueba gratis del catálogo dura {int} días', function (dias) {
  assert.strictEqual(this.catalogoSaas.configuracion.diasTrial, dias);
});

Then('el IVA del catálogo es {int}', function (iva) {
  assert.strictEqual(numero(this.catalogoSaas.configuracion.ivaPorcentaje), iva);
});

Then('el descuento anual del catálogo es {int}', function (descuento) {
  assert.strictEqual(numero(this.catalogoSaas.configuracion.descuentoAnualPorcentaje), descuento);
});

When('registro una organización de prueba con el plan {string}', function (planCodigo) {
  const altaResultado = alta(planCodigo);
  this.saasOrg = altaResultado.body.organizacionNombre;
  this.saasEmail = altaResultado.body.adminEmail;
  this.saasPassword = altaResultado.body.adminPassword;
  this.saasSuscripcionId = altaResultado.data.suscripcionId;
  this.saasRegistro = altaResultado.data;
});

Then('la prueba informada dura {int} días', function (dias) {
  assert.strictEqual(this.saasRegistro.diasTrial, dias);
});

When('ingreso con el administrador de esa prueba', function () {
  const sesion = login(this.saasEmail, this.saasPassword);
  assert.ok(sesion.success, JSON.stringify(sesion.response));
  this.accessToken = sesion.accessToken;
});

When('intento crear una segunda sede en la suscripción de prueba', function () {
  const n = Date.now();
  this.saasOperacion = POST('/api/centrosAtencion', {
    nombre: `Sede extra ${n}`,
    direccion: `Otra calle ${n}`,
    localidad: 'CABA',
    provincia: 'Buenos Aires',
    telefono: String(4200000000 + (n % 1000000)),
    latitud: -34.61,
    longitud: -58.39
  }, this.accessToken);
});

Then('la operación SaaS es rechazada por límite del plan', function () {
  assert.notStrictEqual(this.saasOperacion.statusCode, 200, JSON.stringify(this.saasOperacion.response));
  const texto = JSON.stringify(this.saasOperacion.response);
  assert.match(texto, /máximo|sede|centro/i);
});

When('intento una escritura del centro en solo lectura', function () {
  this.saasOperacion = POST('/api/pacientes', {
    nombre: 'No',
    apellido: 'Deberia',
    dni: String(Date.now() % 100000000)
  }, this.accessToken);
});

Then('la escritura es rechazada con código {string}', function (codigo) {
  assert.strictEqual(this.saasOperacion.httpStatusCode, 403, JSON.stringify(this.saasOperacion.response));
  assert.strictEqual(this.saasOperacion.response.code, codigo);
  assert.match(this.saasOperacion.response.status_text || '', /solo lectura/i);
});

When('intento pagar la suscripción suspendida', function () {
  this.saasOperacion = POST('/api/saas/mi-suscripcion/pagar', { ciclo: 'MENSUAL' }, this.accessToken);
});

Then('el intento de pago no responde suscripción de solo lectura', function () {
  assert.notStrictEqual(this.saasOperacion.response.code, 'SUSCRIPCION_SOLO_LECTURA');
  assert.match(JSON.stringify(this.saasOperacion.response), /suspendida/i);
});

When('el superadministrador escribe aunque el centro esté suspendido', function () {
  const token = loginSuperadmin();
  this.saasOperacion = POST('/api/especialidades', {
    nombre: `Especialidad suspendida ${Date.now()}`
  }, token);
});

Then('la escritura del superadministrador no queda en solo lectura', function () {
  assert.notStrictEqual(this.saasOperacion.response.code, 'SUSCRIPCION_SOLO_LECTURA',
    JSON.stringify(this.saasOperacion.response));
});

function featuresDelPlan(token) {
  const result = GET('/api/saas/features', token);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  return result.response.data;
}

Then('las funciones de mi plan no incluyen {string}', function (feature) {
  const data = featuresDelPlan(this.accessToken);
  assert.strictEqual(data.aplica, true, JSON.stringify(data));
  assert.ok(!data.features.includes(feature), JSON.stringify(data));
});

Then('las funciones de mi plan incluyen {string}', function (feature) {
  const data = featuresDelPlan(this.accessToken);
  assert.ok(data.features.includes(feature), JSON.stringify(data));
});

When('consulto el panel de auditoría del centro', function () {
  this.saasOperacion = GET('/api/audit/page?page=0&size=5', this.accessToken);
});

Then('la consulta es rechazada porque el plan no incluye {string}', function (feature) {
  assert.strictEqual(this.saasOperacion.httpStatusCode, 403, JSON.stringify(this.saasOperacion.response));
  assert.strictEqual(this.saasOperacion.response.code, 'PLAN_SIN_FEATURE');
  assert.strictEqual(this.saasOperacion.response.data.feature, feature);
  assert.match(this.saasOperacion.response.status_text || '', /Disponible en plan/);
});

Then('la consulta no es rechazada por el plan', function () {
  assert.notStrictEqual(this.saasOperacion.response && this.saasOperacion.response.code, 'PLAN_SIN_FEATURE',
    JSON.stringify(this.saasOperacion.response));
});

When('pago la suscripción de prueba con ciclo {string}', function (ciclo) {
  this.saasOperacion = POST('/api/saas/mi-suscripcion/pagar', { ciclo }, this.accessToken);
  assert.strictEqual(this.saasOperacion.statusCode, 200, JSON.stringify(this.saasOperacion.response));
});

Then('mi suscripción SaaS queda en estado {string}', function (estado) {
  const result = GET('/api/saas/mi-suscripcion', this.accessToken);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  assert.strictEqual(result.response.data.estado, estado);
  assert.ok(result.response.data.mpPreapprovalId);
});

When('ingreso como superadministrador de CheTurno', function () {
  this.accessToken = loginSuperadmin();
});

Then('el listado SaaS incluye la organización de prueba', function () {
  const result = GET(`/api/saas/admin/suscripciones?q=${encodeURIComponent(this.saasOrg)}`, this.accessToken);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  const encontrada = (result.response.data || []).find((item) => item.organizacion && item.organizacion.nombre === this.saasOrg);
  assert.ok(encontrada, JSON.stringify(result.response.data));
  this.saasSuscripcionId = encontrada.id;
});

When('el superadministrador suspende la suscripción de prueba', function () {
  this.saasOperacion = POST(`/api/saas/admin/suscripciones/${this.saasSuscripcionId}/suspender`, { motivo: 'control de prueba' }, this.accessToken);
  assert.strictEqual(this.saasOperacion.statusCode, 200, JSON.stringify(this.saasOperacion.response));
});

Then('la suscripción de prueba queda en estado {string}', function (estado) {
  if (!this.accessToken) {
    assert.strictEqual(this.saasRegistro.estado, estado);
    return;
  }
  const result = GET(`/api/saas/admin/suscripciones/${this.saasSuscripcionId}`, this.accessToken);
  assert.strictEqual(result.statusCode, 200, JSON.stringify(result.response));
  assert.strictEqual(result.response.data.estado, estado);
});

When('el superadministrador reactiva la suscripción de prueba', function () {
  this.saasOperacion = POST(`/api/saas/admin/suscripciones/${this.saasSuscripcionId}/reactivar`, {}, this.accessToken);
  assert.strictEqual(this.saasOperacion.statusCode, 200, JSON.stringify(this.saasOperacion.response));
});

When('el superadministrador cambia la suscripción de prueba al plan {string}', function (plan) {
  this.saasOperacion = POST(`/api/saas/admin/suscripciones/${this.saasSuscripcionId}/cambiar-plan`, { planCodigo: plan }, this.accessToken);
  assert.strictEqual(this.saasOperacion.statusCode, 200, JSON.stringify(this.saasOperacion.response));
});

Then('la suscripción de prueba usa el plan {string}', function (plan) {
  const result = GET(`/api/saas/admin/suscripciones/${this.saasSuscripcionId}`, this.accessToken);
  assert.strictEqual(result.response.data.plan.codigo, plan);
});

When('el superadministrador fija el precio base de {string} en {int}', function (codigo, precio) {
  const catalogo = GET('/api/saas/admin/planes', this.accessToken);
  assert.strictEqual(catalogo.statusCode, 200, JSON.stringify(catalogo.response));
  const plan = planDe(catalogo.response.data, codigo);
  assert.ok(plan);
  plan.precioBaseMensual = precio;
  const guardado = PUT(`/api/saas/admin/planes/${plan.id}`, plan, this.accessToken);
  assert.strictEqual(guardado.statusCode, 200, JSON.stringify(guardado.response));
});

Then('el catálogo público muestra el plan {string} a {int}', function (codigo, precio) {
  const catalogo = GET('/api/saas/planes');
  assert.strictEqual(numero(planDe(catalogo.response.data, codigo).precioBaseMensual), precio);
});

When('el superadministrador restaura los precios y la prueba de referencia', function () {
  restaurarCatalogo(this.accessToken);
  const catalogo = GET('/api/saas/planes');
  assert.strictEqual(numero(planDe(catalogo.response.data, 'PROFESIONAL').precioBaseMensual), 25000);
  assert.strictEqual(catalogo.response.data.configuracion.diasTrial, 30);
});

After({ tags: '@saas' }, function () {
  try {
    restaurarCatalogo(loginSuperadmin());
  } catch (error) {
    console.error('No se pudo restaurar el catálogo SaaS:', error.message);
  }
});

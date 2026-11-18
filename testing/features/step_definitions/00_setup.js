/**
 * Prepara la base antes de la suite y vuelve a sembrar los datos
 * multi-tenant justo antes del primer escenario @multi-tenant.
 * Los features legacy (1a–5a) corren antes en orden alfabético y
 * construyen sus propios datos encima de una base vacía.
 */

const { BeforeAll, Before } = require('@cucumber/cucumber');
const rawRequest = require('sync-request');
const { apiUrl } = require('../../support/config');
const { prepareLegacyBaseline, seedMultiTenant } = require('../../support/db');
const { clearAccessToken, getAccessToken } = require('../../support/auth');

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForBackend(timeoutMs = 120000) {
  const started = Date.now();
  let lastError = 'sin respuesta';
  while (Date.now() - started < timeoutMs) {
    try {
      const res = rawRequest('GET', apiUrl('/'), { timeout: 3000 });
      if (res.statusCode === 200) {
        return;
      }
      lastError = `HTTP ${res.statusCode}`;
    } catch (error) {
      lastError = error.message;
    }
    await sleep(2000);
  }
  throw new Error(`El backend no respondió en ${apiUrl('/')} (${lastError})`);
}

BeforeAll({ timeout: 180000 }, async function () {
  await waitForBackend();
  prepareLegacyBaseline();
  clearAccessToken();
  const token = getAccessToken();
  if (!token) {
    throw new Error('El superadmin de prueba no pudo iniciar sesión después del reset');
  }
  console.log('🧹 Base reiniciada y superadmin de prueba listo');
});

function heredarCentrosSembrados() {
  const res = rawRequest('GET', apiUrl('/api/saas/planes'), { timeout: 20000 });
  if (res.statusCode !== 200) {
    throw new Error(`No se pudieron heredar los centros sembrados: HTTP ${res.statusCode}`);
  }
}

let multiTenantSeeded = false;
let multiTenantSeedError = null;

Before({ tags: '@multi-tenant', timeout: 180000 }, function () {
  if (multiTenantSeeded) {
    return;
  }
  if (multiTenantSeedError) {
    throw multiTenantSeedError;
  }
  try {
    seedMultiTenant();
    heredarCentrosSembrados();
    clearAccessToken();
    multiTenantSeeded = true;
    console.log('🌱 Datos multi-tenant cargados desde staging/userinit.sql');
  } catch (error) {
    multiTenantSeedError = error;
    throw error;
  }
});

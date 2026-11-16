/**
 * Login perezoso del superadmin de prueba.
 * El hash de staging/userinit.sql corresponde a la password "password".
 */

const rawRequest = require('sync-request');
const { apiUrl } = require('./config');

const SUPERADMIN = {
  email: process.env.E2E_SUPERADMIN_EMAIL || 'superadmin@turnero.com',
  password: process.env.E2E_SUPERADMIN_PASSWORD || 'password'
};

let cachedToken = null;

function clearAccessToken() {
  cachedToken = null;
}

function getAccessToken() {
  if (process.env.CUCUMBER_NO_DEFAULT_AUTH === '1') {
    return null;
  }
  if (cachedToken) {
    return cachedToken;
  }
  try {
    const res = rawRequest('POST', apiUrl('/auth/login'), {
      json: SUPERADMIN,
      timeout: 15000,
      headers: { 'Content-Type': 'application/json' }
    });
    const body = JSON.parse(res.getBody('utf8'));
    cachedToken = body.data && body.data.accessToken ? body.data.accessToken : null;
    if (!cachedToken) {
      console.log('⚠️ Login de superadmin sin accessToken:', JSON.stringify(body));
    }
    return cachedToken;
  } catch (error) {
    const detail = error.body ? error.body.toString('utf8') : error.message;
    console.log('⚠️ No se pudo autenticar al superadmin de prueba:', detail);
    return null;
  }
}

module.exports = {
  SUPERADMIN,
  getAccessToken,
  clearAccessToken
};

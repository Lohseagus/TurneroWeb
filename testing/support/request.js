/**
 * Cliente HTTP de los steps legacy.
 * Reescribe hosts viejos (http://backend:8080) hacia API_BASE_URL
 * e intercepta los endpoints de reset que el backend ya no expone.
 */

const rawRequest = require('sync-request');
const { apiUrl } = require('./config');
const { resetDomain, resetEspecialidades } = require('./db');
const { getAccessToken } = require('./auth');

function pathOf(input) {
  const absolute = apiUrl(input);
  try {
    return new URL(absolute).pathname;
  } catch (e) {
    return String(input);
  }
}

function fakeResponse(body, statusCode = 200) {
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return {
    statusCode,
    headers: {},
    getBody(encoding) {
      return encoding ? text : Buffer.from(text);
    }
  };
}

/**
 * Misma firma que sync-request: request(method, url, options).
 * Por defecto adjunta el JWT del superadmin de prueba, porque varios
 * POST/PUT quedaron anotados con @PreAuthorize. Pasar { noAuth: true }
 * para pegarle al endpoint como anónimo.
 */
function request(method, url, options = {}) {
  const pathname = pathOf(url);
  const verb = String(method || 'GET').toUpperCase();

  if (verb === 'DELETE' && (pathname === '/deleteAll' || pathname === '/api/deleteAll')) {
    resetDomain();
    return fakeResponse({
      status_code: 200,
      status_text: 'Datos de dominio reiniciados',
      data: null
    });
  }

  if (verb === 'DELETE' && pathname.endsWith('/especialidades/reset')) {
    resetEspecialidades();
    return fakeResponse({
      status_code: 200,
      status_text: 'Especialidades reiniciadas',
      data: null
    });
  }

  const opts = {
    timeout: 20000,
    ...options,
    headers: { ...(options.headers || {}) }
  };
  delete opts.noAuth;

  if (!options.noAuth && !opts.headers.Authorization && !opts.headers.authorization) {
    const token = getAccessToken();
    if (token) {
      opts.headers.Authorization = `Bearer ${token}`;
    }
  }

  return rawRequest(verb, apiUrl(url), opts);
}

module.exports = request;
module.exports.request = request;
module.exports.rawRequest = rawRequest;

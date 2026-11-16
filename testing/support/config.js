/**
 * Configuración compartida de la suite Cucumber.
 * API_BASE_URL incluye el prefijo /api (default http://localhost:8080/api).
 */

const API_BASE_URL = (process.env.API_BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');

function origin() {
  return API_BASE_URL.replace(/\/api$/, '');
}

/**
 * Resuelve una ruta de la API.
 * Acepta paths relativos (/centrosAtencion), paths ya prefijados (/api/auth/login)
 * y URLs legacy absolutas (http://backend:8080/...).
 */
function apiUrl(input) {
  if (input == null || input === '') {
    return API_BASE_URL;
  }

  let path = String(input);
  const legacyHosts = [
    'http://backend:8080',
    'http://localhost:8080',
    'http://127.0.0.1:8080'
  ];
  for (const host of legacyHosts) {
    if (path.startsWith(host)) {
      path = path.slice(host.length) || '/';
      break;
    }
  }

  if (/^https?:\/\//.test(path)) {
    return path;
  }
  if (!path.startsWith('/')) {
    path = `/${path}`;
  }

  // Endpoints fuera del prefijo /api (health del proceso).
  if (path === '/' || path.startsWith('/health') || path.startsWith('/actuator')) {
    return `${origin()}${path}`;
  }
  if (path.startsWith('/api/')) {
    return `${origin()}${path}`;
  }
  return `${API_BASE_URL}${path}`;
}

const dbConfig = {
  host: process.env.PGHOST || process.env.DB_HOST || '127.0.0.1',
  port: process.env.PGPORT || process.env.DB_PORT || '5432',
  user: process.env.PGUSER || process.env.DB_USERNAME || 'APP',
  password: process.env.PGPASSWORD || process.env.DB_PASSWORD || 'APP',
  database: process.env.PGDATABASE || process.env.DB_NAME || 'labprog'
};

module.exports = {
  API_BASE_URL,
  origin,
  apiUrl,
  dbConfig
};

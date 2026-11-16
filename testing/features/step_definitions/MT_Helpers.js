/**
 * MT_Helpers.js
 * Funciones auxiliares y constantes para tests Multi-Tenant
 * 
 * Este archivo contiene:
 * - Configuración de endpoints
 * - Credenciales de prueba
 * - Funciones de utilidad para HTTP requests
 * - Helpers de autenticación JWT
 */

const request = require('sync-request');
const { apiUrl, API_BASE_URL } = require('../../support/config');

// =====================================================================
// CONFIGURACIÓN BASE
// =====================================================================

const BASE_URL = API_BASE_URL;

const ENDPOINTS = {
  // Autenticación (relativas a API_BASE_URL, que ya incluye /api)
  LOGIN: '/auth/login',
  REFRESH: '/auth/refresh',
  
  // Entidades principales
  CENTROS_ATENCION: '/centrosAtencion',
  ADMINS: '/admins',
  OPERADORES: '/operadores',
  MEDICOS: '/medicos',
  CONSULTORIOS: '/consultorios',
  ESPECIALIDADES: '/especialidades',
  STAFF_MEDICO: '/staff-medico',
  TURNOS: '/turno',
  PACIENTES: '/pacientes',
  OBRAS_SOCIALES: '/obra-social', // Backend usa /obra-social no /obrasSociales
  
  // Endpoints específicos
  MEDICOS_DISPONIBLES: '/medicos/disponibles',
  OPERADORES_ACTIVOS: '/operadores/activos',
  OPERADORES_CREATE_BY_ADMIN: '/operadores/create-by-admin',
  MEDICOS_CREATE_BY_ADMIN: '/medicos/create-by-admin',
  ADMINS_CREATE_OPERADOR: '/admins/operadores',
  DISPONIBILIDADES_MEDICO: '/disponibilidades-medico',
  ESQUEMA_TURNO: '/esquema-turno'
};

// =====================================================================
// CREDENCIALES DE PRUEBA
// =====================================================================

const TEST_USERS = {
  // SUPERADMIN - Acceso global
  SUPERADMIN: {
    email: 'superadmin@turnero.com',
    password: 'password',
    role: 'SUPERADMIN',
    centroAtencionId: null,
    centroNombre: null
  },
  
  // ADMINISTRADORES - Acceso por centro
  ADMIN_CENTRO_1: {
    email: 'admin.santamaria@turnero.com',
    password: 'password',
    role: 'ADMINISTRADOR',
    centroAtencionId: 1,
    centroNombre: 'Clínica Santa María'
  },
  ADMIN_CENTRO_2: {
    email: 'admin.delsur@turnero.com',
    password: 'password',
    role: 'ADMINISTRADOR',
    centroAtencionId: 2,
    centroNombre: 'Clínica del Sur'
  },
  ADMIN_CENTRO_3: {
    email: 'admin.delsol@turnero.com',
    password: 'password',
    role: 'ADMINISTRADOR',
    centroAtencionId: 3,
    centroNombre: 'Consultorios del Sol'
  },
  
  // OPERADORES - Centro 1
  OPERADOR_1_CENTRO_1: {
    email: 'operador1.santamaria@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 1,
    centroNombre: 'Clínica Santa María'
  },
  OPERADOR_2_CENTRO_1: {
    email: 'operador2.santamaria@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 1,
    centroNombre: 'Clínica Santa María'
  },
  
  // OPERADORES - Centro 2
  OPERADOR_1_CENTRO_2: {
    email: 'operador1.delsur@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 2,
    centroNombre: 'Clínica del Sur'
  },
  OPERADOR_2_CENTRO_2: {
    email: 'operador2.delsur@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 2,
    centroNombre: 'Clínica del Sur'
  },
  
  // OPERADORES - Centro 3
  OPERADOR_1_CENTRO_3: {
    email: 'operador1.delsol@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 3,
    centroNombre: 'Consultorios del Sol'
  },
  OPERADOR_2_CENTRO_3: {
    email: 'operador2.delsol@turnero.com',
    password: 'password',
    role: 'OPERADOR',
    centroAtencionId: 3,
    centroNombre: 'Consultorios del Sol'
  },
  
  // MÉDICO - Acceso global
  MEDICO: {
    email: 'medico@turnero.com',
    password: 'password',
    role: 'MEDICO',
    centroAtencionId: null,
    centroNombre: null
  },
  
  // PACIENTES - Acceso global
  PACIENTE_1: {
    email: 'aguspalqui@hotmail.com',
    password: 'password',
    role: 'PACIENTE',
    centroAtencionId: null,
    dni: '43808170'
  },
  PACIENTE_2: {
    email: 'paciente1.santamaria@turnero.com',
    password: 'password',
    role: 'PACIENTE',
    centroAtencionId: null,
    dni: '44444441'
  },
  PACIENTE_3: {
    email: 'paciente1.delsur@turnero.com',
    password: 'password',
    role: 'PACIENTE',
    centroAtencionId: null,
    dni: '44444443'
  }
};

// =====================================================================
// CENTROS DE ATENCIÓN
// =====================================================================

const CENTROS = {
  1: {
    id: 1,
    nombre: 'Clínica Santa María',
    consultorios: ['Consultorio A', 'Consultorio B', 'Consultorio C', 'Consultorio D']
  },
  2: {
    id: 2,
    nombre: 'Clínica del Sur',
    consultorios: ['Consultorio Sur 1', 'Consultorio Sur 2', 'Consultorio Sur 3', 'Consultorio Sur 4']
  },
  3: {
    id: 3,
    nombre: 'Consultorios del Sol',
    consultorios: ['Consultorio Sol 1', 'Consultorio Sol 2', 'Consultorio Sol 3', 'Consultorio Sol 4']
  }
};

// =====================================================================
// FUNCIONES HTTP HELPERS
// =====================================================================

/**
 * Realiza una petición HTTP con manejo de errores
 * @param {string} method - GET, POST, PUT, DELETE
 * @param {string} endpoint - Ruta del endpoint
 * @param {object} options - { json, headers, token }
 * @returns {object} { statusCode, response }
 */
function httpRequest(method, endpoint, options = {}) {
  const url = apiUrl(endpoint);
  const requestOptions = {
    retry: false,
    timeout: 5000
  };
  
  // Headers base
  requestOptions.headers = {
    'Content-Type': 'application/json',
    ...options.headers
  };
  
  // Agregar token JWT si existe
  if (options.token) {
    requestOptions.headers['Authorization'] = `Bearer ${options.token}`;
  }
  
  // Body JSON
  if (options.json) {
    requestOptions.json = options.json;
  }
  
  try {
    const res = request(method, url, requestOptions);
    const httpStatusCode = res.statusCode;
    let response = {};
    
    try {
      const bodyText = res.getBody('utf8');
      response = bodyText ? JSON.parse(bodyText) : {};
    } catch (e) {
      // Si no es JSON válido, devolver body crudo
      const bodyText = res.getBody('utf8');
      response = { raw: bodyText };
    }
    
    // Extract DataPackage status code - este es el status real de la aplicación
    const statusCode = response.status_code || httpStatusCode;
    
    console.log(`\n🔹 HTTP REQUEST`);
  console.log(`   ${method} ${url}`);
  if (options.json) {
    console.log(`   Body:`, JSON.stringify(options.json, null, 2));
  }
  if (options.token) {
    console.log(`   Auth: Bearer ${options.token.substring(0, 20)}...`);
  }

    // LOGGING RESPUESTA - mostrar ambos status
    console.log(`✅ RESPONSE: HTTP ${httpStatusCode}, DataPackage ${statusCode}`);
    if (statusCode !== 200 && statusCode !== 201) {
      console.log(`   Error:`, JSON.stringify(response, null, 2));
    }
    
    return { statusCode, response, httpStatusCode };
  } catch (error) {
    // Manejar errores HTTP (4xx, 5xx) - sync-request lanza excepción en algunos casos
    if (error.statusCode) {
      let response = {};
      try {
        const bodyString = error.body ? error.body.toString('utf8') : '';
        response = bodyString ? JSON.parse(bodyString) : {};
      } catch (e) {
        response = { error: error.message };
      }
      
      // Extract DataPackage status code from error response too - este es el status real
      const httpStatusCode = error.statusCode;
      const statusCode = response.status_code || httpStatusCode;
      
      console.log(`❌ RESPONSE: HTTP ${httpStatusCode}, DataPackage ${statusCode}`);
      console.log(`   Error:`, JSON.stringify(response, null, 2));
      
      return { statusCode, response, httpStatusCode };
    }
    // Error de conexión u otro error
    console.error('❌ HTTP Request Error:', error.message);
    throw error;
  }
}

/**
 * Helper para GET requests
 */
function GET(endpoint, token = null) {
  return httpRequest('GET', endpoint, { token });
}

/**
 * Helper para POST requests
 */
function POST(endpoint, json, token = null) {
  return httpRequest('POST', endpoint, { json, token });
}

/**
 * Helper para PUT requests
 */
function PUT(endpoint, json, token = null) {
  return httpRequest('PUT', endpoint, { json, token });
}

/**
 * Helper para DELETE requests
 */
function DELETE(endpoint, token = null) {
  return httpRequest('DELETE', endpoint, { token });
}

// =====================================================================
// FUNCIONES DE AUTENTICACIÓN
// =====================================================================

/**
 * Realiza login y retorna tokens
 * @param {string} email 
 * @param {string} password 
 * @returns {object} { success, accessToken, refreshToken, role, centroAtencionId, fullName, response }
 */
function login(email, password) {
  const result = POST(ENDPOINTS.LOGIN, { email, password });
  
  if (result.statusCode === 200 && result.response.data) {
    const data = result.response.data;
    return {
      success: true,
      statusCode: result.statusCode,
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      role: data.role,
      allRoles: data.allRoles,
      centroAtencionId: data.centroAtencionId,
      fullName: data.fullName,
      email: data.email,
      response: result.response
    };
  }
  
  return {
    success: false,
    statusCode: result.statusCode,
    response: result.response
  };
}

/**
 * Refresca el access token usando el refresh token
 * @param {string} refreshToken 
 * @returns {object} { success, accessToken, response }
 */
function refreshAccessToken(refreshToken) {
  const result = POST(ENDPOINTS.REFRESH, { refreshToken });
  
  if (result.statusCode === 200 && result.response.data) {
    return {
      success: true,
      statusCode: result.statusCode,
      accessToken: result.response.data.accessToken,
      centroAtencionId: result.response.data.centroAtencionId,
      response: result.response
    };
  }
  
  return {
    success: false,
    statusCode: result.statusCode,
    response: result.response
  };
}

// =====================================================================
// FUNCIONES DE VALIDACIÓN
// =====================================================================

/**
 * Verifica que la respuesta tenga estructura estándar
 * @param {object} response 
 * @returns {boolean}
 */
function hasStandardStructure(response) {
  return response 
    && typeof response.status_code === 'number'
    && typeof response.status_text === 'string'
    && response.hasOwnProperty('data');
}

/**
 * Verifica que todos los elementos de un array pertenecen a un centro
 * @param {array} items - Array de objetos
 * @param {number} centroId - ID del centro esperado
 * @param {string} fieldName - Nombre del campo que contiene el centro ID
 * @returns {boolean}
 */
function allBelongToCenter(items, centroId, fieldName = 'centroAtencionId') {
  if (!Array.isArray(items)) return false;
  return items.every(item => item[fieldName] === centroId);
}

/**
 * Verifica que ningún elemento pertenece a un centro específico
 * @param {array} items 
 * @param {number} centroId 
 * @param {string} fieldName 
 * @returns {boolean}
 */
function noneFromCenter(items, centroId, fieldName = 'centroAtencionId') {
  if (!Array.isArray(items)) return true;
  return items.every(item => item[fieldName] !== centroId);
}

/**
 * Filtra elementos que pertenecen a un centro
 * @param {array} items 
 * @param {number} centroId 
 * @param {string} fieldName 
 * @returns {array}
 */
function filterByCenter(items, centroId, fieldName = 'centroAtencionId') {
  if (!Array.isArray(items)) return [];
  return items.filter(item => item[fieldName] === centroId);
}

// =====================================================================
// FUNCIONES DE FECHAS
// =====================================================================

/**
 * Genera una fecha futura en formato dd-MM-yyyy
 * Siempre retorna miércoles o jueves (días con disponibilidad)
 * @param {number} daysFromNow 
 * @returns {string}
 */
function getFutureDate(daysFromNow = 7) {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  
  // Solo permitir miércoles (3) o jueves (4) - días con disponibilidad
  // 0=Domingo, 1=Lunes, 2=Martes, 3=Miércoles, 4=Jueves, 5=Viernes, 6=Sábado
  const diaSemana = date.getDay();
  
  if (diaSemana === 0) { // Domingo -> Miércoles
    date.setDate(date.getDate() + 3);
  } else if (diaSemana === 1) { // Lunes -> Miércoles
    date.setDate(date.getDate() + 2);
  } else if (diaSemana === 2) { // Martes -> Miércoles
    date.setDate(date.getDate() + 1);
  } else if (diaSemana === 5) { // Viernes -> Miércoles siguiente
    date.setDate(date.getDate() + 5);
  } else if (diaSemana === 6) { // Sábado -> Miércoles siguiente
    date.setDate(date.getDate() + 4);
  }
  // Si ya es miércoles (3) o jueves (4), dejar como está
  
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  return `${year}-${month}-${day}`;
}

/**
 * Genera fecha futura que cae en un día específico de la semana
 * @param {number} targetDay - 0=Domingo, 1=Lunes, 2=Martes, etc.
 * @param {number} weeksAhead - Cuántas semanas hacia adelante buscar
 * @returns {string}
 */
function getFutureDateOnDay(targetDay, weeksAhead = 1) {
  const date = new Date();
  const currentDay = date.getDay();
  
  // Calcular días hasta el día objetivo
  let daysToAdd = targetDay - currentDay;
  if (daysToAdd <= 0) {
    daysToAdd += 7; // Si ya pasó esta semana, ir a la próxima
  }
  
  // Agregar semanas adicionales si se especificó
  daysToAdd += (weeksAhead - 1) * 7;
  
  date.setDate(date.getDate() + daysToAdd);
  
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  return `${year}-${month}-${day}`;
}

/**
 * Genera fecha de hoy en formato dd-MM-yyyy
 * @returns {string}
 */
function getToday() {
  return getFutureDate(0);
}

// =====================================================================
// EXPORTS
// =====================================================================

module.exports = {
  // Configuración
  BASE_URL,
  ENDPOINTS,
  TEST_USERS,
  CENTROS,
  
  // HTTP helpers
  httpRequest,
  GET,
  POST,
  PUT,
  DELETE,
  
  // Autenticación
  login,
  refreshAccessToken,
  
  // Validación
  hasStandardStructure,
  allBelongToCenter,
  noneFromCenter,
  filterByCenter,
  
  // Fechas
  getFutureDate,
  getFutureDateOnDay,
  getToday
};

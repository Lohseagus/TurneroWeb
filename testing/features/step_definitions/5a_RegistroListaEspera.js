const { Given, When, Then, AfterAll } = require("@cucumber/cucumber");
const assert = require("assert");
const rawRequest = require("sync-request");
const { Client } = require("pg");
const { apiUrl, dbConfig: dbEnv } = require("../../support/config");

// --- CONFIGURACIÓN ---
const dbConfig = {
  user: dbEnv.user,
  host: dbEnv.host,
  database: dbEnv.database,
  password: dbEnv.password,
  port: Number(dbEnv.port),
};

function request(method, path, options) {
  return rawRequest(method, apiUrl(path), options);
}

// --- ESTADO DEL TEST ---
let usuarioData = {};
let activationToken = null;
let authToken = null; // JWT
let lastStatus = null;

// --- UTILIDADES ---
function generarUsuarioAleatorio() {
  const timestamp = Date.now();
  return {
    nombre: "Integration",
    apellido: "Test",
    dni: Math.floor(10000000 + Math.random() * 90000000).toString(),
    email: `flow_test_${timestamp}@turnero.com`,
    password: "Password123!",
    telefono: "1122334455",
  };
}

async function obtenerTokenDeBaseDeDatos(email) {
  const client = new Client(dbConfig);
  try {
    await client.connect();
    const query = `
            SELECT t.token 
            FROM account_activation_tokens t
            JOIN users u ON t.user_id = u.id
            WHERE u.email = $1
            ORDER BY t.expires_at DESC 
            LIMIT 1;
        `;
    const res = await client.query(query, [email]);
    return res.rows[0] ? res.rows[0].token : null;
  } catch (err) {
    console.error("❌ Error DB:", err.message);
    return null;
  } finally {
    try {
      await client.end();
    } catch (e) {}
  }
}

// ===========================
// PASOS DEL ESCENARIO
// ===========================

Given("que existe un sistema de gestión de turnos funcionando", function () {
  // Health check simple
});

// 1. REGISTRO
Given("que un nuevo paciente se registra en el sistema", function () {
  usuarioData = generarUsuarioAleatorio();
  console.log(`📝 Registrando usuario: ${usuarioData.email}`);

  try {
    const res = request("POST", "/api/auth/register", {
      json: usuarioData,
    });
    const body = JSON.parse(res.getBody("utf8"));
    lastStatus = body.status_code || res.statusCode;
    assert.strictEqual(
      lastStatus,
      200,
      `Falló registro. Status: ${lastStatus} ${body.status_text || ""}`
    );
    console.log("✅ Registro enviado correctamente.");
  } catch (err) {
    assert.fail(`Excepción en registro: ${err.message}`);
  }
});

// 2. OBTENCIÓN DE TOKEN
When(
  "el sistema genera el token de activación",
  { timeout: 10000 },
  async function () {
    await new Promise((r) => setTimeout(r, 2000));
    activationToken = await obtenerTokenDeBaseDeDatos(usuarioData.email);
    assert.ok(activationToken, "❌ No se pudo recuperar el token de la BD.");
    console.log(`🎟️ Token recuperado: ${activationToken.substring(0, 10)}...`);
  }
);

// 3. ACTIVACIÓN
When("el paciente valida su email con dicho token", function () {
  try {
    const res = request("POST", "/api/auth/activate-account", {
      json: { token: activationToken },
    });
    const body = JSON.parse(res.getBody("utf8"));
    lastStatus = body.status_code || res.statusCode;
    assert.strictEqual(
      lastStatus,
      200,
      `Falló la activación. Status: ${lastStatus} ${body.status_text || ""}`
    );
    console.log("✅ Cuenta activada exitosamente.");
  } catch (err) {
    assert.fail(`Excepción activando cuenta: ${err.message}`);
  }
});

// 4. LOGIN
When("inicia sesión con sus nuevas credenciales", function () {
  try {
    const res = request("POST", "/api/auth/login", {
      json: {
        email: usuarioData.email,
        password: usuarioData.password,
      },
    });

    const body = JSON.parse(res.getBody("utf8"));
    assert.strictEqual(body.status_code || res.statusCode, 200, `Login falló: ${body.status_text || ""}`);

    if (body.data && body.data.accessToken) {
      authToken = body.data.accessToken;
    } else {
      authToken = body.accessToken;
    }

    assert.ok(authToken, "No se recibió JWT en login");
    console.log("🔑 Login exitoso.");
  } catch (err) {
    assert.fail(`Excepción en login: ${err.message}`);
  }
});

// 5. LISTA DE ESPERA
When(
  "solicita unirse a la lista de espera para {string}",
  function (especialidadNombre) {
    try {
      const especialidades = JSON.parse(
        request("GET", "/api/especialidades", {
          headers: { Authorization: `Bearer ${authToken}` },
        }).getBody("utf8")
      ).data || [];
      const centros = JSON.parse(
        request("GET", "/api/centrosAtencion", {
          headers: { Authorization: `Bearer ${authToken}` },
        }).getBody("utf8")
      ).data || [];
      const pacientes = JSON.parse(
        request("GET", `/api/pacientes/by-email/${encodeURIComponent(usuarioData.email)}`, {
          headers: { Authorization: `Bearer ${authToken}` },
        }).getBody("utf8")
      );

      const especialidad = especialidades.find(
        (esp) => esp.nombre && esp.nombre.toLowerCase() === especialidadNombre.toLowerCase()
      );
      const centro = centros[0];
      const pacienteId = pacientes.data && (pacientes.data.id || pacientes.data.pacienteId);

      assert.ok(especialidad, `No existe la especialidad ${especialidadNombre}`);
      assert.ok(centro, "No hay centros de atención para la lista de espera");
      assert.ok(pacienteId, `No se encontró el paciente ${usuarioData.email}`);

      const res = request("POST", "/api/lista-espera", {
        headers: {
          Authorization: `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
        json: {
          pacienteId,
          especialidadId: especialidad.id,
          centroAtencionId: centro.id,
          urgenciaMedica: "BAJA",
          estado: "PENDIENTE",
        },
      });

      const body = JSON.parse(res.getBody("utf8"));
      lastStatus = body.status_code || res.statusCode;
      this.listaEsperaBody = body;

      if (lastStatus >= 400) {
        console.log("⚠️ Error Backend (Body):", body);
      }
    } catch (err) {
      if (!err.statusCode) {
        throw err;
      }
      lastStatus = err.statusCode;
      if (err.body) {
        console.log("⚠️ Error API:", err.body.toString());
      } else {
        console.log("⚠️ Error API:", err.message);
      }
    }
  }
);
// 6. VALIDACIÓN FINAL
Then("el sistema confirma su ingreso a la lista exitosamente", function () {
  const texto = (this.listaEsperaBody && this.listaEsperaBody.status_text) || "";
  assert.strictEqual(
    lastStatus,
    200,
    `Falló ingreso a lista. Status: ${lastStatus} ${texto}`
  );
  console.log("🎉 Flujo completo verificado correctamente.");
});

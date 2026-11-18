# Auditoría de seguridad del backend — CheTurno

Fecha: 17 de noviembre de 2026. Base auditada: `e91df79` en `master`.
Alcance: backend Spring Boot (`backend/`), más el borde que lo publica (Caddy, nginx, compose, workflows) y el cruce con el cliente Angular. Esta entrega es el informe. No se modificó código de aplicación y no se mergeó nada.

El inventario completo de endpoints está en [ENDPOINTS.md](ENDPOINTS.md) (329 handlers).

## Cómo se leyó

Se recorrieron `SecurityConfig`, los presenters, los servicios de turno, paciente, operador, agenda, HCE y SaaS, el manejo de JWT, los tokens de mail, los adjuntos y la configuración que viaja en el JAR y en `docker-compose.prod.yml`.

Donde hizo falta contrastar con el sitio real, se usaron solo GET y OPTIONS contra `https://cheturno.site`. No se hizo login, no se probó la contraseña de seed y no se mandó ningún POST.

No se pudo levantar el backend en esta máquina: no hay Docker, no hay Maven y no hay Postgres. Por eso no hay una corrida local con un token por rol. La matriz de “quién llega” sale de la primera regla que matchea en `SecurityConfig` con `security.dev.mode=false`, cruzada con `@PreAuthorize` y con la jerarquía de roles. Los IDOR salen de leer el servicio: si el método no compara titular ni sede, se marca como tal.

`npm audit` se corrió sobre el lockfile del frontend y de `testing/`. No se corrió `mvn dependency:tree` ni un escáner de CVE de Java, así que este informe no inventa CVEs de Spring.

## Resumen

| Severidad | Cantidad |
| --- | --- |
| Crítica | 2 |
| Alta | 16 |
| Media | 20 |
| Baja | 8 |
| Info | 8 |

Lo que más duele, en orden: secretos y cuentas por defecto embebidos en el JAR que el compose de producción no pisa; historia clínica y turnos leíbles o modificables de otra persona o de otra sede; la suscripción vencida que no frena la mayoría de las escrituras.

Controles que sí están: BCrypt, registro público solo como `PACIENTE`, HCE con `HceAccessGuard` por rol primario, webhook de Mercado Pago que hoy rechaza todo hasta que exista el HMAC, CORS de producción que rechaza un origen ajeno, y el backend sin puerto publicado (solo Caddy en 80/443).

## Issues que había que confirmar

| Sospecha | Resultado |
| --- | --- |
| `GET /api/turno/paciente/{id}` le niega al PACIENTE sus turnos | Confirmado con `security.dev.mode=false`. La ruta cae en `/api/turno/**`, que pide `MEDICO`, `OPERADOR` o `ADMINISTRADOR`. En el sitio, un GET anónimo a `/api/turno/paciente/1` respondió 403, o sea que producción no está en modo desarrollo. |
| `/api/turno/historial/{pacienteId}` sin chequeo de titular | Confirmado. La regla deja pasar a cualquier rol (heredan `PACIENTE`) y el servicio solo mira que el id exista. |
| El refresh no valida el tipo de token | A medias, y al revés de como sonaba. `POST /api/auth/refresh` sí exige `tokenType=refresh` (`JwtTokenProvider.validateRefreshToken`, líneas 195-199, usado en `AuthController` 160-161). Un access token no renueva la sesión. El filtro de acceso, en cambio, acepta un refresh como Bearer: `validateToken` solo mira subject y vencimiento. |

## Jerarquía real

En `SecurityConfig.roleHierarchy` (líneas 130-137) y en el enum `Role`:

```
SUPERADMIN > ADMINISTRADOR > MEDICO > PACIENTE
SUPERADMIN > ADMINISTRADOR > OPERADOR > PACIENTE
```

`MEDICO` y `OPERADOR` son hermanos. No existe la cadena `OPERADOR > MEDICO`. Un médico no entra a lo que pide `OPERADOR`. Un operador no entra a lo que pide `MEDICO`. Los dos, más el administrador y el superadmin, sí entran a todo lo marcado `hasRole("PACIENTE")`.

Eso convierte varias rutas “del paciente” en rutas de todo el personal, y esas rutas casi nunca comparan el id del path con el titular.

Encima, `User.tieneAccesoGlobal()` (líneas 202-203) trata al `PACIENTE` igual que al `SUPERADMIN`: `TenantContext.getFilteredCentroId()` devuelve null y no filtra. Está pensado para que el paciente saque turno en cualquier sede. El efecto es que cualquier query “filtrada por sede” se abre de par en par si algún día un paciente llega a ese método.

## Verificación en cheturno.site

Solo lectura. 17 de noviembre de 2026.

| Prueba | Resultado |
| --- | --- |
| `GET /api/saas/planes` sin `Origin` | 200. Cuerpo con `status_code`, `status_text`, `data`. |
| El mismo GET con `Origin: https://evil.example` | 403, sin `Access-Control-Allow-Origin`. |
| El mismo GET con `Origin: https://cheturno.site` y con `https://www.cheturno.site` | 200, `access-control-allow-origin` igual al origen, `access-control-allow-credentials: true`. |
| `OPTIONS /api/auth/login` con origen del sitio | 200, métodos `GET,POST,PUT,DELETE,OPTIONS,PATCH`, `max-age: 3600`. |
| `GET /api/turno/paciente/1` y `GET /api/turno/historial/1` sin token | 403. El modo desarrollo no está activo en este deploy. |
| `GET /api/saas/planes` con `Authorization: Bearer` basura | 401. Un header inválido tapa también un endpoint público. |
| `HEAD /api/saas/planes` | 403. La regla es solo `GET`. |
| `GET /` de la SPA | 200, `server: nginx/1.31.8`, `via: 1.1 Caddy`. No hay CSP, HSTS, `X-Frame-Options` ni `nosniff` en el HTML. |
| `GET /api/saas/planes` (respuesta de API) | Spring manda `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY`. No hay `Strict-Transport-Security`: el backend ve HTTP detrás de Caddy. |
| `GET /actuator` y `GET /health` | 200 con el HTML de la SPA. Caddy no reenvía esas rutas al backend. |

## Hallazgos

Cada uno: id, severidad, dónde, evidencia, impacto, arreglo, esfuerzo (S/M/L).

### Crítica

#### SEC-C1 — Contraseña de mail embebida y usada si no hay variable

- **Dónde:** `backend/src/main/resources/application.properties:43`. `docker-compose.prod.yml` no define `MAIL_PASSWORD` ni `MAIL_USERNAME`.
- **Evidencia:** el default de `spring.mail.password` es un app password de Gmail (no se copia acá). El compose de producción inyecta `JWT_SECRET` y `SECURITY_DEV_MODE`, pero no el mail. Quien arranca con ese compose usa el valor del JAR.
- **Impacto:** la casilla `soporte.cheturno@gmail.com` queda usable para mandar mails de activación, recuperación y credenciales. Con el debug SMTP prendido por default (SEC-M11) el contenido de esos mails puede ir a los logs del contenedor.
- **Arreglo:** sacar el default, rotar el app password en Google, poner `MAIL_PASSWORD` solo en el `.env` del servidor y `MAIL_DEBUG=false` en el compose.
- **Esfuerzo:** S.

#### SEC-C2 — SUPERADMIN de seed con contraseña en el repo

- **Dónde:** `application.properties:65-67`, `AdminInitializer.java:25-86`.
- **Evidencia:** `admin.seed.enabled` defaultea a `true`. Email `admin@turneroweb.com`. La contraseña está en el properties y otra vez como default del `@Value` en el initializer (no se copia acá). Si el mail ya existe, no la resetea: solo la crea la primera vez. El compose de producción no pone `ADMIN_SEED_ENABLED=false`.
- **Impacto:** en una base creada con este default, hay un `SUPERADMIN` global, con mail verificado, y la contraseña está en el historial de git. Desde ahí se llega a roles, mails arbitrarios (`/api/email/**`) y al admin de SaaS. No se intentó el login en producción: no se sabe si alguien ya la cambió.
- **Arreglo:** default `enabled=false`, exigirlo apagado en el compose, rotar esa cuenta si sigue con la clave publicada, y no loguear la contraseña en el arranque (hoy no la imprime, pero el warn de “cambiala” no alcanza).
- **Esfuerzo:** S.

### Alta

#### SEC-A1 — El modo de seguridad nace abierto

- **Dónde:** `application.properties:82`, `SecurityConfig.java:150-213`.
- **Evidencia:** `security.dev.mode=${SECURITY_DEV_MODE:true}`. En `true`, `anyRequest().permitAll()`. `docker-compose.prod.yml:44` pone `SECURITY_DEV_MODE=false`. El GET anónimo a `/api/turno/paciente/1` en el sitio dio 403, así que este deploy está cerrado.
- **Impacto:** cualquier arranque del JAR sin la variable (un perfil nuevo, un compose de prueba que se promueve, un `java -jar`) publica toda la API, HCE incluida.
- **Arreglo:** default `false`. Si el perfil es prod y el flag quedó en `true`, no arrancar.
- **Esfuerzo:** S.

#### SEC-A2 — Secreto JWT de ejemplo como fallback

- **Dónde:** `application.properties:22`, `JwtTokenProvider.java:32-33`.
- **Evidencia:** el default es la clave de muestra de la documentación de jjwt (64 hex). Firma HS256. El compose de producción sí inyecta `JWT_SECRET` desde `.env`. Si esa variable no está en el entorno del proceso y tampoco la pisa compose, el JAR firma con la clave pública.
- **Impacto:** con esa clave se fabrica un access token de cualquier usuario, `SUPERADMIN` incluido.
- **Arreglo:** sin default. Fallar el arranque si falta o si es corta. Rotar si algún ambiente llegó a usarla. No hace falta pegarla en los tests: el test `SaasAdminSecurityTest` hoy repite el mismo literal.
- **Esfuerzo:** S.

#### SEC-A3 — Un refresh token sirve como access token

- **Dónde:** `JwtTokenProvider.java:165-169` y `187-190`, `JwtAuthenticationFilter.java:62-63`.
- **Evidencia:** el refresh pone `tokenType=refresh` y vive 7 días (`604800000` ms). El access no pone tipo. `validateToken` no rechaza `tokenType=refresh`. El filtro autentica con `validateToken`.
- **Impacto:** robar el refresh (está en `localStorage`, ver SEC-M10) da 7 días de API, no 15 minutos. El camino inverso está bien: el access no pasa `validateRefreshToken`.
- **Arreglo:** claim `tokenType=access` en el access, y rechazarlo en el filtro si el tipo no es `access`. Test de regresión con el refresh en `Authorization`.
- **Esfuerzo:** S.

#### SEC-A4 — No hay logout, ni denylist, ni rotación de refresh

- **Dónde:** `AuthController.refreshToken` (líneas 168-189) devuelve el mismo refresh. No hay `POST /logout`. Cambiar la contraseña (`PasswordResetService`, `PasswordService`) solo actualiza el hash.
- **Evidencia:** el filtro tampoco mira `enabled` ni `emailVerified` después de validar la firma (`JwtAuthenticationFilter.java:60-71`).
- **Impacto:** un token robado, o uno emitido antes de deshabilitar la cuenta o de cambiar la clave, sigue sirviendo hasta vencer. El refresh, una semana, y se puede reusar todas las veces que se quiera.
- **Arreglo:** `tokenVersion` (o `pwdChangedAt`) adentro del JWT, chequeado en el filtro. Rotar el refresh y guardar el jti vigente. Al cambiar clave o deshabilitar, subir la versión.
- **Esfuerzo:** L.

#### SEC-A5 — Historial de turnos de cualquier paciente

- **Dónde:** `SecurityConfig.java:252`, `TurnoPresenter.java:807-1082`, `TurnoService.getHistorialPaciente` (alrededor de 2746 y 2808).
- **Evidencia:** `/api/turno/historial/**` es `hasRole("PACIENTE")`, o sea los cinco roles. El servicio hace `existsById(pacienteId)` y sigue. El claim `pacienteId` que el JWT sí arma (`JwtTokenProvider.java:149`) no se consulta. Misma omisión en filtrado, búsqueda, estadísticas, próximos, cancelados, pendientes, CSV y `GET /api/turno/historial/detalle/{turnoId}`.
- **Impacto:** enumerar ids devuelve turnos, médicos, sedes y el CSV de otro paciente. Es dato de salud.
- **Arreglo:** si el rol primario es `PACIENTE`, el id tiene que ser el del token. Staff: otra ruta, filtrada por sede y por relación de atención.
- **Esfuerzo:** M.

#### SEC-A6 — El paciente no puede ver sus turnos por `/paciente/{id}`, y el staff ve los de cualquier sede

- **Dónde:** `SecurityConfig.java:270`, `TurnoPresenter.java:88-91`, `TurnoService.java:163-166`.
- **Evidencia:** no hay una regla anterior para `/api/turno/paciente/**`. Cae en `/api/turno/**`. `findByPaciente_Id` no filtra sede ni titular.
- **Impacto:** el paciente, en producción, recibe 403 en el endpoint que parece suyo (el front termina usando el historial, que es el IDOR de SEC-A5). Un médico u operador de la sede A lee los turnos de ese paciente en la sede B.
- **Arreglo:** `GET /api/turno/mios` armado desde el token, y en la variante de staff forzar `getFilteredCentroId()` de verdad (sin tratar al paciente como global).
- **Esfuerzo:** M.

#### SEC-A7 — Leer, borrar o cambiar un turno de otra sede si se conoce el id

- **Dónde:** `TurnoService.findById` (158-160), `delete` (405-426), `confirmarTurno` (606-622), `completarTurno` (1679-1691). `validarAccesoCentroTurno` (528-541) solo se llama desde `save` cuando el id ya existe (183) y desde `cancelarTurno` (446).
- **Evidencia:** confirmar, completar, borrar, asistencia, reagendar, cambio de estado y el GET por id no pasan por ese método. El delete además traga el error de auditoría y borra igual.
- **Impacto:** operador o médico de la sede A confirma, completa o borra un turno de la sede B. Borrar es físico (`deleteById`).
- **Arreglo:** una sola guarda al empezar cualquier operación por id. El delete no debería commitear si la auditoría falla, o al revés: la auditoría no puede ser opcional en un dato clínico.
- **Esfuerzo:** M.

#### SEC-A8 — Búsqueda y export toman la sede del cliente

- **Dónde:** `TurnoService` en el armado del filtro de búsqueda y de `findForExport` (el `centroAtencionId` sale del DTO, no de `TenantContext`). `ExportPresenter` del reporte de atención recibe `centroAtencionId` por query.
- **Evidencia:** `findByPage` sí usa `getFilteredCentroId()` (línea 387). Search, filtrar y los POST/GET de `/api/turno/export/*` y `/api/export/turnos/*` no pisan el centro si el cliente manda otro.
- **Impacto:** un operador exporta CSV, HTML o PDF de turnos de otra sede, con nombre y apellido de pacientes.
- **Arreglo:** si `getFilteredCentroId()` no es null, ese es el único centro válido. Rechazar el mismatch en vez de ignorarlo.
- **Esfuerzo:** M.

#### SEC-A9 — Notificaciones de otro paciente

- **Dónde:** `NotificacionPresenter.java:31-89`. Regla: `/api/notificaciones/**` con `hasRole("PACIENTE")` (`SecurityConfig.java:250`).
- **Evidencia:** listar, contar, marcar leída y borrar usan el `pacienteId` del path. No se compara con el token.
- **Impacto:** cualquier usuario autenticado lee y borra avisos de otro paciente (turnos, cancelaciones).
- **Arreglo:** ignorar el id del path y usar el paciente del usuario. Staff no debería entrar por esta ruta.
- **Esfuerzo:** S.

#### SEC-A10 — Encuesta de un turno ajeno

- **Dónde:** `EncuestaPacientePresenter.java:40-114`, con `@PreAuthorize("hasRole('PACIENTE')")` que el propio comentario dice que deja pasar a admin y operador.
- **Evidencia:** busca el turno por id y muestra o graba la respuesta. No mira si el turno es del que llama.
- **Impacto:** se lee el contexto del turno y se puede responder la encuesta de otro, o pisarla.
- **Arreglo:** el rol primario `PACIENTE` solo opera su turno. El resto, por el módulo de admin.
- **Esfuerzo:** S.

#### SEC-A11 — La agenda “de todos” filtra mal la sede y en días excepcionales devuelve el nombre del paciente

- **Dónde:** `AgendaPresenter.obtenerTodosLosEventos` (líneas 94-148), regla `GET /api/agenda/**` = `hasRole("PACIENTE")`. `AgendaService` método privado `asignarDatosPaciente` (471-486).
- **Evidencia:** el centro del query se respeta. Solo si viene vacío y el rol es administrador u operador se reemplaza por el del usuario. Un paciente o un médico que no manda centro ve los esquemas de todas las sedes. En el handler de día excepcional, el slot ocupado copia `pacienteId`, nombre y apellido.
- **Impacto:** dato de salud en un GET que el paciente puede pegar. Un admin de la sede A pide `centroId` de la sede B y la guarda no lo frena.
- **Arreglo:** forzar la sede del staff siempre. Para el paciente, devolver el DTO público (`TurnoPublicoDTO`), que ya existe y no trae paciente. El slot excepcional no debería cargar el nombre.
- **Esfuerzo:** M.

#### SEC-A12 — Suscripción vencida no deja la clínica en solo lectura

- **Dónde:** `SuscripcionAccessService.assertOperacion` (líneas 52-68). Se llama en el alta de turno, médico, operador, consultorio, staff, lista de espera y sede. No está en HCE, ni en pacientes, ni en confirmar/cancelar/reagendar, ni en config, ni en encuestas admin.
- **Evidencia:** si el centro no tiene suscripción, el método retorna sin bloquear (líneas 54-56). `EstadoSuscripcion.permiteEscribir()` solo deja `TRIAL` y `ACTIVA`, pero eso no se consulta en la mayoría de los POST.
- **Impacto:** una organización morosa, vencida o suspendida sigue cargando historia clínica, adjuntos, pacientes y cambios de turno. Un centro viejo nunca vinculado no tiene tope ni bloqueo.
- **Arreglo:** un filtro o aspecto para POST/PUT/PATCH/DELETE, con lista blanca (login, pago, lectura, webhook). Los centros sin suscripción tienen que quedar explícitos: o se vinculan, o no escriben.
- **Esfuerzo:** L.

#### SEC-A13 — `GET` y `PUT /api/admins` devuelven el hash de la contraseña

- **Dónde:** `AdminPresenter.java:37-38` y `77`. `User.java:36` y `263-264`.
- **Evidencia:** se serializa la entidad `User`. Tiene getter de `hashedPassword` (Lombok) y `getPassword()` de `UserDetails`, que devuelve el mismo hash. No hay `@JsonIgnore`.
- **Impacto:** un `SUPERADMIN` (o quien llegue a esa ruta) se baja hashes BCrypt. Con la política de 6 caracteres (SEC-B1) varios se parten offline.
- **Arreglo:** DTO de admin sin secreto, y `@JsonIgnore` en el hash y en `getPassword` por las dudas.
- **Esfuerzo:** S.

#### SEC-A14 — El estado del turno se setea desde el body

- **Dónde:** `TurnoService.toEntity`, líneas 1099-1104, y `save` persiste esa entidad.
- **Evidencia:** si `TurnoDTO.estado` viene informado, se hace `EstadoTurno.valueOf` y se guarda. No pasa por confirmar, completar ni cancelar.
- **Impacto:** un operador crea o edita un turno directo en `COMPLETO` o `CONFIRMADO`, se saltea ventanas, motivos y la suscripción (el `assertOperacion` de turno corre en el alta, no en esta transición).
- **Arreglo:** en el alta, estado fijo `PROGRAMADO`. Los cambios de estado solo por los métodos que ya tienen reglas. Ignorar `estado` en el PUT genérico.
- **Esfuerzo:** M.

#### SEC-A15 — El token de recuperación y el de activación se loguean enteros

- **Dónde:** `PasswordResetService.java:161-171`, `AccountActivationService` (log del token no encontrado, alrededor de la línea 189).
- **Evidencia:** `logger.warn("... {}", tokenValue)` con el valor crudo. El token es `SecureRandom`, 64 caracteres, de un solo uso, y se guarda en claro en la tabla. Vive 1440 minutos (reset) o 2880 (activación).
- **Impacto:** quien lee los logs (o el debug SMTP, SEC-M11) canjea el reset y se queda con la cuenta. El almacenamiento en claro suma: un dump de la base también.
- **Arreglo:** loguear solo el id o un prefijo. Guardar `SHA-256` del token, no el token. Bajar la ventana de 24/48 horas si el producto lo permite.
- **Esfuerzo:** S para el log, M para el hash en base.

#### SEC-A16 — Cruce de sede en operadores, y el paciente cuenta como “acceso global”

- **Dónde:** `OperadorPresenter.deleteLogico` (168-171), `findByDni` (178-182), `findByEmail` (189-201). `User.tieneAccesoGlobal` (202-203), `TenantContext.getFilteredCentroId` (105-109).
- **Evidencia:** borrar en lógico y buscar por DNI o email no comparan sede. `GET/PUT/DELETE /api/operadores/{id}` sí comparan, salvo si `hasGlobalAccess()` es true. Ese flag es true para `SUPERADMIN` y también para `PACIENTE`. Hoy el paciente no entra a `/api/operadores/**` (la regla es admin u operador), pero el mismo flag abre listados en otros servicios el día que la regla de seguridad sea más laxa que el servicio.
- **Impacto:** un operador de la sede A desactiva al operador de la sede B y lee sus datos por DNI o mail. El diseño de “paciente = global” es una trampa para el próximo endpoint.
- **Arreglo:** la guarda de sede en todos los métodos del presenter, no solo en tres. `tieneAccesoGlobal()` solo para `SUPERADMIN`. El paciente se autoriza por titularidad, no por “ver todo”.
- **Esfuerzo:** M.

### Media

#### SEC-M1 — `POST /api/auth/check-email` enumera cuentas

- **Dónde:** `AuthController` alrededor de la línea 263. La ruta es `permitAll` por `/api/auth/**`.
- **Evidencia:** si el mail existe, 200 con nombre, apellido y rol. Si no, 404. El registro responde 409 distinto para mail y para DNI (líneas 205-211). El login, después de autenticar bien, responde 403 “cuenta no verificada” (líneas 102-107) y 401 si la clave está mal: confirma que la clave era la correcta.
- **Impacto:** se arma un padrón de mails, nombres y roles, y se puede probar claves contra cuentas sin activar sin el mismo mensaje que un fallo normal.
- **Arreglo:** sacar `check-email` del público o responder siempre igual. En el login, el mismo 401 mientras no esté verificada. Forgot-password ya está bien: el mensaje no dice si el mail existe.
- **Esfuerzo:** S.

#### SEC-M2 — Tres rutas de `/api/admins` le quedan al administrador de sede

- **Dónde:** `SecurityConfig.java:240-245`, `AdminPresenter.java:109-177`.
- **Evidencia:** `GET /api/admins` y el alta son `SUPERADMIN`. `GET /check-email`, `GET /active-count` y `POST /test-encuesta-invitaciones` no tienen `@PreAuthorize` y caen en `/api/admins/**` = `ADMINISTRADOR`. El POST de prueba fuerza el envío de todas las invitaciones pendientes.
- **Impacto:** cualquier admin de sede pregunta si un mail está registrado en toda la plataforma y dispara mails de encuesta fuera de tiempo.
- **Arreglo:** `@PreAuthorize("hasRole('SUPERADMIN')")` en los tres, o borrarlos si eran de prueba (`test-encuesta-invitaciones` lo parece).
- **Esfuerzo:** S.

#### SEC-M3 — Webhook de Mercado Pago sin firma

- **Dónde:** `MercadoPagoGateway.validarFirma` (93-104), `SaasPresenter` (el POST público, regla en `SecurityConfig.java:233`).
- **Evidencia:** el método tiene el TODO del HMAC (`ts` y `v1` de `x-signature`) y `return false`. Hoy no se aplica un pago falso: la notificación se rechaza. El endpoint sigue siendo público. No hay ventana de timestamp ni idempotencia para `PREAPPROVAL` (sí hay por `mpPaymentId` en pagos).
- **Impacto:** el día que alguien cambie el `return false` por un parseo a medias, cualquiera postea un pago. Mientras tanto, Mercado Pago real no puede confirmar solo.
- **Arreglo:** HMAC en tiempo constante, secreto obligatorio, tolerancia de reloj, e idempotencia por id de notificación. Recién ahí dejar el POST público.
- **Esfuerzo:** M.

#### SEC-M4 — Tope de plan fácil de rodear, y pacientes sin cupo

- **Dónde:** `SuscripcionAccessService` y los `assertOperacion` de altas. `LimitesPlan` cuenta médicos, operadores, consultorios, sedes y especialidades. No cuenta pacientes ni mensajes.
- **Evidencia:** el chequeo está en el create. Update y delete de centro, consultorio y especialidad no vuelven a preguntar si la suscripción sigue pudiendo escribir (SEC-A12). Un médico que ya está en la organización no consume cupo al sumarlo a otra sede. `POST /api/centrosAtencion` no es el mismo camino que `POST /api/saas/mi-suscripcion/sedes`.
- **Impacto:** el plan se cumple solo en el formulario “feliz”. Otra pantalla crea el mismo recurso.
- **Arreglo:** la guarda del aspecto de SEC-A12, más el cupo en toda creación, no solo en un presenter.
- **Esfuerzo:** M.

#### SEC-M5 — Sin límite de intentos en login ni en los públicos

- **Dónde:** `AuthController` login, register, forgot-password, resend-activation, google, check-email. No hay filtro de rate limit en el backend ni en el `Caddyfile`.
- **Evidencia:** no aparece Bucket4j, Resilience4j ni una regla equivalente.
- **Impacto:** fuerza bruta de claves cortas (SEC-B1), spam de mails de reset y enumeración rápida de `check-email`.
- **Arreglo:** límite por IP y por mail en Caddy o en un filtro, antes del controlador. Captcha solo si el límite no alcanza.
- **Esfuerzo:** M.

#### SEC-M6 — Adjuntos de HCE: el tipo lo declara el cliente

- **Dónde:** `HceStorageService.java:49-74`. Tope 10 MB, alineado con `spring.servlet.multipart`.
- **Evidencia:** la allowlist es el `Content-Type` del multipart. El nombre se sanitiza y `resolver` rechaza `..` y paths que se salgan de la raíz. La descarga pasa por `HceAccessGuard`.
- **Impacto:** se puede guardar un ejecutable con `Content-Type: image/jpeg`. No hay traversal obvio ni IDOR de descarga en el guard. El riesgo es contenido activo servido después con un content type mentiroso.
- **Arreglo:** mirar magic bytes (PDF `%PDF`, JPEG `FF D8`, PNG, etc.) y guardar con extensión propia, no la del cliente. Servir `Content-Disposition: attachment` y `nosniff` (el nosniff de Spring ya está en la API).
- **Esfuerzo:** M.

#### SEC-M7 — CSV de export sin defensa de fórmulas

- **Dónde:** `ExportService.escapeCSV` (558-568).
- **Evidencia:** envuelve en comillas si hay coma, comilla o salto. No toca celdas que empiezan con `=`, `+`, `-` o `@`. El HTML de export sí escapa (574).
- **Impacto:** un nombre de paciente `=HYPERLINK(...)` se ejecuta cuando alguien abre el CSV en Excel. El export además puede ser de otra sede (SEC-A8).
- **Arreglo:** prefijar con apóstrofo esos comienzos.
- **Esfuerzo:** S.

#### SEC-M8 — El HTTP real es casi siempre 200

- **Dónde:** `Response.response` (líneas 11-17).
- **Evidencia:** el `ResponseEntity` se arma con `HttpStatus.OK`. El 401, 403 o 404 va en `status_code` del JSON. HCE usa otro sobre y ahí el status HTTP sí coincide. El filtro JWT, cuando rechaza un Bearer, sí manda HTTP 401.
- **Impacto:** un proxy, un monitor o un cliente que mire el status no ve el error. Un 403 de “no es tu turno” parece éxito de red. Complica cache y alertas.
- **Arreglo:** que el `ResponseEntity` use el mismo status que el campo. Hacerlo de una vez, con el front, porque hoy el front lee el campo y no el HTTP.
- **Esfuerzo:** M. Es un cambio de contrato.

#### SEC-M9 — Headers del borde

- **Dónde:** `deploy/Caddyfile` (sin `header`), `frontend/nginx-custom.conf` (solo cache). La API, por default de Spring Security, manda `nosniff` y `X-Frame-Options: DENY`.
- **Evidencia:** el HTML de `https://cheturno.site/` no trae CSP, HSTS, frame ni nosniff, y publica `nginx/1.31.8`. La API no trae HSTS porque el request que ve Spring no es HTTPS.
- **Impacto:** la SPA se puede enmarcar desde otro sitio (el JWT está en `localStorage`, SEC-M10: un XSS o un frame malicioso importa). Sin HSTS, la primera visita admite un downgrade.
- **Arreglo:** en Caddy, `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options` o `frame-ancestors`, `Referrer-Policy` y una CSP corta. Sacar la versión de nginx (`server_tokens off`).
- **Esfuerzo:** M.

#### SEC-M10 — El JWT vive en `localStorage`

- **Dónde:** `frontend/cli/src/app/inicio-sesion/auth.service.ts` (claves de access y refresh) y `auth.interceptor.ts`.
- **Evidencia:** no hay cookie `HttpOnly`. CSRF está apagado en `SecurityConfig.java:205`, coherente con un Bearer y sesión `STATELESS`: un sitio ajeno no manda el header solo. El problema es XSS, no CSRF.
- **Impacto:** cualquier script en el origen lee access y refresh. Con SEC-A3 el refresh son 7 días de sesión.
- **Arreglo:** cookie `HttpOnly` + `Secure` + `SameSite` para el refresh, o al menos no persistir el refresh en `localStorage`. CSP de SEC-M9 baja la chance de XSS.
- **Esfuerzo:** L.

#### SEC-M11 — `MAIL_DEBUG` prende solo

- **Dónde:** `application.properties:49`, default `true`. El compose de producción no lo apaga.
- **Evidencia:** Jakarta Mail en debug vuelca el diálogo SMTP, incluidos los cuerpos con links de reset.
- **Impacto:** se suma a SEC-C1 y SEC-A15.
- **Arreglo:** default `false` y `MAIL_DEBUG=false` en el compose.
- **Esfuerzo:** S.

#### SEC-M12 — Casi no hay `@Valid`, y Jackson ignora campos desconocidos

- **Dónde:** ningún presenter usa `@Valid`. `application.properties:16` tiene `fail-on-unknown-properties=false`.
- **Evidencia:** la validación es manual y desigual (clave mínima en unos flujos, nada en otros). Un JSON con campos de más no falla.
- **Impacto:** mass assignment donde el servicio copia el DTO entero (SEC-A14, SEC-M13) y payloads a medias que llegan al dominio.
- **Arreglo:** Bean Validation en los DTO de auth, turno, paciente y SaaS. En prod, fallar si sobra un campo en esos DTO.
- **Esfuerzo:** L, se puede hacer por módulo.

#### SEC-M13 — Plantillas de encuesta reciben la entidad

- **Dónde:** `EncuestaAdminPresenter` POST/PUT de preguntas y plantillas (alrededor de 47-101).
- **Evidencia:** el body es `Pregunta` o `EncuestaPlantilla`, no un DTO. En el PUT el cliente puede mandar `centroAtencion` y `especialidad`.
- **Impacto:** una plantilla queda colgada de otra sede o de otra especialidad. El rol que llega es administrador (y superadmin por herencia), así que es cruce entre sedes de admins, no de pacientes.
- **Arreglo:** DTO de escritura y asignación de la sede en el servidor.
- **Esfuerzo:** M.

#### SEC-M14 — `ddl-auto=update` en todos los ambientes

- **Dónde:** `application.properties:6`.
- **Evidencia:** Hibernate 7 altera el esquema al arrancar. No hay migraciones versionadas en el módulo.
- **Impacto:** un deploy puede cambiar columnas en la base de producción sin revisión, y no hay un down claro. En una base con historia clínica eso es difícil de deshacer.
- **Arreglo:** `validate` en producción y migraciones (Flyway o Liquibase) revisadas en el PR.
- **Esfuerzo:** L.

#### SEC-M15 — Mensajes de error con `e.getMessage()`

- **Dónde:** `AuthController` login (145-146) y refresh (192). Varios presenters arman el texto del `Response` con la excepción.
- **Evidencia:** no hay `@ControllerAdvice`. No está seteado `server.error.include-stacktrace`, así que el default de Boot manda el stack según el perfil. El cuerpo de negocio a veces incluye el mensaje crudo.
- **Impacto:** se filtra detalle de SQL, de archivos o de clases al cliente.
- **Arreglo:** un advice que loguee el stack y responda un texto fijo, con el status HTTP real (SEC-M8).
- **Esfuerzo:** M.

#### SEC-M16 — `System.out` con datos de pacientes y de tokens

- **Dónde:** `JwtTokenProvider.java:145` imprime el `Optional` del paciente. `TurnoService` tiene decenas de `System.out.println` con ids, estados y mails. `GoogleAuthService.java:29` imprime el error de verificación. `UserService` imprime el mail en el flujo de Google.
- **Impacto:** PII en stdout del contenedor, que en producción rota a json-file pero igual se puede leer con `docker logs`.
- **Arreglo:** borrarlos. Si hace falta rastro, logger en debug y sin el valor del token ni el mail completo.
- **Esfuerzo:** M.

#### SEC-M17 — Cambiar el mail no pide verificar el nuevo

- **Dónde:** `AuthController` `update-profile`, alrededor de 736-760. La ruta es pública en el filtro (`/api/auth/**`) y la auth es manual con el Bearer.
- **Evidencia:** `user.setEmail(...)` y save. No hay token al correo nuevo. `change-password` está en el mismo esquema.
- **Impacto:** con un access token robado se cambia el identificador de login. El patrón “todo `/api/auth` es público y cada método se acuerda de mirar el token” es frágil: el próximo endpoint se olvida.
- **Arreglo:** sacar change-password y update-profile del `permitAll`. El cambio de mail queda pendiente hasta que el correo nuevo confirme.
- **Esfuerzo:** M.

#### SEC-M18 — `AuditInterceptor` no está registrado, y si se registra confía en un header

- **Dónde:** `AuditInterceptor.java:36-67`. No hay `WebMvcConfigurer` que lo agregue.
- **Evidencia:** `X-User-ID` o `?userId=` ganan sobre el JWT. Si hay Bearer, devuelve el string `"ADMIN"`. En cancelar/confirmar/reagendar, el default también es `"ADMIN"`.
- **Impacto:** hoy es código muerto. Si alguien lo cablea para “arreglar la auditoría”, el cliente elige el autor del log.
- **Arreglo:** borrarlo o reescribirlo para leer solo el `Authentication` de Spring. El autor del audit de turnos tiene que salir de ahí, no de un parámetro.
- **Esfuerzo:** S.

#### SEC-M19 — Acciones de GitHub por tag, y el push a master despliega

- **Dónde:** `.github/workflows/ci-cd.yml`.
- **Evidencia:** `permissions: contents: read` a nivel workflow. El job `deploy` suma `packages: write` y usa `GITHUB_TOKEN` para GHCR y `SSH_HOST` / `SSH_USER` / `SSH_PRIVATE_KEY`. No hay `pull_request_target`. Las acciones están en `@v7`, `@v6`, `@v4`, no en SHA. El deploy corre en `push` a `master` (`if: github.event_name == 'push'`).
- **Impacto:** un tag movido de una acción tercera entra al pipeline que publica en el servidor. Un commit a master, si pasa CI, llega a producción. Este PR de documentación no debería deployar nada malo, pero el gatillo es el merge.
- **Arreglo:** pin por SHA. Environment de GitHub con aprobación para el job de deploy, o desplegar solo tags.
- **Esfuerzo:** M.

#### SEC-M20 — En desarrollo, registro y recupero pegan a `/api/api`

- **Dónde:** `activacion-cuenta.service.ts:15`, `registro.service.ts:40`, `recuperarContrasena.service.ts`. `proxy.conf.json` reescribe `^/rest` → `/api`.
- **Evidencia:** en dev la base es `rest/api/auth`. El proxy la deja en `/api/api/auth`. `auth.service.ts` usa `rest/auth`, que sí cae en `/api/auth`. En producción la base es `environment.apiUrl` y el workflow ya falla el build si el bundle contiene `/api/api`.
- **Impacto:** registro, activación y recupero rotos contra el backend de desarrollo. No afecta al sitio, pero esconde bugs de auth hasta producción.
- **Arreglo:** las tres bases de dev a `rest/auth`.
- **Esfuerzo:** S.

### Baja

#### SEC-B1 — Clave mínima de 6 caracteres

- **Dónde:** registro, reset y change-password (por ejemplo `AuthController` alrededor de 392).
- **Arreglo:** un piso más alto, igual en los tres flujos. Esfuerzo S.

#### SEC-B2 — `@CrossOrigin(origins = "*")` en `AuthController`

- **Dónde:** línea 54. El CORS que de verdad corre es el de `SecurityConfig` (orígenes explícitos, credenciales true). En el sitio, un origen ajeno recibió 403 y el origen propio fue aceptado.
- **Arreglo:** sacar la anotación para que no haya dos políticas. Esfuerzo S.

#### SEC-B3 — Reglas muertas: `/health` y `/actuator/**`

- **Dónde:** `SecurityConfig.java:226`. No hay dependencia de actuator en el `pom` ni un controller `/health`. `GET /` sí existe (`BackendApplication`).
- **Evidencia en el sitio:** `/health` y `/actuator` responden el HTML de la SPA, porque Caddy solo proxea `/api/*`.
- **Arreglo:** borrar los matchers, o agregar un `/api/health` autenticado o público a propósito, sin abrir actuator. Esfuerzo S.
- **Redundante:** `GET /api/agenda/publica/**` no agrega paths: el único recurso es `GET /api/agenda/publica`, que ya tiene regla propia.

#### SEC-B4 — `HEAD` en un `permitAll` de `GET` responde 403

- **Evidencia:** `HEAD https://cheturno.site/api/saas/planes` → 403. El GET del mismo path → 200.
- **Arreglo:** si hace falta, incluir `HEAD` en esas reglas. Esfuerzo S.

#### SEC-B5 — Un Bearer roto le gana al `permitAll`

- **Dónde:** `JwtAuthenticationFilter.java:72-83`. Solo perdona los paths que empiezan con `/api/auth/`.
- **Evidencia:** `GET /api/saas/planes` con `Bearer aaa.bbb.ccc` en el sitio → 401.
- **Impacto:** un token vencido en el browser tapa planes, cotización y la agenda pública.
- **Arreglo:** en rutas `permitAll`, un token inválido se ignora en vez de cortar. Esfuerzo S.

#### SEC-B6 — `npm audit` del frontend: 17 altas, todas de toolchain

- **Evidencia:** `npm audit --package-lock-only` sobre `frontend/cli` al commit `e91df79`. Las 17 son high, cero critical. La raíz es `picomatch` (ReDoS, GHSA-fc5q-39xc-vjpf), arrastrada por Angular CLI, Vite, Vitest y `webpack-dev-server`. `testing/` dio cero.
- **Impacto:** no viaja en el bundle del paciente. Afecta `ng serve` y el proxy de desarrollo.
- **Arreglo:** subir el CLI cuando el aviso tenga fix, o aceptar el riesgo de dev. Esfuerzo S.
- **Java:** sin Maven en el entorno no se armó el árbol ni se contrastó con un feed de CVE. Versiones declaradas: Boot 4.1.2, jjwt 0.13.0, iText 9.8.1, OpenCSV 5.12.0. No se afirma ninguna CVE que no se haya mirado.

#### SEC-B7 — Anónimo recibe 403, no 401

- **Evidencia:** sin header, `/api/turno/paciente/1` dio 403. Con Bearer inválido, 401. Spring Security 6+, sin `httpBasic`, usa un entry point 403 para el anónimo.
- **Impacto:** el front no distingue “no hay sesión” de “no tenés permiso”.
- **Arreglo:** un `AuthenticationEntryPoint` que mande 401 si no hay autenticación. Esfuerzo S.

#### SEC-B8 — El cliente llama rutas que el backend no tiene

Métodos sin caller en componentes, o rutas que no existen:

| Cliente | Ruta que arma | En el backend |
| --- | --- | --- |
| `turno.service.ts` `estadisticasUrl` | `/api/estadisticas/medico` y subrutas | No hay controller. Los métodos no tienen callers. |
| `audit.service.ts` `getLogsByDateRange` | `/api/audit/date-range` | El presenter expone `/api/audit/daterange`. Sin callers. |
| `esquemaTurno.service.ts` | `.../estadisticas-distribucion` | No existe. Sin callers. |
| `lista-espera.service.ts` | `/api/lista-espera/page` | No hay `GET /page`. |
| `agenda.service.ts` | `GET/POST /api/agenda`, `/medico/{id}`, `/centro/{id}`, `PATCH /{id}/estado` | La agenda real es `/publica`, `/eventos/todos`, slots y días excepcionales. |
| `centro-especialidad.service.ts` | `/centrosAtencion/centro/{id}` y `/especialidad/{id}` | El presenter solo tiene `GET /centro-especialidad`. |

En el otro sentido, el backend tiene superficie que el Angular no usa y que igual está prendida: `/api/email/**` (mandar mail arbitrario, solo superadmin), `/api/role-management/**`, `POST /api/turno/ejecutar-recordatorios` y `ejecutar-cancelacion-automatica`, `POST /api/admins/test-encuesta-invitaciones`.

Esfuerzo: S para borrar cliente muerto, M si alguna ruta hay que implementarla de verdad.

### Info

#### SEC-I1 — La HCE es el módulo mejor cerrado

`HceAccessPolicy` (líneas 21-29) mira el rol primario, no la jerarquía de Spring. El paciente ve clínico solo si es el titular. El médico ve clínico. Operador, administrador y superadmin ven metadatos. `HceAccessGuard` suma sede, autor y consentimiento. `/api/hce/**` en el filtro es solo `authenticated()`: la decisión está en el servicio, y es de las pocas que está completa. No se reabrió el diff de recetas digitales: otro agente lo está tocando.

#### SEC-I2 — Auth que está bien encaminada

- BCrypt (`SecurityConfig.java:76-77`).
- Registro público crea `PACIENTE` (`RegistrationService`).
- Reset y activación: `SecureRandom`, largo 64, un solo uso, vencimiento configurable.
- Deep link: 64 bytes, un solo uso, 48 horas. No es adivinable. Conviene rate limit igual (SEC-M5).
- Google: `GoogleIdTokenVerifier` con audience = `google.client.id` (el client id es público, `application.properties:91`). Issuer y expiración los verifica el SDK. Falta mirar `email_verified` antes de dar de alta o de loguear: si Google entrega un token con mail no verificado, hoy se acepta. Severidad baja mientras la firma y el audience se respeten; el arreglo es una línea y entra en el lote de auth.
- Mercado Pago: `MP_ACCESS_TOKEN` y `MP_WEBHOOK_SECRET` no tienen default. El riesgo es el TODO de la firma (SEC-M3), no un secreto commiteado.
- CSRF apagado es razonable mientras la sesión no viva en una cookie que el browser mande solo.

#### SEC-I3 — CORS de producción, contra el default del código

`application.properties:88` incluye `http://cheturno.site` además de https y localhost. El compose de producción lo pisa con `https://cheturno.site,https://www.cheturno.site`. El chequeo en vivo coincide con el compose, no con el default del JAR. `allowCredentials(true)` no compra mucho: no hay cookie de sesión. Se puede dejar en false hasta que exista la cookie de SEC-M10.

#### SEC-I4 — Credenciales de desarrollo, no de producción

`docker-compose.yml` (el de dev) publica Postgres en 5432 con usuario y clave `APP`. `staging/` documenta usuarios demo con clave `password`. No están en el compose de producción. No mezclar esos archivos con el servidor.

#### SEC-I5 — Permisos del token de GitHub

El workflow no pide `contents: write` ni permisos de pull request. El deploy escribe paquetes y entra por SSH con secretos. Está acotado. El punto flojo es el pin y el auto-deploy (SEC-M19).

#### SEC-I6 — Convenciones que no cierran

- Paths: `/api/turno` y `/api/obra-social` en singular, `/api/pacientes` y `/api/medicos` en plural, `centrosAtencion` en camel. Prefijo `/api` sí es consistente en los controllers.
- Envelope `status_code` versus HTTP 200 (SEC-M8). Algunas rutas de agenda devuelven la lista cruda, sin envelope (`obtenerTodosLosEventos`).
- Borrado: paciente y turno se van con `deleteById`. Admin se deshabilita. Operador tiene delete físico y `/logico`. No hay una política única.
- Auditoría: turnos y usuarios van a `audit_log`. HCE tiene la suya. El interceptor genérico está muerto (SEC-M18). Si la auditoría de un turno tira, el `catch` sigue y el cambio queda igual.
- Zona horaria: Jackson está en `America/Argentina/Buenos_Aires` (`application.properties:15`). Hay mezcla de `LocalDate`/`LocalDateTime` con `java.util.Date` en la fecha de nacimiento del paciente. `RelojArgentina` existe en SaaS y no es el reloj del resto.
- `EncuestaPlantilla.preguntas` está en `EAGER`: listar plantillas arrastra las preguntas.
- `ConsultorioService` (línea 198) tiene un `@RequestMapping` adentro de un `@Service`. No se publica. Es un resto de un presenter.
- N+1: no se midió con un trace. El candidato obvio es ese `EAGER` y `AgendaPresenter`, que recorre todos los esquemas en memoria para armar los eventos.

#### SEC-I7 — Roles en el front

El enum del cliente usa los mismos nombres (`PACIENTE`, `MEDICO`, `OPERADOR`, `ADMINISTRADOR`, `SUPERADMIN`). El superadmin de la UI mira el rol primario del JWT, igual que la HCE, no la herencia. Hay pantallas que todavía leen `localStorage.userRole`, que se puede desfasar del token.

#### SEC-I8 — Qué no se pudo probar con un token por rol

Sin Maven, sin Docker y sin Postgres no se levantó el perfil de test ni se firmó un JWT local para cada rol. La columna “roles efectivos” de [ENDPOINTS.md](ENDPOINTS.md) es el resultado de aplicar las reglas en el orden del filtro, no una corrida de MockMvc. `SaasAdminSecurityTest` ya cubre una parte (SaaS admin, mails, roles) pero no se ejecutó en esta pasada.

No se buscó el secreto en todo el `git log` línea por línea. Alcanza con que esté en `master` ahora: está en la historia. No se imprimen los valores.

## Plan de remediación

Cada lote es un PR chico, mergeable solo. Ninguno de estos es este PR: este PR es documentación.

### Lote 1 — Secretos y arranque (S)

SEC-C1, SEC-C2, SEC-A1, SEC-A2, SEC-M11.

Sacar defaults de mail, JWT y seed. `security.dev.mode=false` y `admin.seed.enabled=false` por default. `MAIL_DEBUG=false`. El compose de producción tiene que declarar esas variables, no heredarlas del JAR. Rotar el app password y la cuenta `admin@turneroweb.com` en el servidor si siguen con el valor publicado. No incluir los valores en el diff.

### Lote 2 — Tipo de token y sesión (S + el versionado que es L)

SEC-A3, SEC-A4, SEC-B5, SEC-B7, SEC-M1 (el oráculo del login).

`tokenType` obligatorio. 401 claro para anónimo. En `permitAll`, ignorar un Bearer roto. Mismo mensaje para clave mala y cuenta sin verificar. El versionado de token y la rotación del refresh pueden ir en un segundo commit del mismo PR si entra, o en el lote 2b.

### Lote 3 — Titularidad del paciente (M)

SEC-A5, SEC-A6, SEC-A9, SEC-A10, y el POST de lista de espera que toma `pacienteId` del body.

Un helper solo: el paciente del token. Las rutas de staff no reutilizan el path del paciente. Recién ahí se puede devolverle al paciente `GET` de sus turnos sin abrir el de los demás.

### Lote 4 — Sede en turnos, export y agenda (M)

SEC-A7, SEC-A8, SEC-A11, SEC-A16.

`validarAccesoCentroTurno` en toda operación por id. El centro del filtro no se acepta del cliente si el usuario tiene sede. La agenda pública no devuelve nombre de paciente. `tieneAccesoGlobal()` deja de incluir a `PACIENTE`.

### Lote 5 — Solo lectura de la suscripción (L)

SEC-A12, SEC-M3, SEC-M4.

Aspecto o filtro de escritura, lista blanca corta, centros sin suscripción decididos a propósito. HMAC del webhook en el mismo PR o en el siguiente, antes de poner `saas.payment.provider=mercadopago` en el servidor.

### Lote 6 — Datos que no tienen que salir ni entrar (M)

SEC-A13, SEC-A14, SEC-A15, SEC-M7, SEC-M13, SEC-M6.

DTO de admin sin hash. Estado del turno fuera del `save` genérico. No loguear tokens. CSV y magic bytes de adjuntos. DTO de encuesta.

### Lote 7 — Borde HTTP (M)

SEC-M9, SEC-M10, SEC-B2, SEC-I3.

Headers en Caddy, `server_tokens off`, default de CORS sin `http://cheturno.site`. La mudanza del refresh a cookie puede ser otro PR: toca al front.

### Lote 8 — Contrato y restos (M/L, se puede partir)

SEC-M8, SEC-M12, SEC-M14, SEC-M15, SEC-M16, SEC-M18, SEC-M20, SEC-B3, SEC-B8, SEC-M19.

Status HTTP de verdad, `@Valid` en los DTO críticos, `ddl-auto=validate` cuando existan migraciones, advice de errores, borrar `System.out` y el interceptor muerto, alinear el proxy de dev, borrar cliente huérfano, pin de acciones.

### Qué no mezclar

No meter HCE de recetas ni el arreglo de `medicoId` en estos lotes: hay otros cambios en curso sobre esos archivos. El lote 3 y el 4 tienen que respetar `HceAccessGuard` y no reimplementar esa política en el presenter de turnos.

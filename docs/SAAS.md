# CheTurno como SaaS

CheTurno se vende por organización. Una organización es el suscriptor y es dueña de uno o más centros de atención. Los usuarios operativos siguen atados a un centro: el aislamiento entre centros no cambia.

Los precios se guardan **netos**, en pesos. El IVA es un parámetro (21% al instalar) y el total con IVA se calcula al mostrar y al cobrar. En la interfaz los importes se leen como “+ IVA” y también se muestra el total.

## Planes de referencia

| Plan | Precio neto | Qué incluye |
| --- | --- | --- |
| PROFESIONAL | $ 25.000 / mes | 1 médico, 1 sede, hasta 1 operador y 1 consultorio |
| CONSULTORIO | $ 18.000 / mes **por profesional** | De 2 a 10 profesionales, una sede, reportes. El modelo es `precio base + precio por profesional` (el base arranca en 0) |
| CENTRO | $ 200.000 / mes | 1 sede, médicos y operadores sin tope, reportes |
| RED | $ 350.000 / mes | 3 sedes incluidas. Cada sede extra: $ 120.000. Reportes consolidados y soporte prioritario |

La prueba gratis dura **30 días**, sin tarjeta. Esos tres números (precios, IVA, días y el 15% anual) los edita el SUPERADMIN en **SaaS → Planes y precios**. Un reinicio no los pisa: el seed solo crea lo que todavía no existe.

El anual aplica el descuento sobre el neto de 12 meses y después suma el IVA. Con 15%: `neto mensual × 12 × 0,85`, y sobre eso el 21%.

WhatsApp y SMS son complementos opcionales con precio de referencia. No envían mensajes: quedan marcados como placeholder para enchufar un proveedor después.

## Cuándo se aplican los límites

`saas.enforcement.enabled` (`SAAS_ENFORCEMENT_ENABLED`) arranca en **false**. Mientras esté apagado, la suscripción se calcula y se guarda igual (trial, vencimiento, mora, pagos) pero no se rechaza ninguna escritura y la interfaz no muestra el modo solo lectura ni le impide reservar al paciente. `GET /api/saas/aviso` y la ficha de Mi plan traen `enforcementEnabled` para que el frontend decida.

Con `true` vale la regla de solo lectura de abajo. Los pasos que siguen (compuerta de features por plan, cupos y el selector de sede de la red) tienen que consultar la misma bandera antes de bloquear: si está apagada, no aplican el límite.

La suite Cucumber y `docker-compose.e2e.yml` levantan el backend con `SAAS_ENFORCEMENT_ENABLED=true` para que los escenarios de solo lectura sigan viendo el 403. Producción y el compose de desarrollo quedan en false hasta el día del cobro real.

## Estados y solo lectura

`TRIAL`, `ACTIVA`, `VENCIDA`, `MOROSA`, `SUSPENDIDA`, `CANCELADA`.

Esta sección describe el comportamiento con la bandera prendida.

- `VENCIDA`: se acabó la prueba y nadie pagó.
- `MOROSA`: el período pago venció.
- `SUSPENDIDA` y `CANCELADA`: las decide un operador de CheTurno (o Mercado Pago, cuando esté conectado).

En esos cuatro estados la cuenta queda de **solo lectura**. También queda en solo lectura un centro que no tiene suscripción. No se crean ni se modifican turnos, historia clínica, recetas, órdenes, pacientes, disponibilidades, listas de espera, esquemas, personal ni la configuración del centro. Nada se borra: se puede seguir leyendo. Un pago simulado (o, más adelante, un `authorized` de Mercado Pago) vuelve a `ACTIVA`. `SUSPENDIDA` no se paga sola: la reactiva el SUPERADMIN.

La regla vive en un filtro HTTP (`SuscripcionSoloLecturaFilter`), después del JWT y antes del controller. Corta `POST`, `PUT`, `PATCH` y `DELETE`. `GET`, `HEAD` y `OPTIONS` pasan. El SUPERADMIN no entra nunca en el filtro. La respuesta es **403** con `code: SUSCRIPCION_SOLO_LECTURA` y un texto en español. Si quien escribe es un paciente, el texto dice que el centro no está tomando turnos y que no puede reservar, confirmar, cancelar ni reagendar.

Siguen abiertas, aunque el centro esté en solo lectura: login, refresh, logout, cambio de contraseña y el resto de `/api/auth/**`; `POST /api/saas/registro` y `/api/saas/cotizar`; todo `/api/saas/mi-suscripcion/**` (pagar, cambiar de plan, sedes); todo `/api/saas/admin/**`; el webhook de Mercado Pago; la verificación pública de recetas y órdenes; y las consultas que viajan como POST (búsqueda de turnos, exportaciones, validar conflictos). `GET /api/saas/aviso` le dice al personal y al paciente si el centro está en solo lectura, sin mostrar precios.

Los centros que ya existían al desplegar esto reciben una suscripción `ACTIVA` heredada del plan Centro, una por centro, para que producción no quede bloqueada ni se mezclen organizaciones distintas. No entran en el MRR hasta que pasen a un cobro real. `heredarCentrosExistentes` corre al arrancar y cada vez que se pide el catálogo, el alta o el resumen de SUPERADMIN: cubre un centro sin organización y también una organización que se quedó sin fila de suscripción. La suite Cucumber, que vacía la base después del arranque, vuelve a pedir `GET /api/saas/planes` al sembrar `userinit.sql` para que esa herencia corra antes de los escenarios. Un centro creado después, que todavía no pasó por esa herencia, queda en solo lectura hasta el próximo arranque o hasta que alguien consulte el catálogo.

Si el catálogo se vacía (el reset de Cucumber trunca las tablas), el próximo `GET /api/saas/planes` o el alta lo vuelven a sembrar. No pisa precios, IVA ni días de prueba que el SUPERADMIN ya haya editado.

## Pasarela

`PasarelaPago` habla el idioma de Mercado Pago Subscriptions:

- `preapproval_plan` por plan y ciclo (`mpPreapprovalPlanIdMensual` / `Anual`)
- `preapproval` por suscripción (`mpPreapprovalId`)
- id de pago en cada movimiento (`mpPaymentId`)

Estados de Mercado Pago:

| Mercado Pago | CheTurno |
| --- | --- |
| `authorized` | `ACTIVA` |
| `paused` | `SUSPENDIDA` |
| `cancelled` | `CANCELADA` |
| `pending` | no cambia el estado (la prueba sigue si todavía es válida) |

Por defecto `saas.payment.provider=simulated` (`SAAS_PAYMENT_PROVIDER`). `SimulatedPaymentGateway` autoriza el cobro en el acto y no sale a internet. `MercadoPagoGateway` solo se crea con `SAAS_PAYMENT_PROVIDER=mercadopago`. Es un stub: no llama a la API.

### Activar Mercado Pago

Variables de entorno, nunca en el repositorio:

```bash
SAAS_PAYMENT_PROVIDER=mercadopago
MP_ACCESS_TOKEN=...          # Access token de la aplicación
MP_WEBHOOK_SECRET=...        # Clave para validar x-signature
# opcional
MP_API_BASE=https://api.mercadopago.com
```

Falta cablear, en `MercadoPagoGateway`:

1. `POST /preapproval_plan` con `auto_recurring.transaction_amount` = total con IVA, `currency_id=ARS`, frecuencia mensual o anual. Guardar el id en el plan.
2. `POST /preapproval` con `preapproval_plan_id`, `external_reference=suscripcion-{id}`, `payer_email` y `back_url`.
3. `PUT /preapproval/{id}` para pausar (`paused`), reactivar (`authorized`), cancelar (`cancelled`) o actualizar el monto cuando cambian el cupo, las sedes extra o los complementos.
4. La firma del webhook. El aviso trae `x-signature` (`ts=...,v1=...`) y `x-request-id`. El manifest es `id:{data.id};request-id:{x-request-id};ts:{ts};`, HMAC-SHA256 con `MP_WEBHOOK_SECRET`. Hoy `validarFirma` devuelve false a propósito: `POST /api/saas/webhooks/mercadopago` responde 401 y no toca la suscripción. El cuerpo real no trae el estado; hay que hacer `GET /preapproval/{id}` o `GET /v1/payments/{id}` y recién ahí mapear.

El endpoint ya está público en producción (`/api/saas/webhooks/mercadopago`). Cuando la firma valide, `SaasService.aplicarEventoPasarela` aplica el estado y registra el pago.

## Pantallas

- `/planes` — catálogo público, mensual/anual, “+ IVA” y total.
- `/registro-saas` — alta de organización, centro y administrador. Arranca la prueba.
- `/mi-plan` — el administrador del centro ve días de prueba, uso contra límites, paga (simulado) y cambia de plan o de cupo. Si el plan lo permite, agrega sedes.
- `/saas` — solo SUPERADMIN: tablero (activas, pruebas por vencer, vencidas, morosas, MRR neto, cobrado del mes, corte por plan), listado, ficha (extender prueba, suspender, reactivar, cancelar, cambiar plan), precios y pagos.

El menú “SaaS” está en el panel de SUPERADMIN. “Mi plan” solo lo ve quien tiene ADMINISTRADOR como rol principal.

## API

Públicas:

- `GET /api/saas/planes`
- `POST /api/saas/cotizar`
- `POST /api/saas/registro`
- `POST /api/saas/webhooks/mercadopago`

Del administrador del centro (`ADMINISTRADOR`; el SUPERADMIN también pasa la regla de rol, pero no tiene centro):

- `GET /api/saas/aviso` — cualquier usuario autenticado. El personal ve su centro; el paciente pasa `?centroId=`
- `GET /api/saas/mi-suscripcion`
- `POST /api/saas/mi-suscripcion/pagar`
- `POST /api/saas/mi-suscripcion/cambiar-plan`
- `POST /api/saas/mi-suscripcion/sedes`
- `GET /api/saas/mi-suscripcion/reporte-consolidado` (solo plan RED)

SUPERADMIN, bajo `/api/saas/admin/**`:

- `GET /resumen`, `GET /suscripciones`, `GET /suscripciones/{id}`
- `POST /suscripciones/{id}/extender-trial|suspender|reactivar|cancelar|cambiar-plan|vincular-centro`
- `GET|PUT /planes`, `PUT /configuracion`, `PUT /addons/{id}`, `GET /pagos`

En producción esas rutas están en `SecurityConfig` además del `@PreAuthorize`.

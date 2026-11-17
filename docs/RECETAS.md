# Recetas electrónicas y órdenes de estudio

Módulo de prescripción de CheTurno. Se apoya en la consulta de la historia clínica: la receta y la orden nacen ahí, se emiten, quedan inmutables y se anulan con motivo. No se borran.

La integración con una plataforma inscripta en ReNaPDiS y con el repositorio nacional **no está en este cambio**. Hay una interfaz para enchufarla después. Con `recetas.plataforma=local` (el default) el identificador lo genera CheTurno y el CUIR oficial queda vacío.

## Qué pide la norma, y qué hace este módulo

Ley 27.553 (recetas electrónicas o digitales), el Decreto 98/2023 y las resoluciones del Ministerio de Salud arman el régimen: plataformas registradas en ReNaPDiS, Clave Única de Identificación de Receta (CUIR), intercambio con el repositorio nacional y validación en farmacias. La Ley 25.649 exige el nombre genérico (DCI); la marca comercial es optativa. La firma digital de la Ley 25.506 es otra cosa, y este módulo todavía no la tiene.

| Tema | En este módulo | Pendiente |
| --- | --- | --- |
| Receta vinculada a la consulta, el paciente, el médico y el centro | Sí | — |
| Hasta 3 medicamentos por receta (`RECETAS_MAX_ITEMS`) | Sí | El tope se puede cambiar por entorno |
| Nombre genérico obligatorio, marca opcional, concentración, forma, cantidad en números y en letras, dosis, frecuencia, duración, vía, indicaciones, «no sustituir» con justificación | Sí | — |
| Diagnóstico CIE-10 tomado de la consulta | Sí | — |
| Datos del paciente en la receta: nombre, DNI, sexo, nacimiento, obra social o prepaga y número de afiliado si están | Sí. `paciente.sexo` y `paciente.numero_afiliado` son opcionales en la ficha y obligatorios para emitir (sexo y nacimiento) | Validar la cobertura contra la obra social |
| Prescriptor: nombre, matrícula, matrícula nacional, matrícula provincial, especialidad, REFEPS | Sí, con lo que haya cargado. `medico.matricula_nacional` y `medico.matricula_provincial` son opcionales | Validar la matrícula contra REFEPS |
| Emisión, vencimiento a 30 días (`RECETAS_VIGENCIA_DIAS`) | Sí | — |
| Identificador propio con forma estable (`CTR` o `CTO` + fecha + 20 caracteres) y columna `cuir` para el valor oficial | Sí. El local no es un CUIR | Que el repositorio nacional asigne el CUIR |
| Huella SHA-256 del contenido canónico, guardada con el documento | Sí | — |
| QR a una página pública de verificación | Sí | — |
| Firma | Sello `ELECTRONICA_LOCAL`: fecha, autor y huella. El PDF lo dice | Firma digital o electrónica del profesional según la Ley 25.506 |
| Psicofármacos y estupefacientes (listas II a IV) | Bloqueados. El mensaje es que requieren receta oficial archivada. El catálogo semilla marca clonazepam, alprazolam y codeína, y un listado de nombres frena el texto libre | El circuito de receta archivada en una plataforma habilitada |
| Dispensa parcial o total | El estado existe y lo puede registrar la plataforma. No hay endpoint para la farmacia | Autenticación de farmacia y aviso al repositorio |
| Anulación con motivo, autor y fecha. Sin baja física | Sí | Avisar al repositorio nacional |
| Orden de estudio con el mismo ciclo, práctica, código opcional, diagnóstico, indicación y urgencia | Sí | Nomenclador oficial |
| Registro en ReNaPDiS y repositorio nacional | Interfaz `PlataformaRecetaElectronica`. `local` no sale a la red. `renapdis` es un stub que rechaza la operación para no inventar un CUIR | Alta de la plataforma, credenciales y el cliente HTTP |

No se citan números de artículo: el detalle operativo está en el decreto y en las resoluciones del Ministerio, y cambia con cada norma.

## Modelo

| Tabla | Rol |
| --- | --- |
| `hce_receta` | Receta. `BORRADOR`, `EMITIDA`, `DISPENSADA_PARCIAL`, `DISPENSADA_TOTAL`, `ANULADA`, `VENCIDA` |
| `hce_receta_item` | Medicamento. No se borra la receta: si está emitida, se anula |
| `hce_orden_estudio` | Orden. Mismos estados |
| `hce_orden_estudio_item` | Práctica, con sistema de código y código opcionales |
| `hce_catalogo_medicamento` | Vademécum chico, importable. La clave es nombre + concentración + forma |
| `hce_acceso` | Auditoría, la misma de la historia clínica |

Al emitir se congela una copia del paciente, del profesional y del centro. Un cambio posterior en la ficha no reescribe el documento. La huella cubre ese contenido (versión `v1`) y no el estado ni el motivo de anulación: anular o vencer no rompe la huella; alterar un ítem, sí.

`VENCIDA` se aplica al leer si la fecha de vencimiento ya pasó y el estado era `EMITIDA` o `DISPENSADA_PARCIAL`.

Hibernate `ddl-auto=update` crea las tablas y las columnas nuevas (`paciente.sexo`, `paciente.numero_afiliado`, `medico.matricula_nacional`, `medico.matricula_provincial`).

## Configuración

Solo variables de entorno. No hay secretos.

| Propiedad | Variable | Default |
| --- | --- | --- |
| `recetas.plataforma` | `RECETAS_PLATAFORMA` | `local` |
| `recetas.max-items` | `RECETAS_MAX_ITEMS` | `3` |
| `recetas.vigencia-dias` | `RECETAS_VIGENCIA_DIAS` | `30` |
| `recetas.orden-max-items` | `ORDENES_MAX_ITEMS` | `8` |
| `recetas.public-base-url` | `RECETAS_PUBLIC_BASE_URL` | `app.url` |
| `recetas.verificacion-por-minuto` | `RECETAS_VERIFICACION_POR_MINUTO` | `30` |

`RECETAS_PLATAFORMA=renapdis` selecciona `RepositorioNacionalStub`. No llama a ningún host: responde que la plataforma no está conectada.

La interfaz `PlataformaRecetaElectronica` expone `emitir` (devuelve el CUIR cuando exista), `anular`, `consultarEstado` y `registrarDispensa`.

## API

El sobre es el de siempre (`status_code`, `status_text`, `data`). El PDF responde `application/pdf`.

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/recetas/catalogo?q=` | Vademécum. Solo médico |
| GET | `/api/recetas?consultaId=` | Recetas de la consulta |
| POST | `/api/recetas` | Borrador |
| PUT | `/api/recetas/{id}` | Edita solo un borrador del autor. Emitida: 409 |
| POST | `/api/recetas/{id}/emitir` | Firma, identificador, huella y código |
| POST | `/api/recetas/{id}/anular` | `{ "motivo" }`. Sin motivo: 400 |
| GET | `/api/recetas/{id}` y `/api/recetas/{id}/pdf` | Lectura y PDF |
| GET | `/api/recetas/mias` | Portal del paciente |
| GET | `/api/recetas/verificar/{codigo}` | Público. Sin sesión |
| GET/POST/PUT | `/api/ordenes-estudio` | Igual, para órdenes |
| GET | `/api/ordenes-estudio/verificar/{codigo}` | Público |

No hay DELETE. No hay dispensa por HTTP: falta la farmacia autenticada.

La verificación devuelve estado, vigencia, emisión, vencimiento, profesional y matrícula, ítems (genérico, concentración, forma, cantidad) e iniciales del paciente con el DNI enmascarado. No devuelve diagnóstico, indicaciones ni el nombre completo. El código son 32 bytes en hexadecimal. Además hay un tope por IP (`429`).

El QR apunta a `{RECETAS_PUBLIC_BASE_URL}/verificar-receta/{codigo}` (o `/verificar-orden/{codigo}`).

## Permisos

Se mira el rol primario, igual que en la historia clínica. `ADMINISTRADOR` y `SUPERADMIN` heredan `ROLE_MEDICO` en Spring y por eso no alcanza con `hasRole`.

| Rol | Receta y orden |
| --- | --- |
| MEDICO autor, con staff en el centro y vínculo de atención (la misma regla que para crear una consulta) | Crea, emite y anula las suyas. Lee las del centro |
| MEDICO del mismo centro | Lee |
| MEDICO de otro centro | 403, salvo consentimiento de cruce de la historia |
| PACIENTE | Lee y descarga las propias |
| OPERADOR, ADMINISTRADOR | 403 sobre el contenido. En los metadatos de la historia ven el conteo |
| SUPERADMIN | 403 sobre el contenido. Ve el conteo de cualquier paciente |
| Sin sesión | 401, salvo la verificación pública |

Cada alta, lectura, emisión, anulación, descarga y verificación pública queda en `hce_acceso`. El detalle no copia el texto clínico. La verificación anónima se anota como `verificacion-publica`.

## Pantallas

- `/medico-consulta/:id`: sección **Recetas y órdenes**. Alta, autocompletado del vademécum, vista previa, emisión, anulación y PDF.
- Línea de tiempo de la historia: filtros `RECETA` y `ORDEN`.
- `/paciente-recetas`: estado y descarga. También desde el panel y el menú del paciente.
- `/verificar-receta/:codigo` y `/verificar-orden/:codigo`: sin login.

## FHIR

`GET /api/hce/pacientes/{id}/fhir` suma, para los documentos que el lector puede ver:

| Recurso | Origen |
| --- | --- |
| MedicationRequest | Cada ítem de `hce_receta`. `identifier` con `urn:cheturno:receta` y, si existe, el CUIR en `https://recetas.msal.gob.ar/cuir` (URI provisional). `substitution.allowedBoolean = false` si no se sustituye |
| ServiceRequest | Cada práctica de `hce_orden_estudio`. Prioridad `routine`, `urgent` o `stat` |

Estados: borrador `draft`, emitida `active`, dispensa total `completed`, anulada `cancelled`, vencida `stopped`.

Sigue siendo una exportación de lectura. No autentica contra el bus ni contra el repositorio de recetas.

## Por qué no se emiten listas II a IV

Esas sustancias siguen el régimen de psicofármacos y estupefacientes, con receta oficial archivada y talonarios o plataformas habilitadas para ese fin. Una receta común digital, encima sin plataforma inscripta ni firma de la Ley 25.506, no las reemplaza. Por eso el alta y la emisión las rechazan, aunque el nombre se escriba a mano.

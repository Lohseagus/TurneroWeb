# Historia clínica electrónica

Módulo propio de CheTurno. No reemplaza la historia de turnos (`Turno.observaciones`, `asistio`): esa sigue siendo la agenda. La historia clínica guarda el contenido asistencial.

La integración con la Red Nacional de Salud Digital (bus FHIR R4, federador de pacientes, REFEPS, SNOMED CT AR) **no está en este cambio**. El modelo deja los identificadores y los sistemas de códigos listos para mapear.

## Modelo

| Tabla | Rol |
| --- | --- |
| `hce_perfil` | Antecedentes personales, familiares y quirúrgicos, grupo sanguíneo, hábitos y el flag `comparte_entre_centros` |
| `hce_alergia` | Alergias. No se borran: se marcan `activa = false` |
| `hce_medicacion` | Medicación habitual. Misma regla de baja lógica |
| `hce_vacuna` | Vacunas. Solo se agregan |
| `hce_consulta` | Evolución. `BORRADOR` o `CERRADA`. Opcionalmente ligada a un `Turno` (una sola por turno) |
| `hce_diagnostico` | Código con `system_uri` + `code` + `display`. El catálogo semilla es CIE-10 |
| `hce_enmienda` | Corrección posterior al cierre. Autor, fecha y motivo. No se edita |
| `hce_adjunto` | Metadatos del archivo. El binario vive en disco |
| `hce_acceso` | Auditoría de lectura, alta, cierre, enmienda, descarga y acceso denegado |
| `hce_cie10` | Subconjunto ambulatorio importable. Clave `(system_uri, code)` |

Columnas nuevas en entidades que ya existían:

- `paciente.identificador_federado`: id del federador nacional, opcional. El DNI sigue en `paciente.dni`.
- `medico.refeps_id`: id REFEPS, opcional. La matrícula sigue en `medico.matricula`.

Hibernate `ddl-auto=update` crea las tablas y las columnas al arrancar.

Signos vitales de la consulta: tensión arterial, frecuencia cardíaca, temperatura, peso, talla. El IMC se calcula como peso (kg) / talla (m)² y se guarda junto con la consulta.

## Archivos

Propiedad `hce.storage.path` (`HCE_STORAGE_PATH`, default `./data/hce`). Tope `hce.storage.max-bytes` (10 MB). Tipos: PDF, JPEG, PNG, WEBP, GIF.

En producción, `docker-compose.prod.yml` monta el volumen nombrado `cheturno_hce_files` en `/var/cheturno/hce` y define `HCE_STORAGE_PATH`. El deploy no debe borrar ese volumen: no está en el de Postgres. El compose de desarrollo local usa el volumen `hce_files` con la misma ruta.

## API (`/api/hce`)

Todas responden el sobre habitual (`status_code`, `status_text`, `data`) con el HTTP real (201, 403, 409, 404). El PDF responde `application/pdf`.

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/cie10?q=` | Búsqueda del catálogo (médico) |
| GET | `/mis-pacientes` | Pacientes que el médico atendió |
| GET | `/turnos/{id}` | Contexto para abrir la consulta desde un turno |
| POST | `/consultas` | Alta de borrador, con o sin turno |
| PUT | `/consultas/{id}` | Edita solo un borrador del autor |
| POST | `/consultas/{id}/cerrar` | Firma. Después es inmutable |
| POST | `/consultas/{id}/enmiendas` | Addenda con motivo |
| GET | `/consultas/{id}` y `/consultas/{id}/pdf` | Lectura y PDF de una consulta |
| GET/PUT | `/pacientes/{id}/perfil` | Perfil clínico |
| GET | `/pacientes/{id}/timeline?especialidadId&centroId&medicoId&tipo` | Línea de tiempo |
| GET | `/pacientes/{id}/metadata` | Conteos, sin texto clínico |
| GET | `/pacientes/{id}/auditoria` | Quién vio o escribió |
| GET | `/pacientes/{id}/fhir` | Bundle JSON con forma FHIR R4, solo lectura |
| GET | `/pacientes/{id}/pdf` | Resumen |
| POST | `/consultas/{id}/adjuntos` y `/pacientes/{id}/adjuntos` | Subida (`archivo`, `tipo`) |
| GET | `/adjuntos/{id}` | Descarga |
| GET | `/mi`, `/mi/pdf` | Portal del paciente |
| PUT | `/mi/consentimiento` | `{ "comparteEntreCentros": true }` |

No hay DELETE de consultas, enmiendas, vacunas ni adjuntos.

## Permisos

Se evalúa el **rol primario**, no la jerarquía de Spring. `ADMINISTRADOR` y `SUPERADMIN` heredan `ROLE_MEDICO` en `SecurityConfig`, así que un `hasRole("MEDICO")` no alcanza. `/api/hce/**` exige autenticación; el servicio decide.

| Rol | Contenido clínico | Metadatos (conteos, auditoría) |
| --- | --- | --- |
| MEDICO autor o con staff en el centro de la entrada | Lee y, si es el autor, edita el borrador y cierra | Sí |
| MEDICO del mismo centro | Lee | Sí |
| MEDICO de otro centro | Solo si `comparteEntreCentros` | Solo con ese consentimiento o si el paciente se atendió en su centro |
| PACIENTE | Lee la historia propia, descarga PDF y cambia el consentimiento | Sí, la propia |
| OPERADOR / ADMINISTRADOR | No | Solo de pacientes atendidos en su centro |
| SUPERADMIN | No | Sí, de cualquier paciente |
| Sin sesión | 401 | 401 |

Una consulta suelta (sin turno) solo la abre un médico que ya atendió a ese paciente en ese centro. Con turno, el turno tiene que ser de ese médico, de ese paciente y de ese centro.

## Cumplimiento (Ley 26.529 y 25.326)

- El borrador se puede corregir. Al cerrarlo queda firmado: un PUT devuelve 409.
- La corrección posterior es una enmienda con autor, marca de tiempo y motivo. No pisa el texto original.
- No hay baja física de la evolución, las enmiendas, las vacunas ni los archivos. Alergias y medicación se inactivan.
- Cada lectura, alta, cierre, enmienda, descarga, exportación y acceso denegado queda en `hce_acceso` (quién, rol, cuándo, qué recurso). El detalle no copia el texto clínico.
- El paciente consulta su historia y decide si otros centros pueden leerla.

## Pantallas

- Agenda del médico: en cada turno, **Iniciar consulta** y **Historia clínica**.
- `/medico-hce` y `/medico-hce/:pacienteId`: listado, línea de tiempo filtrable y perfil.
- `/medico-consulta/nueva` y `/medico-consulta/:id`: evolución, CIE-10, signos, cierre, enmienda, adjuntos y PDF.
- `/paciente-historia-clinica`: lectura, consentimiento y descarga de PDF. También desde el panel del paciente y el menú.

## Mapeo FHIR (futuro)

Los URI de DNI, federador y REFEPS son provisionales hasta que la Red Nacional publique los NamingSystem oficiales.

| Recurso FHIR | Origen | Notas |
| --- | --- | --- |
| Patient | `Paciente` + perfil | `identifier` DNI en `http://renaper.gob.ar/dni` y, si existe, el id federado en `https://bus-nacional.msal.gob.ar/fhir/NamingSystem/paciente` |
| Practitioner | `Medico` | Matrícula en `urn:cheturno:matricula`. REFEPS en `https://sisa.msal.gov.ar/refeps` |
| Encounter | `Consulta` | `class` AMB. `finished` si está cerrada, `in-progress` si es borrador. Motivo en `reasonCode.text` |
| Condition | `DiagnosticoConsulta` | `code.coding.system` + `code` (CIE-10: `http://hl7.org/fhir/sid/icd-10`). SNOMED CT AR se suma como otro system sin cambiar la tabla |
| AllergyIntolerance | `Alergia` | `clinicalStatus` active/inactive. Código opcional con su system |
| MedicationStatement | `MedicacionHabitual` activa | Texto y, si hay, system + code |
| MedicationRequest | `hce_receta` emitida o en borrador, un recurso por ítem | Ver [RECETAS.md](RECETAS.md). El CUIR oficial viaja en `identifier` solo cuando la plataforma lo asigna |
| ServiceRequest | `hce_orden_estudio`, un recurso por práctica | Indicación en `note`, urgencia en `priority` |
| Observation | Signos de la consulta | LOINC 8480-6, 8462-4, 8867-4, 8310-5, 29463-7, 8302-2, 39156-5 |
| DocumentReference | `AdjuntoClinico` | Tipo, título y tamaño. El binario no viaja en el JSON |

`GET /api/hce/pacientes/{id}/fhir` devuelve un Bundle `collection` con esa forma. No autentica contra el bus, no resuelve el federador y no valida con el perfil nacional: es la exportación de lectura para el conector que venga después. Las recetas y las órdenes entran como MedicationRequest y ServiceRequest; el camino de cumplimiento de la Ley 27.553 está en [RECETAS.md](RECETAS.md).

El catálogo CIE-10 de semilla se puede reemplazar o ampliar cargando filas en `hce_cie10` con el mismo `system_uri`. Un código que no esté en el catálogo se rechaza al guardar el diagnóstico.

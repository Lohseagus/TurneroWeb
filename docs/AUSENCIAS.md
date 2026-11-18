# Ausencias del médico

Una ausencia (`AUSENCIA_MEDICO`) bloquea la agenda de un profesional entre dos fechas y cancela los turnos futuros de ese rango. El paciente reserva un turno nuevo. Los feriados y el mantenimiento no usan esta regla.

## Anticipación

La fecha de inicio tiene que ser al menos `turnos.dias_max_no_confirm` días después de hoy, en `America/Argentina/Buenos_Aires`. El valor sale de `ConfiguracionService.getDiasMaxNoConfirm(centroId)`: si el centro tiene override, gana; si no, vale la global (hoy 7).

El médico no puede cargar antes de esa fecha, aunque mande `urgente=true`. La respuesta es un HTTP 400:

> Las ausencias se cargan con al menos N días de anticipación. Para una urgencia, pedile a la administración del centro que la cargue.

Un administrador, un operador o el superadmin puede cargarla antes solo con `urgente=true` y un motivo de al menos 5 caracteres. El flag y el motivo quedan en la ausencia y en el audit log. La cancelación y el aviso al paciente son los de siempre.

La misma regla corre si se mueve la fecha de inicio de una ausencia ya cargada. No depende de `SAAS_ENFORCEMENT_ENABLED`.

`GET /api/agenda/ausencias/anticipacion?centroId=` devuelve `dias` y `fechaMinima` para el datepicker.

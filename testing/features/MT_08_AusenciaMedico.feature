# language: es

@multi-tenant @ausencia
Característica: El paciente reserva un turno nuevo cuando el médico se ausenta

  El enlace de la cancelación abre los horarios del mismo profesional
  y la reserva crea un turno nuevo. El turno cancelado no se reabre.

  Escenario: Del enlace de la ausencia a un turno nuevo
    Cuando el médico marca una ausencia que cancela un turno futuro del paciente
    Y el paciente abre el enlace de esa cancelación
    Y elige un horario libre del mismo médico
    Entonces queda reservado un turno nuevo y el anterior figura cancelado

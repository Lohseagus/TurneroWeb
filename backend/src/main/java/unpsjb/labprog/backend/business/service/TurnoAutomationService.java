package unpsjb.labprog.backend.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.TurnoRepository;
import unpsjb.labprog.backend.model.EstadoTurno;
import unpsjb.labprog.backend.model.Turno;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Servicio para automatización de turnos
 * Maneja cancelaciones automáticas por falta de confirmación
 */
@Service
public class TurnoAutomationService {

    private static final Logger logger = LoggerFactory.getLogger(TurnoAutomationService.class);
    
    @Autowired
    private TurnoRepository turnoRepository;
    
    @Autowired
    private AuditLogService auditLogService;
    
    @Autowired
    private EmailService emailService;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private ReglasReservaTurno reglasReserva;

    @Autowired
    private ListaEsperaService listaEsperaService;

    @Value("${turnos.auto-cancel.enabled:true}")
    private Boolean autoCancelEnabled;
    
    @Value("${turnos.auto-cancel.hours-before:48}")
    private Integer horasAnticipacion;
    
    @Value("${app.url:http://localhost:4200}")
    private String appUrl;

    // Zona horaria de Argentina
    private static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Job que se ejecuta cada hora para cancelar turnos no confirmados
     * Cancela turnos PROGRAMADOS que no fueron confirmados dentro del tiempo límite
     */
    @Scheduled(fixedRateString = "${turnos.auto-cancel.check-interval:3600000}") // Default: cada hora
    @Transactional
    public void cancelarTurnosNoConfirmados() {
        cancelarTurnos(null);
    }

    private void cancelarTurnos(Integer centroId) {
        if (!autoCancelEnabled) {
            logger.debug("🔧 Cancelación automática de turnos está deshabilitada");
            return;
        }
        
        try {
            logger.info("🔄 Iniciando proceso de cancelación automática de turnos...");
            
            // Misma regla que el job de medianoche (zona cerrada por centro, PROGRAMADO y REAGENDADO).
            // Después de cancelar, ofrece el hueco a la lista de espera si todavía sirve.
            ZonedDateTime ahoraArgentina = ZonedDateTime.now(ARGENTINA_ZONE);
            LocalDateTime ahora = ahoraArgentina.toLocalDateTime();
            int horas = reglasReserva != null ? reglasReserva.horasParaBuscarCandidatos() : horasAnticipacion;
            logger.info("📅 Fecha/hora actual (Argentina UTC-3): {} | Ventana compartida: {} horas", ahora, horas);

            List<Turno> turnosACancelar = turnoService.candidatosCancelacionAutomatica(ahora).stream()
                    .filter(turno -> centroId == null || centroId.equals(CentroAccessGuard.centroDe(turno)))
                    .toList();
            
            if (turnosACancelar.isEmpty()) {
                logger.info("✅ No hay turnos para cancelar automáticamente");
                return;
            }
            
            logger.info("⚠️  Encontrados {} turnos para cancelar automáticamente", turnosACancelar.size());
            
            int cancelados = 0;
            int errores = 0;
            
            for (Turno turno : turnosACancelar) {
                try {
                    cancelarTurnoAutomaticamente(turno);
                    cancelados++;
                } catch (Exception e) {
                    errores++;
                    logger.error("❌ Error al cancelar turno ID {}: {}", turno.getId(), e.getMessage());
                }
            }
            
            logger.info("✅ Proceso completado: {} turnos cancelados automáticamente, {} errores", 
                       cancelados, errores);
            
        } catch (Exception e) {
            logger.error("❌ Error crítico en proceso de cancelación automática: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Cancela un turno específico por falta de confirmación
     * @param turno el turno a cancelar
     */
    private void cancelarTurnoAutomaticamente(Turno turno) {
        logger.info("🚫 Cancelando turno ID {} - Paciente: {} {} - Fecha: {} {}", 
            turno.getId(), 
            turno.getPaciente().getNombre(),
            turno.getPaciente().getApellido(),
            turno.getFecha(),
            turno.getHoraInicio()
        );

        String estadoPrevio = turno.getEstado() == null ? "PROGRAMADO" : turno.getEstado().name();
        int horas = reglasReserva != null
                ? reglasReserva.horasZonaCerrada(ReglasReservaTurno.centroId(turno))
                : horasAnticipacion;
        turno.setEstado(EstadoTurno.CANCELADO);

        turnoRepository.save(turno);
        auditLogService.logTurnoCancelledAutomatically(
            turno.getId() != null ? turno.getId().longValue() : null,
            turno.getPaciente() != null && turno.getPaciente().getId() != null ? turno.getPaciente().getId().longValue() : null,
            String.format("Cancelación automática por falta de confirmación dentro de las %d horas previas", horas),
            estadoPrevio
        );

        logger.debug("✅ Turno ID {} cancelado y registrado en auditoría", turno.getId());

        if (listaEsperaService != null) {
            listaEsperaService.ofrecerHuecoLiberado(turno);
        }

        // Notificar al paciente por email
        notificarCancelacionAutomatica(turno);

        logger.debug("✅ Notificación enviada al paciente para turno ID {}", turno.getId());
    }
    
    /**
     * Envía notificación por email al paciente sobre la cancelación automática
     * @param turno el turno que fue cancelado
     */
    private void notificarCancelacionAutomatica(Turno turno) {
        try {
            // Verificar que el paciente tenga email
            if (turno.getPaciente() == null || turno.getPaciente().getEmail() == null || turno.getPaciente().getEmail().trim().isEmpty()) {
                logger.warn("⚠️  No se puede enviar notificación: paciente sin email para turno ID {}", turno.getId());
                return;
            }
            
            String patientEmail = turno.getPaciente().getEmail();
            String patientName = turno.getPaciente().getNombre() + " " + turno.getPaciente().getApellido();
            
            // Construir detalles del turno
            String appointmentDetails = String.format(
                "<p><strong>Fecha:</strong> %s</p>" +
                "<p><strong>Hora:</strong> %s</p>" +
                "<p><strong>Médico:</strong> %s %s</p>" +
                "<p><strong>Especialidad:</strong> %s</p>" +
                "<p><strong>Centro:</strong> %s</p>" +
                "<p><strong>Consultorio:</strong> %s</p>",
                turno.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                turno.getHoraInicio(),
                turno.getStaffMedico() != null && turno.getStaffMedico().getMedico() != null ? turno.getStaffMedico().getMedico().getNombre() : "N/A",
                turno.getStaffMedico() != null && turno.getStaffMedico().getMedico() != null ? turno.getStaffMedico().getMedico().getApellido() : "",
                turno.getStaffMedico() != null && turno.getStaffMedico().getEspecialidad() != null ? turno.getStaffMedico().getEspecialidad().getNombre() : "N/A",
                turno.getConsultorio() != null && turno.getConsultorio().getCentroAtencion() != null ? turno.getConsultorio().getCentroAtencion().getNombre() : "N/A",
                turno.getConsultorio() != null ? turno.getConsultorio().getNombre() : "N/A"
            );
            
            // URL para reagendar (puede ser la URL base de la aplicación)
            String rescheduleUrl = appUrl;
            
            logger.info("📧 Enviando notificación de cancelación automática a {} para turno ID {}", patientEmail, turno.getId());
            
            // Enviar email de forma asíncrona
            emailService.sendAutomaticCancellationEmail(patientEmail, patientName, appointmentDetails, rescheduleUrl)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("❌ Error al enviar notificación de cancelación automática para turno ID {}: {}", turno.getId(), throwable.getMessage());
                    } else {
                        logger.info("✅ Notificación de cancelación automática enviada exitosamente para turno ID {}", turno.getId());
                    }
                });
                
        } catch (Exception e) {
            logger.error("❌ Error al preparar notificación de cancelación automática para turno ID {}: {}", turno.getId(), e.getMessage());
        }
    }
    
    /**
     * Obtiene estadísticas de turnos programados próximos a vencer
     * @return cantidad de turnos que están por ser cancelados automáticamente
     */
    public long contarTurnosPorVencer() {
        if (!autoCancelEnabled) {
            return 0L;
        }
        
        // Usar zona horaria de Argentina
        ZonedDateTime ahoraArgentina = ZonedDateTime.now(ARGENTINA_ZONE);
        LocalDateTime ahora = ahoraArgentina.toLocalDateTime();
        return turnoService.candidatosCancelacionAutomatica(ahora).size();
    }
    
    /**
     * Ejecuta manualmente el proceso de cancelación automática
     * Útil para testing y ejecución manual desde admin
     */
    public void ejecutarCancelacionManual() {
        ejecutarCancelacionManual(null);
    }

    /**
     * @param centroId null cancela en todos los centros. Un id, solo en ese centro.
     */
    public void ejecutarCancelacionManual(Integer centroId) {
        logger.info("🔧 Ejecución manual del proceso de cancelación automática solicitada{}",
                centroId == null ? "" : " para el centro " + centroId);
        cancelarTurnos(centroId);
    }
}
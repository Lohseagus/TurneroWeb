package unpsjb.labprog.backend.config;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import unpsjb.labprog.backend.business.hce.HceAccessDeniedException;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.hce.HceNotFoundException;
import unpsjb.labprog.backend.exception.FichaAjenaException;
import unpsjb.labprog.backend.exception.MedicoSinFichaException;
import unpsjb.labprog.backend.exception.OverlapException;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;
import unpsjb.labprog.backend.exception.TenantAccessException;
import unpsjb.labprog.backend.exception.TurnoTomadoException;

/**
 * Traduce las excepciones que ningún controller atrapó al mismo sobre JSON
 * ({@code status_code}, {@code status_text}, {@code data}) con el HTTP status real.
 * Un 500 no incluye el mensaje ni el stack: eso queda en el log.
 * {@code message} repite {@code status_text} porque varias pantallas leen ese campo.
 */
@RestControllerAdvice
public class ApiErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

    static final String ERROR_INTERNO = "No se pudo completar la operación";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> validacion(MethodArgumentNotValidException e) {
        String texto = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(". "));
        if (texto.isBlank()) {
            texto = "Los datos enviados no son válidos.";
        }
        return cuerpo(HttpStatus.BAD_REQUEST, texto, null);
    }

    @ExceptionHandler({
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Object> pedidoInvalido(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException) {
            return cuerpo(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.", null);
        }
        return cuerpo(HttpStatus.BAD_REQUEST, mensajeSeguro(e.getMessage(), "Los datos enviados no son válidos."), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> cuerpoIlegible(HttpMessageNotReadableException e) {
        log.debug("Pedido con cuerpo ilegible", e);
        return cuerpo(HttpStatus.BAD_REQUEST, "El pedido no tiene un formato válido.", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> argumento(IllegalArgumentException e) {
        String mensaje = mensajeSeguro(e.getMessage(), "Los datos enviados no son válidos.");
        HttpStatus status = esNoEncontrado(mensaje) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return cuerpo(status, mensaje, null);
    }

    @ExceptionHandler({AccessDeniedException.class, TenantAccessException.class})
    public ResponseEntity<Object> prohibido(RuntimeException e) {
        return cuerpo(HttpStatus.FORBIDDEN, mensajeDePermiso(e.getMessage()), null);
    }

    @ExceptionHandler(HceAccessDeniedException.class)
    public ResponseEntity<Object> prohibidoClinico(HceAccessDeniedException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus());
        if (status == null || status.is5xxServerError()) {
            log.error("Error de historia clínica", e);
            return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, null);
        }
        String fallback = status == HttpStatus.UNAUTHORIZED
                ? "Autenticación requerida"
                : "No tenés permiso para esta operación";
        return cuerpo(status, mensajeSeguro(e.getMessage(), fallback), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> autenticacion(AuthenticationException e) {
        return cuerpo(HttpStatus.UNAUTHORIZED, "Autenticación requerida", null);
    }

    @ExceptionHandler({
            EntityNotFoundException.class,
            java.util.NoSuchElementException.class,
            EmptyResultDataAccessException.class,
            PacienteSinFichaException.class,
            MedicoSinFichaException.class,
            HceNotFoundException.class
    })
    public ResponseEntity<Object> noEncontrado(Exception e) {
        return cuerpo(HttpStatus.NOT_FOUND, mensajeSeguro(e.getMessage(), "No se encontró el recurso"), null);
    }

    @ExceptionHandler(TurnoTomadoException.class)
    public ResponseEntity<Object> turnoTomado(TurnoTomadoException e) {
        return cuerpo(HttpStatus.CONFLICT, TurnoTomadoException.MENSAJE, null);
    }

    @ExceptionHandler(OverlapException.class)
    public ResponseEntity<Object> solapado(OverlapException e) {
        return cuerpo(HttpStatus.CONFLICT, mensajeSeguro(e.getMessage(), "Ese horario ya está ocupado."), e.getConflicts());
    }

    @ExceptionHandler({FichaAjenaException.class, HceImmutableException.class})
    public ResponseEntity<Object> conflicto(RuntimeException e) {
        return cuerpo(HttpStatus.CONFLICT, mensajeSeguro(e.getMessage(), "No se pudo completar por un conflicto"), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> integridad(DataIntegrityViolationException e) {
        log.warn("Conflicto de integridad", e);
        return cuerpo(HttpStatus.CONFLICT, "El dato ya existe o está en uso", null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> estado(IllegalStateException e) {
        String mensaje = mensajeSeguro(e.getMessage(), "No se pudo completar la operación");
        HttpStatus status = esNoEncontrado(mensaje) ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return cuerpo(status, mensaje, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> status(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            log.error("Error {}", e.getStatusCode().value(), e);
            return cuerpo(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, ERROR_INTERNO, null);
        }
        return cuerpo(status, mensajeSeguro(e.getReason(), textoPorStatus(status)), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> resto(Exception e) throws Exception {
        // 404 de ruta, 405 de método y el resto de los ErrorResponse de Spring
        // siguen con su status. Este consejo no los convierte en 500.
        if (e instanceof ErrorResponse && !(e instanceof ResponseStatusException)) {
            throw e;
        }
        log.error("Error no controlado", e);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO, null);
    }

    private static ResponseEntity<Object> cuerpo(HttpStatus status, String mensaje, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("status_code", status.value());
        body.put("status_text", mensaje);
        body.put("message", mensaje);
        body.put("data", data);
        return ResponseEntity.status(status).body(body);
    }

    private static String mensajeDePermiso(String mensaje) {
        if (mensaje == null || mensaje.isBlank() || mensaje.equals("Access Denied")
                || mensaje.startsWith("Access Denied")) {
            return "No tenés permiso para esta operación";
        }
        return mensajeSeguro(mensaje, "No tenés permiso para esta operación");
    }

    static String mensajeSeguro(String mensaje, String fallback) {
        if (mensaje == null || mensaje.isBlank()) {
            return fallback;
        }
        String lower = mensaje.toLowerCase();
        if (mensaje.contains("\n") || mensaje.contains("\tat ") || lower.contains("exception")
                || lower.contains("sql") || lower.contains("jdbc") || mensaje.contains("org.")
                || mensaje.contains("java.")) {
            return fallback;
        }
        return mensaje;
    }

    private static boolean esNoEncontrado(String mensaje) {
        String lower = mensaje.toLowerCase();
        return lower.contains("no encontr") || lower.contains("no existe");
    }

    private static String textoPorStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Los datos enviados no son válidos.";
            case UNAUTHORIZED -> "Autenticación requerida";
            case FORBIDDEN -> "No tenés permiso para esta operación";
            case NOT_FOUND -> "No se encontró el recurso";
            case CONFLICT -> "No se pudo completar por un conflicto";
            default -> ERROR_INTERNO;
        };
    }
}

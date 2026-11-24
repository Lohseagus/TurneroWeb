package unpsjb.labprog.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import unpsjb.labprog.backend.exception.TurnoTomadoException;

class ApiErrorAdviceTest {

    private final ApiErrorAdvice advice = new ApiErrorAdvice();

    @Test
    void validacionEs400ConElMensaje() {
        Map<String, Object> body = cuerpo(advice.argumento(new IllegalArgumentException("La fecha es obligatoria")));
        assertEquals(400, body.get("status_code"));
        assertEquals("La fecha es obligatoria", body.get("status_text"));
        assertEquals(body.get("status_text"), body.get("message"));
    }

    @Test
    void turnoTomadoSigueSiendo409() {
        ResponseEntity<Object> respuesta = advice.turnoTomado(new TurnoTomadoException());
        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        Map<String, Object> body = cuerpo(respuesta);
        assertEquals(409, body.get("status_code"));
        assertEquals(TurnoTomadoException.MENSAJE, body.get("status_text"));
    }

    @Test
    void permisoEs403() {
        ResponseEntity<Object> respuesta = advice.prohibido(
                new AccessDeniedException("No tenés permiso para operar datos de otro centro"));
        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        assertEquals("No tenés permiso para operar datos de otro centro", cuerpo(respuesta).get("status_text"));
    }

    @Test
    void accessDeniedGenericoQuedaEnEspanol() {
        assertEquals("No tenés permiso para esta operación",
                cuerpo(advice.prohibido(new AccessDeniedException("Access Denied"))).get("status_text"));
    }

    @Test
    void noEncontradoEs404() {
        ResponseEntity<Object> respuesta = advice.noEncontrado(new EntityNotFoundException("Turno no encontrado"));
        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertEquals("Turno no encontrado", cuerpo(respuesta).get("status_text"));
    }

    @Test
    void conflictoDeEstadoEs409() {
        ResponseEntity<Object> respuesta = advice.estado(
                new IllegalStateException("Ya existe una disponibilidad para este staff médico"));
        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("Ya existe una disponibilidad para este staff médico", cuerpo(respuesta).get("status_text"));
    }

    @Test
    void el500NoFiltraElMensajeInterno() throws Exception {
        ResponseEntity<Object> respuesta = advice.resto(new RuntimeException("java.sql.SQLException: password=secret"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        String texto = String.valueOf(cuerpo(respuesta));
        assertEquals(true, texto.contains(ApiErrorAdvice.ERROR_INTERNO));
        assertFalse(texto.toLowerCase().contains("sql"));
        assertFalse(texto.contains("secret"));
        assertFalse(texto.contains("Exception"));
        assertFalse(texto.contains("password"));
    }

    @Test
    void laIntegridadNoDevuelveElSql() {
        String texto = String.valueOf(cuerpo(advice.integridad(
                new DataIntegrityViolationException("duplicate key value violates unique constraint users_email"))));
        assertEquals(true, texto.contains("409"));
        assertFalse(texto.contains("users_email"));
        assertFalse(texto.toLowerCase().contains("duplicate"));
    }

    @Test
    void unResponseStatusDeCincoNoFiltraLaRazon() throws Exception {
        String texto = String.valueOf(cuerpo(advice.status(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "NullPointerException at UserService"))));
        assertEquals(true, texto.contains(ApiErrorAdvice.ERROR_INTERNO));
        assertFalse(texto.contains("NullPointer"));
        assertFalse(texto.contains("UserService"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cuerpo(ResponseEntity<Object> respuesta) {
        return (Map<String, Object>) respuesta.getBody();
    }
}

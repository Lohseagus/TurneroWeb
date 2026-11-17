package unpsjb.labprog.backend.presenter;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import unpsjb.labprog.backend.business.hce.HceAccessDeniedException;
import unpsjb.labprog.backend.business.hce.HceImmutableException;
import unpsjb.labprog.backend.business.hce.HceNotFoundException;
import unpsjb.labprog.backend.business.receta.VerificacionLimitadaException;

final class RespuestaClinica {

    private RespuestaClinica() {
    }

    static ResponseEntity<Object> ejecutar(Proveedor proveedor) {
        try {
            return proveedor.obtener();
        } catch (VerificacionLimitadaException e) {
            return json(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);
        } catch (HceAccessDeniedException e) {
            return json(HttpStatus.valueOf(e.getStatus()), e.getMessage(), null);
        } catch (HceImmutableException e) {
            return json(HttpStatus.CONFLICT, e.getMessage(), null);
        } catch (HceNotFoundException e) {
            return json(HttpStatus.NOT_FOUND, e.getMessage(), null);
        } catch (IllegalStateException e) {
            return json(HttpStatus.CONFLICT, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            return json(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        } catch (RuntimeException e) {
            return json(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación", null);
        }
    }

    static ResponseEntity<Object> pdf(PdfProveedor proveedor, String filename) {
        try {
            byte[] bytes = proveedor.obtener();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(bytes));
        } catch (HceAccessDeniedException e) {
            return json(HttpStatus.valueOf(e.getStatus()), e.getMessage(), null);
        } catch (HceNotFoundException e) {
            return json(HttpStatus.NOT_FOUND, e.getMessage(), null);
        } catch (RuntimeException e) {
            return json(HttpStatus.BAD_REQUEST, e.getMessage(), null);
        }
    }

    static ResponseEntity<Object> ok(Object data, String mensaje) {
        return json(HttpStatus.OK, mensaje, data);
    }

    static ResponseEntity<Object> creado(Object data, String mensaje) {
        return json(HttpStatus.CREATED, mensaje, data);
    }

    static ResponseEntity<Object> json(HttpStatus status, String mensaje, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("status_code", status.value());
        body.put("status_text", mensaje);
        body.put("data", data);
        return ResponseEntity.status(status).body(body);
    }

    @FunctionalInterface
    interface Proveedor {
        ResponseEntity<Object> obtener();
    }

    @FunctionalInterface
    interface PdfProveedor {
        byte[] obtener();
    }
}

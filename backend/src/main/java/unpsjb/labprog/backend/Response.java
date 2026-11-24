package unpsjb.labprog.backend;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Response {

public static ResponseEntity<Object> response(HttpStatus status, String message, Object responseObj) {
Map<String, Object> map = new HashMap<>();
map.put("status_code", status.value()); 
map.put("status_text", message);
map.put("data", responseObj);

return new ResponseEntity<>(map, HttpStatus.OK); 
}

public static ResponseEntity<Object> ok(Object responseObj) {
return response(HttpStatus.OK, "OK", responseObj);
}

public static ResponseEntity<Object> ok(Object responseObj, String msj) {
return response(HttpStatus.OK, msj, responseObj);
}

    public static ResponseEntity<Object> notFound() {
        return response(HttpStatus.NOT_FOUND, "Not found", null);
    }

public static ResponseEntity<Object> notFound(String msj) {
        return response(HttpStatus.NOT_FOUND, msj, null);
    }
    public static ResponseEntity<Object> forbidden(String msj) {
        return response(HttpStatus.FORBIDDEN, msj, null);
    }
public static ResponseEntity<Object> error(Object responseObj, String msj) {
return response(HttpStatus.BAD_REQUEST, msj, responseObj);
}

public static ResponseEntity<Object> dbError(String msj) {
return response(HttpStatus.CONFLICT, msj, null);
}

/** HTTP 409 de verdad, no solo el status_code del cuerpo. */
public static ResponseEntity<Object> conflicto(String msj) {
    Map<String, Object> map = new HashMap<>();
    map.put("status_code", HttpStatus.CONFLICT.value());
    map.put("status_text", msj);
    map.put("data", null);
    return new ResponseEntity<>(map, HttpStatus.CONFLICT);
}

public static ResponseEntity<Object> serverError(String msj) {
return response(HttpStatus.INTERNAL_SERVER_ERROR, msj, null); 
}

}
package unpsjb.labprog.backend.presenter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import unpsjb.labprog.backend.Response;
import unpsjb.labprog.backend.business.service.ListaEsperaService;
import unpsjb.labprog.backend.exception.PacienteSinFichaException;

/**
 * Lista de espera del paciente autenticado.
 * Vive bajo /api/pacientes/me, que SecurityConfig ya deja al rol PACIENTE.
 */
@RestController
@RequestMapping("/api/pacientes/me/lista-espera")
public class PacienteListaEsperaPresenter {

    @Autowired
    private ListaEsperaService listaEspera;

    @GetMapping
    public ResponseEntity<Object> mias() {
        try {
            return Response.ok(listaEspera.deLaCuenta(), "Tus solicitudes de lista de espera");
        } catch (PacienteSinFichaException e) {
            return Response.notFound(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> cancelar(@PathVariable Long id) {
        try {
            return Response.ok(listaEspera.cancelarPropia(id), "Solicitud cancelada");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (PacienteSinFichaException e) {
            return Response.notFound(e.getMessage());
        } catch (RuntimeException e) {
            return Response.error(null, e.getMessage());
        }
    }
}

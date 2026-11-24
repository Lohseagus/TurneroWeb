package unpsjb.labprog.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.exception.AuditInmutableException;

class AuditLogGuardTest {

    @Test
    void unRegistroNoSeActualizaNiSeBorra() {
        AuditInmutableException error = assertThrows(AuditInmutableException.class,
                () -> new AuditLogGuard().rechazar(new AuditLog()));
        assertEquals(AuditLogGuard.MENSAJE, error.getMessage());
    }

    @Test
    void elActorSistemaQuedaComoSistemaYElRestoComoUsuario() {
        assertEquals(AuditSource.SISTEMA, AuditSource.deActor("SISTEMA"));
        assertEquals(AuditSource.SISTEMA, AuditSource.deActor(" sistema_automatico "));
        assertEquals(AuditSource.SISTEMA, AuditSource.deActor("SYSTEM_SEED"));
        assertEquals(AuditSource.USUARIO, AuditSource.deActor("ana@turnero.com"));
        assertEquals(AuditSource.USUARIO, AuditSource.deActor(null));
    }
}

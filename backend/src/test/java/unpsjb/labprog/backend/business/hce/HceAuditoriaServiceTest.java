package unpsjb.labprog.backend.business.hce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import unpsjb.labprog.backend.business.repository.AccesoHistoriaClinicaRepository;
import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;
import unpsjb.labprog.backend.model.hce.AccionAuditoriaHce;

@ExtendWith(MockitoExtension.class)
class HceAuditoriaServiceTest {

    @Mock
    private AccesoHistoriaClinicaRepository repository;

    @Test
    void unCheckRotoNoRompeLaOperacionQueAudita() {
        doThrow(new DataIntegrityViolationException("hce_acceso_accion_check"))
                .when(repository).save(any(AccesoHistoriaClinica.class));

        HceAuditoriaService servicio = new HceAuditoriaService(repository, new TransaccionNueva());

        assertDoesNotThrow(() -> servicio.registrar(9, AccionAuditoriaHce.VER_RECETA, "RECETA", "mias", null,
                "Listado de recetas propias"));
        verify(repository).save(any(AccesoHistoriaClinica.class));
    }

    @Test
    void guardaEnUnaTransaccionNueva() {
        TransaccionNueva transacciones = new TransaccionNueva();
        HceAuditoriaService servicio = new HceAuditoriaService(repository, transacciones);

        servicio.registrar(9, AccionAuditoriaHce.VER_PERFIL, "PERFIL", "9", null, "Lectura");

        verify(repository).save(any(AccesoHistoriaClinica.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW, transacciones.propagacion);
        org.junit.jupiter.api.Assertions.assertTrue(transacciones.confirmada);
    }

    private static final class TransaccionNueva implements PlatformTransactionManager {
        private int propagacion = -1;
        private boolean confirmada;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagacion = definition.getPropagationBehavior();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            confirmada = true;
        }

        @Override
        public void rollback(TransactionStatus status) {
            confirmada = false;
        }
    }
}

package unpsjb.labprog.backend.business.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.PagoSuscripcion;
import unpsjb.labprog.backend.saas.EstadoPago;

@Repository
public interface PagoSuscripcionRepository extends JpaRepository<PagoSuscripcion, Integer> {

    List<PagoSuscripcion> findBySuscripcion_IdOrderByFechaDesc(Integer suscripcionId);

    List<PagoSuscripcion> findByFechaBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, EstadoPago estado);

    List<PagoSuscripcion> findTop200ByOrderByFechaDesc();

    boolean existsByMpPaymentId(String mpPaymentId);
}

package unpsjb.labprog.backend.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.PlanSuscripcion;

@Repository
public interface PlanSuscripcionRepository extends JpaRepository<PlanSuscripcion, Integer> {

    Optional<PlanSuscripcion> findByCodigo(String codigo);

    List<PlanSuscripcion> findByActivoTrueOrderByOrdenAsc();

    List<PlanSuscripcion> findAllByOrderByOrdenAsc();
}

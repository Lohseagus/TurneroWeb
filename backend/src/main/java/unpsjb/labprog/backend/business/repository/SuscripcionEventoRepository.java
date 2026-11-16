package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.SuscripcionEvento;

@Repository
public interface SuscripcionEventoRepository extends JpaRepository<SuscripcionEvento, Integer> {

    List<SuscripcionEvento> findBySuscripcion_IdOrderByFechaDesc(Integer suscripcionId);
}

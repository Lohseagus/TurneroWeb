package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.SuscripcionAddon;

@Repository
public interface SuscripcionAddonRepository extends JpaRepository<SuscripcionAddon, Integer> {

    List<SuscripcionAddon> findBySuscripcion_Id(Integer suscripcionId);

    List<SuscripcionAddon> findBySuscripcion_IdAndActivoTrue(Integer suscripcionId);
}

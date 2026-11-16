package unpsjb.labprog.backend.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.AddonMensajeria;

@Repository
public interface AddonMensajeriaRepository extends JpaRepository<AddonMensajeria, Integer> {

    Optional<AddonMensajeria> findByCodigo(String codigo);

    List<AddonMensajeria> findByActivoTrueOrderByNombreAsc();
}

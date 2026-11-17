package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.CatalogoMedicamento;

public interface CatalogoMedicamentoRepository extends JpaRepository<CatalogoMedicamento, Long> {

    List<CatalogoMedicamento> findTop20ByNombreGenericoContainingIgnoreCaseOrderByNombreGenericoAsc(String nombre);

    boolean existsByNombreGenericoAndConcentracionAndFormaFarmaceutica(String nombreGenerico, String concentracion,
            String formaFarmaceutica);
}

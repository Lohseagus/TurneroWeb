package unpsjb.labprog.backend.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import unpsjb.labprog.backend.model.hce.Receta;

public interface RecetaRepository extends JpaRepository<Receta, Long> {

    List<Receta> findByConsulta_IdOrderByCreadaEnDesc(Long consultaId);

    List<Receta> findByPaciente_IdOrderByCreadaEnDesc(Integer pacienteId);

    long countByPaciente_Id(Integer pacienteId);

    Optional<Receta> findByCodigoVerificacion(String codigoVerificacion);

    Optional<Receta> findByIdentificador(String identificador);

    @Query("select r.centroAtencion.id from Receta r where r.id = :id")
    Optional<Integer> findCentroIdByRecetaId(@Param("id") Long id);
}

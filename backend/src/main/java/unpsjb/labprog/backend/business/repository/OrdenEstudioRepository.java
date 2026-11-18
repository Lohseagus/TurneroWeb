package unpsjb.labprog.backend.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import unpsjb.labprog.backend.model.hce.OrdenEstudio;

public interface OrdenEstudioRepository extends JpaRepository<OrdenEstudio, Long> {

    List<OrdenEstudio> findByConsulta_IdOrderByCreadaEnDesc(Long consultaId);

    List<OrdenEstudio> findByPaciente_IdOrderByCreadaEnDesc(Integer pacienteId);

    long countByPaciente_Id(Integer pacienteId);

    Optional<OrdenEstudio> findByCodigoVerificacion(String codigoVerificacion);

    Optional<OrdenEstudio> findByIdentificador(String identificador);

    @Query("select o.centroAtencion.id from OrdenEstudio o where o.id = :id")
    Optional<Integer> findCentroIdByOrdenId(@Param("id") Long id);
}

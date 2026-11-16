package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import unpsjb.labprog.backend.model.hce.DiagnosticoConsulta;

public interface DiagnosticoConsultaRepository extends JpaRepository<DiagnosticoConsulta, Long> {

    List<DiagnosticoConsulta> findByConsulta_IdOrderByIdAsc(Long consultaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DiagnosticoConsulta d WHERE d.consulta.id = :consultaId")
    void deleteByConsultaId(@Param("consultaId") Long consultaId);
}

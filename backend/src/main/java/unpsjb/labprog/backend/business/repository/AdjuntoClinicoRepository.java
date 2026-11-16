package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.AdjuntoClinico;

public interface AdjuntoClinicoRepository extends JpaRepository<AdjuntoClinico, Long> {
    List<AdjuntoClinico> findByPaciente_IdOrderByCreadoEnDesc(Integer pacienteId);

    List<AdjuntoClinico> findByConsulta_IdOrderByCreadoEnAsc(Long consultaId);
}

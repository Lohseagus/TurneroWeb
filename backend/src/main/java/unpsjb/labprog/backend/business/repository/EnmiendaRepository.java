package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.Enmienda;

public interface EnmiendaRepository extends JpaRepository<Enmienda, Long> {
    List<Enmienda> findByConsulta_IdOrderByCreadaEnAsc(Long consultaId);

    List<Enmienda> findByConsulta_Paciente_IdOrderByCreadaEnDesc(Integer pacienteId);
}

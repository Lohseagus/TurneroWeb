package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.hce.Consulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    boolean existsByTurno_Id(Integer turnoId);

    boolean existsByPaciente_IdAndCentroAtencion_IdAndMedico_Id(Integer pacienteId, Integer centroId, Integer medicoId);

    List<Consulta> findByPaciente_IdOrderByCreadaEnDesc(Integer pacienteId);

    @Query("SELECT DISTINCT c.paciente FROM Consulta c WHERE c.medico.id = :medicoId")
    List<Paciente> findPacientesDelMedico(@Param("medicoId") Integer medicoId);

    @Query("SELECT c.paciente.id, COUNT(c), MAX(c.creadaEn) FROM Consulta c WHERE c.medico.id = :medicoId GROUP BY c.paciente.id")
    List<Object[]> resumenConsultasPorPaciente(@Param("medicoId") Integer medicoId);
}

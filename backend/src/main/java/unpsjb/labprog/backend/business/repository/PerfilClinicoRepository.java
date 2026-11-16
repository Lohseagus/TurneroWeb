package unpsjb.labprog.backend.business.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.PerfilClinico;

public interface PerfilClinicoRepository extends JpaRepository<PerfilClinico, Long> {
    Optional<PerfilClinico> findByPaciente_Id(Integer pacienteId);
}

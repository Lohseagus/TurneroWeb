package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.Vacuna;

public interface VacunaRepository extends JpaRepository<Vacuna, Long> {
    List<Vacuna> findByPerfil_IdOrderByFechaAplicacionDescIdDesc(Long perfilId);
}

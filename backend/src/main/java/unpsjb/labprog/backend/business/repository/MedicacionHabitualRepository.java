package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.MedicacionHabitual;

public interface MedicacionHabitualRepository extends JpaRepository<MedicacionHabitual, Long> {
    List<MedicacionHabitual> findByPerfil_IdOrderByIdAsc(Long perfilId);
}

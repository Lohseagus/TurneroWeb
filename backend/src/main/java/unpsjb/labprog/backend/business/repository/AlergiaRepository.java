package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.Alergia;

public interface AlergiaRepository extends JpaRepository<Alergia, Long> {
    List<Alergia> findByPerfil_IdOrderByIdAsc(Long perfilId);
}

package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import unpsjb.labprog.backend.model.hce.AccesoHistoriaClinica;

public interface AccesoHistoriaClinicaRepository extends JpaRepository<AccesoHistoriaClinica, Long> {
    List<AccesoHistoriaClinica> findByPacienteIdOrderByOcurridoEnDesc(Integer pacienteId);
}

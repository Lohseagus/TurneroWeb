package unpsjb.labprog.backend.business.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.ConfiguracionSaas;

@Repository
public interface ConfiguracionSaasRepository extends JpaRepository<ConfiguracionSaas, Integer> {
}

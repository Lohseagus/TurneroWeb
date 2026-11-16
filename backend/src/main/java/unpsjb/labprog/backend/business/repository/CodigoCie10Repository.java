package unpsjb.labprog.backend.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import unpsjb.labprog.backend.model.hce.CodigoCie10;

public interface CodigoCie10Repository extends JpaRepository<CodigoCie10, Long> {

    Optional<CodigoCie10> findBySystemUriAndCode(String systemUri, String code);

    @Query("""
            SELECT c FROM CodigoCie10 c
            WHERE LOWER(c.code) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(c.display) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY c.code
            """)
    List<CodigoCie10> buscar(@Param("q") String q);
}

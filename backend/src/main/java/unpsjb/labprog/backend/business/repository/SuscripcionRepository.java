package unpsjb.labprog.backend.business.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.Suscripcion;
import unpsjb.labprog.backend.saas.EstadoSuscripcion;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Integer> {

    Optional<Suscripcion> findByOrganizacion_Id(Integer organizacionId);

    Optional<Suscripcion> findByMpPreapprovalId(String mpPreapprovalId);

    List<Suscripcion> findByEstado(EstadoSuscripcion estado);

    long countByEstado(EstadoSuscripcion estado);

    @Query("""
            SELECT s FROM Suscripcion s
            JOIN s.plan p
            JOIN s.organizacion o
            WHERE (:estado IS NULL OR s.estado = :estado)
              AND (:plan IS NULL OR p.codigo = :plan)
              AND (:q IS NULL OR LOWER(o.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(o.emailContacto) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY s.fechaInicio DESC
            """)
    List<Suscripcion> buscar(
            @Param("estado") EstadoSuscripcion estado,
            @Param("plan") String plan,
            @Param("q") String q);

    @Query("""
            SELECT s FROM Suscripcion s
            WHERE s.estado = :estado
              AND s.fechaFinTrial IS NOT NULL
              AND s.fechaFinTrial <= :limite
            """)
    List<Suscripcion> trialsQueVencenHasta(
            @Param("estado") EstadoSuscripcion estado,
            @Param("limite") LocalDateTime limite);

    List<Suscripcion> findByEstadoAndHeredadaFalse(EstadoSuscripcion estado);
}

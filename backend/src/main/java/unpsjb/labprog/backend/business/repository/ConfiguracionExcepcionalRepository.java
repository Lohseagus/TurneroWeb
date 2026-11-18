package unpsjb.labprog.backend.business.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.ConfiguracionExcepcional;

@Repository
public interface ConfiguracionExcepcionalRepository extends CrudRepository<ConfiguracionExcepcional, Integer>, 
                                                           PagingAndSortingRepository<ConfiguracionExcepcional, Integer> {
    
    // Buscar todas las configuraciones activas para una fecha
    List<ConfiguracionExcepcional> findByFechaAndActivoTrue(LocalDate fecha);
    
    // Buscar configuraciones por tipo y fecha
    List<ConfiguracionExcepcional> findByTipoAndFechaAndActivoTrue(
        ConfiguracionExcepcional.TipoExcepcion tipo, LocalDate fecha);
    
    // Buscar configuraciones por rango de fechas
    List<ConfiguracionExcepcional> findByFechaBetweenAndActivoTrue(LocalDate fechaInicio, LocalDate fechaFin);
    
    // Buscar configuraciones por centro de atención y fecha
    List<ConfiguracionExcepcional> findByFechaAndCentroAtencion_IdAndActivoTrue(LocalDate fecha, Integer centroId);
    
    // Buscar configuraciones por consultorio y fecha (para mantenimientos)
    List<ConfiguracionExcepcional> findByFechaAndConsultorio_IdAndActivoTrue(LocalDate fecha, Integer consultorioId);
    
    // Buscar configuraciones por esquema de turno y fecha
    List<ConfiguracionExcepcional> findByFechaAndEsquemaTurno_IdAndActivoTrue(LocalDate fecha, Integer esquemaTurnoId);
    
    // Verificar si existe un feriado para una fecha
    boolean existsByFechaAndTipoAndActivoTrue(LocalDate fecha, ConfiguracionExcepcional.TipoExcepcion tipo);
    
    // Buscar por rango de fechas y centro (para filtros en frontend)
    List<ConfiguracionExcepcional> findByFechaBetweenAndCentroAtencion_IdAndActivoTrue(
        LocalDate fechaInicio, LocalDate fechaFin, Integer centroId);

    List<ConfiguracionExcepcional> findByTipoAndActivoTrueOrderByFechaDesc(
        ConfiguracionExcepcional.TipoExcepcion tipo);

    List<ConfiguracionExcepcional> findByTipoAndStaffMedico_IdAndActivoTrueOrderByFechaDesc(
        ConfiguracionExcepcional.TipoExcepcion tipo, Integer staffMedicoId);

    List<ConfiguracionExcepcional> findByTipoAndCentroAtencion_IdAndActivoTrueOrderByFechaDesc(
        ConfiguracionExcepcional.TipoExcepcion tipo, Integer centroId);

    /**
     * Ausencias activas de un profesional que cubren el día: un registro de un solo
     * día (fechaFin null) o un rango inclusive.
     */
    @Query("""
        SELECT c FROM ConfiguracionExcepcional c
        WHERE c.activo = true
          AND c.tipo = :tipo
          AND c.staffMedico.id = :staffId
          AND (
            (c.fechaFin IS NULL AND c.fecha = :fecha)
            OR (c.fechaFin IS NOT NULL AND c.fecha <= :fecha AND c.fechaFin >= :fecha)
          )
        """)
    List<ConfiguracionExcepcional> findAusenciasVigentes(
        @Param("staffId") Integer staffId,
        @Param("fecha") LocalDate fecha,
        @Param("tipo") ConfiguracionExcepcional.TipoExcepcion tipo);
}

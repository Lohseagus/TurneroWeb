package unpsjb.labprog.backend.business.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.StaffMedico;

@Repository
public interface StaffMedicoRepository extends JpaRepository<StaffMedico, Integer> {

    List<StaffMedico> findByCentroAtencionId(Integer centroId);

    List<StaffMedico> findByMedicoId(Integer medicoId);

    boolean existsByMedicoAndCentroAtencionAndEspecialidad(Medico medico, CentroAtencion centro, Especialidad especialidad);

    StaffMedico findByMedicoAndCentroAtencionAndEspecialidad(Medico medico, CentroAtencion centroAtencion, Especialidad especialidad);

    /**
     * Búsqueda paginada avanzada con filtros combinados y ordenamiento dinámico
     * @param medico Filtro por nombre/apellido/dni de médico (LIKE, opcional)
     * @param especialidad Filtro por nombre de especialidad (LIKE, opcional)
     * @param centro Filtro por nombre de centro de atención (LIKE, opcional)
     * @param consultorio Filtro por nombre o ID de consultorio (LIKE, opcional)
     * @param pageable Configuración de paginación y ordenamiento
     * @return Página de staff médicos filtrados y ordenados
     */
    @Query("""
        SELECT sm FROM StaffMedico sm
        JOIN sm.medico m
        JOIN sm.especialidad e
        JOIN sm.centroAtencion ca
        LEFT JOIN sm.consultorio c
        WHERE (:medico IS NULL OR
               LOWER(m.nombre) LIKE LOWER(CONCAT('%', CAST(:medico AS string), '%')) OR
               LOWER(m.apellido) LIKE LOWER(CONCAT('%', CAST(:medico AS string), '%')) OR
               CAST(m.dni AS string) LIKE LOWER(CONCAT('%', CAST(:medico AS string), '%')))
           AND (:especialidad IS NULL OR
                LOWER(e.nombre) LIKE LOWER(CONCAT('%', CAST(:especialidad AS string), '%')))
           AND (:centro IS NULL OR
                LOWER(ca.nombre) LIKE LOWER(CONCAT('%', CAST(:centro AS string), '%')))
           AND (:consultorio IS NULL OR
                LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:consultorio AS string), '%')) OR
                CAST(c.id AS string) LIKE CONCAT('%', CAST(:consultorio AS string), '%'))
        """)
    Page<StaffMedico> findByFiltros(@Param("medico") String medico,
                                    @Param("especialidad") String especialidad,
                                    @Param("centro") String centro,
                                    @Param("consultorio") String consultorio,
                                    Pageable pageable);

    /**
     * Busca StaffMedico por DNI del médico
     * Usado para encontrar el staff médico asociado a un usuario con rol MEDICO
     * @param dni DNI del médico
     * @return StaffMedico o null
     */
    @Query("SELECT sm FROM StaffMedico sm WHERE sm.medico.dni = :dni")
    List<StaffMedico> findByMedico_Dni(@Param("dni") Long dni);
    
    /**
     * Verifica si un médico ya está vinculado a un centro (sin importar especialidad)
     * @param medicoId ID del médico
     * @param centroId ID del centro de atención
     * @return true si el médico ya trabaja en ese centro
     */
    @Query("SELECT COUNT(sm) > 0 FROM StaffMedico sm WHERE sm.medico.id = :medicoId AND sm.centroAtencion.id = :centroId")
    boolean existsByMedicoIdAndCentroId(@Param("medicoId") Integer medicoId, @Param("centroId") Integer centroId);

    @Query("select count(distinct s.medico.id) from StaffMedico s where s.centroAtencion.organizacion.id = :orgId")
    long countDistinctMedicosByOrganizacion(@Param("orgId") Integer orgId);

    @Query("""
            select count(distinct s.medico.id) from StaffMedico s
            where s.centroAtencion.organizacion.id = :orgId and s.medico.id = :medicoId
            """)
    long countMedicoEnOrganizacion(@Param("orgId") Integer orgId, @Param("medicoId") Integer medicoId);

    @Query("select s.centroAtencion.id from StaffMedico s where s.id = :id")
    java.util.Optional<Integer> findCentroIdByStaffId(@Param("id") Integer id);

}
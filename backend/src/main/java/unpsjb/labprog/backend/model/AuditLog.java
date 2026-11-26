package unpsjb.labprog.backend.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad inmutable para el registro de auditoría de cambios en entidades del
 * sistema.
 * Una vez creado, un registro no puede ser modificado ni eliminado.
 * Soporta auditoría genérica para múltiples tipos de entidades.
 */
@Entity
@Immutable
@EntityListeners(AuditLogGuard.class)
@Table(indexes = {
        @Index(name = "idx_audit_entity_type_id", columnList = "entityType, entityId"),
        @Index(name = "idx_audit_performed_at", columnList = "performedAt"),
        @Index(name = "idx_audit_performed_by", columnList = "performedBy"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_source", columnList = "source"),
        @Index(name = "idx_audit_actor_role", columnList = "actorRole"),
        @Index(name = "idx_audit_centro", columnList = "centroAtencionId")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "turno_id", updatable = false)
    private Turno turno; // Mantener para compatibilidad con turnos

    @Column(length = 50, nullable = false, updatable = false)
    private String entityType; // TURNO, USUARIO, OPERADOR, CONSULTORIO, etc.

    @Column(updatable = false)
    private Long entityId; // ID de la entidad auditada

    @Column(nullable = false, length = 50, updatable = false)
    private String action; // CREATE, UPDATE, DELETE, LOGIN, etc.

    @Column(nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @Column(nullable = false, length = 100, updatable = false)
    private String performedBy; // Usuario que realizó la acción

    /**
     * Rol de la cuenta en el momento de la acción. Vacío si el actor es el sistema
     * o si la fila es anterior a esta columna y no se pudo cruzar con users.
     */
    @Column(name = "actor_role", length = 30, updatable = false)
    private String actorRole;

    /**
     * SISTEMA (jobs, cancelaciones automáticas, ofertas de lista de espera) o
     * USUARIO. Nullable para que ddl-auto=update agregue la columna sin rellenar
     * las filas viejas; el arranque las completa.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, updatable = false)
    private AuditSource source;

    /**
     * Centro al que pertenece el hecho. Null en acciones de plataforma
     * (las ve el SUPERADMIN). El administrador de un centro no las ve.
     */
    @Column(name = "centro_atencion_id", updatable = false)
    private Integer centroAtencionId;

    @Column(length = 500, updatable = false)
    private String estadoAnterior; // Estado anterior (genérico, puede ser JSON)

    @Column(length = 500, updatable = false)
    private String estadoNuevo; // Nuevo estado (genérico, puede ser JSON)

    // Hibernate 6.2+ mapea @Lob String a oid en PostgreSQL. En producción estas columnas son text.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text", updatable = false)
    private String oldValues; // JSON con valores anteriores de la entidad

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text", updatable = false)
    private String newValues; // JSON con nuevos valores de la entidad

    @Column(length = 500, updatable = false)
    private String reason; // Motivo del cambio (opcional)

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) {
            performedAt = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDateTime();
        }
    }

    // Constructor para auditoría de turnos (compatibilidad hacia atrás)
    public AuditLog(Turno turno, String action, String performedBy, String previousStatus,
            String newStatus, String oldValues, String newValues, String reason) {
        this.turno = turno;
        this.entityType = "TURNO";
        this.entityId = turno != null ? turno.getId().longValue() : null;
        this.action = action;
        this.performedBy = performedBy;
        this.estadoAnterior = previousStatus;
        this.estadoNuevo = newStatus;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.reason = reason;
        this.performedAt = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDateTime();
    }

    // Constructor genérico para auditoría de cualquier entidad
    public AuditLog(String entityType, Long entityId, String action, String performedBy,
            String estadoAnterior, String estadoNuevo, String oldValues, String newValues, String reason) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.performedBy = performedBy;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.reason = reason;
        this.performedAt = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDateTime();
    }

    // Constantes para las acciones de auditoría
    public static final class Actions {
        // Acciones generales
        public static final String CREATE = "CREATE";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String VIEW = "VIEW";
        public static final String LOGIN = "LOGIN";
        public static final String LOGOUT = "LOGOUT";
        public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
        public static final String ENABLE = "ENABLE";
        public static final String DISABLE = "DISABLE";

        // Acciones específicas de turnos
        public static final String UPDATE_STATUS = "UPDATE_STATUS";
        public static final String CANCEL = "CANCEL";
        public static final String CONFIRM = "CONFIRM";
        public static final String COMPLETE = "COMPLETE";
        public static final String RESCHEDULE = "RESCHEDULE";
        public static final String ASSIGN = "ASSIGN";
        public static final String MODIFY = "MODIFY";
        public static final String CANCELACION_TURNO_NOTIFICACION = "CANCELACION_TURNO_NOTIFICACION";

        // Acciones específicas de usuarios y roles
        public static final String ROLE_CHANGE = "ROLE_CHANGE";
        public static final String USER_CREATE = "USER_CREATE";
        public static final String USER_UPDATE = "USER_UPDATE";
        public static final String USER_DELETE = "USER_DELETE";
        public static final String USER_ENABLE = "USER_ENABLE";
        public static final String USER_DISABLE = "USER_DISABLE";
    }

    // Constantes para tipos de entidad
    public static final class EntityTypes {
        public static final String TURNO = "TURNO";
        public static final String USUARIO = "USUARIO";
        public static final String OPERADOR = "OPERADOR";
        public static final String MEDICO = "MEDICO";
        public static final String PACIENTE = "PACIENTE";
        public static final String CONSULTORIO = "CONSULTORIO";
        public static final String CENTRO_ATENCION = "CENTRO_ATENCION";
        public static final String ESPECIALIDAD = "ESPECIALIDAD";
        public static final String OBRA_SOCIAL = "OBRA_SOCIAL";
        public static final String DISPONIBILIDAD_MEDICO = "DISPONIBILIDAD_MEDICO";
        public static final String ESQUEMA_TURNO = "ESQUEMA_TURNO";
        public static final String AGENDA = "AGENDA";
        public static final String STAFF_MEDICO = "STAFF_MEDICO";
        public static final String NOTIFICACION = "NOTIFICACION";
        public static final String CONFIGURACION = "CONFIG";
    }
}

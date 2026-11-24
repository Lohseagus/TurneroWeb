package unpsjb.labprog.backend.model;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad para manejar configuraciones excepcionales (feriados, mantenimientos, etc.)
 * Separada de la entidad Agenda para simplificar la lógica y evitar duplicación.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionExcepcional {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private LocalDate fecha;

    /** Fin inclusive. Si es null, la excepción es solo {@link #fecha}. */
    private LocalDate fechaFin;
    
    @Enumerated(EnumType.STRING)
    private TipoExcepcion tipo;
    
    private String descripcion;
    
    // Para atención especial
    private LocalTime horaInicio;
    private LocalTime horaFin;
    
    // Para mantenimientos y atención especial
    private Integer duracion;
    
    // Relaciones opcionales. En un feriado, null es de todo el sistema.
    // ddl-auto=update deja la columna nullable: no hay que rellenar filas viejas.
    @ManyToOne(optional = true)
    @JoinColumn(name = "centro_atencion_id", nullable = true)
    private CentroAtencion centroAtencion;
    
    @ManyToOne(optional = true) 
    private Consultorio consultorio;        // Para mantenimientos específicos
    
    @ManyToOne(optional = true)
    private EsquemaTurno esquemaTurno;      // Para excepciones de esquemas específicos

    @ManyToOne(optional = true)
    private StaffMedico staffMedico;        // Para una ausencia de un profesional
    
    private boolean activo = true;

    /** Ausencia cargada por la administración dentro del plazo mínimo. */
    private boolean urgente = false;

    public LocalDate finInclusive() {
        return fechaFin != null ? fechaFin : fecha;
    }

    public enum TipoExcepcion {
        FERIADO,           // Sin centro: todo el sistema. Con centro: solo ese centro.
        MANTENIMIENTO,     // Aplica a un consultorio específico
        ATENCION_ESPECIAL, // Horarios especiales para un esquema específico
        AUSENCIA_MEDICO    // El profesional no atiende en el rango
    }
}

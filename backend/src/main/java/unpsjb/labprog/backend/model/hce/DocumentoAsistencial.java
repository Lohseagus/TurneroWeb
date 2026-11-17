package unpsjb.labprog.backend.model.hce;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;

/**
 * Datos comunes de una receta o una orden de estudio.
 * Al emitir se congela una copia del paciente, del prescriptor y del centro:
 * un cambio posterior en la ficha no reescribe el documento firmado.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class DocumentoAsistencial {

    public static final String FIRMA_ELECTRONICA_LOCAL = "ELECTRONICA_LOCAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "centro_atencion_id", nullable = false)
    private CentroAtencion centroAtencion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EstadoPrescripcion estado = EstadoPrescripcion.BORRADOR;

    /** Identificador propio, con lugar para reemplazarlo por el CUIR oficial. */
    @Column(length = 64, unique = true)
    private String identificador;

    /** CUIR asignado por el repositorio nacional. Vacío hasta esa integración. */
    @Column(length = 64)
    private String cuir;

    /** Token largo de la URL pública. No se genera hasta la emisión. */
    @Column(name = "codigo_verificacion", length = 80, unique = true)
    private String codigoVerificacion;

    @Column(name = "hash_contenido", length = 64)
    private String hashContenido;

    @Column(length = 40)
    private String tipoFirma;

    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    private LocalDateTime firmadoEn;

    @Column(name = "diagnostico_system", length = 200)
    private String diagnosticoSystem;

    @Column(name = "diagnostico_code", length = 16)
    private String diagnosticoCode;

    @Column(name = "diagnostico_display", length = 400)
    private String diagnosticoDisplay;

    @Column(name = "paciente_nombre", length = 120)
    private String pacienteNombre;

    @Column(name = "paciente_apellido", length = 120)
    private String pacienteApellido;

    private Long pacienteDni;

    @Column(name = "paciente_sexo", length = 1)
    private String pacienteSexo;

    private LocalDate pacienteNacimiento;

    @Column(name = "obra_social_nombre", length = 160)
    private String obraSocialNombre;

    @Column(length = 40)
    private String numeroAfiliado;

    @Column(name = "prescriptor_nombre", length = 120)
    private String prescriptorNombre;

    @Column(name = "prescriptor_apellido", length = 120)
    private String prescriptorApellido;

    @Column(length = 40)
    private String matricula;

    @Column(name = "matricula_nacional", length = 40)
    private String matriculaNacional;

    @Column(name = "matricula_provincial", length = 40)
    private String matriculaProvincial;

    @Column(name = "refeps_id", length = 40)
    private String refepsId;

    @Column(name = "especialidad_nombre", length = 160)
    private String especialidadNombre;

    @Column(name = "centro_nombre", length = 160)
    private String centroNombre;

    @Column(length = 300)
    private String resumen;

    @Column(length = 40)
    private String plataforma;

    @Column(length = 500)
    private String motivoAnulacion;

    @Column(length = 180)
    private String anuladoPor;

    private LocalDateTime anuladoEn;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime actualizadaEn;
}

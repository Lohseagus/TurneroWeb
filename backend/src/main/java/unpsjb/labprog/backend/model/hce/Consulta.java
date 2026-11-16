package unpsjb.labprog.backend.model.hce;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import unpsjb.labprog.backend.model.CentroAtencion;
import unpsjb.labprog.backend.model.Especialidad;
import unpsjb.labprog.backend.model.Medico;
import unpsjb.labprog.backend.model.Paciente;
import unpsjb.labprog.backend.model.Turno;

@Entity
@Table(name = "hce_consulta")
@Getter
@Setter
@NoArgsConstructor
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne
    @JoinColumn(name = "turno_id", unique = true)
    private Turno turno;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "centro_atencion_id", nullable = false)
    private CentroAtencion centroAtencion;

    @ManyToOne
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConsulta estado = EstadoConsulta.BORRADOR;

    @Column(length = 500)
    private String motivoConsulta;

    @Column(columnDefinition = "TEXT")
    private String anamnesis;

    @Column(columnDefinition = "TEXT")
    private String examenFisico;

    @Column(columnDefinition = "TEXT")
    private String indicaciones;

    @Column(columnDefinition = "TEXT")
    private String plan;

    private LocalDate proximoControl;

    private Integer tensionSistolica;
    private Integer tensionDiastolica;
    private Integer frecuenciaCardiaca;
    private Double temperatura;
    private Double pesoKg;
    private Double tallaCm;
    private Double imc;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime actualizadaEn;

    private LocalDateTime cerradaEn;
}

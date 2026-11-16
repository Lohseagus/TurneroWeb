package unpsjb.labprog.backend.model.hce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hce_cie10", uniqueConstraints = @UniqueConstraint(columnNames = {"system_uri", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class CodigoCie10 {

    public static final String SISTEMA_CIE10 = "http://hl7.org/fhir/sid/icd-10";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_uri", nullable = false, length = 200)
    private String systemUri = SISTEMA_CIE10;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 400)
    private String display;
}

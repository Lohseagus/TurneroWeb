package unpsjb.labprog.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import unpsjb.labprog.backend.business.repository.CodigoCie10Repository;
import unpsjb.labprog.backend.model.hce.CodigoCie10;

/**
 * Subconjunto ambulatorio de CIE-10. El catálogo completo se puede importar
 * después: la clave es (systemUri, code).
 */
@Component
@Order(3)
public class Cie10Initializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(Cie10Initializer.class);

    private static final String[][] CODIGOS = {
            {"A09", "Diarrea y gastroenteritis de presunto origen infeccioso"},
            {"B34", "Infección viral de sitio no especificado"},
            {"D50", "Anemia por deficiencia de hierro"},
            {"E03", "Hipotiroidismo"},
            {"E05", "Tirotoxicosis"},
            {"E10", "Diabetes mellitus tipo 1"},
            {"E11", "Diabetes mellitus tipo 2"},
            {"E66", "Obesidad"},
            {"E78", "Trastornos del metabolismo de las lipoproteínas"},
            {"E86", "Depleción del volumen"},
            {"F32", "Episodio depresivo"},
            {"F41", "Otros trastornos de ansiedad"},
            {"G43", "Migraña"},
            {"G44", "Otros síndromes de cefalea"},
            {"H10", "Conjuntivitis"},
            {"H66", "Otitis media supurativa y la no especificada"},
            {"I10", "Hipertensión esencial (primaria)"},
            {"I11", "Enfermedad cardíaca hipertensiva"},
            {"I21", "Infarto agudo de miocardio"},
            {"I25", "Enfermedad isquémica crónica del corazón"},
            {"I50", "Insuficiencia cardíaca"},
            {"I63", "Infarto cerebral"},
            {"J00", "Rinofaringitis aguda (resfriado común)"},
            {"J02", "Faringitis aguda"},
            {"J03", "Amigdalitis aguda"},
            {"J06", "Infecciones agudas de las vías respiratorias superiores"},
            {"J18", "Neumonía, organismo no especificado"},
            {"J20", "Bronquitis aguda"},
            {"J30", "Rinitis alérgica y vasomotora"},
            {"J44", "Otras enfermedades pulmonares obstructivas crónicas"},
            {"J45", "Asma"},
            {"K21", "Enfermedad del reflujo gastroesofágico"},
            {"K29", "Gastritis y duodenitis"},
            {"K59", "Otros trastornos funcionales del intestino"},
            {"K80", "Colelitiasis"},
            {"L20", "Dermatitis atópica"},
            {"L30", "Otras dermatitis"},
            {"M25", "Otros trastornos articulares"},
            {"M54", "Dorsalgia"},
            {"M79", "Otros trastornos de los tejidos blandos"},
            {"N20", "Cálculo del riñón y del uréter"},
            {"N39", "Otros trastornos del sistema urinario"},
            {"N94", "Dolor y otras afecciones de los órganos genitales femeninos"},
            {"R05", "Tos"},
            {"R10", "Dolor abdominal y pélvico"},
            {"R42", "Mareo y desvanecimiento"},
            {"R50", "Fiebre de otro origen y de origen desconocido"},
            {"R51", "Cefalea"},
            {"U07.1", "COVID-19, virus identificado"},
            {"Z00", "Examen general e investigación de personas sin quejas"},
            {"Z23", "Necesidad de inmunización contra enfermedad bacteriana única"},
            {"Z76", "Personas en contacto con los servicios de salud en otras circunstancias"}
    };

    private final CodigoCie10Repository repository;

    public Cie10Initializer(CodigoCie10Repository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int nuevos = 0;
        for (String[] fila : CODIGOS) {
            if (repository.findBySystemUriAndCode(CodigoCie10.SISTEMA_CIE10, fila[0]).isEmpty()) {
                CodigoCie10 codigo = new CodigoCie10();
                codigo.setSystemUri(CodigoCie10.SISTEMA_CIE10);
                codigo.setCode(fila[0]);
                codigo.setDisplay(fila[1]);
                repository.save(codigo);
                nuevos++;
            }
        }
        if (nuevos > 0) {
            logger.info("Catálogo CIE-10: {} códigos nuevos", nuevos);
        }
    }
}

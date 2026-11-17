package unpsjb.labprog.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import unpsjb.labprog.backend.business.repository.CatalogoMedicamentoRepository;
import unpsjb.labprog.backend.model.hce.CatalogoMedicamento;
import unpsjb.labprog.backend.model.hce.ListaControlada;
import unpsjb.labprog.backend.model.hce.TipoReceta;

/**
 * Vademécum chico, importable después. La clave es nombre + concentración + forma.
 * Las filas controladas están para que el autocompletado avise: no se pueden emitir.
 */
@Component
@Order(4)
public class CatalogoMedicamentoInitializer implements CommandLineRunner {

    private static final String[][] COMUNES = {
            {"ibuprofeno", "400 mg", "comprimidos", "oral"},
            {"ibuprofeno", "600 mg", "comprimidos", "oral"},
            {"paracetamol", "500 mg", "comprimidos", "oral"},
            {"paracetamol", "1 g", "comprimidos", "oral"},
            {"amoxicilina", "500 mg", "cápsulas", "oral"},
            {"amoxicilina", "875 mg", "comprimidos", "oral"},
            {"losartán", "50 mg", "comprimidos", "oral"},
            {"enalapril", "10 mg", "comprimidos", "oral"},
            {"amlodipina", "5 mg", "comprimidos", "oral"},
            {"metformina", "850 mg", "comprimidos", "oral"},
            {"omeprazol", "20 mg", "cápsulas", "oral"},
            {"atorvastatina", "20 mg", "comprimidos", "oral"},
            {"salbutamol", "100 mcg", "aerosol", "inhalatoria"},
            {"cetirizina", "10 mg", "comprimidos", "oral"},
            {"loratadina", "10 mg", "comprimidos", "oral"}
    };

    private static final String[][] CONTROLADOS = {
            {"clonazepam", "0,5 mg", "comprimidos", "oral", "PSICOTROPICO", "IV"},
            {"alprazolam", "0,5 mg", "comprimidos", "oral", "PSICOTROPICO", "IV"},
            {"codeína", "30 mg", "comprimidos", "oral", "ESTUPEFACIENTE", "II"}
    };

    private final CatalogoMedicamentoRepository repository;

    public CatalogoMedicamentoInitializer(CatalogoMedicamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        for (String[] fila : COMUNES) {
            guardar(fila[0], fila[1], fila[2], fila[3], TipoReceta.COMUN, ListaControlada.NINGUNA);
        }
        for (String[] fila : CONTROLADOS) {
            guardar(fila[0], fila[1], fila[2], fila[3], TipoReceta.valueOf(fila[4]), ListaControlada.valueOf(fila[5]));
        }
    }

    private void guardar(String nombre, String concentracion, String forma, String via, TipoReceta tipo,
            ListaControlada lista) {
        if (repository.existsByNombreGenericoAndConcentracionAndFormaFarmaceutica(nombre, concentracion, forma)) {
            return;
        }
        CatalogoMedicamento medicamento = new CatalogoMedicamento();
        medicamento.setNombreGenerico(nombre);
        medicamento.setConcentracion(concentracion);
        medicamento.setFormaFarmaceutica(forma);
        medicamento.setVia(via);
        medicamento.setTipo(tipo);
        medicamento.setLista(lista);
        repository.save(medicamento);
    }
}

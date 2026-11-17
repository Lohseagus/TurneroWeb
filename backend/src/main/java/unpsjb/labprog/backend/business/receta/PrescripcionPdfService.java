package unpsjb.labprog.backend.business.receta;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescripcionPdfService {

    private final RecetaService recetas;
    private final OrdenEstudioService ordenes;
    private final DatosDocumento datos;

    public PrescripcionPdfService(RecetaService recetas, OrdenEstudioService ordenes, DatosDocumento datos) {
        this.recetas = recetas;
        this.ordenes = ordenes;
        this.datos = datos;
    }

    @Transactional
    public byte[] receta(Long id) {
        var receta = recetas.paraPdf(id);
        return PrescripcionPdf.receta(receta, datos.url("/verificar-receta/", receta.getCodigoVerificacion()));
    }

    @Transactional
    public byte[] orden(Long id) {
        var orden = ordenes.paraPdf(id);
        return PrescripcionPdf.orden(orden, datos.url("/verificar-orden/", orden.getCodigoVerificacion()));
    }
}

package unpsjb.labprog.backend.saas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import unpsjb.labprog.backend.model.AddonMensajeria;
import unpsjb.labprog.backend.model.ConfiguracionSaas;
import unpsjb.labprog.backend.model.PlanSuscripcion;

class CalculadoraPrecioSaasTest {

    private ConfiguracionSaas config;

    @BeforeEach
    void setUp() {
        config = new ConfiguracionSaas();
        config.setIvaPorcentaje(new BigDecimal("21.00"));
        config.setDescuentoAnualPorcentaje(new BigDecimal("15.00"));
        config.setDiasTrial(30);
    }

    @Test
    void profesionalMensualYAnualConIva() {
        PlanSuscripcion plan = plan(CodigosPlan.PROFESIONAL, "25000.00", "0");

        PrecioCalculado mensual = CalculadoraPrecioSaas.calcular(
                plan, config, 1, 1, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(new BigDecimal("25000.00"), mensual.netoMensual());
        assertEquals(new BigDecimal("5250.00"), mensual.iva());
        assertEquals(new BigDecimal("30250.00"), mensual.total());

        PrecioCalculado anual = CalculadoraPrecioSaas.calcular(
                plan, config, 1, 1, List.of(), CicloFacturacion.ANUAL);
        assertEquals(new BigDecimal("255000.00"), anual.netoPeriodo());
        assertEquals(new BigDecimal("53550.00"), anual.iva());
        assertEquals(new BigDecimal("308550.00"), anual.total());
    }

    @Test
    void consultorioCobraPorProfesionalEntreDosYDiez() {
        PlanSuscripcion plan = plan(CodigosPlan.CONSULTORIO, "0", "18000.00");
        plan.setMinProfesionales(2);
        plan.setMaxProfesionales(10);

        PrecioCalculado dos = CalculadoraPrecioSaas.calcular(
                plan, config, 2, 1, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(new BigDecimal("36000.00"), dos.netoMensual());
        assertEquals(new BigDecimal("43560.00"), dos.total());
        assertEquals(2, dos.profesionalesFacturados());

        PrecioCalculado pedidoBajoElMinimo = CalculadoraPrecioSaas.calcular(
                plan, config, 1, 1, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(2, pedidoBajoElMinimo.profesionalesFacturados());

        assertThrows(ReglaSuscripcionException.class, () -> CalculadoraPrecioSaas.calcular(
                plan, config, 11, 1, List.of(), CicloFacturacion.MENSUAL));
    }

    @Test
    void centroEsPrecioFijoYRedCobraSedeExtra() {
        PlanSuscripcion centro = plan(CodigosPlan.CENTRO, "200000.00", "0");
        centro.setSedesIncluidas(1);
        centro.setMaxCentros(1);
        PrecioCalculado precioCentro = CalculadoraPrecioSaas.calcular(
                centro, config, 1, 1, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(new BigDecimal("242000.00"), precioCentro.total());

        PlanSuscripcion red = plan(CodigosPlan.RED, "350000.00", "0");
        red.setSedesIncluidas(3);
        red.setPrecioPorSedeExtra(new BigDecimal("120000.00"));

        PrecioCalculado incluida = CalculadoraPrecioSaas.calcular(
                red, config, 1, 1, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(0, incluida.sedesExtra());
        assertEquals(new BigDecimal("423500.00"), incluida.total());

        PrecioCalculado cuartaSede = CalculadoraPrecioSaas.calcular(
                red, config, 1, 4, List.of(), CicloFacturacion.MENSUAL);
        assertEquals(1, cuartaSede.sedesExtra());
        assertEquals(new BigDecimal("470000.00"), cuartaSede.netoMensual());
        assertEquals(new BigDecimal("568700.00"), cuartaSede.total());
    }

    @Test
    void addonSumaAlNeto() {
        PlanSuscripcion plan = plan(CodigosPlan.PROFESIONAL, "25000.00", "0");
        AddonMensajeria whatsapp = new AddonMensajeria();
        whatsapp.setPrecioMensualNeto(new BigDecimal("12000.00"));

        PrecioCalculado precio = CalculadoraPrecioSaas.calcular(
                plan, config, 1, 1, List.of(whatsapp.getPrecioMensualNeto()), CicloFacturacion.MENSUAL);
        assertEquals(new BigDecimal("37000.00"), precio.netoMensual());
        assertEquals(new BigDecimal("44770.00"), precio.total());
    }

    private PlanSuscripcion plan(String codigo, String base, String porProfesional) {
        PlanSuscripcion plan = new PlanSuscripcion();
        plan.setCodigo(codigo);
        plan.setNombre(codigo);
        plan.setPrecioBaseMensual(new BigDecimal(base));
        plan.setPrecioPorProfesional(new BigDecimal(porProfesional));
        plan.setSedesIncluidas(1);
        return plan;
    }
}

package com.cloud_technological.aura_pos.services.causacion_gasto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Calculo;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.LineaCrear;
import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Cálculo de IVA, retenciones y neto de una causación de gasto. */
class CalculadoraCausacionTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static TarifaRetencionEntity servicios() {
        TarifaRetencionEntity t = new TarifaRetencionEntity();
        t.setId(7L);
        t.setTipo("RETEFUENTE");
        t.setConcepto("Servicios generales");
        t.setTarifaNatural(v("6"));
        t.setTarifaJuridica(v("4"));
        t.setBaseMinima(v("199000"));
        t.setActivo(true);
        return t;
    }

    private static LineaCrear linea(String base, String iva, Long tarifaId) {
        LineaCrear l = new LineaCrear();
        l.setCuentaId(5135L);
        l.setBase(v(base));
        l.setTarifaIva(iva != null ? v(iva) : null);
        l.setTarifaRetencionId(tarifaId);
        return l;
    }

    @Test
    void personaJuridicaUsaSuTarifaYCalculaReteivaYReteica() {
        LineaCrear l = linea("1000000", "19", 7L);
        l.setTarifaReteiva(v("15"));
        l.setTarifaReteica(v("0.966"));

        Calculo c = CalculadoraCausacion.calcular(List.of(l), "JURIDICA", Map.of(7L, servicios()));

        assertEquals(0, v("190000").compareTo(c.getIva()));
        assertEquals(0, v("40000").compareTo(c.getRetefuente()));
        assertEquals(0, v("28500").compareTo(c.getReteiva()));
        assertEquals(0, v("9660").compareTo(c.getReteica()));
        assertEquals(0, v("1111840").compareTo(c.getTotalPagar()));
    }

    @Test
    void personaNaturalUsaLaTarifaNatural() {
        Calculo c = CalculadoraCausacion.calcular(List.of(linea("500000", null, 7L)), "NATURAL",
                Map.of(7L, servicios()));
        assertEquals(0, v("30000").compareTo(c.getRetefuente()));
        assertEquals("NATURAL", c.getTipoPersona());
    }

    @Test
    void laBaseMinimaSeMideConLaSumaDelConceptoEnElDocumento() {
        // 120.000 + 100.000 = 220.000 ≥ 199.000 → sí retiene en las dos líneas.
        Calculo si = CalculadoraCausacion.calcular(
                List.of(linea("120000", null, 7L), linea("100000", null, 7L)), "JURIDICA", Map.of(7L, servicios()));
        assertEquals(0, v("8800").compareTo(si.getRetefuente()));
        assertTrue(si.getAvisos().isEmpty());

        // 150.000 < 199.000 → no retiene y avisa.
        Calculo no = CalculadoraCausacion.calcular(List.of(linea("150000", null, 7L)), "JURIDICA",
                Map.of(7L, servicios()));
        assertEquals(0, BigDecimal.ZERO.compareTo(no.getRetefuente()));
        assertEquals(1, no.getAvisos().size());
    }

    @Test
    void laTarifaEscritaAManoMandaSobreLaDelConcepto() {
        LineaCrear l = linea("1000000", null, 7L);
        l.setTarifaRetefuente(v("11"));
        Calculo c = CalculadoraCausacion.calcular(List.of(l), "JURIDICA", Map.of(7L, servicios()));
        assertEquals(0, v("110000").compareTo(c.getRetefuente()));
    }

    @Test
    void rechazaLineasSinValorYRetencionesQueSeComenLaFactura() {
        assertThrows(GlobalException.class,
                () -> CalculadoraCausacion.calcular(List.of(linea("0", null, null)), "JURIDICA", Map.of()));
        LineaCrear l = linea("100000", null, null);
        l.setTarifaRetefuente(v("100"));
        assertThrows(GlobalException.class,
                () -> CalculadoraCausacion.calcular(List.of(l), "JURIDICA", Map.of()));
        assertThrows(GlobalException.class,
                () -> CalculadoraCausacion.calcular(List.of(), "JURIDICA", Map.of()));
    }

    @Test
    void conceptoInexistenteEsError() {
        assertThrows(GlobalException.class,
                () -> CalculadoraCausacion.calcular(List.of(linea("500000", null, 99L)), "JURIDICA", Map.of()));
    }
}

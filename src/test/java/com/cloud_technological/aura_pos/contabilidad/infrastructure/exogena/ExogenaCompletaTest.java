package com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaCalculadora.Linea;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaCalculadora.Mapeo;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaCalculadora.Reglas;
import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;

/** Exógena completa (V225): columnas reales, 1003, 1010, 2276 e identificación DIAN. */
class ExogenaCompletaTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static CuentaTerceroMovimientoDto mov(String cuenta, Long tercero, String db, String cr) {
        return new CuentaTerceroMovimientoDto(cuenta, tercero, bd(db), bd(cr));
    }

    @Test
    void el1001JuntaPagoYRetencionEnColumnasDistintas() {
        List<Mapeo> mapeos = List.of(
                new Mapeo(5002L, "5110", null, "MOVIMIENTO_DB", null),
                new Mapeo(5002L, "236515", null, "MOVIMIENTO_CR", "RET_RENTA"));
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("511025", 7L, "2000000", "0"), mov("236515", 7L, "0", "200000")),
                List.of(), mapeos, new Reglas("PAGO_DED", null, bd("100000")));
        assertEquals(1, lineas.size());
        assertEquals(0, bd("2000000").compareTo(lineas.get(0).valor("PAGO_DED")));
        assertEquals(0, bd("200000").compareTo(lineas.get(0).valor("RET_RENTA")));
    }

    @Test
    void el1003SoloReportaTercerosQueLeRetuvieron() {
        List<Mapeo> mapeos = List.of(
                new Mapeo(1303L, "41", null, "MOVIMIENTO_CR", "BASE"),
                new Mapeo(1303L, "135515", null, "MOVIMIENTO_DB", "RETENCION"));
        List<Linea> lineas = ExogenaCalculadora.calcular(
                List.of(mov("4170", 1L, "0", "10000000"), mov("135515", 1L, "400000", "0"),
                        mov("4170", 2L, "0", "5000000")),
                List.of(), mapeos, new Reglas("BASE", "RETENCION", BigDecimal.ZERO));
        assertEquals(1, lineas.size(), "la EPS 2 no le retuvo: no va");
        assertEquals(1L, lineas.get(0).terceroId());
        assertEquals(0, bd("400000").compareTo(lineas.get(0).valor("RETENCION")));
    }

    @Test
    void elPorcentajeDeSociosSaleDelValorPatrimonial() {
        List<Linea> lineas = List.of(
                new Linea(1L, 1L, Map.of("VALOR_PATRIMONIAL", bd("6000000")), false, "VALOR_PATRIMONIAL"),
                new Linea(1L, 2L, Map.of("VALOR_PATRIMONIAL", bd("4000000")), false, "VALOR_PATRIMONIAL"));
        List<Linea> con = ExogenaCalculadora.conPorcentaje(lineas, "VALOR_PATRIMONIAL", "PORCENTAJE");
        assertEquals(0, bd("60.00").compareTo(con.get(0).valor("PORCENTAJE")));
        assertEquals(0, bd("40.00").compareTo(con.get(1).valor("PORCENTAJE")));
    }

    @Test
    void sinUmbralNoHayCuantiasMenoresSalvoSinTercero() {
        List<Linea> lineas = ExogenaCalculadora.calcular(List.of(),
                List.of(mov("111005", 9L, "50000", "0"), mov("111010", null, "30000", "0")),
                List.of(new Mapeo(1110L, "1110", null, "SALDO_DB", "VALOR")), new Reglas("VALOR", null, BigDecimal.ZERO));
        assertEquals(2, lineas.size());
        assertTrue(lineas.stream().anyMatch(l -> l.terceroId() == null && l.cuantiaMenor()));
    }

    @Test
    void el2276ClasificaLaNominaPorColumna() {
        Map<String, BigDecimal> v = Clasificador2276.valores(List.of(
                new Clasificador2276.Pago("SALARIO", "Basico", "DEVENGADO", true, "PERIODICO", bd("24000000")),
                new Clasificador2276.Pago("AUX_TRANSP", "Transporte", "DEVENGADO", false, "PERIODICO", bd("2400000")),
                new Clasificador2276.Pago("PRIMA", "Primas", "DEVENGADO", false, "PRIMA", bd("2000000")),
                new Clasificador2276.Pago("CESANTIAS", "Cesantias", "DEVENGADO", false, "CESANTIAS", bd("2000000")),
                new Clasificador2276.Pago("INT_CESANTIAS", "Cesantias", "DEVENGADO", false, "INTERESES_CESANTIAS",
                        bd("240000")),
                new Clasificador2276.Pago("BONO", "Bonificaciones", "DEVENGADO", false, "PERIODICO", bd("1000000")),
                new Clasificador2276.Pago("DED_SALUD", "Salud", "DEDUCCION", false, "PERIODICO", bd("960000")),
                new Clasificador2276.Pago("DED_PENSION", "FondoPension", "DEDUCCION", false, "PERIODICO", bd("960000")),
                new Clasificador2276.Pago("PRESTAMO", "Libranza", "DEDUCCION", false, "PERIODICO", bd("500000"))));
        assertEquals(0, bd("24000000").compareTo(v.get("SALARIOS")));
        assertEquals(0, bd("3400000").compareTo(v.get("OTROS")), "transporte + bono no salarial");
        assertEquals(0, bd("2000000").compareTo(v.get("PRESTACIONES")));
        assertEquals(0, bd("2000000").compareTo(v.get("CESANTIAS_FONDO")));
        assertEquals(0, bd("240000").compareTo(v.get("CESANTIAS_PAGADAS")));
        assertEquals(0, bd("31640000").compareTo(v.get("TOTAL_BRUTO")));
        assertEquals(0, bd("960000").compareTo(v.get("APORTE_SALUD")));
        assertEquals(0, bd("960000").compareTo(v.get("APORTE_PENSION")));
        assertTrue(!v.containsKey("RETENCION") || v.get("RETENCION").signum() == 0, "la libranza no se reporta");
    }

    private static TerceroExogenaDto tercero(String tipo, String numero, String dv, String razon, String n1, String n2,
                                             String a1, String a2, String tipoPersona, String dane, String pais) {
        return new TerceroExogenaDto(1L, tipo, numero, dv, razon, null, null, "CRA 1 # 2-3", null,
                n1, n2, a1, a2, tipoPersona, dane, pais);
    }

    @Test
    void identificacionDeUnaPersonaNatural() {
        ExogenaExcelExporter.Fila f = new ExogenaExcelExporter.Fila("5001",
                tercero("CC", "1.066.598.235", null, null, "KAREN", "LORENA", "VERGARA", "SUAREZ", "NATURAL",
                        "23466", "CO"), Map.of());
        assertEquals("13", IdentidadDian.valor("TDOC", f));
        assertEquals("1066598235", IdentidadDian.valor("NID", f));
        assertEquals("", IdentidadDian.valor("DV", f), "solo el NIT lleva DV");
        assertEquals("VERGARA", IdentidadDian.valor("APL1", f));
        assertEquals("SUAREZ", IdentidadDian.valor("APL2", f));
        assertEquals("KAREN", IdentidadDian.valor("NOM1", f));
        assertEquals("", IdentidadDian.valor("RAZ", f));
        assertEquals("23", IdentidadDian.valor("DPTO", f));
        assertEquals("466", IdentidadDian.valor("MUN", f));
        assertEquals("169", IdentidadDian.valor("PAIS", f));
    }

    @Test
    void identificacionDeUnaSociedadConDvCalculado() {
        ExogenaExcelExporter.Fila f = new ExogenaExcelExporter.Fila("1303",
                tercero("NIT", "900373115", null, "EPS SURA S.A.", null, null, null, null, "JURIDICA", "05001", null),
                Map.of());
        assertEquals("31", IdentidadDian.valor("TDOC", f));
        assertEquals("EPS SURA S.A.", IdentidadDian.valor("RAZ", f));
        assertEquals("", IdentidadDian.valor("APL1", f));
        assertTrue(IdentidadDian.valor("DV", f).matches("\\d"), "DV calculado");
    }

    @Test
    void cuantiasMenoresLlevanLaUbicacionDelInformante() {
        ExogenaExcelExporter.Fila f = new ExogenaExcelExporter.Fila("5016", null, Map.of());
        ExogenaExcelExporter.Informante inf = new ExogenaExcelExporter.Informante("CL 10 # 5-20", "23466");
        assertEquals("43", IdentidadDian.valor("TDOC", f, inf));
        assertEquals("222222222", IdentidadDian.valor("NID", f, inf));
        assertEquals("CUANTÍAS MENORES", IdentidadDian.valor("RAZ", f, inf));
        assertEquals("CL 10 # 5-20", IdentidadDian.valor("DIR", f, inf));
        assertEquals("23", IdentidadDian.valor("DPTO", f, inf));
        assertEquals("466", IdentidadDian.valor("MUN", f, inf));
    }
}

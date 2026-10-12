package com.cloud_technological.aura_pos.services.conciliacion_fiscal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Ajuste;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.FilaBalance;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Renglon;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Resumen;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;

/** Análisis de la conciliación fiscal y lectura del balance en Excel. */
class AnalizadorConciliacionTest {

    private static final BigDecimal TARIFA = new BigDecimal("35");

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static Cuenta cuenta(String codigo, String nombre, String saldo) {
        Cuenta c = new Cuenta();
        c.setCodigo(codigo);
        c.setNombre(nombre);
        c.setSaldo(v(saldo));
        c.setRenglon(RenglonesFiscales.clasificar(codigo));
        return c;
    }

    /** Balance cuadrado antes del cierre: utilidad 300 = 1000 − 500 − 200. */
    private static List<Cuenta> balance() {
        return new ArrayList<>(List.of(
                cuenta("110505", "Caja general", "1300"),
                cuenta("135515", "Retención en la fuente", "100"),
                cuenta("220505", "Proveedores nacionales", "400"),
                cuenta("261005", "Para obligaciones laborales", "50"),
                cuenta("261505", "Para obligaciones fiscales", "150"),
                cuenta("310505", "Capital autorizado", "500"),
                cuenta("413505", "Comercio al por mayor", "1000"),
                cuenta("613505", "Costo de ventas", "500"),
                cuenta("511595", "Gravamen a los movimientos financieros", "40"),
                cuenta("540505", "Impuesto de renta y complementarios", "60"),
                cuenta("539520", "Multas, sanciones y litigios", "100"),
                cuenta("810505", "Cuentas de orden", "999")));
    }

    private static Renglon renglon(Resumen r, String codigo) {
        return r.getRenglones().stream().filter(x -> x.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    void clasificaPorElPrefijoMasLargoYDejaFueraLasCuentasDeOrden() {
        assertEquals("A04", RenglonesFiscales.clasificar("135515"));
        assertEquals("A03", RenglonesFiscales.clasificar("130505"));
        assertEquals("C01", RenglonesFiscales.clasificar("613505"));
        assertEquals(RenglonesFiscales.NO_APLICA, RenglonesFiscales.clasificar("810505"));
        assertEquals(RenglonesFiscales.NO_APLICA, RenglonesFiscales.clasificar("590505"));
        assertNull(RenglonesFiscales.clasificar("000"));
    }

    @Test
    void cuadraConElResultadoDelAnoEnElPatrimonio() {
        Resumen r = AnalizadorConciliacion.analizar(balance(), List.of(), TARIFA);

        assertEquals(0, v("1400").compareTo(r.getActivosContable()));
        assertEquals(0, v("600").compareTo(r.getPasivosContable()));
        assertEquals(0, v("800").compareTo(r.getPatrimonioContable()), "capital 500 + resultado 300");
        assertEquals(0, v("300").compareTo(r.getResultadoContable()));
        assertTrue(r.getValidaciones().stream().anyMatch(x -> "OK".equals(x.getNivel())));
        assertTrue(r.getValidaciones().stream().noneMatch(x -> "ERROR".equals(x.getNivel())));
    }

    @Test
    void proponeLosAjustesTipicosSinRepetirLosYaAplicados() {
        Resumen r = AnalizadorConciliacion.analizar(balance(), List.of(), TARIFA);
        var claves = r.getSugerencias().stream().map(s -> s.getClave()).toList();
        assertTrue(claves.contains("RENTA:540505"));
        assertTrue(claves.contains("GMF:511595"));
        assertTrue(claves.contains("MULTAS:539520"));
        assertTrue(claves.contains("ESTIMADOS:261505"));
        assertFalse(claves.contains("ESTIMADOS:261005"), "las provisiones laborales sí son pasivo fiscal");
        var gmf = r.getSugerencias().stream().filter(s -> s.getClave().equals("GMF:511595")).findFirst().orElseThrow();
        assertEquals(0, v("20").compareTo(gmf.getValor()), "solo el 50 % del GMF");

        Ajuste aplicado = new Ajuste();
        aplicado.setRenglon("G01");
        aplicado.setTipo("MENOR");
        aplicado.setDiferencia("PERMANENTE");
        aplicado.setValor(v("20"));
        aplicado.setSugerencia("GMF:511595");
        Resumen r2 = AnalizadorConciliacion.analizar(balance(), new ArrayList<>(List.of(aplicado)), TARIFA);
        assertTrue(r2.getSugerencias().stream().noneMatch(s -> s.getClave().equals("GMF:511595")));
    }

    @Test
    void losAjustesCambianLaRentaLiquidaYLasTemporariasGeneranImpuestoDiferido() {
        Ajuste renta = new Ajuste();
        renta.setRenglon("G04");
        renta.setTipo("MENOR");
        renta.setDiferencia("PERMANENTE");
        renta.setValor(v("60"));
        Ajuste provision = new Ajuste();
        provision.setRenglon("P06");
        provision.setTipo("MENOR");
        provision.setDiferencia("TEMPORARIA");
        provision.setValor(v("150"));

        Resumen r = AnalizadorConciliacion.analizar(balance(), new ArrayList<>(List.of(renta, provision)), TARIFA);

        // Gastos fiscales 200 − 60 = 140 → renta 1000 − 500 − 140 = 360; impuesto 35 % = 126.
        assertEquals(0, v("360").compareTo(r.getRentaLiquida()));
        assertEquals(0, v("126").compareTo(r.getImpuestoRenta()));
        assertEquals(0, v("50").compareTo(renglon(r, "P06").getFiscal()));
        // Pasivo contable mayor que el fiscal → diferencia deducible → activo por impuesto diferido.
        assertEquals(1, r.getImpuestoDiferido().size());
        assertEquals("DEDUCIBLE", r.getImpuestoDiferido().get(0).getTipo());
        assertEquals(0, v("52.50").compareTo(r.getImpuestoDiferidoActivo()));
    }

    @Test
    void avisaCuandoElBalanceNoCuadraOVieneVacio() {
        List<Cuenta> malo = balance();
        malo.add(cuenta("110510", "Caja menor", "77"));
        Resumen r = AnalizadorConciliacion.analizar(malo, List.of(), TARIFA);
        assertTrue(r.getValidaciones().stream().anyMatch(x -> "ERROR".equals(x.getNivel())));

        Resumen vacio = AnalizadorConciliacion.analizar(List.of(), List.of(), TARIFA);
        assertEquals("ERROR", vacio.getValidaciones().get(0).getNivel());
    }

    // ── Lectura del Excel ──

    private static FilaBalance fila(int n, String cuenta, String saldo) {
        FilaBalance f = new FilaBalance();
        f.setFila(n);
        f.setCuenta(cuenta);
        f.setNombre("Cuenta " + cuenta);
        f.setSaldo(saldo != null ? v(saldo) : null);
        return f;
    }

    @Test
    void tomaSoloAuxiliaresYDetectaElSignoDebitoPositivo() {
        Resultado res = new Resultado();
        List<Cuenta> c = ConciliacionFiscalService.leerBalance(List.of(
                fila(2, "1", "1000"),
                fila(3, "1105", "1000"),
                fila(4, "110505", "1000"),
                fila(5, "220505", "-400"),
                fila(6, "310505", "-600")), res);

        assertEquals(3, c.size(), "1 y 1105 son nivel mayor");
        assertEquals(0, v("400").compareTo(c.stream().filter(x -> x.getCodigo().equals("220505")).findFirst().orElseThrow().getSaldo()));
        assertEquals(0, res.getErrores());
        assertTrue(res.getAvisos().stream().anyMatch(a -> a.contains("débito positivo")));
    }

    @Test
    void debitoYCreditoSeConviertenALaNaturalezaYLosRepetidosSonError() {
        FilaBalance pasivo = fila(2, "2205.05", null);
        pasivo.setDebito(v("100"));
        pasivo.setCredito(v("500"));
        Resultado res = new Resultado();
        List<Cuenta> c = ConciliacionFiscalService.leerBalance(List.of(pasivo, fila(3, "220505", "1")), res);
        assertEquals(0, v("400").compareTo(c.get(0).getSaldo()));
        assertEquals(1, res.getErrores());
    }
}

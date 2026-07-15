package com.cloud_technological.aura_pos.contabilidad.infrastructure.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.conciliacion.ConciliacionBancariaService.LineaImportada;

/**
 * Parser CSV genérico del extracto (E9): fecha, descripción, valor con signo
 * del banco. Debe tolerar encabezado, separadores ; , y tab, fechas dd/MM/yyyy
 * y montos con formato colombiano ($1.234.567,89).
 */
class ConciliacionCsvParserTest {

    @Test
    void parseaCsvConPuntoYComaYEncabezado() {
        String csv = """
                Fecha;Descripción;Valor
                2026-07-01;CONSIGNACION EFECTIVO;1500000.00
                2026-07-03;COMISION MANEJO;-25000
                """;
        List<LineaImportada> lineas = ConciliacionBancariaService.parsearCsv(csv);

        assertEquals(2, lineas.size(), "el encabezado se descarta");
        assertEquals(LocalDate.of(2026, 7, 1), lineas.get(0).fecha());
        assertEquals("CONSIGNACION EFECTIVO", lineas.get(0).descripcion());
        assertTrue(new BigDecimal("1500000.00").compareTo(lineas.get(0).valor()) == 0);
        assertTrue(new BigDecimal("-25000").compareTo(lineas.get(1).valor()) == 0);
    }

    @Test
    void parseaFechaColombianaYMontoConMilesYComaDecimal() {
        String csv = "05/07/2026;GMF 4X1000;-$1.234.567,89";
        List<LineaImportada> lineas = ConciliacionBancariaService.parsearCsv(csv);

        assertEquals(LocalDate.of(2026, 7, 5), lineas.get(0).fecha());
        assertTrue(new BigDecimal("-1234567.89").compareTo(lineas.get(0).valor()) == 0);
    }

    @Test
    void parseaSeparadorComaConMilesEnPunto() {
        String csv = "2026-07-10,ABONO INTERESES,12345.67";
        List<LineaImportada> lineas = ConciliacionBancariaService.parsearCsv(csv);

        assertEquals("ABONO INTERESES", lineas.get(0).descripcion());
        assertTrue(new BigDecimal("12345.67").compareTo(lineas.get(0).valor()) == 0);
    }

    @Test
    void descripcionConVariasColumnasSeUneYValorEsLaUltima() {
        String csv = "2026-07-02;PAGO;PROVEEDOR ACME;-200000";
        List<LineaImportada> lineas = ConciliacionBancariaService.parsearCsv(csv);

        assertEquals("PAGO PROVEEDOR ACME", lineas.get(0).descripcion());
        assertTrue(new BigDecimal("-200000").compareTo(lineas.get(0).valor()) == 0);
    }

    @Test
    void montoSoloConPuntosDeMilesSeInterpretaEntero() {
        String csv = "2026-07-08;RETIRO CAJERO;-1.234.567";
        List<LineaImportada> lineas = ConciliacionBancariaService.parsearCsv(csv);

        assertTrue(new BigDecimal("-1234567").compareTo(lineas.get(0).valor()) == 0);
    }

    @Test
    void fechaInvalidaEnFilaDeDatosRevienta() {
        String csv = """
                2026-07-01;OK;100
                no-es-fecha;MAL;200
                """;
        assertThrows(ResponseStatusException.class,
                () -> ConciliacionBancariaService.parsearCsv(csv));
    }

    @Test
    void valorInvalidoRevienta() {
        assertThrows(ResponseStatusException.class,
                () -> ConciliacionBancariaService.parsearCsv("2026-07-01;X;abc"));
    }
}

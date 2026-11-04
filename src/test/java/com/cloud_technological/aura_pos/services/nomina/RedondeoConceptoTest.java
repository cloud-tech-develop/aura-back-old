package com.cloud_technological.aura_pos.services.nomina;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RedondeoConceptoTest {

    private static BigDecimal r(String valor, String precision, String modo) {
        return RedondeoConcepto.aplicar(new BigDecimal(valor), precision, modo);
    }

    @Test
    void sinRedondeoNoCambia() {
        assertEquals(new BigDecimal("1234.56"), r("1234.56", "NINGUNO", "AUTOMATICO"));
        assertEquals(new BigDecimal("1234.56"), r("1234.56", null, null));
    }

    @Test
    void enteroAutomatico() {
        assertEquals(new BigDecimal("1235.00"), r("1234.56", "ENTERO", "AUTOMATICO"));
        assertEquals(new BigDecimal("1234.00"), r("1234.49", "ENTERO", "AUTOMATICO"));
    }

    @Test
    void centenaHaciaArribaComoLosAportes() {
        assertEquals(new BigDecimal("55200.00"), r("55158.86", "CENTENA", "ARRIBA"));
    }

    @Test
    void milHaciaArribaYHaciaAbajo() {
        assertEquals(new BigDecimal("2000.00"), r("1234", "MIL", "ARRIBA"));
        assertEquals(new BigDecimal("1000.00"), r("1999", "MIL", "ABAJO"));
        assertEquals(new BigDecimal("1000.00"), r("1000", "MIL", "ARRIBA"));
    }
}

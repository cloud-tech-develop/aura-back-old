package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RecargoFormaPagoCalculoTest {

    private static BigDecimal r(String monto, String pct) {
        return RecargoFormaPagoService.recargo(new BigDecimal(monto), new BigDecimal(pct));
    }

    @Test
    void cincoPorCientoDeCienMil() {
        assertEquals(0, r("100000", "5").compareTo(new BigDecimal("5000")));
    }

    @Test
    void redondeaAlPeso() {
        // 33.333 × 2,5 % = 833,325 → 833
        assertEquals(0, r("33333", "2.5").compareTo(new BigDecimal("833")));
    }

    @Test
    void sinRecargoOMontoCeroNoSuma() {
        assertEquals(0, r("100000", "0").signum());
        assertEquals(0, r("0", "5").signum());
        assertEquals(0, RecargoFormaPagoService.recargo(null, new BigDecimal("5")).signum());
    }
}

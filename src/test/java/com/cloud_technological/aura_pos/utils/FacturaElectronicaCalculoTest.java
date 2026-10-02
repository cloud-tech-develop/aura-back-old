package com.cloud_technological.aura_pos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

class FacturaElectronicaCalculoTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, bd(esperado).compareTo(real), () -> "esperado " + esperado + " y fue " + real);
    }

    @Test
    void ivaDeLaLineaSaleDeLaBaseNoDelTotal() {
        // base 10.000 + IVA 1.900 = 11.900 → 19 %, no 16 %
        igual("19.00", FacturaElectronicaCalculo.ivaPorcentajeLinea(bd("11900"), bd("1900")));
    }

    @Test
    void ivaConRedondeoSeAjustaALaTarifa() {
        // 4621.85 + 878.15 = 5500 → 19.0002 % → 19
        igual("19.00", FacturaElectronicaCalculo.ivaPorcentajeLinea(bd("5500"), bd("878.15")));
        igual("5.00", FacturaElectronicaCalculo.ivaPorcentajeLinea(bd("1050"), bd("50")));
        igual("0.00", FacturaElectronicaCalculo.ivaPorcentajeLinea(bd("3000"), bd("0")));
    }

    @Test
    void descuentoDeLineaDelDiezPorCiento() {
        // 1 × 11.900 con IVA; se cobraron 10.710 (10 % de descuento)
        igual("10.0000", FacturaElectronicaCalculo.tasaDescuento(bd("11900"), bd("10710")));
    }

    @Test
    void descuentoGeneralSeSumaAlDeLaLinea() {
        // La línea cobró 10.710; el descuento general deja la venta en 90 % → 9.639
        BigDecimal cobrado = bd("10710").multiply(bd("0.9"));
        BigDecimal tasa = FacturaElectronicaCalculo.tasaDescuento(bd("11900"), cobrado);
        // Factus: 11.900 × (1 − tasa) = lo cobrado
        BigDecimal total = bd("11900").multiply(BigDecimal.ONE.subtract(tasa.divide(bd("100"))))
                .setScale(2, RoundingMode.HALF_UP);
        igual("9639.00", total);
    }

    @Test
    void sinDescuentoEsCero() {
        igual("0", FacturaElectronicaCalculo.tasaDescuento(bd("11900"), bd("11900")));
        igual("0", FacturaElectronicaCalculo.tasaDescuento(bd("11900"), bd("12000")));
    }

    @Test
    void mediosDePago() {
        assertEquals("10", FacturaElectronicaCalculo.medioPagoDian("EFECTIVO"));
        assertEquals("47", FacturaElectronicaCalculo.medioPagoDian("TRANSFERENCIA"));
        assertEquals("47", FacturaElectronicaCalculo.medioPagoDian("NEQUI"));
        assertEquals("48", FacturaElectronicaCalculo.medioPagoDian("TARJETA"));
    }
}

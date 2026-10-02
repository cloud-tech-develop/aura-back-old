package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CostoPromedioServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, bd(esperado).compareTo(real), () -> "esperado " + esperado + " y fue " + real);
    }

    @Test
    void primeraCompraFijaElCosto() {
        // No había nada: 10 a $100
        igual("100", CostoPromedioService.calcular(bd("10"), bd("10"), bd("1000"), bd("0")));
    }

    @Test
    void segundaCompraPromedia() {
        // Había 10 a $100, entran 10 a $200 → 20 a $150
        igual("150", CostoPromedioService.calcular(bd("20"), bd("10"), bd("2000"), bd("100")));
    }

    @Test
    void ventaEntreComprasPromediaSobreLoQueQueda() {
        // 10 a $100, se venden 5 (quedan 5 a $100), entran 5 a $200 → 10 a $150
        igual("150", CostoPromedioService.calcular(bd("10"), bd("5"), bd("1000"), bd("100")));
    }

    @Test
    void stockNegativoUsaElCostoDeLaEntrada() {
        // Se vendió sin existencias (−3); entran 10 a $120 → queda 7 a $120
        igual("120", CostoPromedioService.calcular(bd("7"), bd("10"), bd("1200"), bd("80")));
    }

    @Test
    void notaCreditoDeCompraSacaAlCostoDeLaFactura() {
        // 20 a $150 (10 a $100 + 10 a $200); se devuelven al proveedor 10 de los de $200
        igual("100", CostoPromedioService.calcular(bd("10"), bd("-10"), bd("-2000"), bd("150")));
    }

    @Test
    void anularLaCompraDevuelveElPromedioAnterior() {
        // 20 a $150; se anula la compra de 10 a $200 → 10 a $100
        igual("100", CostoPromedioService.calcular(bd("10"), bd("-10"), bd("-2000"), bd("150")));
    }

    @Test
    void sinExistenciaFinalConservaElPromedio() {
        igual("150", CostoPromedioService.calcular(bd("0"), bd("-10"), bd("-2000"), bd("150")));
    }

    @Test
    void presentacionConDecimales() {
        // Paca de 6 a $10.000: 6 unidades a 1666.666667
        igual("1666.666667", CostoPromedioService.calcular(bd("6"), bd("6"), bd("10000"), bd("0")));
    }
}

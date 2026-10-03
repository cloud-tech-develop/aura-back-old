package com.cloud_technological.aura_pos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DepreciacionCalculoTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, bd(esperado).compareTo(real), () -> "esperado " + esperado + " y fue " + real);
    }

    private static BigDecimal lr(String costo, String acumulada, int vida, int hechas) {
        return DepreciacionCalculo.cuota(DepreciacionCalculo.LINEA_RECTA, bd(costo), BigDecimal.ZERO,
                bd(acumulada), vida, hechas, null, null);
    }

    @Test
    void lineaRectaEsFijaMesAMes() {
        // 3.600.000 a 36 meses = 100.000 cada mes, no una cuota que baja.
        igual("100000", lr("3600000", "0", 36, 0));
        igual("100000", lr("3600000", "1000000", 36, 10));
        igual("100000", lr("3600000", "3500000", 36, 35));
    }

    @Test
    void laUltimaCuotaAbsorbeElRedondeo() {
        // 1.000 a 3 meses: 333,33 + 333,34 + 333,33 = 1.000 exactos. Cada mes
        // reparte lo que falta en lo que queda, así el redondeo no se acumula.
        igual("333.33", lr("1000", "0", 3, 0));
        igual("333.34", lr("1000", "333.33", 3, 1));
        igual("333.33", lr("1000", "666.67", 3, 2));
    }

    @Test
    void nuncaDepreciaPorDebajoDelResidual() {
        BigDecimal cuota = DepreciacionCalculo.cuota(DepreciacionCalculo.LINEA_RECTA, bd("1200"), bd("200"),
                bd("950"), 10, 9, null, null);
        igual("50", cuota);
        igual("0", DepreciacionCalculo.cuota(DepreciacionCalculo.LINEA_RECTA, bd("1200"), bd("200"),
                bd("1000"), 10, 10, null, null));
    }

    @Test
    void unaAdicionReparteSuValorEnLosMesesQueQuedan() {
        // 1.200 a 12 meses, 6 depreciados (600). Adición de 600 sin alargar la
        // vida: quedan 1.200 en 6 meses = 200.
        igual("200", lr("1800", "600", 12, 6));
    }

    @Test
    void saldoDecrecienteDobleYLuegoLineaRecta() {
        // 1.000 a 10 meses: primer mes 20 % del valor en libros = 200.
        igual("200", DepreciacionCalculo.cuota(DepreciacionCalculo.SALDO_DECRECIENTE, bd("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 10, 0, null, null));
        // Al final la línea recta de lo que queda supera al doble: 107,37 en 1 mes → todo.
        igual("107.37", DepreciacionCalculo.cuota(DepreciacionCalculo.SALDO_DECRECIENTE, bd("1000"),
                BigDecimal.ZERO, bd("892.63"), 10, 9, null, null));
    }

    @Test
    void unidadesDeProduccionSegunElUsoDelMes() {
        // Máquina de 10.000.000 para 100.000 horas; en el mes trabajó 1.500.
        igual("150000", DepreciacionCalculo.cuota(DepreciacionCalculo.UNIDADES_PRODUCCION, bd("10000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 60, 0, bd("1500"), bd("100000")));
        // Sin lectura del mes no deprecia.
        igual("0", DepreciacionCalculo.cuota(DepreciacionCalculo.UNIDADES_PRODUCCION, bd("10000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 60, 0, null, bd("100000")));
    }
}

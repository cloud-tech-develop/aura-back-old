package com.cloud_technological.aura_pos.services.nomina;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Reglas mensuales aplicadas por quincenas: la suma de las dos da lo del mes. */
class ReglasMensualesTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static AcumuladoMes previo(String ibc, String salarial, String noSalarial, String fsp, String rete) {
        return new AcumuladoMes(1, bd(ibc), bd(salarial), bd(noSalarial), BigDecimal.ZERO, BigDecimal.ZERO,
                bd(fsp), bd(rete));
    }

    @Test
    void ley1393SinExcesoCuandoLoNoSalarialNoPasaDel40() {
        // Salarial 2.160.000 + bono 1.000.000: el 40 % de 3.160.000 es 1.264.000.
        assertEquals(0, ReglasMensuales.excesoNoSalarial(bd("2160000"), bd("1000000")).signum());
    }

    @Test
    void ley1393ExcesoEntraAlIbc() {
        // Salarial 2.000.000 + no salarial 3.000.000: límite 2.000.000, exceso 1.000.000.
        assertEquals(bd("1000000.00"), ReglasMensuales.excesoNoSalarial(bd("2000000"), bd("3000000")));
    }

    @Test
    void ley1393EnLaSegundaQuincenaSoloLaParteNueva() {
        // 1ª quincena: 1.000.000 salarial, sin no salarial. 2ª: 1.000.000 + bono 3.000.000.
        // Mes: 2.000.000 / 3.000.000 → exceso 1.000.000, todo en la 2ª.
        AcumuladoMes q1 = previo("1000000", "1000000", "0", "0", "0");
        assertEquals(bd("1000000.00"),
                ReglasMensuales.excesoNoSalarialDeEstaNomina(q1, bd("1000000"), bd("3000000")));
    }

    @Test
    void topeIbcDescuentaLoCotizadoEnElMes() {
        // Tope 25 SMMLV = 43.772.625; ya se cotizaron 30.000.000 en la 1ª quincena.
        AcumuladoMes q1 = previo("30000000", "30000000", "0", "0", "0");
        assertEquals(bd("13772625"), ReglasMensuales.topeIbcRestante(bd("43772625"), q1));
        AcumuladoMes topado = previo("50000000", "50000000", "0", "0", "0");
        assertEquals(0, ReglasMensuales.topeIbcRestante(bd("43772625"), topado).signum());
    }

    @Test
    void fondoDeSolidaridadSeCompletaEnLaSegundaQuincena() {
        // Salario 10.000.000 quincenal: el mes (10 M ≈ 5,7 SMMLV) paga FSP 1 % = 100.000.
        // La 1ª quincena ya descontó 50.000; a la 2ª le quedan 50.000.
        AcumuladoMes q1 = previo("5000000", "5000000", "0", "50000", "0");
        BigDecimal ibcMes = ReglasMensuales.ibcMes(q1, bd("5000000"));
        assertEquals(bd("10000000"), ibcMes);
        assertEquals(bd("50000"), ReglasMensuales.pendiente(bd("100000"), q1.fsp()));
    }

    @Test
    void pendienteNoDevuelveLoDescontadoDeMas() {
        assertEquals(0, ReglasMensuales.pendiente(bd("40000"), bd("50000")).signum());
    }

    @Test
    void primeraNominaDelMesNoCambiaNada() {
        AcumuladoMes v = AcumuladoMes.VACIO;
        assertEquals(bd("43772625"), ReglasMensuales.topeIbcRestante(bd("43772625"), v));
        assertEquals(bd("120000"), ReglasMensuales.pendiente(bd("120000"), v.retencion()));
    }
}

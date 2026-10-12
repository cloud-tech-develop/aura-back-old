package com.cloud_technological.aura_pos.services.nomina.electronica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/** ¿Las nóminas confirmadas cubren el mes del empleado? */
class CoberturaMesTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final LocalDate INGRESO = LocalDate.of(2025, 1, 1);

    private static LocalDate d(int dia) {
        return SEP.atDay(dia);
    }

    @Test
    void mensualConfirmadaEstaCompleta() {
        assertTrue(CoberturaMes.evaluar(SEP, d(1), d(30), INGRESO, null, false).completo());
    }

    @Test
    void soloPrimeraQuincenaNoEstaCompleta() {
        CoberturaMes c = CoberturaMes.evaluar(SEP, d(1), d(15), INGRESO, null, false);
        assertFalse(c.completo());
        assertTrue(c.motivo().contains("2026-09-16"));
    }

    @Test
    void dosQuincenasEstanCompletas() {
        assertTrue(CoberturaMes.evaluar(SEP, d(1), d(30), INGRESO, null, false).completo());
    }

    @Test
    void ingresoAMitadDeMesCuentaDesdeElIngreso() {
        assertTrue(CoberturaMes.evaluar(SEP, d(16), d(30), d(16), null, false).completo());
    }

    @Test
    void retiroAMitadDeMesConDefinitiva() {
        assertTrue(CoberturaMes.evaluar(SEP, d(1), d(20), INGRESO, d(20), true).completo());
    }

    @Test
    void contratoFijoQueVenceDespuesNoAcortaElMes() {
        // Fecha fin pactada a futuro, sin retiro: el mes sigue hasta el 30.
        assertFalse(CoberturaMes.evaluar(SEP, d(1), d(15), INGRESO, d(20), false).completo());
    }

    @Test
    void sinNominasNoEstaCompleto() {
        assertFalse(CoberturaMes.evaluar(SEP, null, null, INGRESO, null, false).completo());
    }
}

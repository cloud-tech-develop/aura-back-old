package com.cloud_technological.aura_pos.services.nomina.vinculacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReglasVinculacionTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    @Test
    void pruebaDeDosMesesEnIndefinidoEsValida() {
        assertTrue(ReglasVinculacion.periodoPrueba(INICIO, null, LocalDate.of(2026, 2, 28)).isEmpty());
    }

    @Test
    void pruebaDeMasDeDosMesesNoEsValida() {
        assertFalse(ReglasVinculacion.periodoPrueba(INICIO, null, LocalDate.of(2026, 3, 1)).isEmpty());
    }

    @Test
    void pruebaAntesDelInicioNoEsValida() {
        assertFalse(ReglasVinculacion.periodoPrueba(INICIO, null, LocalDate.of(2025, 12, 31)).isEmpty());
    }

    @Test
    void fijoDeSeisMesesAdmiteHastaLaQuintaParte() {
        LocalDate fin = LocalDate.of(2026, 6, 30);   // 181 días → máximo 36 días de prueba
        assertTrue(ReglasVinculacion.periodoPrueba(INICIO, fin, LocalDate.of(2026, 2, 5)).isEmpty());
        assertFalse(ReglasVinculacion.periodoPrueba(INICIO, fin, LocalDate.of(2026, 2, 10)).isEmpty());
    }

    @Test
    void fijoDeUnAnoAdmiteLosDosMeses() {
        assertTrue(ReglasVinculacion.periodoPrueba(INICIO, LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 2, 28)).isEmpty());
    }

    @Test
    void jornadaSobreElMaximoLegalNoEsValida() {
        assertFalse(ReglasVinculacion.jornada(new BigDecimal("44"), new BigDecimal("42")).isEmpty());
        assertTrue(ReglasVinculacion.jornada(new BigDecimal("24"), new BigDecimal("42")).isEmpty());
        assertTrue(ReglasVinculacion.jornada(null, new BigDecimal("42")).isEmpty());
    }

    @Test
    void aprendizExigeFase() {
        assertFalse(ReglasVinculacion.faseAprendiz("APRENDIZAJE", null).isEmpty());
        assertTrue(ReglasVinculacion.faseAprendiz("APRENDIZAJE", "LECTIVA").isEmpty());
        assertTrue(ReglasVinculacion.faseAprendiz("INDEFINIDO", null).isEmpty());
    }
}

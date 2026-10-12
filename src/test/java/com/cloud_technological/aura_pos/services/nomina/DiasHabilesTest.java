package com.cloud_technological.aura_pos.services.nomina;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DiasHabilesTest {

    @Test
    void noCuentanDomingosNiFestivosPeroSiSabados() {
        // 7 al 13 de diciembre de 2026: el 8 es festivo y el 13 domingo.
        assertEquals(5, DiasHabiles.entre(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 13)));
        assertEquals(0, DiasHabiles.entre(LocalDate.of(2026, 12, 13), LocalDate.of(2026, 12, 7)));
    }

    @Test
    void elRegresoEsElSiguienteDiaHabil() {
        assertEquals(LocalDate.of(2026, 12, 14), DiasHabiles.siguienteHabil(LocalDate.of(2026, 12, 12)));
        // 25 de diciembre (viernes) es festivo: regresa el sábado 26.
        assertEquals(LocalDate.of(2026, 12, 26), DiasHabiles.siguienteHabil(LocalDate.of(2026, 12, 24)));
    }
}

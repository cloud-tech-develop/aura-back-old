package com.cloud_technological.aura_pos.services.asistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ReglasMarcajeTest {

    @Test
    void estadoVacioDependeDeSiTraeHoras() {
        assertEquals("ASISTIO", ReglasMarcaje.estado(null, true).orElseThrow());
        assertEquals("SIN_REGISTRO", ReglasMarcaje.estado("  ", false).orElseThrow());
    }

    @Test
    void estadoEnPalabrasDelUsuario() {
        assertEquals("NO_ASISTIO", ReglasMarcaje.estado("No asistió", false).orElseThrow());
        assertEquals("LLEGO_TARDE", ReglasMarcaje.estado("llego tarde", true).orElseThrow());
        assertEquals("INCAPACIDAD", ReglasMarcaje.estado("INCAPACIDAD", false).orElseThrow());
        assertEquals("ASISTIO", ReglasMarcaje.estado("Asistio", true).orElseThrow());
    }

    @Test
    void estadoDesconocidoNoSeAcepta() {
        assertTrue(ReglasMarcaje.estado("de fiesta", false).isEmpty());
    }

    @Test
    void horasConYSinCeroInicial() {
        assertEquals(LocalTime.of(7, 0), ReglasMarcaje.hora("7:00").orElseThrow());
        assertEquals(LocalTime.of(20, 0), ReglasMarcaje.hora("20:00").orElseThrow());
        assertEquals(LocalTime.of(6, 30), ReglasMarcaje.hora("06:30:00").orElseThrow());
        assertTrue(ReglasMarcaje.hora("25:00").isEmpty());
        assertTrue(ReglasMarcaje.hora("siete").isEmpty());
    }

    @Test
    void soloLosEstadosPresentesExigenHoras() {
        assertTrue(ReglasMarcaje.exigeHoras("ASISTIO"));
        assertFalse(ReglasMarcaje.exigeHoras("PERMISO"));
    }
}

package com.cloud_technological.aura_pos.services.servicio_tecnico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasOrdenServicioTest {

    @Test
    void imeiValidoConEspaciosYGuiones() {
        assertEquals("490154203237518", ReglasOrdenServicio.imei("49-015420 323751-8", "El IMEI"));
    }

    @Test
    void imeiVacioEsNull() {
        assertNull(ReglasOrdenServicio.imei("  ", "El IMEI"));
    }

    @Test
    void imeiConDigitoDeControlMaloSeRechaza() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ReglasOrdenServicio.imei("490154203237519", "El IMEI"));
        assertTrue(e.getMessage().contains("no es válido"));
    }

    @Test
    void imeiCortoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> ReglasOrdenServicio.imei("12345", "El IMEI"));
    }

    @Test
    void soloSeEntregaListaONoReparada() {
        assertFalse(ReglasOrdenServicio.permitidos("EN_REPARACION").contains("ENTREGADO"));
        assertTrue(ReglasOrdenServicio.permitidos("LISTO").contains("ENTREGADO"));
        assertTrue(ReglasOrdenServicio.permitidos("NO_REPARADO").contains("ENTREGADO"));
        assertNotNull(ReglasOrdenServicio.motivoTransicionInvalida("RECIBIDO", "ENTREGADO", null));
    }

    @Test
    void sePuedeSaltarAdelanteYVolverAtras() {
        assertNull(ReglasOrdenServicio.motivoTransicionInvalida("RECIBIDO", "EN_REPARACION", null));
        assertNull(ReglasOrdenServicio.motivoTransicionInvalida("LISTO", "EN_REPARACION", null));
    }

    @Test
    void finalesNoCambian() {
        assertEquals(List.of(), ReglasOrdenServicio.permitidos("ENTREGADO"));
        assertNotNull(ReglasOrdenServicio.motivoTransicionInvalida("CANCELADO", "RECIBIDO", null));
    }

    @Test
    void cancelarExigeMotivo() {
        assertNotNull(ReglasOrdenServicio.motivoTransicionInvalida("RECIBIDO", "CANCELADO", " "));
        assertNull(ReglasOrdenServicio.motivoTransicionInvalida("RECIBIDO", "CANCELADO", "El cliente no volvió"));
    }

    @Test
    void vencidaSoloSiSigueEnElTaller() {
        LocalDate hoy = LocalDate.of(2026, 11, 17);
        assertTrue(ReglasOrdenServicio.vencida("EN_REPARACION", hoy.minusDays(1), hoy));
        assertFalse(ReglasOrdenServicio.vencida("LISTO", hoy.minusDays(1), hoy));
        assertFalse(ReglasOrdenServicio.vencida("EN_REPARACION", hoy, hoy));
        assertFalse(ReglasOrdenServicio.vencida("EN_REPARACION", null, hoy));
    }

    @Test
    void listasSeUnenSinRepetidosNiSeparador() {
        assertEquals("CARGADOR|FUNDA", ReglasOrdenServicio.unirLista(List.of("cargador", " Funda ", "CARGADOR", ""), 300));
        assertEquals(List.of("CARGADOR", "FUNDA"), ReglasOrdenServicio.separarLista("CARGADOR|FUNDA"));
        assertEquals("A B", ReglasOrdenServicio.unirLista(List.of("a|b"), 300));
        assertNull(ReglasOrdenServicio.unirLista(List.of(), 300));
    }

    @Test
    void baseSinIvaDesdeValorFinal() {
        assertEquals(new BigDecimal("100000.00"),
                ReglasOrdenServicio.base(new BigDecimal("119000"), new BigDecimal("19")));
        assertEquals(new BigDecimal("50000.00"),
                ReglasOrdenServicio.base(new BigDecimal("50000"), BigDecimal.ZERO));
    }

    @Test
    void mimeDesdeElContenido() {
        byte[] jpg = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        assertEquals("image/jpeg", ReglasOrdenServicio.mimeImagen(jpg));
        assertNull(ReglasOrdenServicio.mimeImagen("<html>hola mundo</html>".getBytes()));
    }
}

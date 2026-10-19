package com.cloud_technological.aura_pos.services.correo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorreoPlantillaTest {

    @Test
    void escapaElMensajeYRespetaLosSaltosDeLinea() {
        String html = CorreoPlantilla.html("Ferretería <X>", null, "Hola,\n<script>alert(1)</script>", "CC-105.pdf");
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("Hola,<br>"));
        assertTrue(html.contains("Ferretería &lt;X&gt;"));
        assertTrue(html.contains("CC-105.pdf"));
    }

    @Test
    void soloMuestraLogosPublicosConHttps() {
        assertFalse(CorreoPlantilla.html("E", "javascript:alert(1)", "m", "a.pdf").contains("<img"));
        assertTrue(CorreoPlantilla.html("E", "https://pub.r2.dev/logo.png", "m", "a.pdf").contains("<img"));
    }
}

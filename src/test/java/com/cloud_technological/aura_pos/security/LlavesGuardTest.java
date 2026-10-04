package com.cloud_technological.aura_pos.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** El backend no arranca con una llave quemada, vacía o corta (PLAN_SEGURIDAD C-01). */
class LlavesGuardTest {

    private static String aleatoria(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void unaLlaveNuevaArranca() {
        assertDoesNotThrow(() -> LlavesGuard.validar(aleatoria(64), aleatoria(32)));
    }

    @Test
    void vaciaOCortaNoArranca() {
        assertThrows(IllegalStateException.class, () -> LlavesGuard.validar("", aleatoria(32)));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> LlavesGuard.validar(aleatoria(16), aleatoria(32)));
        assertTrue(e.getMessage().contains("muy corta"));
    }

    @Test
    void noBase64NoArranca() {
        assertThrows(IllegalStateException.class, () -> LlavesGuard.validar("esto no es base64 !!!", null));
    }

    @Test
    void lasHuellasQuemadasSonSha256() {
        for (String h : LlavesGuard.QUEMADAS) assertTrue(h.matches("[0-9a-f]{64}"));
    }
}

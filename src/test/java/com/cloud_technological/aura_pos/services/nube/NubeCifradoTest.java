package com.cloud_technological.aura_pos.services.nube;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class NubeCifradoTest {

    private static final String LLAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private final NubeCifrado cifrado = new NubeCifrado(LLAVE);
    private final byte[] plano = "factura de prueba".getBytes(StandardCharsets.UTF_8);

    @Test
    void ida_y_vuelta() {
        NubeCifrado.Cifrado c = cifrado.cifrar(7, "nube/7/a", plano);
        assertFalse(Arrays.equals(plano, Arrays.copyOf(c.datos(), plano.length)));
        assertArrayEquals(plano, cifrado.descifrar(7, "nube/7/a", c.datos(), c.iv(), NubeCifrado.VERSION_LLAVE_ACTUAL));
    }

    @Test
    void otra_empresa_no_puede_descifrar() {
        NubeCifrado.Cifrado c = cifrado.cifrar(7, "nube/7/a", plano);
        assertThrows(IllegalStateException.class,
                () -> cifrado.descifrar(8, "nube/7/a", c.datos(), c.iv(), NubeCifrado.VERSION_LLAVE_ACTUAL));
    }

    @Test
    void mover_el_objeto_de_clave_falla() {
        NubeCifrado.Cifrado c = cifrado.cifrar(7, "nube/7/a", plano);
        assertThrows(IllegalStateException.class,
                () -> cifrado.descifrar(7, "nube/7/b", c.datos(), c.iv(), NubeCifrado.VERSION_LLAVE_ACTUAL));
    }

    @Test
    void bytes_alterados_fallan() {
        NubeCifrado.Cifrado c = cifrado.cifrar(7, "nube/7/a", plano);
        byte[] alterado = c.datos().clone();
        alterado[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> cifrado.descifrar(7, "nube/7/a", alterado, c.iv(), NubeCifrado.VERSION_LLAVE_ACTUAL));
    }

    @Test
    void llave_mal_formada_no_arranca() {
        assertThrows(IllegalStateException.class, () -> new NubeCifrado(""));
        assertThrows(IllegalStateException.class, () -> new NubeCifrado("no-es-base64!!"));
        assertThrows(IllegalStateException.class,
                () -> new NubeCifrado(Base64.getEncoder().encodeToString(new byte[16])));
    }
}

package com.cloud_technological.aura_pos.contabilidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Carga y compara los golden files de {@code /asientos-esperados}. El assert
 * central de TODO test de generador: Σ débitos = Σ créditos + cada partida
 * (cuenta, débito, crédito, tercero) en el orden esperado.
 */
public final class GoldenAsientos {

    public record PartidaEsperada(Long cuenta, BigDecimal debito, BigDecimal credito, Long tercero) {
    }

    public record AsientoEsperado(String descripcion, List<PartidaEsperada> partidas) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoldenAsientos() {
    }

    public static AsientoEsperado cargar(String archivo) {
        try (InputStream in = GoldenAsientos.class.getResourceAsStream("/asientos-esperados/" + archivo)) {
            if (in == null) {
                throw new IllegalArgumentException("Golden file no encontrado: " + archivo);
            }
            return MAPPER.readValue(in, AsientoEsperado.class);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el golden file " + archivo, e);
        }
    }

    public static void assertCoincide(String archivo, Asiento real) {
        AsientoEsperado esperado = cargar(archivo);

        assertTrue(real.totalDebito().compareTo(real.totalCredito()) == 0,
                "El asiento debe estar cuadrado: débito=" + real.totalDebito()
                        + " crédito=" + real.totalCredito());
        assertEquals(esperado.descripcion(), real.descripcion(), "descripción del asiento");
        assertEquals(esperado.partidas().size(), real.partidas().size(),
                "número de partidas en " + archivo);

        for (int i = 0; i < esperado.partidas().size(); i++) {
            PartidaEsperada e = esperado.partidas().get(i);
            Partida r = real.partidas().get(i);
            String linea = archivo + " partida[" + i + "]";
            assertEquals(e.cuenta(), r.cuentaId(), linea + " cuenta");
            assertTrue(e.debito().compareTo(r.debito()) == 0,
                    linea + " débito esperado=" + e.debito() + " real=" + r.debito());
            assertTrue(e.credito().compareTo(r.credito()) == 0,
                    linea + " crédito esperado=" + e.credito() + " real=" + r.credito());
            assertEquals(e.tercero(), r.terceroId(), linea + " tercero");
        }
    }
}

package com.cloud_technological.aura_pos.contabilidad.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.OrigenDocumento;

/**
 * Invariantes del dominio: es imposible construir un asiento descuadrado,
 * con negativos o sin partidas. Sin Spring, sin base de datos.
 */
class AsientoBuilderTest {

    private static final OrigenDocumento ORIGEN = new OrigenDocumento("VENTA", 1L);
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 9);

    private AsientoBuilder builder() {
        return Asiento.builder(ORIGEN, FECHA).prefijo("VT").descripcion("test");
    }

    @Test
    void construyeAsientoCuadrado() {
        Asiento asiento = builder()
                .debito(1105L, "Caja", new BigDecimal("119000"))
                .credito(4135L, "Ingresos", new BigDecimal("100000"))
                .credito(2408L, "IVA", new BigDecimal("19000"))
                .build();

        assertEquals(0, asiento.totalDebito().compareTo(new BigDecimal("119000")));
        assertEquals(0, asiento.totalDebito().compareTo(asiento.totalCredito()));
        assertEquals(3, asiento.partidas().size());
        assertEquals(EstadoAsiento.CONTABILIZADO, asiento.estado());
    }

    @Test
    void rechazaDescuadre() {
        AsientoBuilder b = builder()
                .debito(1105L, "Caja", new BigDecimal("100000"))
                .credito(4135L, "Ingresos", new BigDecimal("99999.99"));

        assertThrows(AsientoDescuadradoException.class, b::build);
    }

    @Test
    void rechazaMontosNegativos() {
        assertThrows(IllegalArgumentException.class, () -> builder()
                .debito(1105L, "Caja", new BigDecimal("-5000")));
    }

    @Test
    void rechazaAsientoSinPartidas() {
        assertThrows(AsientoDescuadradoException.class, () -> builder().build());
    }

    @Test
    void rechazaUnaSolaPartida() {
        AsientoBuilder b = builder().debito(1105L, "Caja", BigDecimal.TEN);
        assertThrows(AsientoDescuadradoException.class, b::build);
    }

    @Test
    void ignoraMontosNulosYCeros() {
        Asiento asiento = builder()
                .debito(1105L, "Caja", new BigDecimal("100"))
                .debito(1305L, "Cartera", BigDecimal.ZERO)
                .debito(1310L, "Otra cartera", null)
                .credito(4135L, "Ingresos", new BigDecimal("100"))
                .build();

        assertEquals(2, asiento.partidas().size());
    }

    @Test
    void normalizaEscalaDosHalfUp() {
        Asiento asiento = builder()
                .debito(1105L, "Caja", new BigDecimal("100.005"))
                .credito(4135L, "Ingresos", new BigDecimal("100.01"))
                .build();

        assertEquals(0, asiento.partidas().get(0).debito()
                .compareTo(new BigDecimal("100.01")));
    }

    @Test
    void conEstadoDevuelveCopiaSinMutar() {
        Asiento original = builder()
                .debito(1105L, "Caja", BigDecimal.TEN)
                .credito(4135L, "Ingresos", BigDecimal.TEN)
                .build();

        Asiento borrador = original.conEstado(EstadoAsiento.BORRADOR);

        assertEquals(EstadoAsiento.CONTABILIZADO, original.estado());
        assertEquals(EstadoAsiento.BORRADOR, borrador.estado());
        assertEquals(original.totalDebito(), borrador.totalDebito());
        assertTrue(borrador.partidas().equals(original.partidas()));
    }
}

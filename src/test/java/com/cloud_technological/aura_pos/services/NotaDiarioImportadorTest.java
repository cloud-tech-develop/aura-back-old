package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.services.implementations.NotaDiarioImportador;

/** Lo que el contador pega desde Excel, en los formatos que Excel realmente produce. */
class NotaDiarioImportadorTest {

    @Test
    @DisplayName("Números en formato colombiano, inglés y contable")
    void numeros() {
        assertEquals(0, new BigDecimal("1500000").compareTo(NotaDiarioImportador.numero("$ 1.500.000")));
        assertEquals(0, new BigDecimal("1500000.5").compareTo(NotaDiarioImportador.numero("1.500.000,50")));
        assertEquals(0, new BigDecimal("1500000.5").compareTo(NotaDiarioImportador.numero("1,500,000.50")));
        assertEquals(0, new BigDecimal("1500000").compareTo(NotaDiarioImportador.numero("1500000")));
        assertEquals(0, new BigDecimal("12.5").compareTo(NotaDiarioImportador.numero("12,5")));
        assertEquals(0, new BigDecimal("-1000").compareTo(NotaDiarioImportador.numero("(1.000)")));
        assertEquals(0, BigDecimal.ZERO.compareTo(NotaDiarioImportador.numero("")));
        assertThrows(NumberFormatException.class, () -> NotaDiarioImportador.numero("mil"));
    }

    @Test
    @DisplayName("Salta el encabezado, las filas vacías y acepta punto y coma")
    void leer() {
        List<NotaDiarioImportador.FilaCruda> filas = NotaDiarioImportador.leer(
                "Cuenta;Tercero;CC;Desc;Débito;Crédito\r\n"
                        + "\r\n"
                        + "110505;;;Caja;;500\r\n"
                        + "519530\t800\t01\t\"Útiles; varios\"\t500\t\n");
        assertEquals(2, filas.size());
        assertEquals(3, filas.get(0).fila());
        assertEquals("500", filas.get(0).credito());
        assertEquals("Útiles; varios", filas.get(1).descripcion());
        assertEquals("01", filas.get(1).centroCosto());
    }

    @Test
    @DisplayName("El documento se limpia de puntos y dígito de verificación")
    void documento() {
        assertEquals("900123456", NotaDiarioImportador.documento("900.123.456-7"));
        assertEquals("1020304050", NotaDiarioImportador.documento(" 1020304050 "));
    }
}

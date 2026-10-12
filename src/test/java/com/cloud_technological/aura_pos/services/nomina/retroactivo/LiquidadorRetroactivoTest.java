package com.cloud_technological.aura_pos.services.nomina.retroactivo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.repositories.nomina_documentos.RetroactivoQueryRepository.NominaPagada;

class LiquidadorRetroactivoTest {

    private static final BigDecimal ANTERIOR = new BigDecimal("1500000");
    private static final BigDecimal NUEVO = new BigDecimal("1650000");

    @Test
    void unMesCompletoPagaLaDiferenciaEntera() {
        NominaPagada enero = new NominaPagada(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), ANTERIOR, ANTERIOR);
        assertEquals(new BigDecimal("150000.00"), LiquidadorRetroactivo.valor(NUEVO, ANTERIOR, enero,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)));
    }

    @Test
    void conAusenciasPagaEnLaProporcionDeLoQueSePago() {
        // 20 de 30 días pagados (salario proporcional 1.000.000).
        NominaPagada enero = new NominaPagada(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), ANTERIOR,
                new BigDecimal("1000000"));
        assertEquals(new BigDecimal("100000.00"), LiquidadorRetroactivo.valor(NUEVO, ANTERIOR, enero,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)));
    }

    @Test
    void siElIncrementoRigeAMitadDePeriodoSoloPagaEsaParte() {
        // Quincena del 1 al 15 pagada completa, incremento desde el 6: 10 de 15 días.
        NominaPagada q = new NominaPagada(1L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 15), ANTERIOR,
                new BigDecimal("750000"));
        assertEquals(new BigDecimal("50000.00"), LiquidadorRetroactivo.valor(NUEVO, ANTERIOR, q,
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 15)));
    }
}

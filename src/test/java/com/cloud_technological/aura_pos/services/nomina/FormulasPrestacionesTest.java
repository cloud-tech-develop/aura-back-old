package com.cloud_technological.aura_pos.services.nomina;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FormulasPrestacionesTest {

    private static final BigDecimal SALARIO = new BigDecimal("1300000");
    private static final BigDecimal AUXILIO = new BigDecimal("162000");
    private static final BigDecimal SMMLV = new BigDecimal("1300000");

    @Test
    void unAnioCompletoSon360DiasYUnMesSon30() {
        assertEquals(360, FormulasPrestaciones.diasBase360(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertEquals(30, FormulasPrestaciones.diasBase360(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
    }

    @Test
    void lasCesantiasDeUnAnioSonUnMesDeSalarioMasAuxilio() {
        assertEquals(new BigDecimal("1462000.00"),
                FormulasPrestaciones.valor("CESANTIAS", SALARIO, AUXILIO, null, 360, false));
    }

    @Test
    void laPrimaDeUnSemestreEsMedioMes() {
        assertEquals(new BigDecimal("731000.00"),
                FormulasPrestaciones.valor("PRIMA", SALARIO, AUXILIO, null, 180, false));
    }

    @Test
    void lasVacacionesCausadasDeUnAnioSonQuinceDiasSinAuxilio() {
        assertEquals(new BigDecimal("650000.00"),
                FormulasPrestaciones.valor("VACACIONES", SALARIO, AUXILIO, null, 360, false));
    }

    @Test
    void losInteresesDeUnAnioSonEl12PorCientoDeLasCesantias() {
        assertEquals(new BigDecimal("175440.00"),
                FormulasPrestaciones.valor("INTERESES_CESANTIAS", SALARIO, AUXILIO, null, 360, false));
    }

    @Test
    void indefinidoDeDosAniosConSalarioBajoSon50Dias() {
        BigDecimal dias = FormulasPrestaciones.diasIndemnizacion("INDEFINIDO", SALARIO, SMMLV,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 30), null);
        assertEquals(0, new BigDecimal("50").compareTo(dias.setScale(0, RoundingMode.HALF_UP)));
    }

    @Test
    void fijoPagaLosDiasQueFaltabanYObraElMinimoDe15() {
        BigDecimal fijo = FormulasPrestaciones.diasIndemnizacion("FIJO", SALARIO, SMMLV,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));
        assertEquals(30, fijo.intValue());
        assertEquals(15, FormulasPrestaciones.diasIndemnizacion("OBRA_LABOR", SALARIO, SMMLV,
                null, LocalDate.of(2026, 3, 1), null).intValue());
        assertEquals(new BigDecimal("1300000.00"), FormulasPrestaciones.valorIndemnizacion(SALARIO, new BigDecimal("30")));
    }

    @Test
    void lasVacacionesSePaganPorDiaDeSalarioMasPromedioYAdmitenDecimales() {
        assertEquals(new BigDecimal("650000.00"),
                FormulasPrestaciones.valorVacaciones(SALARIO, null, new BigDecimal("15")));
        assertEquals(new BigDecimal("75000.00"),
                FormulasPrestaciones.valorVacaciones(new BigDecimal("1200000"), new BigDecimal("300000"),
                        new BigDecimal("1.5")));
    }
}

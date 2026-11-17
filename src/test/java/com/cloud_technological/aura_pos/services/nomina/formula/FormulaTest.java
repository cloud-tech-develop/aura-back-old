package com.cloud_technological.aura_pos.services.nomina.formula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FormulaTest {

    private static final Map<String, BigDecimal> VARIABLES = Map.of(
            "SALARIO_BASICO", new BigDecimal("2757943"),
            "DIAS_LIQUIDADOS", new BigDecimal("15"),
            "SMMLV", new BigDecimal("1423500"),
            "AUX_TRANSPORTE", new BigDecimal("200000"),
            "BASE_IBC", new BigDecimal("1378971.50"));

    private static BigDecimal calcular(String formula) {
        AnalizadorFormula.Analisis a = AnalizadorFormula.analizar(formula);
        return EvaluadorFormula.evaluar(a.arbol(), VARIABLES, n -> n == 1 ? new BigDecimal("1378971.50") : null)
                .valor();
    }

    @Test
    void salarioProporcional() {
        assertEquals(new BigDecimal("1378971.50"), calcular("SALARIO_BASICO / 30 * DIAS_LIQUIDADOS"));
    }

    @Test
    void porcentajeLiteral() {
        assertEquals(new BigDecimal("55158.86"), calcular("BASE_IBC * 4%"));
    }

    @Test
    void condicionalAuxilio() {
        // 2.757.943 > 2 SMMLV (2.847.000)? No → paga el auxilio proporcional.
        assertEquals(new BigDecimal("100000.00"),
                calcular("SI(SALARIO_BASICO <= 2 * SMMLV; AUX_TRANSPORTE / 30 * DIAS_LIQUIDADOS; 0)"));
    }

    @Test
    void referenciaAOtroConceptoYMinusculas() {
        assertEquals(new BigDecimal("114868.33"), calcular("[1] * 8.33%"));
        assertEquals(new BigDecimal("114868.33"), calcular("[1] * 8.33 / 100"));
    }

    @Test
    void funcionesYRedondeoAlMil() {
        assertEquals(new BigDecimal("200000.00"), calcular("min(aux_transporte; smmlv)"));
        assertEquals(new BigDecimal("1379000.00"), calcular("REDONDEAR([1]; -3)"));
        assertEquals(new BigDecimal("1"), calcular("Y(1 > 0, 2 >= 2)").stripTrailingZeros());
    }

    @Test
    void registraLoQueUsa() {
        AnalizadorFormula.Analisis a = AnalizadorFormula.analizar("[32] + BASE_IBC * 2%");
        assertEquals(Set.of(32), a.conceptos());
        assertEquals(Set.of("BASE_IBC"), a.variables());
    }

    @Test
    void erroresEnPalabras() {
        assertThrows(ErrorFormula.class, () -> AnalizadorFormula.analizar("SALARIO_X * 2"));
        assertThrows(ErrorFormula.class, () -> AnalizadorFormula.analizar("EJECUTAR(1)"));
        assertThrows(ErrorFormula.class, () -> AnalizadorFormula.analizar("(SMMLV * 2"));
        assertThrows(ErrorFormula.class, () -> AnalizadorFormula.analizar("SI(1; 2)"));
        assertThrows(ErrorFormula.class, () -> AnalizadorFormula.analizar(""));
        ErrorFormula e = assertThrows(ErrorFormula.class, () -> calcular("SMMLV / (DIAS_LIQUIDADOS - 15)"));
        assertTrue(e.getMessage().contains("cero"));
    }

    @Test
    void siSoloEvaluaLaRamaElegida() {
        // La rama falsa divide por cero, pero no se evalúa.
        assertEquals(new BigDecimal("5.00"), calcular("SI(1 = 1; 5; 1 / 0)"));
    }
}

package com.cloud_technological.aura_pos.services.nomina.formula;

import java.math.BigDecimal;
import java.util.List;

/**
 * Árbol de una fórmula de concepto ya analizada (F3 de
 * docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md). Solo hay números, variables con nombre,
 * referencias a otros conceptos, operaciones y funciones de una lista cerrada:
 * no hay forma de expresar llamadas a código, ciclos ni acceso a datos.
 */
public sealed interface NodoFormula {

    /** Número literal (un {@code 4%} ya llega dividido entre 100). */
    record Numero(BigDecimal valor) implements NodoFormula {
    }

    /** Variable de la liquidación: SALARIO_BASICO, BASE_IBC, SMMLV… */
    record Variable(String nombre) implements NodoFormula {
    }

    /** Valor de otro concepto ya calculado en la liquidación: {@code [32]}. */
    record Concepto(int numero) implements NodoFormula {
    }

    /** {@code -x}. */
    record Negativo(NodoFormula valor) implements NodoFormula {
    }

    /** {@code x%} sobre una expresión: se divide entre 100. */
    record Porcentaje(NodoFormula valor) implements NodoFormula {
    }

    /** Operación de dos lados: + - * / y comparaciones (= &lt;&gt; &lt; &lt;= &gt; &gt;=). */
    record Binaria(String operador, NodoFormula izquierda, NodoFormula derecha) implements NodoFormula {
    }

    /** Función de la lista cerrada (SI, MIN, MAX…). */
    record Funcion(String nombre, List<NodoFormula> argumentos) implements NodoFormula {
    }
}

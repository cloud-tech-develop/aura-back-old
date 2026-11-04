package com.cloud_technological.aura_pos.services.nomina.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Binaria;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Concepto;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Funcion;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Negativo;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Numero;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Porcentaje;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Variable;

/**
 * Evalúa el árbol de una fórmula con los valores de una liquidación (F3 de
 * docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md). Todo en BigDecimal; los resultados
 * lógicos son 1 (verdadero) o 0 (falso). Deja, en orden, el valor de cada
 * variable y concepto que usó: eso es la traza que ve el usuario.
 */
public final class EvaluadorFormula {

    private static final int ESCALA_DIVISION = 10;

    /** Resultado: el valor (a 2 decimales) y los datos que se usaron para llegar a él. */
    public record Resultado(BigDecimal valor, Map<String, BigDecimal> usados) {
    }

    private final Map<String, BigDecimal> variables;
    private final IntFunction<BigDecimal> conceptos;
    private final Map<String, BigDecimal> usados = new LinkedHashMap<>();

    private EvaluadorFormula(Map<String, BigDecimal> variables, IntFunction<BigDecimal> conceptos) {
        this.variables = variables;
        this.conceptos = conceptos;
    }

    /**
     * Evalúa una fórmula ya analizada.
     *
     * @param variables valor de cada variable (las que falten valen 0)
     * @param conceptos valor de un concepto ya calculado, por número (0 si no está)
     * @throws ErrorFormula si hay una división por cero
     */
    public static Resultado evaluar(NodoFormula arbol, Map<String, BigDecimal> variables,
            IntFunction<BigDecimal> conceptos) {
        EvaluadorFormula e = new EvaluadorFormula(variables, conceptos);
        BigDecimal v = e.valor(arbol).setScale(2, RoundingMode.HALF_UP);
        return new Resultado(v, e.usados);
    }

    private BigDecimal valor(NodoFormula n) {
        if (n instanceof Numero num) return num.valor();
        if (n instanceof Variable v) {
            BigDecimal x = variables.getOrDefault(v.nombre(), BigDecimal.ZERO);
            usados.put(v.nombre(), x);
            return x;
        }
        if (n instanceof Concepto c) {
            BigDecimal x = conceptos.apply(c.numero());
            if (x == null) x = BigDecimal.ZERO;
            usados.put("[" + c.numero() + "]", x);
            return x;
        }
        if (n instanceof Negativo neg) return valor(neg.valor()).negate();
        if (n instanceof Porcentaje p) return valor(p.valor()).movePointLeft(2);
        if (n instanceof Binaria b) return binaria(b);
        if (n instanceof Funcion f) return funcion(f);
        throw new ErrorFormula("Parte de la fórmula no reconocida.", 0);
    }

    private BigDecimal binaria(Binaria b) {
        BigDecimal x = valor(b.izquierda());
        BigDecimal y = valor(b.derecha());
        return switch (b.operador()) {
            case "+" -> x.add(y);
            case "-" -> x.subtract(y);
            case "*" -> x.multiply(y);
            case "/" -> {
                if (y.signum() == 0) throw new ErrorFormula("La fórmula divide por cero.", 0);
                yield x.divide(y, ESCALA_DIVISION, RoundingMode.HALF_UP);
            }
            case "=" -> logico(x.compareTo(y) == 0);
            case "<>" -> logico(x.compareTo(y) != 0);
            case "<" -> logico(x.compareTo(y) < 0);
            case "<=" -> logico(x.compareTo(y) <= 0);
            case ">" -> logico(x.compareTo(y) > 0);
            case ">=" -> logico(x.compareTo(y) >= 0);
            default -> throw new ErrorFormula("Operador desconocido: " + b.operador(), 0);
        };
    }

    private BigDecimal funcion(Funcion f) {
        List<NodoFormula> a = f.argumentos();
        return switch (f.nombre()) {
            // Perezosa: solo se evalúa la rama elegida.
            case "SI" -> verdadero(valor(a.get(0))) ? valor(a.get(1)) : valor(a.get(2));
            case "MIN" -> a.stream().map(this::valor).reduce(BigDecimal::min).orElse(BigDecimal.ZERO);
            case "MAX" -> a.stream().map(this::valor).reduce(BigDecimal::max).orElse(BigDecimal.ZERO);
            case "ABS" -> valor(a.get(0)).abs();
            case "REDONDEAR" -> redondear(a, RoundingMode.HALF_UP);
            case "REDONDEAR_ARRIBA" -> redondear(a, RoundingMode.CEILING);
            case "REDONDEAR_ABAJO" -> redondear(a, RoundingMode.FLOOR);
            case "Y" -> logico(a.stream().allMatch(x -> verdadero(valor(x))));
            case "O" -> logico(a.stream().anyMatch(x -> verdadero(valor(x))));
            case "NO" -> logico(!verdadero(valor(a.get(0))));
            default -> throw new ErrorFormula("La función " + f.nombre() + " no existe.", 0);
        };
    }

    /** REDONDEAR(x; decimales). Decimales negativos redondean a decenas, centenas, miles: -3 = al mil. */
    private BigDecimal redondear(List<NodoFormula> a, RoundingMode modo) {
        BigDecimal x = valor(a.get(0));
        int decimales = a.size() > 1 ? valor(a.get(1)).intValue() : 0;
        return x.setScale(decimales, modo);
    }

    private static boolean verdadero(BigDecimal v) {
        return v.signum() != 0;
    }

    private static BigDecimal logico(boolean b) {
        return b ? BigDecimal.ONE : BigDecimal.ZERO;
    }
}

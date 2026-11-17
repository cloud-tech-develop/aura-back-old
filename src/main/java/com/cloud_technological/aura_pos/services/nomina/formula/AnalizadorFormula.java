package com.cloud_technological.aura_pos.services.nomina.formula;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Binaria;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Concepto;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Funcion;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Negativo;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Numero;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Porcentaje;
import com.cloud_technological.aura_pos.services.nomina.formula.NodoFormula.Variable;

/**
 * Convierte el texto de una fórmula en su árbol (F3 de
 * docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md). Analizador descendente recursivo propio:
 * no usa {@code eval()}, SpEL ni ningún motor de scripts.
 *
 * <h2>Gramática</h2>
 * <pre>
 * expresion   := comparacion
 * comparacion := suma ( ( = | &lt;&gt; | &lt; | &lt;= | &gt; | &gt;= ) suma )?
 * suma        := producto ( ( + | - ) producto )*
 * producto    := unario ( ( * | / ) unario )*
 * unario      := - unario | primario %?
 * primario    := NUMERO | [NUMERO] | VARIABLE | FUNCION ( args ) | ( expresion )
 * args        := expresion ( ( ; | , ) expresion )*
 * </pre>
 * El decimal es el punto ({@code 8.33}); los argumentos se separan con punto y
 * coma o coma. Mayúsculas y minúsculas dan igual.
 */
public final class AnalizadorFormula {

    /** Largo máximo del texto y profundidad máxima del árbol: una fórmula no es un programa. */
    public static final int LARGO_MAXIMO = 2000;
    private static final int PROFUNDIDAD_MAXIMA = 60;

    /** Funciones permitidas con su número de argumentos (mínimo, máximo; -1 = sin límite). */
    public static final Map<String, int[]> FUNCIONES = Map.of(
            "SI", new int[] { 3, 3 },
            "MIN", new int[] { 1, -1 },
            "MAX", new int[] { 1, -1 },
            "ABS", new int[] { 1, 1 },
            "REDONDEAR", new int[] { 1, 2 },
            "REDONDEAR_ARRIBA", new int[] { 1, 2 },
            "REDONDEAR_ABAJO", new int[] { 1, 2 },
            "Y", new int[] { 1, -1 },
            "O", new int[] { 1, -1 },
            "NO", new int[] { 1, 1 });

    /** Resultado del análisis: el árbol y lo que la fórmula usa. */
    public record Analisis(NodoFormula arbol, Set<Integer> conceptos, Set<String> variables) {
    }

    private final String texto;
    private int pos;
    private int profundidad;
    private final Set<Integer> conceptos = new LinkedHashSet<>();
    private final Set<String> variables = new LinkedHashSet<>();

    private AnalizadorFormula(String texto) {
        this.texto = texto;
    }

    /**
     * Analiza una fórmula.
     *
     * @throws ErrorFormula con el mensaje y la posición del primer error
     */
    public static Analisis analizar(String formula) {
        if (formula == null || formula.isBlank()) throw new ErrorFormula("La fórmula está vacía.", 0);
        if (formula.length() > LARGO_MAXIMO)
            throw new ErrorFormula("La fórmula pasa de " + LARGO_MAXIMO + " caracteres.", LARGO_MAXIMO);
        AnalizadorFormula a = new AnalizadorFormula(formula);
        NodoFormula arbol = a.expresion();
        a.espacios();
        if (a.pos < a.texto.length())
            throw new ErrorFormula("No se esperaba '" + a.texto.charAt(a.pos) + "'.", a.pos + 1);
        return new Analisis(arbol, a.conceptos, a.variables);
    }

    // ── Reglas ───────────────────────────────────────────────────────────────

    private NodoFormula expresion() {
        if (++profundidad > PROFUNDIDAD_MAXIMA)
            throw new ErrorFormula("La fórmula tiene demasiados niveles de paréntesis o funciones.", pos + 1);
        try {
            return comparacion();
        } finally {
            profundidad--;
        }
    }

    private NodoFormula comparacion() {
        NodoFormula izq = suma();
        espacios();
        for (String op : new String[] { "<=", ">=", "<>", "=", "<", ">" }) {
            if (texto.startsWith(op, pos)) {
                pos += op.length();
                return new Binaria(op, izq, suma());
            }
        }
        return izq;
    }

    private NodoFormula suma() {
        NodoFormula n = producto();
        while (true) {
            espacios();
            char c = actual();
            if (c != '+' && c != '-') return n;
            pos++;
            n = new Binaria(String.valueOf(c), n, producto());
        }
    }

    private NodoFormula producto() {
        NodoFormula n = unario();
        while (true) {
            espacios();
            char c = actual();
            if (c != '*' && c != '/') return n;
            pos++;
            n = new Binaria(String.valueOf(c), n, unario());
        }
    }

    private NodoFormula unario() {
        espacios();
        if (actual() == '-') {
            pos++;
            return new Negativo(unario());
        }
        NodoFormula n = primario();
        espacios();
        if (actual() == '%') {
            pos++;
            if (n instanceof Numero num) return new Numero(num.valor().movePointLeft(2));
            return new Porcentaje(n);
        }
        return n;
    }

    private NodoFormula primario() {
        espacios();
        char c = actual();
        if (c == '(') {
            pos++;
            NodoFormula n = expresion();
            esperar(')');
            return n;
        }
        if (c == '[') {
            pos++;
            espacios();
            int inicio = pos;
            while (Character.isDigit(actual())) pos++;
            if (inicio == pos) throw new ErrorFormula("Después de '[' va el código de un concepto, p. ej. [32].", pos + 1);
            int numero = Integer.parseInt(texto.substring(inicio, pos));
            esperar(']');
            conceptos.add(numero);
            return new Concepto(numero);
        }
        if (Character.isDigit(c) || c == '.') return numero();
        if (Character.isLetter(c) || c == '_') return identificador();
        if (c == '\0') throw new ErrorFormula("La fórmula termina incompleta.", pos + 1);
        throw new ErrorFormula("No se esperaba '" + c + "'.", pos + 1);
    }

    private NodoFormula numero() {
        int inicio = pos;
        while (Character.isDigit(actual())) pos++;
        if (actual() == '.') {
            pos++;
            while (Character.isDigit(actual())) pos++;
        }
        String s = texto.substring(inicio, pos);
        if (s.equals(".")) throw new ErrorFormula("Número mal escrito.", inicio + 1);
        return new Numero(new BigDecimal(s));
    }

    private NodoFormula identificador() {
        int inicio = pos;
        while (Character.isLetterOrDigit(actual()) || actual() == '_') pos++;
        String nombre = quitarTildes(texto.substring(inicio, pos)).toUpperCase();
        espacios();
        if (actual() == '(') {
            int[] aridad = FUNCIONES.get(nombre);
            if (aridad == null) throw new ErrorFormula("La función " + nombre + " no existe.", inicio + 1);
            pos++;
            List<NodoFormula> args = new ArrayList<>();
            espacios();
            if (actual() != ')') {
                args.add(expresion());
                espacios();
                while (actual() == ';' || actual() == ',') {
                    pos++;
                    args.add(expresion());
                    espacios();
                }
            }
            esperar(')');
            if (args.size() < aridad[0] || (aridad[1] >= 0 && args.size() > aridad[1]))
                throw new ErrorFormula("La función " + nombre + " recibe " + textoAridad(aridad) + ".", inicio + 1);
            return new Funcion(nombre, args);
        }
        if (VariableFormula.de(nombre).isEmpty())
            throw new ErrorFormula("La variable " + nombre + " no existe.", inicio + 1);
        variables.add(nombre);
        return new Variable(nombre);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private char actual() {
        return pos < texto.length() ? texto.charAt(pos) : '\0';
    }

    private void espacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) pos++;
    }

    private void esperar(char c) {
        espacios();
        if (actual() != c) throw new ErrorFormula("Falta '" + c + "'.", pos + 1);
        pos++;
    }

    private static String textoAridad(int[] a) {
        if (a[0] == a[1]) return a[0] + (a[0] == 1 ? " argumento" : " argumentos");
        if (a[1] < 0) return "al menos " + a[0] + (a[0] == 1 ? " argumento" : " argumentos");
        return "entre " + a[0] + " y " + a[1] + " argumentos";
    }

    private static String quitarTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}

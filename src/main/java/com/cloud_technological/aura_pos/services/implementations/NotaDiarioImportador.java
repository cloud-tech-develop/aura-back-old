package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Interpreta líneas copiadas de Excel. Sin estado ni base de datos: solo
 * convierte texto en filas crudas, para poder probarlo aparte.
 *
 * <p>Columnas, en orden: cuenta (código) · tercero (documento) · centro de
 * costo (código) · descripción · débito · crédito. Las tres del medio pueden ir
 * vacías. Excel separa las celdas con tabulador; también se acepta punto y coma
 * (CSV en configuración regional de Colombia). La coma NO es separador: en
 * Colombia es el separador decimal.
 */
public final class NotaDiarioImportador {

    private NotaDiarioImportador() {
    }

    /** Fila tal como vino, con su número visible (1 = primera fila pegada). */
    public record FilaCruda(int fila, String cuenta, String tercero, String centroCosto,
            String descripcion, String debito, String credito) {}

    /** Miles con punto: 1.234.567 o 1.234.567,89 */
    private static final Pattern MILES_PUNTO = Pattern.compile("^\\d{1,3}(\\.\\d{3})+(,\\d+)?$");
    /** Miles con coma: 1,234,567 o 1,234,567.89 */
    private static final Pattern MILES_COMA = Pattern.compile("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$");

    public static List<FilaCruda> leer(String texto) {
        List<FilaCruda> filas = new ArrayList<>();
        if (texto == null) return filas;
        String[] renglones = texto.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < renglones.length; i++) {
            String r = renglones[i];
            if (r.isBlank()) continue;
            String sep = r.contains("\t") ? "\t" : ";";
            String[] c = r.split(Pattern.quote(sep), -1);
            String cuenta = celda(c, 0);
            // Encabezado: la primera celda no parece un código de cuenta.
            if (filas.isEmpty() && !cuenta.isEmpty() && !cuenta.matches("\\d+")) continue;
            filas.add(new FilaCruda(i + 1, cuenta, celda(c, 1), celda(c, 2), celda(c, 3),
                    celda(c, 4), celda(c, 5)));
        }
        return filas;
    }

    /**
     * Número tal como lo muestra Excel en Colombia o en inglés: "$ 1.500.000",
     * "1500000", "1.500.000,50", "1,500,000.50", "(1.000)". Vacío = cero.
     *
     * @throws NumberFormatException si no es un número
     */
    public static BigDecimal numero(String s) {
        if (s == null) return BigDecimal.ZERO;
        String v = s.replace("$", "").replace("COP", "").replace(" ", "").replace(" ", "").trim();
        if (v.isEmpty() || v.equals("-")) return BigDecimal.ZERO;
        boolean negativo = false;
        if (v.startsWith("(") && v.endsWith(")")) {
            negativo = true;
            v = v.substring(1, v.length() - 1);
        }
        if (v.startsWith("-")) {
            negativo = true;
            v = v.substring(1);
        }
        if (MILES_PUNTO.matcher(v).matches()) {
            v = v.replace(".", "").replace(',', '.');
        } else if (MILES_COMA.matcher(v).matches()) {
            v = v.replace(",", "");
        } else {
            // Sin separador de miles: la coma, si viene, es el decimal.
            v = v.replace(',', '.');
        }
        BigDecimal n = new BigDecimal(v);
        return negativo ? n.negate() : n;
    }

    /** Documento sin puntos, espacios ni dígito de verificación ("900.123.456-7" → "900123456"). */
    public static String documento(String s) {
        if (s == null) return "";
        String v = s.trim();
        int guion = v.indexOf('-');
        if (guion > 0) v = v.substring(0, guion);
        return v.replace(".", "").replace(" ", "");
    }

    private static String celda(String[] c, int i) {
        if (i >= c.length) return "";
        String v = c[i].trim();
        // Excel envuelve en comillas las celdas con saltos o separadores.
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1).replace("\"\"", "\"").trim();
        }
        return v;
    }
}

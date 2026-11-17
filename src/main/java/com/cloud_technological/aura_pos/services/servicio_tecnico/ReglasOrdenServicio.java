package com.cloud_technological.aura_pos.services.servicio_tecnico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas puras de la orden de servicio (sin base de datos, se prueban solas):
 * estados permitidos, IMEI, listas de accesorios y vencimiento.
 */
public final class ReglasOrdenServicio {

    public static final String RECIBIDO = "RECIBIDO";
    public static final String EN_DIAGNOSTICO = "EN_DIAGNOSTICO";
    public static final String ESPERANDO_APROBACION = "ESPERANDO_APROBACION";
    public static final String ESPERANDO_REPUESTO = "ESPERANDO_REPUESTO";
    public static final String EN_REPARACION = "EN_REPARACION";
    public static final String LISTO = "LISTO";
    public static final String NO_REPARADO = "NO_REPARADO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";

    /** En el orden en que avanza una reparación. */
    public static final List<String> ESTADOS = List.of(RECIBIDO, EN_DIAGNOSTICO, ESPERANDO_APROBACION,
            ESPERANDO_REPUESTO, EN_REPARACION, LISTO, NO_REPARADO, ENTREGADO, CANCELADO);

    public static final Set<String> FINALES = Set.of(ENTREGADO, CANCELADO);

    /** Estados en los que el equipo ya no está en el taller trabajándose. */
    private static final Set<String> SIN_VENCIMIENTO = Set.of(LISTO, NO_REPARADO, ENTREGADO, CANCELADO);

    public static final Set<String> TIPOS_EQUIPO = Set.of("CELULAR", "TABLET", "PORTATIL", "RELOJ", "CONSOLA", "OTRO");
    public static final Set<String> TIPOS_ITEM = Set.of("REPUESTO", "MANO_OBRA", "OTRO");
    public static final Set<String> PRIORIDADES = Set.of("NORMAL", "URGENTE");
    public static final Set<String> MOMENTOS_FOTO = Set.of("RECIBIDO", "REPARACION", "ENTREGA");

    private static final String SEPARADOR = "|";

    private ReglasOrdenServicio() {
    }

    /** ¿Ya no se puede tocar la orden? */
    public static boolean esFinal(String estado) {
        return FINALES.contains(estado);
    }

    /**
     * Estados a los que se puede pasar desde {@code actual}. Se puede saltar hacia
     * adelante o volver atrás mientras la orden siga abierta; entregar solo cuando
     * está lista o se declaró no reparada; cancelar desde cualquier estado abierto.
     */
    public static List<String> permitidos(String actual) {
        if (actual == null || esFinal(actual)) return List.of();
        List<String> out = new ArrayList<>();
        for (String e : ESTADOS) {
            if (e.equals(actual)) continue;
            if (ENTREGADO.equals(e) && !LISTO.equals(actual) && !NO_REPARADO.equals(actual)) continue;
            out.add(e);
        }
        return out;
    }

    /** Motivo por el que no se puede pasar de un estado a otro; null si se puede. */
    public static String motivoTransicionInvalida(String actual, String nuevo, String nota) {
        if (nuevo == null || !ESTADOS.contains(nuevo)) return "Estado no válido";
        if (esFinal(actual))
            return "La orden está " + (ENTREGADO.equals(actual) ? "entregada" : "cancelada") + " y ya no cambia de estado";
        if (nuevo.equals(actual)) return "La orden ya está en ese estado";
        if (ENTREGADO.equals(nuevo) && !permitidos(actual).contains(ENTREGADO))
            return "Solo se entrega una orden lista o marcada como no reparada";
        if (CANCELADO.equals(nuevo) && (nota == null || nota.isBlank()))
            return "Escriba el motivo de la cancelación";
        return null;
    }

    /** ¿Pasó la fecha estimada y el equipo sigue en el taller? */
    public static boolean vencida(String estado, LocalDate fechaEstimada, LocalDate hoy) {
        return fechaEstimada != null && !SIN_VENCIMIENTO.contains(estado) && fechaEstimada.isBefore(hoy);
    }

    /**
     * Normaliza y valida un IMEI: 15 dígitos con dígito de control (Luhn). Acepta
     * espacios, guiones y barras al escribirlo. Vacío = null (equipo sin IMEI).
     *
     * @throws IllegalArgumentException con el mensaje para el usuario
     */
    public static String imei(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) return null;
        String d = valor.replaceAll("[\\s\\-/.]", "");
        if (!d.matches("\\d{15}"))
            throw new IllegalArgumentException(etiqueta + " debe tener 15 dígitos (marque *#06# en el equipo)");
        if (!luhn(d))
            throw new IllegalArgumentException(etiqueta + " no es válido: revise los dígitos");
        return d;
    }

    static boolean luhn(String digitos) {
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int n = digitos.charAt(digitos.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            suma += n;
        }
        return suma % 10 == 0;
    }

    /** Lista → "A|B|C" en mayúsculas y sin repetidos; null si queda vacía. */
    public static String unirLista(List<String> valores, int max) {
        if (valores == null) return null;
        List<String> limpios = valores.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().replace(SEPARADOR, " ").toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (limpios.isEmpty()) return null;
        String s = String.join(SEPARADOR, limpios);
        if (s.length() > max) throw new IllegalArgumentException("La lista es demasiado larga");
        return s;
    }

    /** "A|B|C" → lista. */
    public static List<String> separarLista(String valor) {
        if (valor == null || valor.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(valor.split("\\" + SEPARADOR)));
    }

    /**
     * Base sin IVA de un valor final (IVA incluido): el cliente paga lo que dice la
     * orden y la factura discrimina el impuesto. 119.000 con 19 % → 100.000.
     */
    public static BigDecimal base(BigDecimal valorFinal, BigDecimal ivaPct) {
        if (valorFinal == null) return BigDecimal.ZERO;
        if (ivaPct == null || ivaPct.signum() <= 0) return valorFinal.setScale(2, RoundingMode.HALF_UP);
        return valorFinal.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100).add(ivaPct), 2, RoundingMode.HALF_UP);
    }

    /** ¿Los bytes son una imagen JPG, PNG o WEBP? Devuelve el mime o null. */
    public static String mimeImagen(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B'
                && b[11] == 'P')
            return "image/webp";
        return null;
    }
}

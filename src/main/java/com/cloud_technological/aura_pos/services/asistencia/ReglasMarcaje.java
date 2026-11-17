package com.cloud_technological.aura_pos.services.asistencia;

import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas puras para leer una fila de la plantilla de marcaje (V242): estado de
 * asistencia en palabras del usuario, horas "HH:mm" y qué estados exigen horas.
 * Sin acceso a datos.
 */
public final class ReglasMarcaje {

    /** Estado de asistencia por cada forma en que el usuario lo puede escribir (normalizada). */
    private static final Map<String, String> ESTADOS = new LinkedHashMap<>();

    static {
        alias("ASISTIO", "asistio", "asistió", "si", "sí", "presente", "x");
        alias("NO_ASISTIO", "no asistio", "no asistió", "no", "ausente", "falla", "falto", "faltó");
        alias("LLEGO_TARDE", "llego tarde", "llegó tarde", "tarde");
        alias("SALIO_TEMPRANO", "salio temprano", "salió temprano", "temprano");
        alias("PERMISO", "permiso");
        alias("INCAPACIDAD", "incapacidad", "incapacitado");
        alias("VACACIONES", "vacaciones");
        alias("SUSPENDIDO", "suspendido", "suspension", "suspensión");
        alias("SIN_REGISTRO", "sin registro");
    }

    /** Estados en los que el trabajador estuvo y por eso se exigen entrada y salida. */
    private static final Set<String> CON_HORAS = Set.of("ASISTIO", "LLEGO_TARDE", "SALIO_TEMPRANO");

    private ReglasMarcaje() {
    }

    private static void alias(String estado, String... formas) {
        ESTADOS.put(normalizar(estado), estado);
        for (String f : formas) ESTADOS.put(normalizar(f), estado);
    }

    /**
     * Estado de asistencia de la fila.
     *
     * @param texto    lo que escribió el usuario (puede venir vacío)
     * @param conHoras si la fila trae entrada o salida
     * @return el código del estado; vacío si el texto no es un estado conocido.
     *         Sin texto: ASISTIO si trae horas, SIN_REGISTRO si no.
     */
    public static Optional<String> estado(String texto, boolean conHoras) {
        if (texto == null || texto.isBlank()) return Optional.of(conHoras ? "ASISTIO" : "SIN_REGISTRO");
        return Optional.ofNullable(ESTADOS.get(normalizar(texto)));
    }

    /** ¿El estado exige entrada y salida? */
    public static boolean exigeHoras(String estado) {
        return CON_HORAS.contains(estado);
    }

    /**
     * Hora "H:mm" o "HH:mm" (también "HH:mm:ss").
     *
     * @return vacío si el texto no es una hora válida
     */
    public static Optional<LocalTime> hora(String texto) {
        if (texto == null || texto.isBlank()) return Optional.empty();
        String t = texto.trim();
        if (t.matches("\\d:\\d{2}(:\\d{2})?")) t = "0" + t;
        if (t.length() > 5) t = t.substring(0, 5);
        try {
            return Optional.of(LocalTime.parse(t));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Lista de estados válidos, para el mensaje de error. */
    public static String estadosValidos() {
        return "Asistió, No asistió, Llegó tarde, Salió temprano, Permiso, Incapacidad, Vacaciones, Suspendido";
    }

    private static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toLowerCase().replace('_', ' ').replaceAll("\\s+", " ");
    }
}

package com.cloud_technological.aura_pos.services.empresa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lectura y escritura de {@code empresa.configuracion} (jsonb). Es el único lugar
 * que conoce la forma del JSON:
 *
 * <pre>
 * { "version": 1,
 *   "lineas": ["CONTABILIDAD", "NOMINA"],
 *   "inicio": null,
 *   "arranque": { "CONTABLE": { "resultado": "OK", "ejecutado": "...", "detalle": "..." } } }
 * </pre>
 *
 * <p>Las claves que no son suyas se conservan tal cual: la columna existía antes
 * y no se sabe si alguna empresa trae algo guardado.
 */
final class ConfiguracionEmpresaJson {

    static final int VERSION = 1;
    static final String K_VERSION = "version";
    static final String K_LINEAS = "lineas";
    static final String K_INICIO = "inicio";
    static final String K_ARRANQUE = "arranque";

    static final String OK = "OK";
    static final String ERROR = "ERROR";
    static final String PENDIENTE = "PENDIENTE";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ConfiguracionEmpresaJson() {
    }

    /** Copia mutable de lo guardado; vacía si no hay nada o no es un objeto JSON. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> leer(Object columna) {
        if (columna == null) return new LinkedHashMap<>();
        try {
            if (columna instanceof Map<?, ?> m) return new LinkedHashMap<>((Map<String, Object>) m);
            if (columna instanceof String s && !s.isBlank()) return new LinkedHashMap<>(JSON.readValue(s, Map.class));
        } catch (Exception ignored) {
            // JSON dañado: se trata como vacío; al escribir se reemplaza.
        }
        return new LinkedHashMap<>();
    }

    /**
     * Lo que se asigna a {@code EmpresaEntity.configuracion}. Hibernate mapea ese
     * campo {@code Object} como texto JSON: espera un String y lo manda tal cual al
     * jsonb. Un Map revienta al confirmar ({@code LinkedHashMap cannot be cast to
     * String}), así que siempre se serializa aquí.
     */
    static String aColumna(Map<String, Object> cfg) {
        try {
            return JSON.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la configuración de la empresa", e);
        }
    }

    /** Líneas declaradas válidas, sin repetir y en orden de prioridad; vacía si no declaró. */
    static List<LineaUso> lineas(Map<String, Object> cfg) {
        List<LineaUso> out = new ArrayList<>();
        if (cfg.get(K_LINEAS) instanceof List<?> l) {
            for (Object o : l) {
                LineaUso.de(o != null ? o.toString() : null).ifPresent(x -> {
                    if (!out.contains(x)) out.add(x);
                });
            }
        }
        out.sort(null);
        return out;
    }

    static LineaUso inicio(Map<String, Object> cfg) {
        Object v = cfg.get(K_INICIO);
        return LineaUso.de(v != null ? v.toString() : null).orElse(null);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> estadoPaso(Map<String, Object> cfg, PasoArranque paso) {
        if (cfg.get(K_ARRANQUE) instanceof Map<?, ?> a && a.get(paso.name()) instanceof Map<?, ?> p) {
            return (Map<String, Object>) p;
        }
        return null;
    }

    static void escribirLineas(Map<String, Object> cfg, List<LineaUso> lineas, LineaUso inicio) {
        cfg.put(K_VERSION, VERSION);
        cfg.put(K_LINEAS, lineas.stream().map(Enum::name).toList());
        cfg.put(K_INICIO, inicio != null ? inicio.name() : null);
    }

    @SuppressWarnings("unchecked")
    static void escribirPaso(Map<String, Object> cfg, PasoArranque paso, String resultado, String ejecutado,
            String detalle) {
        cfg.put(K_VERSION, VERSION);
        Map<String, Object> arranque = cfg.get(K_ARRANQUE) instanceof Map<?, ?> a
                ? new LinkedHashMap<>((Map<String, Object>) a)
                : new LinkedHashMap<>();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("resultado", resultado);
        estado.put("ejecutado", ejecutado);
        estado.put("detalle", detalle);
        arranque.put(paso.name(), estado);
        cfg.put(K_ARRANQUE, arranque);
    }
}

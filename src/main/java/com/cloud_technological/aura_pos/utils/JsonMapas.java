package com.cloud_technological.aura_pos.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Conversión entre un mapa y su JSON en texto, para columnas TEXT que guardan
 * atributos flexibles (asignar un mapa a una columna jsonb falla al confirmar la
 * transacción; por eso se guarda como texto).
 */
public final class JsonMapas {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonMapas() {
    }

    /**
     * Mapa → JSON.
     *
     * @param mapa valores (puede ser null o vacío)
     * @return JSON, o null si no hay valores
     * @throws IllegalArgumentException si algún valor no se puede serializar
     */
    public static String aJson(Map<String, Object> mapa) {
        if (mapa == null || mapa.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(mapa);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo convertir a JSON", e);
        }
    }

    /**
     * JSON → mapa (en el orden en que viene).
     *
     * @param json texto (puede ser null)
     * @return mapa; vacío si no hay texto o si el JSON está dañado
     */
    public static Map<String, Object> deJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}

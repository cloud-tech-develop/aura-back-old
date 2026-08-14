package com.cloud_technological.aura_pos.dto.nomina.proceso;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

/**
 * Vista de un proceso asíncrono para el polling del front (Fase 7).
 *
 * <p>Los errores se entregan ya parseados (la entidad los guarda como JSON en
 * texto): así el front los pinta directo sin volver a deserializar.
 */
@Getter
@Setter
public class ProcesoNominaDto {

    private Long id;
    private String tipo;
    private String estado;
    private Integer progreso;
    private String mensaje;
    private Integer totalItems;
    private Integer itemsOk;
    private Integer itemsError;
    private LocalDateTime iniciadoAt;
    private LocalDateTime finalizadoAt;

    /** Uno por item que falló, sin abortar el lote. Vacío si todo salió bien. */
    private List<ErrorItem> errores = new ArrayList<>();

    @Getter
    @Setter
    public static class ErrorItem {
        private String referencia;
        private String error;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ProcesoNominaDto de(ProcesoNominaEntity e) {
        ProcesoNominaDto d = new ProcesoNominaDto();
        d.setId(e.getId());
        d.setTipo(e.getTipo());
        d.setEstado(e.getEstado());
        d.setProgreso(e.getProgreso());
        d.setMensaje(e.getMensaje());
        d.setTotalItems(e.getTotalItems());
        d.setItemsOk(e.getItemsOk());
        d.setItemsError(e.getItemsError());
        d.setIniciadoAt(e.getIniciadoAt());
        d.setFinalizadoAt(e.getFinalizadoAt());
        d.setErrores(parseErrores(e.getErrores()));
        return d;
    }

    private static List<ErrorItem> parseErrores(String json) {
        List<ErrorItem> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            out = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, ErrorItem.class));
        } catch (Exception ignored) {
            // Un JSON corrupto no debe tumbar el polling: se devuelve lista vacía.
        }
        return out;
    }
}

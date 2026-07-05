package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.util.List;

import lombok.Data;

/** Decisiones de revisión por trabajador (aprobar unas horas y otras no). */
@Data
public class RevisarDetallesDto {

    private List<Item> detalles;

    @Data
    public static class Item {
        private Long detalleId;
        /** APROBADO | RECHAZADO | PENDIENTE | AJUSTADO */
        private String estadoRevision;
        private String observacionAdmin;
    }
}

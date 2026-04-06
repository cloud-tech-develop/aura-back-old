package com.cloud_technological.aura_pos.dto.visitas;

import lombok.Data;

@Data
public class ConfirmarLlegadaDto {
    private Double latitud;
    private Double longitud;
    private Boolean confirmacionManual;
    private String observaciones;
}
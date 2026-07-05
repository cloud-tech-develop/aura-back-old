package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SoporteInfoDto {
    private String archivoUrl;
    private String nombreArchivo;
    private String subidoPor;
    private LocalDateTime subidoAt;
}

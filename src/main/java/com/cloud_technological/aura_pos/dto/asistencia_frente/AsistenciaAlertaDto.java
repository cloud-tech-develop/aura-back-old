package com.cloud_technological.aura_pos.dto.asistencia_frente;

import lombok.Data;

@Data
public class AsistenciaAlertaDto {
    private Long id;
    private String tipoAlerta;
    private String nivel;
    private String descripcion;
    private String estado;
    private Long empleadoId;
    private String empleadoNombre;
}

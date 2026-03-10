package com.cloud_technological.aura_pos.dto.error_log;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorLogDetalleDto {

    private Long id;
    private String metodo;
    private String endpoint;
    private Integer statusCode;
    private String categoria;
    private String mensaje;
    private String detalle;
    private String grupoHash;
    private Integer empresaId;
    private String empresaNombre;
    private String usuarioNombre;
    private String ipOrigen;
    private LocalDateTime createdAt;
}

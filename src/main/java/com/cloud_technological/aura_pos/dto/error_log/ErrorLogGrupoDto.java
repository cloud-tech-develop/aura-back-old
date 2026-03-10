package com.cloud_technological.aura_pos.dto.error_log;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorLogGrupoDto {

    private String grupoHash;
    private String metodo;
    private String endpoint;
    private Integer statusCode;
    private String categoria;
    private Long totalOcurrencias;
    private LocalDateTime ultimaOcurrencia;
    private Long empresasAfectadas;

    // Para paginación
    private Long totalRows;
}

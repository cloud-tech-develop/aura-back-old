package com.cloud_technological.aura_pos.dto.error_log;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorLogPageParamsDto {

    private String categoria;
    private Integer empresaId;
    private Integer statusCode;
    private String endpoint;
    private String grupoHash;
    private String desde;
    private String hasta;
}

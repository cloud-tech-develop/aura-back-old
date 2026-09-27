package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NotaDiarioSoporteDto {
    private Long id;
    private String nombreArchivo;
    private String archivoUrl;
    private String contentType;
    private Long tamanoBytes;
    private String subidoPor;
    private String createdAt;
}

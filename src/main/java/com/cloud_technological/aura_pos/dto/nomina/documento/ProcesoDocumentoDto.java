package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Una corrida de liquidación del documento (pestaña Traza). */
@Getter
@Setter
public class ProcesoDocumentoDto {
    private Long id;
    private String estado;
    private Integer progreso;
    private String mensaje;
    private Integer totalItems;
    private Integer itemsOk;
    private Integer itemsError;
    /** JSON con los errores por empleado, como lo guarda el proceso. */
    private String errores;
    private String usuario;
    private LocalDateTime iniciadoAt;
    private LocalDateTime finalizadoAt;
}

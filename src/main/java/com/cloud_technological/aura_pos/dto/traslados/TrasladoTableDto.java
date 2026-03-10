package com.cloud_technological.aura_pos.dto.traslados;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrasladoTableDto {
    private Long id;
    private String sucursalOrigenNombre;
    private String sucursalDestinoNombre;
    private LocalDateTime fecha;
    private String estado;
    private long totalRows;
}

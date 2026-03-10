package com.cloud_technological.aura_pos.dto.merma;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MotivoMermaTableDto {
    private Long id;
    private String nombre;
    private Boolean afectaContabilidad;
    private long totalRows;
}

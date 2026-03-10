package com.cloud_technological.aura_pos.dto.merma;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MotivoMermaDto {
    private Long id;
    private Integer empresaId;
    private String nombre;
    private Boolean afectaContabilidad;
}

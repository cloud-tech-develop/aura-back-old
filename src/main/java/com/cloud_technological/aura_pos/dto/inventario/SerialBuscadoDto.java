package com.cloud_technological.aura_pos.dto.inventario;

import lombok.Getter;
import lombok.Setter;

/** Serial disponible encontrado por el texto escaneado. */
@Getter
@Setter
public class SerialBuscadoDto {
    private Long serialId;
    private String serial;
    private Long productoId;
    private String productoNombre;
}

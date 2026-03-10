package com.cloud_technological.aura_pos.dto.inventario;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SerialProductoTableDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private Long sucursalId;
    private String sucursalNombre;
    private String serial;
    private String estado;
    private long totalRows;
}
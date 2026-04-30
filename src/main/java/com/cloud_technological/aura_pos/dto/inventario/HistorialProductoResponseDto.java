package com.cloud_technological.aura_pos.dto.inventario;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistorialProductoResponseDto {
    private Long productoId;
    private String productoNombre;
    private String sku;
    private List<HistorialMovimientoDto> movimientos;
}
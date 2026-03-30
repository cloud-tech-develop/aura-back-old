package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenCompraDetalleDto {
    private Long id;
    private Long productoId;
    private String productoNombre;
    private BigDecimal cantidad;
    private BigDecimal cantidadRecibida;
    private BigDecimal cantidadPendiente;
    private BigDecimal costoUnitario;
    private BigDecimal subtotalLinea;
}

package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenCompraTableDto {
    private Long id;
    private String numeroOrden;
    private String estado;
    private String proveedorNombre;
    private String sucursalNombre;
    private LocalDate fecha;
    private LocalDate fechaEntregaEsperada;
    private BigDecimal total;
    private Long compraId;
}

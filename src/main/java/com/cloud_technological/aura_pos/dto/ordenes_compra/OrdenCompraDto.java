package com.cloud_technological.aura_pos.dto.ordenes_compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenCompraDto {
    private Long id;
    private String numeroOrden;
    private String estado;
    private Long proveedorId;
    private String proveedorNombre;
    private Integer sucursalId;
    private String sucursalNombre;
    private LocalDate fecha;
    private LocalDate fechaEntregaEsperada;
    private String observaciones;
    private BigDecimal total;
    private Long compraId;
    private List<OrdenCompraDetalleDto> detalles;
}

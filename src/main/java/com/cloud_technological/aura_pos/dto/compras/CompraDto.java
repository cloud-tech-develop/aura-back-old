package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompraDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalId;
    private String sucursalNombre;
    private Long proveedorId;
    private String proveedorNombre;
    private Long usuarioId;
    private String numeroCompra;
    private LocalDateTime fecha;
    private BigDecimal subtotal;
    private BigDecimal descuentoTotal;
    private BigDecimal impuestosTotal;
    private BigDecimal total;
    private String observaciones;
    private String estado;
    private BigDecimal retefuentePct;
    private BigDecimal retefuenteValor;
    private BigDecimal reteivaPct;
    private BigDecimal reteivaValor;
    private BigDecimal reteicaPct;
    private BigDecimal reteicaValor;
    private BigDecimal totalRetenciones;
    private BigDecimal netaAPagar;
    private String formaPago;
    private String tipoDocumento;
    private BigDecimal fletes;
    private List<CompraDetalleDto> detalles;
    private List<CompraPagoDto> pagos;
}

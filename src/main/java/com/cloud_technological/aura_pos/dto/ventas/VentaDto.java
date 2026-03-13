package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalId;
    private String sucursalNombre;
    private Long clienteId;
    private String clienteNombre;
    private String clienteDocumento;
    private Long usuarioId;
    private Long turnoCajaId;
    private String tipoDocumento;
    private String prefijo;
    private Long consecutivo;
    private LocalDateTime fechaEmision;
    private BigDecimal subtotal;
    private BigDecimal descuentoTotal;
    private BigDecimal impuestosTotal;
    private BigDecimal totalPagar;
    private String estadoVenta;
    private String observaciones;
    private Long facturaId;
    private List<VentaDetalleDto> detalles;
    private List<VentaPagoDto> pagos;
    private String cufe;
    private String qrData;
    private String estadoDian;
    private String factusUrl;   // URL pública del documento en Factus
}

package com.cloud_technological.aura_pos.dto.ventas;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentaTableDto {
    private Long id;
    private String prefijo;
    private Long consecutivo;
    private String clienteNombre;
    private String sucursalNombre;
    private LocalDateTime fechaEmision;
    private BigDecimal totalPagar;
    private String estadoVenta;
    private String tipoDocumento;
    private String estadoDian;
    private String factusUrl;
    private String cufe;
    private String factusNumero;
    private long totalRows;
}

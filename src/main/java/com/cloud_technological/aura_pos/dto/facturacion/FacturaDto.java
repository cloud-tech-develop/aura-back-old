package com.cloud_technological.aura_pos.dto.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturaDto {
    private Long id;
    private String prefijo;
    private Long consecutivo;
    private String metodoPago;
    private BigDecimal valor;
    private BigDecimal descuento;
    private String cufe;
    private String descripcion;
    private LocalDateTime fechaHoraEmision;
    private String estadoDian;
    private String tipoAmbiente;
    private Integer empresaId;
    private Integer usuarioId;
    private Long ventaId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Desglose IVA por tarifa (V53)
    private BigDecimal ivaBase0;
    private BigDecimal ivaBase5;
    private BigDecimal ivaValor5;
    private BigDecimal ivaBase19;
    private BigDecimal ivaValor19;
}

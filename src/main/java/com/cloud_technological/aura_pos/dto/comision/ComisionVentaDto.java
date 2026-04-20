package com.cloud_technological.aura_pos.dto.comision;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComisionVentaDto {
    private Long id;
    private Long ventaId;
    private Long ventaDetalleId;
    private Long ventaConsecutivo;
    private String ventaFecha;
    private String productoNombre;
    private String tecnicoNombre;
    private BigDecimal valorTotal;
    private BigDecimal porcentajeTecnico;
    private BigDecimal valorTecnico;
    private BigDecimal valorNegocio;
    private Long liquidacionId;
    private String createdAt;
}

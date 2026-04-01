package com.cloud_technological.aura_pos.dto.comprobante;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComprobanteCajaDto {
    private Long id;
    private String numeroComprobante;
    private String tipo;         // INGRESO | EGRESO
    private String concepto;
    private BigDecimal monto;
    private String metodoPago;
    private String entregadoA;
    private String origen;       // MANUAL | DEVOLUCION | ABONO_CXC | ABONO_CXP
    private Long origenId;
    private Long turnoCajaId;
    private Integer usuarioId;
    private String createdAt;
    private Long totalRows;
}

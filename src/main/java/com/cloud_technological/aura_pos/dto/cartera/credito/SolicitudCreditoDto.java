package com.cloud_technological.aura_pos.dto.cartera.credito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudCreditoDto {
    private Long id;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private BigDecimal montoSolicitado;
    private BigDecimal cupoDisponible;
    private BigDecimal excedente;
    private BigDecimal cupoActual;
    private BigDecimal saldoActual;
    private Integer diasMora;
    private Integer scoreCrediticio;
    private String estado;
    private String observacion;
    private String solicitadoPor;
    private String aprobadoPor;
    private String motivoRechazo;
    private LocalDateTime createdAt;
    private LocalDateTime respondidoAt;
    private LocalDateTime vigenteHasta;
    private LocalDateTime usadaAt;
    private Long ventaId;
    private String numeroVenta;
}

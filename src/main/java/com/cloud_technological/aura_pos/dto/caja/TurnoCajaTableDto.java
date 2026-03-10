package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnoCajaTableDto {
    private Long id;
    private Long cajaId;
    private String cajaNombre;
    private String usuarioNombre;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private BigDecimal baseInicial;
    private BigDecimal totalEfectivoSistema;
    private BigDecimal totalEfectivoReal;
    private BigDecimal diferencia;
    private String estado;
    private long totalRows;
}

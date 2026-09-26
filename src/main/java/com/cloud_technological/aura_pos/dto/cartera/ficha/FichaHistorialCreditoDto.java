package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaHistorialCreditoDto {
    private Long id;
    private String tipoEvento;
    private BigDecimal cupoAnterior;
    private BigDecimal cupoNuevo;
    private Integer scoreAnterior;
    private Integer scoreNuevo;
    private String motivo;
    private String usuarioNombre;
    private String reglaNombre;
    private LocalDateTime createdAt;
}

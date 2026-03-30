package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GastoDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalId;
    private String sucursalNombre;
    private Integer usuarioId;
    private String usuarioNombre;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private LocalDate fecha;
    private Boolean deducible;
    private String estado;
    private LocalDateTime createdAt;
}

package com.cloud_technological.aura_pos.dto.merma;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermaDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalId;
    private String sucursalNombre;
    private Long usuarioId;
    private Long motivoId;
    private String motivoNombre;
    private LocalDateTime fecha;
    private String observacion;
    private BigDecimal costoTotal;
    private String estado;
    private List<MermaDetalleDto> detalles;
}

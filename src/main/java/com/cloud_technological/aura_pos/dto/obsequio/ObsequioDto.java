package com.cloud_technological.aura_pos.dto.obsequio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObsequioDto {
    private Long id;
    private Long sucursalId;
    private String sucursalNombre;
    private Long terceroId;
    private String terceroNombre;
    private String usuarioNombre;
    private LocalDateTime fecha;
    private String motivo;
    private String observacion;
    private BigDecimal costoTotal;
    private BigDecimal baseComercialTotal;
    private BigDecimal ivaTotal;
    private Boolean generaIva;
    private String estado;
    private List<ObsequioDetalleDto> detalles = new ArrayList<>();
}

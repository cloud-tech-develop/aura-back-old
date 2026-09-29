package com.cloud_technological.aura_pos.dto.consumo_interno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsumoInternoDto {
    private Long id;
    private Long sucursalId;
    private String sucursalNombre;
    private Long conceptoId;
    private String conceptoNombre;
    private Long responsableTerceroId;
    private String responsableNombre;
    private String usuarioNombre;
    private LocalDateTime fecha;
    private String observacion;
    private BigDecimal costoTotal;
    private BigDecimal baseComercialTotal;
    private BigDecimal ivaTotal;
    private Boolean generaIva;
    private String estado;
    private List<ConsumoInternoDetalleDto> detalles = new ArrayList<>();
}

package com.cloud_technological.aura_pos.dto.traslados;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrasladoDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalOrigenId;
    private String sucursalOrigenNombre;
    private Long sucursalDestinoId;
    private String sucursalDestinoNombre;
    private Long usuarioId;
    private LocalDateTime fecha;
    private String observacion;
    private String estado;
    private List<TrasladoDetalleDto> detalles;
}

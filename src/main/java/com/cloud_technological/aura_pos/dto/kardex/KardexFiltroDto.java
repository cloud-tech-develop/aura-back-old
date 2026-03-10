package com.cloud_technological.aura_pos.dto.kardex;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KardexFiltroDto {
    private Integer page = 0;
    private Integer rows = 20;
    private Long productoId;       // filtro obligatorio recomendado
    private Long sucursalId;       // filtro opcional
    private Long loteId;           // filtro opcional
    private String tipoMovimiento; // filtro opcional
    private LocalDateTime fechaDesde;
    private LocalDateTime fechaHasta;
}

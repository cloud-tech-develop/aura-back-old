package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MantenimientoActivoDto {
    private Long id;
    private Long activoId;
    private LocalDate fecha;
    /** PREVENTIVO | CORRECTIVO */
    private String tipo;
    private String descripcion;
    private BigDecimal costo;
    private Long terceroId;
    private String terceroNombre;
    private LocalDate proximo;
}

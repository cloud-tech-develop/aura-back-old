package com.cloud_technological.aura_pos.dto.comision;


import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLiquidacionDto {

    // Para tipo TECNICO (usuario)
    private Integer tecnicoId;

    // Para tipo VENDEDOR (empleado)
    private Long vendedorId;

    @NotNull(message = "La fechaDesde es requerida")
    private String fechaDesde; // YYYY-MM-DD (metadato + filtro opcional si no hay comisionIds)

    @NotNull(message = "La fechaHasta es requerida")
    private String fechaHasta; // YYYY-MM-DD

    // Si se especifican, solo se liquidan estas comisiones (selección manual)
    // Si está vacío o null, se incluyen todas las pendientes (con filtro de fechas opcional)
    private List<Long> comisionIds;

    private String observaciones;

    // TECNICO (servicios) | VENDEDOR (ventas) — por defecto TECNICO
    private String tipo = "TECNICO";
}

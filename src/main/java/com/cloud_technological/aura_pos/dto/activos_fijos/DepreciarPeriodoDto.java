package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** Solo para activos por unidades de producción: uso del mes por activo. */
@Getter
@Setter
public class DepreciarPeriodoDto {
    private Map<Long, BigDecimal> unidades;
}

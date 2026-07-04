package com.cloud_technological.aura_pos.dto.asistencia;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsistenciaNovedadDto {
    private Long id;
    private Long periodoNominaId;
    private Long empleadoId;
    private String empleadoNombre;
    private String tipoNovedad;
    private String unidad;
    private BigDecimal cantidad;
    private BigDecimal valorManual;
    private String origen;
    private String estado;
}

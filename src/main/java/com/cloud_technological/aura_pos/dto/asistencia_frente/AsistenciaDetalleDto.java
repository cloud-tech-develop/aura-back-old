package com.cloud_technological.aura_pos.dto.asistencia_frente;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class AsistenciaDetalleDto {
    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private String documento;
    private String cargo;
    /** Formato "HH:mm". */
    private String horaEntrada;
    private String horaSalida;
    private BigDecimal horasTrabajadas;
    private BigDecimal horasExtraDiurnas;
    private BigDecimal horasExtraNocturnas;
    private BigDecimal horasDominicalesFestivas;
    private String estadoAsistencia;
    private String estadoRevision;
    private String observacionLider;
}

package com.cloud_technological.aura_pos.dto.activos_fijos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivoFijoDto {
    private Long id;
    private Integer empresaId;
    private String codigo;
    private String descripcion;
    private String categoria;
    private LocalDate fechaAdquisicion;
    private BigDecimal valorCompra;
    private Integer vidaUtilMeses;
    private String metodoDepreciacion;
    private BigDecimal depreciacionAcumulada;
    private BigDecimal valorResidual;
    private BigDecimal valorEnLibros;
    private String ubicacion;
    private String responsable;
    private String estado;
    private Long cuentaActivoId;
    private Long cuentaDepreciacionId;
    private Long cuentaGastoDepId;
    private Long centroCostoId;
    private Long periodoContableId;
    private Long terceroId;
    private String observaciones;
    private LocalDateTime createdAt;
}

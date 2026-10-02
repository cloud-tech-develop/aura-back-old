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

    // ── Ficha completa (V186) ───────────────────────────────────────────
    private String placa;
    private String serial;
    private String marca;
    private String modelo;
    private Long responsableTerceroId;
    private Long activoPadreId;
    private String aseguradora;
    private String polizaNumero;
    private LocalDate polizaVence;
    /** La depreciación empieza el mes de esta fecha; vacío = fecha de adquisición. */
    private LocalDate fechaInicioDepreciacion;
    /** Solo UNIDADES_PRODUCCION: vida total en unidades. */
    private BigDecimal unidadesEstimadas;
    private String responsableTerceroNombre;
    private String activoPadreCodigo;
    private BigDecimal valorAdiciones;
    /** Compra + adiciones. */
    private BigDecimal costoTotal;
    private Integer mesesDepreciados;
    private LocalDate fechaRetiro;
    private String motivoRetiro;
    private BigDecimal valorVenta;
    private Long compradorTerceroId;
    private Long asientoRetiroId;
    private Long compraId;
    private Long productoId;
    private LocalDateTime createdAt;
}

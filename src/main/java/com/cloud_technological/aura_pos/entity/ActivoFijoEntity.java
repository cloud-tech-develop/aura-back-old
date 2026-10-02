package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "activo_fijo")
@Getter
@Setter
public class ActivoFijoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String descripcion;

    /** MAQUINARIA | VEHICULO | INMUEBLE | EQUIPO | MUEBLES | INTANGIBLE */
    @Column(nullable = false, length = 50)
    private String categoria;

    @Column(name = "fecha_adquisicion", nullable = false)
    private LocalDate fechaAdquisicion;

    @Column(name = "valor_compra", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorCompra;

    @Column(name = "vida_util_meses", nullable = false)
    private Integer vidaUtilMeses;

    /** LINEA_RECTA | UNIDADES_PRODUCCION | SALDO_DECRECIENTE */
    @Column(name = "metodo_depreciacion", nullable = false, length = 20)
    private String metodoDepreciacion = "LINEA_RECTA";

    @Column(name = "depreciacion_acumulada", nullable = false, precision = 18, scale = 2)
    private BigDecimal depreciacionAcumulada = BigDecimal.ZERO;

    @Column(name = "valor_residual", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorResidual = BigDecimal.ZERO;

    @Column(length = 100)
    private String ubicacion;

    @Column(length = 100)
    private String responsable;

    /** ACTIVO | DEPRECIADO | VENDIDO | DADO_DE_BAJA */
    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "cuenta_activo_id")
    private Long cuentaActivoId;

    @Column(name = "cuenta_depreciacion_id")
    private Long cuentaDepreciacionId;

    @Column(name = "cuenta_gasto_dep_id")
    private Long cuentaGastoDepId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(name = "periodo_contable_id")
    private Long periodoContableId;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ── Origen (V185): la compra que creó la ficha ──────────────────────
    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "compra_detalle_id")
    private Long compraDetalleId;

    @Column(name = "producto_id")
    private Long productoId;

    // ── Ficha completa (V186) ───────────────────────────────────────────
    @Column(length = 40)
    private String placa;

    @Column(length = 80)
    private String serial;

    @Column(length = 80)
    private String marca;

    @Column(length = 80)
    private String modelo;

    @Column(name = "responsable_tercero_id")
    private Long responsableTerceroId;

    @Column(name = "activo_padre_id")
    private Long activoPadreId;

    @Column(length = 120)
    private String aseguradora;

    @Column(name = "poliza_numero", length = 60)
    private String polizaNumero;

    @Column(name = "poliza_vence")
    private LocalDate polizaVence;

    /** La depreciación empieza el mes de esta fecha; null = fecha de adquisición. */
    @Column(name = "fecha_inicio_depreciacion")
    private LocalDate fechaInicioDepreciacion;

    /** Unidades de producción: vida total en unidades (horas, km, piezas). */
    @Column(name = "unidades_estimadas", precision = 18, scale = 2)
    private BigDecimal unidadesEstimadas;

    /** Adiciones capitalizadas: el costo depreciable es valorCompra + esto. */
    @Column(name = "valor_adiciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorAdiciones = BigDecimal.ZERO;

    // ── Baja y venta (V186) ─────────────────────────────────────────────
    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Column(name = "motivo_retiro", length = 300)
    private String motivoRetiro;

    @Column(name = "valor_venta", precision = 18, scale = 2)
    private BigDecimal valorVenta;

    @Column(name = "comprador_tercero_id")
    private Long compradorTerceroId;

    @Column(name = "asiento_retiro_id")
    private Long asientoRetiroId;

    /** Costo depreciable: compra + adiciones. */
    public BigDecimal costoTotal() {
        return (valorCompra != null ? valorCompra : BigDecimal.ZERO)
                .add(valorAdiciones != null ? valorAdiciones : BigDecimal.ZERO);
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

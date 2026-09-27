package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * El negocio saca producto de su inventario para usarlo él mismo. Descarga
 * inventario y kardex como la merma, pero va al gasto del concepto y, si
 * aplica, causa el IVA por retiro como el obsequio.
 */
@Getter
@Setter
@Entity
@Table(name = "consumo_interno")
public class ConsumoInternoEntity {

    public static final String ESTADO_APROBADO = "APROBADO";
    public static final String ESTADO_ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    /** Dónde vive el stock (V172). Si el documento no la dice, es la
     *  bodega principal de la sucursal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_id")
    private BodegaEntity bodega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concepto_id")
    private ConceptoConsumoInternoEntity concepto;

    /** Quién lo retiró o para quién. Opcional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_tercero_id")
    private TerceroEntity responsable;

    private LocalDateTime fecha;

    @Column(length = 300)
    private String observacion;

    /** Costo de lo consumido: al gasto y fuera del inventario. */
    @Column(name = "costo_total", precision = 15, scale = 2)
    private BigDecimal costoTotal;

    /** Valor comercial sin IVA, base del impuesto por retiro. */
    @Column(name = "base_comercial_total", precision = 15, scale = 2)
    private BigDecimal baseComercialTotal;

    @Column(name = "iva_total", precision = 15, scale = 2)
    private BigDecimal ivaTotal;

    @Column(name = "genera_iva")
    private Boolean generaIva;

    @Column(length = 20)
    private String estado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
    }
}

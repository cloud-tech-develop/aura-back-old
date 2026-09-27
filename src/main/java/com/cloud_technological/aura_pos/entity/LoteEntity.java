package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lote")
@Getter
@Setter
public class LoteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    /** Dónde vive el stock (V172). Si el documento no la dice, es la
     *  bodega principal de la sucursal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_id")
    private BodegaEntity bodega;

    @Column(name = "codigo_lote")
    private String codigoLote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "stock_actual")
    private BigDecimal stockActual;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    private Boolean activo;

    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "fecha_fabricacion")
    private LocalDate fechaFabricacion;

    /** Línea de compra que creó el lote (la primera, si otra compra suma después). */
    @Column(name = "compra_detalle_id")
    private Long compraDetalleId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

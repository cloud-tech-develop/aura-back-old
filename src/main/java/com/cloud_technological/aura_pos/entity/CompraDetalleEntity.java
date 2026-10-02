package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

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
@Table(name = "compra_detalle")
@Getter
@Setter
public class CompraDetalleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id")
    private CompraEntity compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEntity lote;

    private BigDecimal cantidad;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    @Column(name = "impuesto_valor")
    private BigDecimal impuestoValor;

    @Column(name = "subtotal_linea")
    private BigDecimal subtotalLinea;

    @Column(name = "descuento_pct")
    private BigDecimal descuentoPct;

    @Column(name = "descuento_valor")
    private BigDecimal descuentoValor;

    @Column(name = "precio_venta1")
    private BigDecimal precioVenta1;

    @Column(name = "precio_venta2")
    private BigDecimal precioVenta2;

    @Column(name = "precio_venta3")
    private BigDecimal precioVenta3;

    /** Presentación en que se escribió la línea (4 Pacas); cantidad y costo siguen en unidad base. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_presentacion_id")
    private ProductoPresentacionEntity productoPresentacion;

    @Column(name = "cantidad_presentacion")
    private BigDecimal cantidadPresentacion;

    @Column(name = "costo_presentacion")
    private BigDecimal costoPresentacion;

    /**
     * Clasificación del producto al registrar la línea (V185). Null en compras
     * previas = PRODUCTO. Anular y editar la leen para deshacer lo que la
     * línea hizo, aunque el producto haya cambiado de clasificación después.
     */
    private String clasificacion;

    /** Unidades base sueltas que acompañan a la presentación: 4 Pacas + 2 und (V185). */
    @Column(name = "cantidad_suelta")
    private BigDecimal cantidadSuelta;
}

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

@Getter
@Setter
@Entity
@Table(name = "producto_composicion")
public class ProductoComposicionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_padre_id")
    private ProductoEntity productoPadre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_hijo_id")
    private ProductoEntity productoHijo;

    /**
     * DERIVADA — consumo del hijo, en su unidad base de stock, por 1 unidad del
     * padre. Es la que lee el descuento de inventario en venta; no se edita a
     * mano, la recalcula {@code ProductoComposicionServiceImpl}.
     */
    private BigDecimal cantidad;

    private String tipo;

    /** Cantidad por LOTE en la unidad que eligió el usuario. Campo editable. */
    @Column(name = "cantidad_receta")
    private BigDecimal cantidadReceta;

    /** Unidad en la que está escrita {@link #cantidadReceta}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_medida_id")
    private UnidadMedidaEntity unidadMedida;

    /** Presentación del hijo de la que se dedujo {@link #factorUnidad}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_presentacion_id")
    private ProductoPresentacionEntity productoPresentacion;

    /** Unidades base de stock del hijo que equivalen a 1 unidad escrita. */
    @Column(name = "factor_unidad")
    private BigDecimal factorUnidad = BigDecimal.ONE;

    /** Merma del ingrediente en el proceso, en porcentaje (0..99.99). */
    @Column(name = "merma_porcentaje")
    private BigDecimal mermaPorcentaje = BigDecimal.ZERO;

    private Integer orden;

    private String nota;
}

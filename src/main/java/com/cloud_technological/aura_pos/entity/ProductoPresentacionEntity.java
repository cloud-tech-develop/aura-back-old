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
@Table(name = "producto_presentacion")
public class ProductoPresentacionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    private String nombre;

    /** Único dentro de la empresa, no en todo el sistema (V159); lo valida el servicio. */
    @Column(name = "codigo_barras")
    private String codigoBarras;

    /** Unidades base que contiene la presentación: Caja ×10 → 10 (V159). */
    @Column(name = "factor_conversion")
    private BigDecimal factorConversion;

    /** false = solo para comprar: el POS no la ofrece (V161). */
    @Column(name = "se_vende")
    private Boolean seVende = true;

    @Column(name = "es_default_compra")
    private Boolean esDefaultCompra;

    @Column(name = "es_default_venta")
    private Boolean esDefaultVenta;

    private BigDecimal precio;

    private BigDecimal costo;

    private Boolean activo;
}

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

    @Column(name = "codigo_barras", unique = true)
    private String codigoBarras;

    @Column(name = "factor_conversion")
    private BigDecimal factorConversion;

    @Column(name = "es_default_compra")
    private Boolean esDefaultCompra;

    @Column(name = "es_default_venta")
    private Boolean esDefaultVenta;

    private BigDecimal precio;

    private BigDecimal costo;

    private Boolean activo;
}

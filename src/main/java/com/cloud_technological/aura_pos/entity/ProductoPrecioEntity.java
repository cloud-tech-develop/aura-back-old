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
@Table(name = "producto_precio")
@Getter
@Setter
public class ProductoPrecioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_precio_id")
    private ListaPreciosEntity listaPrecio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_presentacion_id")
    private ProductoPresentacionEntity productoPresentacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    private BigDecimal precio;

    @Column(name = "utilidad_esperada")
    private BigDecimal utilidadEsperada;
}

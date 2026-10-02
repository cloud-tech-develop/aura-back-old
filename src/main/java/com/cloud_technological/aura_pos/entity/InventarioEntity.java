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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventario")
@Getter
@Setter
public class InventarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    /** Dónde vive el stock (V172). Si el documento no la dice, es la
     *  bodega principal de la sucursal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_id")
    private BodegaEntity bodega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @Column(name = "stock_actual")
    private BigDecimal stockActual = BigDecimal.ZERO;

    @Column(name = "stock_minimo")
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    private String ubicacion;

    /** Hasta dónde llenar la bodega al pedir (V185). Null = sin máximo. */
    @Column(name = "stock_maximo")
    private BigDecimal stockMaximo;

    /** Con este saldo o menos hay que pedir (V185). Null = se usa el mínimo. */
    @Column(name = "punto_reorden")
    private BigDecimal puntoReorden;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
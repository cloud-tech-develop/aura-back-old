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
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un producto que estaba en un carrito abandonado. */
@Entity
@Table(name = "carrito_abandonado_item")
@Getter
@Setter
@NoArgsConstructor
public class CarritoAbandonadoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrito_id", nullable = false)
    private CarritoAbandonadoEntity carrito;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "agregado_at")
    private LocalDateTime agregadoAt;
}

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

/**
 * Componente que salió del inventario por una línea de merma u obsequio de un
 * producto con receta. Es lo que devuelve la anulación y lo que acredita el
 * asiento. Ver V157.
 */
@Getter
@Setter
@Entity
@Table(name = "inventario_consumo_componente")
public class InventarioConsumoComponenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id")
    private Integer empresaId;

    /** MERMA | OBSEQUIO. */
    private String origen;

    /** merma_detalle.id u obsequio_detalle.id, según {@link #origen}. */
    @Column(name = "detalle_id")
    private Long detalleId;

    @Column(name = "producto_padre_id")
    private Long productoPadreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_hijo_id")
    private ProductoEntity productoHijo;

    /** En unidad base de stock del componente. */
    private BigDecimal cantidad;

    @Column(name = "costo_unitario")
    private BigDecimal costoUnitario;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

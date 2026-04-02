package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orden_compra_detalle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenCompraDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_compra_id", nullable = false)
    private Long ordenCompraId;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "producto_nombre", nullable = false, length = 500)
    private String productoNombre;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "cantidad_recibida", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;

    @Column(name = "costo_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "subtotal_linea", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalLinea;
}

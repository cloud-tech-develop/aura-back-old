package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orden_compra")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenCompraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "numero_orden", nullable = false, length = 20)
    private String numeroOrden;

    /** BORRADOR | ENVIADA | CONFIRMADA | RECIBIDA_PARCIAL | CERRADA | ANULADA */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String estado = "BORRADOR";

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_entrega_esperada")
    private LocalDate fechaEntregaEsperada;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

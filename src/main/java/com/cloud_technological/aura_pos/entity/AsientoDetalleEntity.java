package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "asiento_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AsientoDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Relación bidireccional: el hijo es dueño del FK asiento_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_id", nullable = false)
    private AsientoContableEntity asiento;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal debito = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal credito = BigDecimal.ZERO;

    /** NIT/Id del tercero involucrado en esta línea (cliente, proveedor, empleado…) */
    @Column(name = "tercero_id")
    private Long terceroId;

    /** Centro de costo al que se imputa esta línea contable */
    @Column(name = "centro_costo_id")
    private Long centroCostoId;
}

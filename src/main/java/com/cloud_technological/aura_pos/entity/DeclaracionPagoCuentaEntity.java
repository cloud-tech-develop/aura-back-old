package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lo que el pago de la declaración cierra de una cuenta: valor &gt; 0 se debita
 * (impuesto por pagar), valor &lt; 0 se acredita (IVA descontable, retenciones
 * que le practicaron).
 */
@Entity
@Table(name = "declaracion_pago_cuenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeclaracionPagoCuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pago_id", nullable = false)
    private Long pagoId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;
}

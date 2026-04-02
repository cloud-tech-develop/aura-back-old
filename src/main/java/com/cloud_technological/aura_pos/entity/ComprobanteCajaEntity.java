package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "comprobante_caja")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ComprobanteCajaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "numero_comprobante", nullable = false, length = 20, unique = true)
    private String numeroComprobante;

    /** INGRESO | EGRESO */
    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false, length = 500)
    private String concepto;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    /** EFECTIVO | TRANSFERENCIA — null para abonos CxC/CxP */
    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    /** Nombre de quien recibe/entrega el dinero (opcional) */
    @Column(name = "entregado_a", length = 200)
    private String entregadoA;

    /** MANUAL | DEVOLUCION | ABONO_CXC | ABONO_CXP */
    @Column(length = 30)
    private String origen;

    @Column(name = "origen_id")
    private Long origenId;

    @Column(name = "turno_caja_id")
    private Long turnoCajaId;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}

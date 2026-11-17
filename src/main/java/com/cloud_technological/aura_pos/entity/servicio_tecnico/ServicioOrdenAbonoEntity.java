package com.cloud_technological.aura_pos.entity.servicio_tecnico;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Abono a una orden de servicio (V250). El dinero se registra como un recibo de
 * caja del cliente que queda como anticipo; al entregar, el anticipo se cruza con
 * la factura.
 */
@Entity
@Table(name = "servicio_orden_abono")
@Getter
@Setter
@NoArgsConstructor
public class ServicioOrdenAbonoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    @Column(name = "orden_id", nullable = false, updatable = false)
    private Long ordenId;

    @Column(name = "recibo_caja_id", nullable = false, updatable = false)
    private Long reciboCajaId;

    @Column(name = "anticipo_id", updatable = false)
    private Long anticipoId;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "metodo_pago", nullable = false, length = 40, updatable = false)
    private String metodoPago;

    /** ACTIVO o ANULADO. */
    @Column(nullable = false, length = 10)
    private String estado = "ACTIVO";

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

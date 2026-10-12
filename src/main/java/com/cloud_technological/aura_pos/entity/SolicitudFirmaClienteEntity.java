package com.cloud_technological.aura_pos.entity;

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
 * Un cliente pide a una firma contable que le lleve la contabilidad (V200,
 * docs/PLAN_FIRMA_CONTABLE.md §9). La firma acepta (se crea el vínculo) o rechaza.
 */
@Entity
@Table(name = "solicitud_firma_cliente")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudFirmaClienteEntity {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACEPTADA = "ACEPTADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_empresa_id", nullable = false)
    private Integer clienteEmpresaId;

    @Column(name = "firma_empresa_id", nullable = false)
    private Integer firmaEmpresaId;

    @Column(nullable = false, length = 15)
    private String estado = PENDIENTE;

    @Column(name = "solicitado_por")
    private Integer solicitadoPor;

    @Column(name = "solicitado_at", nullable = false, updatable = false)
    private LocalDateTime solicitadoAt;

    @Column(name = "respondido_por")
    private Integer respondidoPor;

    @Column(name = "respondido_at")
    private LocalDateTime respondidoAt;

    @Column(length = 300)
    private String motivo;

    @PrePersist
    void alCrear() {
        if (solicitadoAt == null) solicitadoAt = LocalDateTime.now();
        if (estado == null) estado = PENDIENTE;
    }
}

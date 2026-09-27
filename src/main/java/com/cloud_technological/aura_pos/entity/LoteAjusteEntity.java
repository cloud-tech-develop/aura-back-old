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
import lombok.Setter;

/** Corrección manual del código o del vencimiento de un lote, con su motivo. */
@Getter
@Setter
@Entity
@Table(name = "lote_ajuste")
public class LoteAjusteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 40, nullable = false)
    private String campo;

    @Column(name = "valor_anterior", length = 100)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 100)
    private String valorNuevo;

    @Column(length = 300, nullable = false)
    private String motivo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

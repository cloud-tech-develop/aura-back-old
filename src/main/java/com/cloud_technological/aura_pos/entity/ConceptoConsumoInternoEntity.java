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

/**
 * Para qué usó el negocio su propio inventario (aseo, mantenimiento…). Decide
 * la cuenta del gasto y si el documento causa IVA por retiro de entrada.
 */
@Getter
@Setter
@Entity
@Table(name = "concepto_consumo_interno")
public class ConceptoConsumoInternoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(length = 80, nullable = false)
    private String nombre;

    /** Cuenta del gasto o del activo. Null = GASTO_CONSUMO_INTERNO de la configuración contable. */
    @Column(name = "cuenta_id")
    private Long cuentaId;

    @Column(name = "genera_iva", nullable = false)
    private Boolean generaIva = Boolean.TRUE;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

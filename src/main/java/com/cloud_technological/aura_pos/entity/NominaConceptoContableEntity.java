package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cambio de la empresa sobre cómo se contabiliza un concepto de nómina (V218):
 * cuenta del débito, del crédito y a nombre de quién va el crédito. Lo nulo usa
 * el valor por defecto ({@code ReglaContableNomina} + parametrización contable).
 */
@Entity
@Table(name = "nomina_concepto_contable")
@Getter
@Setter
public class NominaConceptoContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "concepto_codigo", nullable = false, length = 30)
    private String conceptoCodigo;

    @Column(name = "cuenta_debito_id")
    private Long cuentaDebitoId;

    @Column(name = "cuenta_credito_id")
    private Long cuentaCreditoId;

    /** EMPLEADO | EPS | AFP | ARL | CCF | DIAN | NINGUNO | FIJO. */
    @Column(name = "tercero_credito", length = 20)
    private String terceroCredito;

    /** Tercero del crédito cuando {@code terceroCredito} es FIJO. */
    @Column(name = "tercero_fijo_id")
    private Long terceroFijoId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

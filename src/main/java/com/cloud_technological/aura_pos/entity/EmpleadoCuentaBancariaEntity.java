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
 * Cuenta bancaria del empleado (V213). La principal se copia al tercero, de
 * donde leen el pago de nómina y la nómina electrónica.
 */
@Entity
@Table(name = "empleado_cuenta_bancaria")
@Getter
@Setter
public class EmpleadoCuentaBancariaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    /** Tercero con rol BANCO. */
    @Column(name = "banco_tercero_id", nullable = false)
    private Long bancoTerceroId;

    /** AHORROS | CORRIENTE | DEPOSITO_ELECTRONICO. */
    @Column(name = "tipo_cuenta", nullable = false, length = 25)
    private String tipoCuenta;

    @Column(name = "numero_cuenta", nullable = false, length = 50)
    private String numeroCuenta;

    @Column(nullable = false)
    private Boolean principal = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean activa = Boolean.TRUE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

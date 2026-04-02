package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cuenta_bancaria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaBancariaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 200)
    private String nombre;

    /** BANCO | CAJA | NEQUI | DAVIPLATA | OTROS */
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(length = 200)
    private String banco;

    @Column(name = "numero_cuenta", length = 100)
    private String numeroCuenta;

    @Column(length = 300)
    private String titular;

    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(name = "saldo_actual", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal saldoActual = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

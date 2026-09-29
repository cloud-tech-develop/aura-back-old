package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Extracto bancario de un período (E9 · conciliación). Se importa del CSV del
 * banco, se concilia línea a línea contra el libro (movimientos de la cuenta
 * contable del banco) y se cierra cuando el saldo conciliado explica el saldo
 * final del extracto.
 */
@Entity
@Table(name = "extracto_bancario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractoBancarioEntity {

    public static final String ESTADO_ABIERTO = "ABIERTO";
    public static final String ESTADO_CONCILIADO = "CONCILIADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "cuenta_bancaria_id", nullable = false)
    private Long cuentaBancariaId;

    /** Período del extracto en formato 'yyyy-MM'. */
    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(name = "saldo_inicial", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(name = "saldo_final", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal saldoFinal = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String estado = ESTADO_ABIERTO;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "conciliado_at")
    private LocalDateTime conciliadoAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}

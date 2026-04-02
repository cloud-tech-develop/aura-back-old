package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tesoreria_movimiento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TesoreriaMovimientoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "cuenta_bancaria_id", nullable = false)
    private Long cuentaBancariaId;

    /** EGRESO | RECAUDO | TRANSFERENCIA_SALIDA | TRANSFERENCIA_ENTRADA */
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 500)
    private String concepto;

    @Column(length = 300)
    private String beneficiario;

    @Column(length = 200)
    private String referencia;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 100)
    private String categoria;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean conciliado = false;

    @Column(name = "fecha_conciliacion")
    private LocalDate fechaConciliacion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean anulado = false;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

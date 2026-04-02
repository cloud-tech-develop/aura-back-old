package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_cuenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlanCuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    /** ACTIVO | PASIVO | PATRIMONIO | INGRESO | GASTO | COSTO | ORDEN */
    @Column(nullable = false, length = 20)
    private String tipo;

    /** DEBITO | CREDITO */
    @Column(nullable = false, length = 10)
    private String naturaleza;

    @Column(nullable = false)
    private Short nivel;

    @Column(name = "padre_id")
    private Long padreId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = Boolean.TRUE;

    /** Si es true, acepta movimientos directos (hoja del árbol) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean auxiliar = Boolean.FALSE;

    /** Código de homologación DIAN para integración fiscal futura (nullable) */
    @Column(name = "codigo_dian", length = 20)
    private String codigoDian;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}

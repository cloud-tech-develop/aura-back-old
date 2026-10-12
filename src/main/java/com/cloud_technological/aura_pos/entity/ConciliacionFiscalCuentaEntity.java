package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/** Una cuenta del balance cargado en la conciliación fiscal (V202). */
@Entity
@Table(name = "conciliacion_fiscal_cuenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConciliacionFiscalCuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conciliacion_id", nullable = false)
    private Long conciliacionId;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(length = 200)
    private String nombre;

    /** Con el signo de la naturaleza de la clase. */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal saldo;

    /** Renglón del formato; null = sin clasificar. */
    @Column(length = 10)
    private String renglon;

    @Column(name = "renglon_manual", nullable = false)
    private Boolean renglonManual;
}

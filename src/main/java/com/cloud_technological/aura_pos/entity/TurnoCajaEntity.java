package com.cloud_technological.aura_pos.entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "turno_caja")
@Getter
@Setter
public class TurnoCajaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id")
    private CajaEntity caja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "base_inicial")
    private BigDecimal baseInicial;

    @Column(name = "total_efectivo_sistema")
    private BigDecimal totalEfectivoSistema;

    @Column(name = "total_efectivo_real")
    private BigDecimal totalEfectivoReal;

    /**
     * Lo que el cajero firmó al cerrar. NO se toca nunca: es la prueba de lo que
     * entregó ese día, y una cifra reescribible deja de probar nada.
     */
    private BigDecimal diferencia;

    /**
     * Copia de {@link #diferencia} tomada al registrar el primer ajuste
     * retroactivo, para que quede constancia de cuál era el cierre original
     * aunque alguien mire solo esta fila.
     */
    @Column(name = "diferencia_original")
    private BigDecimal diferenciaOriginal;

    /** El cierre original más los ajustes posteriores. */
    @Column(name = "diferencia_ajustada")
    private BigDecimal diferenciaAjustada;

    private String estado;
}

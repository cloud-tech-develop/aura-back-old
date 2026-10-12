package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Vacaciones registradas de una vinculación (V220): en tiempo (disfrute) o en dinero
 * (compensadas). Se pagan en la nómina periódica del período como novedad.
 */
@Entity
@Table(name = "nomina_vacacion")
@Getter
@Setter
public class NominaVacacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    /** TIEMPO | DINERO. */
    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Último día de disfrute (solo TIEMPO). */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** Día en que vuelve a trabajar (siguiente día hábil, solo TIEMPO). */
    @Column(name = "fecha_regreso")
    private LocalDate fechaRegreso;

    /** TIEMPO: días hábiles de disfrute; DINERO: días compensados. */
    @Column(name = "dias_habiles", nullable = false, precision = 6, scale = 2)
    private BigDecimal diasHabiles;

    @Column(name = "fecha_regreso_anticipado")
    private LocalDate fechaRegresoAnticipado;

    /** Hábiles que no disfrutó por el regreso anticipado; vuelven al saldo. */
    @Column(name = "dias_no_disfrutados", nullable = false, precision = 6, scale = 2)
    private BigDecimal diasNoDisfrutados = BigDecimal.ZERO;

    /** REGISTRADA | INTERRUMPIDA | ANULADA. */
    @Column(nullable = false, length = 12)
    private String estado = "REGISTRADA";

    /** Vacaciones colectivas registradas juntas. */
    @Column(length = 40)
    private String lote;

    @Column(length = 500)
    private String observacion;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

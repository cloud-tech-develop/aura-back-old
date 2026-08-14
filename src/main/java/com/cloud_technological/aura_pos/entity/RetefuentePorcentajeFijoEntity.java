package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Porcentaje fijo del procedimiento 2 de retefuente (V108).
 *
 * <p>Se calcula en <b>junio y diciembre</b> promediando los 12 meses anteriores,
 * y se aplica a cada mes del semestre siguiente.
 *
 * <p>Es una ruta de cálculo <b>distinta</b> al procedimiento 1, no un flag sobre
 * el mismo cálculo: el proc. 1 depura y busca rango cada mes; el proc. 2 aplica
 * un porcentaje ya fijado sobre el ingreso gravable del mes.
 */
@Getter
@Setter
@Entity
@Table(name = "retefuente_porcentaje_fijo")
public class RetefuentePorcentajeFijoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    /** '2026-1' | '2026-2' */
    @Column(name = "semestre", length = 7, nullable = false)
    private String semestre;

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "base_calculo", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseCalculo;

    /** Puede ser < 12 si el empleado lleva menos tiempo. */
    @Column(name = "meses_promediados", nullable = false)
    private Integer mesesPromediados = 12;

    @Column(name = "calculado_at", nullable = false)
    private LocalDateTime calculadoAt;

    /** Snapshot del cálculo del porcentaje. No se regenera. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "traza", columnDefinition = "jsonb")
    private String traza;

    @PrePersist
    void onCreate() {
        if (calculadoAt == null) calculadoAt = LocalDateTime.now();
    }
}

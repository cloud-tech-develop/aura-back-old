package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Rangos del fondo de solidaridad pensional (V107).
 *
 * <p>Aporte adicional escalonado que paga el empleado cuando su IBC supera
 * 4 SMMLV. Se divide en dos: solidaridad (1%) y subsistencia (0.2% a 1% según
 * el rango).
 *
 * <p>Es tabla y no constantes porque la escala cambia por ley, y porque hay que
 * poder reliquidar un período viejo con la escala de su año.
 *
 * <p><b>⚠️ Los valores semilla de V107 requieren validación con un contador
 * antes de liquidar.</b>
 */
@Getter
@Setter
@Entity
@Table(name = "fondo_solidaridad_rango")
public class FondoSolidaridadRangoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agno", nullable = false)
    private Integer agno;

    @Column(name = "smmlv_desde", nullable = false, precision = 6, scale = 2)
    private BigDecimal smmlvDesde;

    /** NULL = sin tope superior (último rango). */
    @Column(name = "smmlv_hasta", precision = 6, scale = 2)
    private BigDecimal smmlvHasta;

    @Column(name = "pct_solidaridad", nullable = false, precision = 5, scale = 3)
    private BigDecimal pctSolidaridad = BigDecimal.ZERO;

    @Column(name = "pct_subsistencia", nullable = false, precision = 5, scale = 3)
    private BigDecimal pctSubsistencia = BigDecimal.ZERO;

    /** Total a descontar en este rango. */
    public BigDecimal pctTotal() {
        return pctSolidaridad.add(pctSubsistencia);
    }
}

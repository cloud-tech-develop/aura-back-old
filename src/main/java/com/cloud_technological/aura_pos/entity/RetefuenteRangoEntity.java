package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Rango de la tabla de retención del art. 383 ET (V108).
 *
 * <p>Fórmula: {@code retencion_uvt = (base_uvt − uvt_resta) × tarifa/100 + uvt_suma}
 *
 * <p>Es tabla y no constantes porque la escala cambia por ley y hay que poder
 * reliquidar con la escala del año que corresponda.
 *
 * <p><b>⚠️ Los valores semilla de V108 requieren validación con un contador
 * antes de liquidar. Retefuente mal calculada es un problema con la DIAN.</b>
 */
@Getter
@Setter
@Entity
@Table(name = "retefuente_rango")
public class RetefuenteRangoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agno", nullable = false)
    private Integer agno;

    @Column(name = "uvt_desde", nullable = false, precision = 12, scale = 2)
    private BigDecimal uvtDesde;

    /** NULL = último rango, sin tope. */
    @Column(name = "uvt_hasta", precision = 12, scale = 2)
    private BigDecimal uvtHasta;

    @Column(name = "tarifa", nullable = false, precision = 5, scale = 2)
    private BigDecimal tarifa;

    @Column(name = "uvt_resta", nullable = false, precision = 12, scale = 2)
    private BigDecimal uvtResta = BigDecimal.ZERO;

    @Column(name = "uvt_suma", nullable = false, precision = 12, scale = 2)
    private BigDecimal uvtSuma = BigDecimal.ZERO;

    /**
     * Aplica la fórmula del rango a una base expresada en UVT.
     *
     * @return retención en UVT. Nunca negativa.
     */
    @Transient
    public BigDecimal calcularUvt(BigDecimal baseUvt) {
        if (baseUvt == null) return BigDecimal.ZERO;
        BigDecimal excedente = baseUvt.subtract(uvtResta);
        if (excedente.signum() <= 0) return uvtSuma;

        return excedente
                .multiply(tarifa)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .add(uvtSuma)
                .max(BigDecimal.ZERO);
    }
}

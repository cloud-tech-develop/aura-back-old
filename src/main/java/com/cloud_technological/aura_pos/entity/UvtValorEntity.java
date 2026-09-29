package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Valor del UVT por año (V108). Lo fija la DIAN cada diciembre.
 *
 * <p>Toda la retefuente se calcula en UVT y se convierte a pesos al final. Por
 * eso este valor es tabla y no constante: sin él no se puede reliquidar un
 * período viejo con el UVT de su año.
 *
 * <p><b>⚠️ El valor semilla de V108 (2026 = 49.799) requiere verificación
 * contra la resolución DIAN antes de liquidar.</b>
 */
@Getter
@Setter
@Entity
@Table(name = "uvt_valor")
public class UvtValorEntity {

    @Id
    @Column(name = "agno")
    private Integer agno;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;
}

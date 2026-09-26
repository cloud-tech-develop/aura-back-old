package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;

/**
 * Conversión de una cantidad escrita en una presentación a la unidad base del
 * inventario. Regla única desde V159: `factor_conversion` son las unidades base
 * que CONTIENE la presentación (Caja ×10 → 10), así que se multiplica.
 *
 * Las presentaciones creadas antes de V159 se guardaron al revés y la migración
 * les dejó 1/factor (la UNIDAD de una paca ×25 quedó en 0,04). Cuando ese
 * inverso no es exacto en 8 decimales (1/12 = 0,08333333) se reconoce el entero
 * de origen y se divide por él: vender 12 unidades descuenta 1 exacto, no
 * 0,99999996.
 */
public final class PresentacionConversion {

    public static final int SCALE_CANTIDAD = 6;

    private static final BigDecimal TOLERANCIA_RECIPROCO = new BigDecimal("0.0001");

    private PresentacionConversion() {
    }

    /** Sin presentación, o con factor inválido, la cantidad ya está en unidad base. */
    public static BigDecimal aBase(BigDecimal cantidad, ProductoPresentacionEntity presentacion) {
        return aBase(cantidad, presentacion != null ? presentacion.getFactorConversion() : null);
    }

    public static BigDecimal aBase(BigDecimal cantidad, BigDecimal factor) {
        if (cantidad == null || factor == null || factor.signum() <= 0) {
            return cantidad;
        }
        BigDecimal divisor = reciprocoEntero(factor);
        if (divisor != null) {
            return cantidad.divide(divisor, SCALE_CANTIDAD, RoundingMode.HALF_UP);
        }
        return cantidad.multiply(factor).setScale(SCALE_CANTIDAD, RoundingMode.HALF_UP);
    }

    /**
     * Para un factor menor que 1: N si el factor es 1/N (con tolerancia de
     * redondeo); si no, null. Es cuántas veces cabe la presentación en la base.
     */
    public static BigDecimal reciprocoEntero(BigDecimal factor) {
        if (factor == null || factor.signum() <= 0 || factor.compareTo(BigDecimal.ONE) >= 0) {
            return null;
        }
        BigDecimal reciproco = BigDecimal.ONE.divide(factor, 10, RoundingMode.HALF_UP);
        BigDecimal entero = reciproco.setScale(0, RoundingMode.HALF_UP);
        boolean esEntero = entero.signum() > 0
                && reciproco.subtract(entero).abs().compareTo(TOLERANCIA_RECIPROCO) <= 0;
        return esEntero ? entero : null;
    }
}

package com.cloud_technological.aura_pos.contabilidad.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;

/**
 * Invariantes del asiento contable. Regla inviolable: Σ débitos = Σ créditos,
 * sin negativos, mínimo dos partidas. Todo redondeo monetario del módulo usa
 * la escala y el modo definidos aquí.
 */
public final class ReglasAsiento {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private ReglasAsiento() {
    }

    /** Nulos monetarios a cero. */
    public static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Nulos a cero y escala 2 HALF_UP. */
    public static BigDecimal normalizar(BigDecimal v) {
        return nz(v).setScale(ESCALA, REDONDEO);
    }

    /**
     * Valida las invariantes sobre las partidas y devuelve los totales
     * [débito, crédito]. Lanza {@link AsientoDescuadradoException} si fallan.
     */
    public static BigDecimal[] validar(List<Partida> partidas) {
        if (partidas == null || partidas.size() < 2) {
            throw new AsientoDescuadradoException(
                    "Un asiento requiere al menos dos partidas (débito y crédito)");
        }

        BigDecimal totalDebito = BigDecimal.ZERO;
        BigDecimal totalCredito = BigDecimal.ZERO;
        for (Partida p : partidas) {
            totalDebito = totalDebito.add(p.debito());
            totalCredito = totalCredito.add(p.credito());
        }

        if (totalDebito.compareTo(totalCredito) != 0) {
            throw new AsientoDescuadradoException(
                    "El asiento no está cuadrado: débito=" + totalDebito
                            + " crédito=" + totalCredito);
        }
        if (totalDebito.signum() == 0) {
            throw new AsientoDescuadradoException(
                    "El asiento no tiene movimientos (débito y crédito en cero)");
        }
        return new BigDecimal[] { totalDebito, totalCredito };
    }
}

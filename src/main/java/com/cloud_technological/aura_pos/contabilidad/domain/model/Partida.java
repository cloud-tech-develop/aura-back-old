package com.cloud_technological.aura_pos.contabilidad.domain.model;

import java.math.BigDecimal;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;

/**
 * Línea de un asiento: imputa un valor a una cuenta, por un solo lado
 * (débito o crédito), opcionalmente con tercero, centro de costo y las
 * dimensiones proyecto/frente (E7). Inmutable; los montos quedan
 * normalizados a escala 2 HALF_UP.
 */
public record Partida(
        Long cuentaId,
        String descripcion,
        BigDecimal debito,
        BigDecimal credito,
        Long terceroId,
        Long centroCostoId,
        Long proyectoId,
        Long frenteId) {

    /** Sin dimensiones proyecto/frente. */
    public Partida(Long cuentaId, String descripcion, BigDecimal debito,
            BigDecimal credito, Long terceroId, Long centroCostoId) {
        this(cuentaId, descripcion, debito, credito, terceroId, centroCostoId, null, null);
    }

    public Partida {
        if (cuentaId == null) {
            throw new IllegalArgumentException("Toda partida debe imputarse a una cuenta");
        }
        debito = ReglasAsiento.normalizar(debito);
        credito = ReglasAsiento.normalizar(credito);
        if (debito.signum() < 0 || credito.signum() < 0) {
            throw new IllegalArgumentException(
                    "Una partida no admite valores negativos: débito=" + debito + " crédito=" + credito);
        }
        if (debito.signum() > 0 && credito.signum() > 0) {
            throw new IllegalArgumentException(
                    "Una partida es débito O crédito, no ambos: débito=" + debito + " crédito=" + credito);
        }
        if (debito.signum() == 0 && credito.signum() == 0) {
            throw new IllegalArgumentException("Una partida no puede ir en ceros");
        }
    }
}

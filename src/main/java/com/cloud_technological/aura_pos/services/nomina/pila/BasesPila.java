package com.cloud_technological.aura_pos.services.nomina.pila;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;

/**
 * IBC y días por subsistema (Fase 6).
 *
 * <h2>No es una base por cuatro tarifas</h2>
 * Cada subsistema tiene su propio IBC y su propio conteo de días, porque las
 * novedades los afectan distinto:
 *
 * <table>
 *   <tr><th>Novedad</th><th>Pensión</th><th>Salud</th><th>ARL</th><th>CCF</th></tr>
 *   <tr><td>Licencia no remunerada (SLN)</td>
 *       <td>NO cotiza</td><td>SÍ cotiza (mínimo 1 SMMLV)</td><td>NO</td><td>NO</td></tr>
 *   <tr><td>Incapacidad general (IGE)</td>
 *       <td>SÍ, sobre el subsidio</td><td>SÍ</td><td>SÍ, sobre el salario</td><td>NO</td></tr>
 *   <tr><td>Licencia de maternidad (LMA)</td>
 *       <td>SÍ</td><td>SÍ</td><td>SÍ</td><td>SÍ</td></tr>
 *   <tr><td>Vacaciones (VAC)</td>
 *       <td>SÍ</td><td>SÍ</td><td>SÍ</td><td>SÍ</td></tr>
 * </table>
 *
 * <p><b>⚠️ Esta tabla es la interpretación del formato UGPP y NO está validada
 * con un contador.</b> Los casos de incapacidad y licencia no remunerada son
 * los que más varían según la interpretación. Antes de presentar una planilla
 * real hay que verificarlos.
 */
@Getter
public class BasesPila {

    private final BigDecimal ibcPension;
    private final BigDecimal ibcSalud;
    private final BigDecimal ibcArl;
    private final BigDecimal ibcCcf;

    private final int diasPension;
    private final int diasSalud;
    private final int diasArl;
    private final int diasCcf;

    private BasesPila(Builder b) {
        this.ibcPension = b.ibcPension;
        this.ibcSalud   = b.ibcSalud;
        this.ibcArl     = b.ibcArl;
        this.ibcCcf     = b.ibcCcf;
        this.diasPension = b.diasPension;
        this.diasSalud   = b.diasSalud;
        this.diasArl     = b.diasArl;
        this.diasCcf     = b.diasCcf;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BigDecimal ibcBase = BigDecimal.ZERO;   // el IBC ya topado del motor
        private BigDecimal smmlv = BigDecimal.ZERO;
        private int diasPeriodo = 30;
        private NovedadesPila novedades = new NovedadesPila();

        private BigDecimal ibcPension = BigDecimal.ZERO;
        private BigDecimal ibcSalud = BigDecimal.ZERO;
        private BigDecimal ibcArl = BigDecimal.ZERO;
        private BigDecimal ibcCcf = BigDecimal.ZERO;
        private int diasPension, diasSalud, diasArl, diasCcf;

        public Builder ibcBase(BigDecimal v)      { this.ibcBase = nz(v); return this; }
        public Builder smmlv(BigDecimal v)        { this.smmlv = nz(v); return this; }
        public Builder diasPeriodo(int v)         { this.diasPeriodo = v; return this; }
        public Builder novedades(NovedadesPila n) { this.novedades = n; return this; }

        public BasesPila build() {
            // Punto de partida: todos los subsistemas con los días del período.
            diasPension = diasSalud = diasArl = diasCcf = diasPeriodo;
            ibcPension = ibcSalud = ibcArl = ibcCcf = ibcBase;

            // ── SLN: licencia no remunerada / suspensión ────────────────────
            // No cotiza pensión, ARL ni CCF. Salud SÍ, y sobre un mínimo de
            // 1 SMMLV — el empleador responde por ella.
            if (novedades.isSln() && novedades.getDiasSln() > 0) {
                int d = Math.min(novedades.getDiasSln(), diasPeriodo);
                diasPension -= d;
                diasArl     -= d;
                diasCcf     -= d;
                // Salud conserva los días, pero con piso de 1 SMMLV proporcional.
                BigDecimal minimoSalud = proporcional(smmlv, diasSalud, diasPeriodo);
                ibcSalud = ibcSalud.max(minimoSalud);
            }

            // ── IGE: incapacidad general ───────────────────────────────────
            // Cotiza los tres, pero el IBC de pensión y salud va sobre el
            // subsidio (2/3 del salario), no sobre el salario pleno.
            // ⚠️ Interpretación: verificar con contador.
            if (novedades.isIge() && novedades.getDiasIge() > 0) {
                // ARL mantiene el IBC pleno; pensión y salud no se reducen aquí
                // porque el subsidio ya viene reflejado en las novedades que el
                // motor sumó al IBC. Reducirlo otra vez sería doble descuento.
                // Los días se mantienen: la incapacidad SÍ cotiza.
            }

            // ── SLN reduce el IBC proporcionalmente en los que no cotizan ──
            if (novedades.isSln() && novedades.getDiasSln() > 0) {
                ibcPension = proporcional(ibcBase, diasPension, diasPeriodo);
                ibcArl     = proporcional(ibcBase, diasArl, diasPeriodo);
                ibcCcf     = proporcional(ibcBase, diasCcf, diasPeriodo);
            }

            // Nadie puede quedar con días negativos.
            diasPension = Math.max(diasPension, 0);
            diasSalud   = Math.max(diasSalud, 0);
            diasArl     = Math.max(diasArl, 0);
            diasCcf     = Math.max(diasCcf, 0);

            // Piso legal: el IBC de pensión y salud no puede ser menor a
            // 1 SMMLV proporcional a los días cotizados.
            if (diasPension > 0) {
                ibcPension = ibcPension.max(proporcional(smmlv, diasPension, diasPeriodo));
            }
            if (diasSalud > 0) {
                ibcSalud = ibcSalud.max(proporcional(smmlv, diasSalud, diasPeriodo));
            }

            return new BasesPila(this);
        }

        private static BigDecimal proporcional(BigDecimal valor, int dias, int diasPeriodo) {
            if (diasPeriodo <= 0) return BigDecimal.ZERO;
            return valor.multiply(BigDecimal.valueOf(dias))
                        .divide(BigDecimal.valueOf(diasPeriodo), 2, RoundingMode.HALF_UP);
        }

        private static BigDecimal nz(BigDecimal v) {
            return v != null ? v : BigDecimal.ZERO;
        }
    }
}

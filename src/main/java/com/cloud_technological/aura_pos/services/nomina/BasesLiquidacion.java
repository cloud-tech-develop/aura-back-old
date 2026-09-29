package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;
import lombok.ToString;

/**
 * Las bases de una liquidación (Fase 0).
 *
 * <h2>Son TRES bases distintas, y confundirlas es el bug que se corrige aquí</h2>
 *
 * <table>
 *   <tr><th>Base</th><th>Qué incluye</th><th>Para qué</th></tr>
 *   <tr>
 *     <td>{@link #totalDevengado}</td>
 *     <td>salario + auxilio + TODAS las novedades devengadas</td>
 *     <td>Lo que se le paga al empleado</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #baseIbc}</td>
 *     <td>salario + novedades salariales. <b>SIN auxilio de transporte.</b>
 *         Con tope de 25 SMMLV y factor 70% si es salario integral.</td>
 *     <td>Seguridad social: salud, pensión, ARL, parafiscales</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #basePrestacional}</td>
 *     <td>salario + auxilio de transporte</td>
 *     <td>Prima y cesantías</td>
 *   </tr>
 * </table>
 *
 * <h2>El bug que esto arregla (B1)</h2>
 * Antes {@code NominaServiceImpl:515} hacía:
 * <pre>
 *   totalDevengado = salarioProporcional + auxilio + novedadesDevengadas;
 *   deduccionSalud = porcentaje(totalDevengado, pct);   // ← MAL
 * </pre>
 * El auxilio de transporte <b>no es base de seguridad social</b>. Se le
 * descontaba de más al empleado y se le cobraba de más al empleador, cada mes.
 *
 * <p>Curiosamente el criterio ya estaba en el mismo archivo: las provisiones
 * usaban {@code baseConAuxilio} para prima/cesantías y {@code salarioProporcional}
 * para vacaciones. Solo no se aplicó al IBC.
 */
@Getter
@ToString
public class BasesLiquidacion {

    private final BigDecimal salarioProporcional;
    private final BigDecimal auxilioTransporte;
    private final BigDecimal novedadesIbc;      // novedades con constituye_ibc = true
    private final BigDecimal novedadesNoIbc;    // bonos no salariales, etc.
    private final BigDecimal novedadesDeducciones;

    /** Lo que se le paga. Incluye auxilio y todas las novedades devengadas. */
    private final BigDecimal totalDevengado;

    /** Base de seguridad social. SIN auxilio. Con tope e integral aplicados. */
    private final BigDecimal baseIbc;

    /** IBC antes del tope: para la traza, y para saber si el tope mordió. */
    private final BigDecimal baseIbcSinTope;

    /** Prima y cesantías: salario + auxilio. */
    private final BigDecimal basePrestacional;

    private final boolean topeAplicado;
    private final boolean salarioIntegral;

    private BasesLiquidacion(Builder b) {
        this.salarioProporcional   = b.salarioProporcional;
        this.auxilioTransporte     = b.auxilioTransporte;
        this.novedadesIbc          = b.novedadesIbc;
        this.novedadesNoIbc        = b.novedadesNoIbc;
        this.novedadesDeducciones  = b.novedadesDeducciones;
        this.totalDevengado        = b.totalDevengado;
        this.baseIbc               = b.baseIbc;
        this.baseIbcSinTope        = b.baseIbcSinTope;
        this.basePrestacional      = b.basePrestacional;
        this.topeAplicado          = b.topeAplicado;
        this.salarioIntegral       = b.salarioIntegral;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Construye las tres bases en el orden legal correcto.
     *
     * <ol>
     *   <li>baseIbc = salario + novedades salariales (SIN auxilio)</li>
     *   <li>Si es salario integral: baseIbc × factor (70%)</li>
     *   <li>Tope: MIN(baseIbc, topeSmmlv × smmlv)</li>
     * </ol>
     *
     * El orden importa: el factor del 70% va ANTES del tope, no después.
     */
    public static class Builder {
        private BigDecimal salarioProporcional  = BigDecimal.ZERO;
        /**
         * B-05 — salario que hace base de IBC. Puede diferir de
         * {@link #salarioProporcional}: durante incapacidad, maternidad y
         * vacaciones el salario PAGADO baja pero la cotización a pensión/salud se
         * mantiene sobre el salario pleno. Solo la licencia no remunerada y la
         * suspensión lo reducen. Si no se fija, iguala a salarioProporcional.
         */
        private BigDecimal salarioParaIbc       = null;
        private BigDecimal auxilioTransporte    = BigDecimal.ZERO;
        private BigDecimal novedadesIbc         = BigDecimal.ZERO;
        private BigDecimal novedadesNoIbc       = BigDecimal.ZERO;
        private BigDecimal novedadesDeducciones = BigDecimal.ZERO;
        private BigDecimal smmlv                = BigDecimal.ZERO;
        private BigDecimal topeIbcSmmlv         = new BigDecimal("25");
        private BigDecimal factorIntegral       = new BigDecimal("70");
        private boolean salarioIntegral         = false;

        private BigDecimal totalDevengado;
        private BigDecimal baseIbc;
        private BigDecimal baseIbcSinTope;
        private BigDecimal basePrestacional;
        private boolean topeAplicado;

        public Builder salarioProporcional(BigDecimal v)  { this.salarioProporcional = nz(v); return this; }
        public Builder salarioParaIbc(BigDecimal v)       { this.salarioParaIbc = v; return this; }
        public Builder auxilioTransporte(BigDecimal v)    { this.auxilioTransporte = nz(v); return this; }
        public Builder novedadesIbc(BigDecimal v)         { this.novedadesIbc = nz(v); return this; }
        public Builder novedadesNoIbc(BigDecimal v)       { this.novedadesNoIbc = nz(v); return this; }
        public Builder novedadesDeducciones(BigDecimal v) { this.novedadesDeducciones = nz(v); return this; }
        public Builder smmlv(BigDecimal v)                { this.smmlv = nz(v); return this; }
        public Builder topeIbcSmmlv(BigDecimal v)         { this.topeIbcSmmlv = nz(v); return this; }
        public Builder factorIntegral(BigDecimal v)       { this.factorIntegral = nz(v); return this; }
        public Builder salarioIntegral(boolean v)         { this.salarioIntegral = v; return this; }

        public BasesLiquidacion build() {
            // Lo que se le paga: todo.
            totalDevengado = salarioProporcional
                    .add(auxilioTransporte)
                    .add(novedadesIbc)
                    .add(novedadesNoIbc);

            // Prima y cesantías.
            basePrestacional = salarioProporcional.add(auxilioTransporte);

            // ── IBC — aquí está la corrección ───────────────────────────────
            // SIN auxilio de transporte. Solo lo salarial. El salario de IBC puede
            // ser mayor que el pagado (B-05): incapacidad/maternidad/vacaciones
            // mantienen la cotización sobre el salario pleno.
            BigDecimal salarioIbc = salarioParaIbc != null ? salarioParaIbc : salarioProporcional;
            BigDecimal ibc = salarioIbc.add(novedadesIbc);

            // Salario integral: el IBC es el 70% (el 30% es factor prestacional).
            // Va ANTES del tope.
            if (salarioIntegral) {
                ibc = ibc.multiply(factorIntegral)
                         .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }
            baseIbcSinTope = ibc;

            // Tope de 25 SMMLV.
            if (smmlv.signum() > 0) {
                BigDecimal tope = smmlv.multiply(topeIbcSmmlv);
                if (ibc.compareTo(tope) > 0) {
                    ibc = tope;
                    topeAplicado = true;
                }
            }
            baseIbc = ibc;

            return new BasesLiquidacion(this);
        }

        private static BigDecimal nz(BigDecimal v) {
            return v != null ? v : BigDecimal.ZERO;
        }
    }

    /** IBC expresado en SMMLV. Para ubicar el rango del fondo de solidaridad. */
    public BigDecimal ibcEnSmmlv(BigDecimal smmlv) {
        if (smmlv == null || smmlv.signum() == 0) return BigDecimal.ZERO;
        return baseIbc.divide(smmlv, 4, RoundingMode.HALF_UP);
    }
}

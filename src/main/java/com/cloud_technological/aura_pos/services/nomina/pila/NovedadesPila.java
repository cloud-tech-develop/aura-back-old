package com.cloud_technological.aura_pos.services.nomina.pila;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Las banderas de novedad de un cotizante en un período, ya derivadas.
 *
 * <h2>Son DERIVADAS, no capturadas</h2>
 * Nadie las digita. Se calculan cruzando contrato + historial salarial +
 * afiliaciones + novedades contra el período:
 *
 * <table>
 *   <tr><th>Bandera</th><th>De dónde sale</th><th>Requiere</th></tr>
 *   <tr><td>{@code ing}</td><td>fecha_inicio del contrato dentro del período</td><td>Fase 2</td></tr>
 *   <tr><td>{@code ret}</td><td>fecha_fin del contrato dentro del período</td><td>Fase 2</td></tr>
 *   <tr><td>{@code vsp}</td><td>cambio en contrato_salario_historial</td><td><b>Fase 2</b></td></tr>
 *   <tr><td>{@code tde}/{@code tae}</td><td>cambio de EPS en contrato_afiliacion</td><td><b>Fase 5.5</b></td></tr>
 *   <tr><td>{@code tdp}/{@code tap}</td><td>cambio de AFP</td><td><b>Fase 5.5</b></td></tr>
 *   <tr><td>{@code sln}/{@code ige}/{@code lma}/{@code vac_lr}</td><td>novedades con sus pares de fechas</td><td>—</td></tr>
 * </table>
 *
 * <p>Por eso PILA depende de las fases anteriores: sin histórico salarial no
 * hay {@code vsp}; sin historial de afiliación no hay traslados.
 */
@Getter
@Setter
public class NovedadesPila {

    // ── Ingreso / retiro ────────────────────────────────────────────────────
    private boolean ing;
    private LocalDate fechaIngreso;
    private boolean ret;
    private LocalDate fechaRetiro;

    // ── Traslados ───────────────────────────────────────────────────────────
    /** Traslado DESDE otra EPS (la anterior). */
    private boolean tde;
    private String codEpsAnterior;
    /** Traslado A esta EPS. */
    private boolean tae;

    private boolean tdp;
    private String codAfpAnterior;
    private boolean tap;

    private boolean tdl;   // ARL
    private boolean tal;
    private boolean tdc;   // CCF
    private boolean tac;
    private boolean tie;   // traslado inicio empleo

    // ── Variaciones de salario ──────────────────────────────────────────────
    /** Variación permanente. Sale del histórico salarial. */
    private boolean vsp;
    private LocalDate fechaInicioVsp;

    /** Variación transitoria (p. ej. horas extra que suben el IBC un mes). */
    private boolean vst;
    private LocalDate fechaInicioVst;
    private LocalDate fechaFinVst;

    // ── Ausentismos ─────────────────────────────────────────────────────────
    /** Suspensión / licencia no remunerada. Cotiza salud pero NO pensión. */
    private boolean sln;
    private LocalDate fechaInicioSln;
    private LocalDate fechaFinSln;

    /** Incapacidad general. */
    private boolean ige;
    private LocalDate fechaInicioIge;
    private LocalDate fechaFinIge;
    private String noAutorizacionIge;

    /** Licencia de maternidad. */
    private boolean lma;
    private LocalDate fechaInicioLma;
    private LocalDate fechaFinLma;
    private String noAutorizacionLma;

    /** Vacaciones o licencia remunerada. */
    private boolean vacLr;
    private LocalDate fechaInicioVacLr;
    private LocalDate fechaFinVacLr;

    /** Incapacidad por riesgo laboral. */
    private boolean irl;
    private LocalDate fechaInicioIrl;
    private LocalDate fechaFinIrl;

    // ── Otras ───────────────────────────────────────────────────────────────
    private boolean avp;          // aporte voluntario a pensión
    private boolean vct;          // variación de centro de trabajo
    private LocalDate fechaInicioVct;
    private LocalDate fechaFinVct;
    private boolean correccion;

    // ── Días de ausentismo, para el conteo por subsistema ───────────────────
    private int diasSln;
    private int diasIge;
    private int diasLma;
    private int diasVacLr;
    private int diasIrl;

    /** Total de días no laborados por ausentismo en el período. */
    public int diasAusentismo() {
        return diasSln + diasIge + diasLma + diasVacLr + diasIrl;
    }
}

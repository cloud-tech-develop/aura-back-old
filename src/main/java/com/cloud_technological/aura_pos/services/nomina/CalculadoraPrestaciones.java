package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Prestaciones sociales y liquidación definitiva (Fase 8).
 *
 * <h2>La base prestacional NO es el salario</h2>
 * Son <b>tres bases distintas</b> y confundirlas es el error clásico:
 * <ul>
 *   <li><b>IBC</b> (Fase 0): salario + novedades salariales, SIN auxilio. Para
 *       seguridad social.</li>
 *   <li><b>Base de retefuente</b> (Fase 4.5): ingreso depurado. Para retención.</li>
 *   <li><b>Base prestacional</b> (esta): salario + auxilio de transporte +
 *       promedio de lo salarial variable. Para prima y cesantías.</li>
 * </ul>
 *
 * <p>Y dentro de esta fase hay otro matiz: <b>vacaciones se liquidan solo sobre
 * salario</b> (sin auxilio), mientras prima y cesantías sí lo incluyen.
 *
 * <h2>⚠️ Sin validar con contador</h2>
 * Las fórmulas están tomadas del CST, pero los bordes (¿360 o 365 días?, ¿el
 * día del retiro cuenta?) necesitan verificación.
 */
@Slf4j
@Component
public class CalculadoraPrestaciones {

    /** El año laboral son 360 días, no 365. Es la convención del CST. */
    private static final BigDecimal DIAS_ANIO = new BigDecimal("360");
    private static final BigDecimal DIAS_SEMESTRE = new BigDecimal("180");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal PCT_INTERESES_CESANTIAS = new BigDecimal("12");
    /** 15 días hábiles por año trabajado. */
    private static final BigDecimal DIAS_VACACIONES_ANIO = new BigDecimal("15");

    // ── Cesantías ───────────────────────────────────────────────────────────

    /**
     * Cesantías: base × días trabajados ÷ 360.
     *
     * <p>Se consignan al fondo antes del 14 de febrero del año siguiente.
     */
    public Resultado cesantias(BigDecimal basePrestacional, int diasTrabajados) {
        BigDecimal valor = basePrestacional
                .multiply(BigDecimal.valueOf(diasTrabajados))
                .divide(DIAS_ANIO, 2, RoundingMode.HALF_UP);

        return new Resultado(valor, diasTrabajados, basePrestacional,
                Traza.vacia()
                        .paso("Base prestacional (salario + auxilio)", basePrestacional)
                        .paso("Días trabajados", BigDecimal.valueOf(diasTrabajados))
                        .paso("Fórmula", "base × días ÷ 360")
                        .paso("Cesantías", valor));
    }

    /**
     * Intereses sobre cesantías: 12% anual sobre el saldo.
     *
     * <p>Se pagan <b>al empleado</b>, no al fondo, antes del 31 de enero.
     * Proporcional a los días si no fue el año completo.
     */
    public Resultado interesesCesantias(BigDecimal cesantias, int diasTrabajados) {
        BigDecimal valor = cesantias
                .multiply(PCT_INTERESES_CESANTIAS).divide(CIEN, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(diasTrabajados))
                .divide(DIAS_ANIO, 2, RoundingMode.HALF_UP);

        return new Resultado(valor, diasTrabajados, cesantias,
                Traza.vacia()
                        .paso("Cesantías", cesantias)
                        .paso("Tarifa", "12 % anual")
                        .paso("Días trabajados", BigDecimal.valueOf(diasTrabajados))
                        .paso("Fórmula", "cesantías × 12% × días ÷ 360")
                        .paso("Intereses", valor));
    }

    // ── Prima de servicios ──────────────────────────────────────────────────

    /**
     * Prima: base × días del semestre ÷ 360.
     *
     * <p>Dos períodos: ene-jun (paga 30 jun) y jul-dic (paga 20 dic).
     *
     * <p>Nota: el divisor es 360 aunque sea semestral — los 180 días del
     * semestre completo dan medio mes de salario, que es lo correcto.
     */
    public Resultado prima(BigDecimal basePrestacional, int diasSemestre) {
        BigDecimal valor = basePrestacional
                .multiply(BigDecimal.valueOf(diasSemestre))
                .divide(DIAS_ANIO, 2, RoundingMode.HALF_UP);

        return new Resultado(valor, diasSemestre, basePrestacional,
                Traza.vacia()
                        .paso("Base prestacional (salario + auxilio)", basePrestacional)
                        .paso("Días del semestre", BigDecimal.valueOf(diasSemestre))
                        .paso("Fórmula", "base × días ÷ 360")
                        .paso("Prima", valor));
    }

    // ── Vacaciones ──────────────────────────────────────────────────────────

    /**
     * Vacaciones: 15 días hábiles por año.
     *
     * <p><b>La base es SOLO el salario, sin auxilio de transporte</b> — a
     * diferencia de prima y cesantías. El auxilio compensa el desplazamiento al
     * trabajo; en vacaciones no hay desplazamiento.
     *
     * <p>Fórmula: salario × días trabajados ÷ 720 (equivale a × 15 ÷ 360 ÷ 2).
     */
    public Resultado vacaciones(BigDecimal salarioSinAuxilio, int diasTrabajados) {
        BigDecimal diasVacaciones = BigDecimal.valueOf(diasTrabajados)
                .multiply(DIAS_VACACIONES_ANIO)
                .divide(DIAS_ANIO, 4, RoundingMode.HALF_UP);

        BigDecimal valor = salarioSinAuxilio
                .multiply(diasVacaciones)
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        return new Resultado(valor, diasVacaciones.setScale(0, RoundingMode.HALF_UP).intValue(),
                salarioSinAuxilio,
                Traza.vacia()
                        .paso("Salario (SIN auxilio de transporte)", salarioSinAuxilio)
                        .paso("Días trabajados", BigDecimal.valueOf(diasTrabajados))
                        .paso("Días de vacaciones causados", diasVacaciones.setScale(2, RoundingMode.HALF_UP))
                        .paso("Fórmula", "salario × días vacaciones ÷ 30")
                        .paso("Vacaciones", valor));
    }

    // ── Indemnización por despido sin justa causa ───────────────────────────

    /**
     * Indemnización (CST art. 64).
     *
     * <p>Depende del tipo de contrato y del salario:
     * <ul>
     *   <li><b>INDEFINIDO, salario &lt; 10 SMMLV:</b> 30 días por el primer año
     *       + 20 días por cada año adicional (proporcional por fracción).</li>
     *   <li><b>INDEFINIDO, salario ≥ 10 SMMLV:</b> 20 días por el primer año
     *       + 15 por cada adicional.</li>
     *   <li><b>FIJO:</b> el tiempo que falte para terminar el plazo.</li>
     *   <li><b>OBRA_LABOR:</b> lo que falte para terminar la obra, <b>mínimo
     *       15 días</b>.</li>
     * </ul>
     *
     * <p><b>La indemnización NO es base de seguridad social ni de prestaciones.</b>
     * Sí puede estar sujeta a retefuente (Fase 4.5).
     */
    public Resultado indemnizacion(ContratoLaboralEntity contrato,
                                   BigDecimal salario,
                                   BigDecimal smmlv,
                                   LocalDate fechaRetiro) {
        if (!"SIN_JUSTA_CAUSA".equals(contrato.getCausaRetiro())) {
            return Resultado.cero("No aplica: causa de retiro = " + contrato.getCausaRetiro());
        }

        int diasTrabajados = (int) ChronoUnit.DAYS.between(contrato.getFechaInicio(), fechaRetiro);
        BigDecimal salarioDiario = salario.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        return switch (contrato.getTipoContrato()) {
            case "INDEFINIDO" -> indemnizacionIndefinido(salario, smmlv, salarioDiario, diasTrabajados);
            case "FIJO"       -> indemnizacionFijo(contrato, salarioDiario, fechaRetiro);
            case "OBRA_LABOR" -> indemnizacionObraLabor(salarioDiario);
            default -> Resultado.cero("Tipo de contrato sin regla de indemnización: "
                    + contrato.getTipoContrato());
        };
    }

    private Resultado indemnizacionIndefinido(BigDecimal salario, BigDecimal smmlv,
                                              BigDecimal salarioDiario, int diasTrabajados) {
        BigDecimal diezSmmlv = smmlv.multiply(BigDecimal.valueOf(10));
        boolean salarioAlto = salario.compareTo(diezSmmlv) >= 0;

        int diasPrimerAnio    = salarioAlto ? 20 : 30;
        int diasAnioAdicional = salarioAlto ? 15 : 20;

        BigDecimal aniosAdicionales = BigDecimal.valueOf(Math.max(0, diasTrabajados - 360))
                .divide(DIAS_ANIO, 4, RoundingMode.HALF_UP);

        BigDecimal diasTotal = BigDecimal.valueOf(diasPrimerAnio)
                .add(aniosAdicionales.multiply(BigDecimal.valueOf(diasAnioAdicional)));

        BigDecimal valor = salarioDiario.multiply(diasTotal).setScale(2, RoundingMode.HALF_UP);

        return new Resultado(valor, diasTotal.intValue(), salario,
                Traza.vacia()
                        .paso("Tipo de contrato", "INDEFINIDO")
                        .paso("Salario", salario)
                        .paso("¿≥ 10 SMMLV?", salarioAlto ? "SÍ" : "NO")
                        .paso("Días por el primer año", BigDecimal.valueOf(diasPrimerAnio))
                        .paso("Años adicionales", aniosAdicionales.setScale(2, RoundingMode.HALF_UP))
                        .paso("Días por año adicional", BigDecimal.valueOf(diasAnioAdicional))
                        .paso("Días totales", diasTotal.setScale(2, RoundingMode.HALF_UP))
                        .paso("Indemnización", valor));
    }

    private Resultado indemnizacionFijo(ContratoLaboralEntity c, BigDecimal salarioDiario, LocalDate fechaRetiro) {
        if (c.getFechaFin() == null) {
            log.warn("Contrato {} es FIJO pero no tiene fecha de fin. No se calcula indemnización.", c.getId());
            return Resultado.cero("Contrato FIJO sin fecha de fin");
        }
        long diasFaltantes = ChronoUnit.DAYS.between(fechaRetiro, c.getFechaFin());
        if (diasFaltantes <= 0) {
            return Resultado.cero("El contrato ya había vencido");
        }
        BigDecimal valor = salarioDiario.multiply(BigDecimal.valueOf(diasFaltantes))
                .setScale(2, RoundingMode.HALF_UP);

        return new Resultado(valor, (int) diasFaltantes, salarioDiario,
                Traza.vacia()
                        .paso("Tipo de contrato", "FIJO")
                        .paso("Fecha de terminación pactada", c.getFechaFin().toString())
                        .paso("Días faltantes", BigDecimal.valueOf(diasFaltantes))
                        .paso("Indemnización", valor));
    }

    private Resultado indemnizacionObraLabor(BigDecimal salarioDiario) {
        // El tiempo que falte para terminar la obra, con mínimo 15 días. No hay
        // forma de saber cuánto falta sin un modelo de avance de obra, así que
        // se aplica el mínimo legal y se avisa.
        BigDecimal valor = salarioDiario.multiply(BigDecimal.valueOf(15)).setScale(2, RoundingMode.HALF_UP);
        log.warn("Indemnización OBRA_LABOR: se aplica el mínimo legal de 15 días. "
                + "Si falta más tiempo para terminar la obra, ajustar manualmente.");

        return new Resultado(valor, 15, salarioDiario,
                Traza.vacia()
                        .paso("Tipo de contrato", "OBRA_LABOR")
                        .paso("Días", "15 (mínimo legal — verificar tiempo faltante de obra)")
                        .paso("Indemnización", valor));
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Días entre dos fechas en convención comercial (360 días/año, 30/mes).
     *
     * <p>No es {@code ChronoUnit.DAYS}: el CST cuenta meses de 30 días. Febrero
     * cuenta 30, y el 31 no existe.
     */
    public int diasComerciales(LocalDate desde, LocalDate hasta) {
        int d1 = Math.min(desde.getDayOfMonth(), 30);
        int d2 = Math.min(hasta.getDayOfMonth(), 30);
        return (hasta.getYear() - desde.getYear()) * 360
             + (hasta.getMonthValue() - desde.getMonthValue()) * 30
             + (d2 - d1) + 1;   // +1: el día de inicio cuenta
    }

    /** Resultado de un cálculo, con su traza. */
    public record Resultado(BigDecimal valor, int dias, BigDecimal base, Traza traza) {
        static Resultado cero(String motivo) {
            return new Resultado(BigDecimal.ZERO, 0, BigDecimal.ZERO,
                    Traza.vacia().paso("Sin valor", motivo));
        }
        public boolean hayValor() {
            return valor != null && valor.signum() > 0;
        }
    }
}

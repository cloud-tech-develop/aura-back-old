package com.cloud_technological.aura_pos.services.nomina.retiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaRetiroEntity;
import com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.LiquidacionPrestacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_retiros.NominaRetiroJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_retiros.NominaRetiroQueryRepository;
import com.cloud_technological.aura_pos.services.implementations.ConceptoNominaService;
import com.cloud_technological.aura_pos.services.nomina.FormulasPrestaciones;
import com.cloud_technological.aura_pos.services.nomina.LineaLiquidacion;
import com.cloud_technological.aura_pos.services.nomina.PromedioSalarialService;
import com.cloud_technological.aura_pos.services.nomina.Traza;
import com.cloud_technological.aura_pos.services.nomina.VacacionesService;
import com.cloud_technological.aura_pos.services.nomina.vinculacion.ReglasAprendiz;

/**
 * Lo propio de la liquidación definitiva de un retiro (rediseño R5): cesantías,
 * intereses y prima desde el día siguiente al último pago de cada una (o el
 * ingreso), con el promedio variable en la base; las vacaciones pendientes según el
 * saldo de días a la fecha de retiro (R7); y la indemnización si el motivo la genera. El salario de los últimos días lo liquida el motor como en
 * cualquier nómina.
 *
 * <p>Mismas fórmulas que el módulo de Prestaciones ({@link FormulasPrestaciones}).
 */
@Component
public class LiquidadorDefinitiva {

    /** Prestación (tipo del módulo de Prestaciones), concepto de nómina con el que se paga y su nombre. */
    private record Prestacion(String tipo, String concepto, String nombre) {
    }

    private static final List<Prestacion> PRESTACIONES = List.of(
            new Prestacion("CESANTIAS", "CESANTIAS", "Cesantías"),
            new Prestacion("INTERESES_CESANTIAS", "INT_CESANTIAS", "Intereses de cesantías"),
            new Prestacion("PRIMA", "PRIMA", "Prima de servicios"));

    private final NominaRetiroJPARepository retiros;
    private final NominaRetiroQueryRepository retiroQuery;
    private final LiquidacionPrestacionJPARepository prestaciones;
    private final ContratoSalarioHistorialJPARepository historialSalario;
    private final PromedioSalarialService promedios;
    private final ConceptoNominaService conceptos;
    private final VacacionesService vacaciones;

    public LiquidadorDefinitiva(NominaRetiroJPARepository retiros,
            NominaRetiroQueryRepository retiroQuery,
            LiquidacionPrestacionJPARepository prestaciones,
            ContratoSalarioHistorialJPARepository historialSalario,
            PromedioSalarialService promedios,
            ConceptoNominaService conceptos,
            VacacionesService vacaciones) {
        this.retiros = retiros;
        this.retiroQuery = retiroQuery;
        this.prestaciones = prestaciones;
        this.historialSalario = historialSalario;
        this.promedios = promedios;
        this.conceptos = conceptos;
        this.vacaciones = vacaciones;
    }

    /** El retiro cuya liquidación definitiva es el documento (vacío si no es una definitiva). */
    public Optional<NominaRetiroEntity> retiroDelDocumento(Long documentoId) {
        if (documentoId == null) return Optional.empty();
        return retiroQuery.retiroDeDocumento(documentoId).flatMap(retiros::findById);
    }

    /**
     * ¿El salario hasta la fecha de retiro ya se pagó en otra nómina? Entonces la
     * definitiva no vuelve a pagar días de salario (solo prestaciones e indemnización).
     */
    public boolean salarioYaPagado(NominaRetiroEntity retiro, Long documentoId) {
        return retiroQuery.salarioPagadoHasta(retiro.getContratoId(), documentoId)
                .map(hasta -> !hasta.isBefore(retiro.getFechaRetiro()))
                .orElse(false);
    }

    /**
     * Líneas de prestaciones e indemnización de la fila (devengados que no entran al
     * IBC). Contratos sin prestaciones laborales (servicios, aprendiz en lectiva) no llevan.
     */
    public List<LineaLiquidacion> lineas(NominaEntity fila, NominaRetiroEntity retiro, NominaConfigEntity config,
            Integer empresaId) {
        ContratoLaboralEntity contrato = fila.getContrato();
        List<LineaLiquidacion> out = new ArrayList<>();
        if (!ReglasAprendiz.causaPrestaciones(contrato))   // servicios y aprendiz en lectiva
            return out;

        LocalDate retiroFecha = retiro.getFechaRetiro();
        Long empleadoId = fila.getEmpleado().getId();
        BigDecimal salario = salarioVigente(contrato, retiroFecha);
        BigDecimal auxilio = auxilio(config, salario);

        for (Prestacion p : PRESTACIONES) {
            LocalDate desde = desde(empresaId, empleadoId, p.tipo(), contrato.getFechaInicio(), retiro);
            if (desde.isAfter(retiroFecha)) continue;
            int dias = FormulasPrestaciones.diasBase360(desde, retiroFecha);
            BigDecimal promedio = promedios.promedioMensualVariable(empresaId, empleadoId, desde, retiroFecha,
                    dias, false);
            BigDecimal valor = FormulasPrestaciones.valor(p.tipo(), salario, auxilio, promedio, dias, false);
            if (valor.signum() <= 0) continue;
            ConceptoNominaEntity c = conceptos.porCodigo(empresaId, p.concepto(), retiroFecha);
            Traza t = new Traza()
                    .paso(p.nombre() + " del " + desde + " al " + retiroFecha, "")
                    .paso("Días (base 360)", BigDecimal.valueOf(dias))
                    .paso("Salario", salario)
                    .paso("Auxilio de transporte (incluido)", auxilio)
                    .paso("Promedio variable mensual", promedio)
                    .paso("Valor", valor);
            LineaLiquidacion l = LineaLiquidacion.devengado(c, valor, t);
            l.setCantidad(BigDecimal.valueOf(dias));
            out.add(l);
        }
        vacacionesPendientes(empresaId, empleadoId, salario, retiroFecha).ifPresent(out::add);

        if (Boolean.TRUE.equals(retiro.getIndemniza())) {
            BigDecimal dias = FormulasPrestaciones.diasIndemnizacion(contrato.getTipoContrato(), salario,
                    config.getSmmlv(), contrato.getFechaInicio(), retiroFecha, retiro.getFechaFinPactada());
            BigDecimal valor = FormulasPrestaciones.valorIndemnizacion(salario, dias);
            if (valor.signum() > 0) {
                ConceptoNominaEntity c = conceptos.porCodigo(empresaId, "INDEMNIZACION", retiroFecha);
                Traza t = new Traza()
                        .paso("Despido sin justa causa — contrato " + contrato.getTipoContrato(), "")
                        .paso("Salario", salario)
                        .paso("Días de indemnización", dias.setScale(2, RoundingMode.HALF_UP))
                        .paso("Indemnización", valor);
                LineaLiquidacion l = LineaLiquidacion.devengado(c, valor, t);
                l.setCantidad(dias.setScale(2, RoundingMode.HALF_UP));
                out.add(l);
            }
        }
        return out;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /**
     * Vacaciones pendientes compensadas en dinero: el saldo de días a la fecha de
     * retiro (causado + saldo inicial − tomado, incluidas las del módulo de vacaciones)
     * × (salario + promedio variable del último año sin horas extra) ÷ 30. Vacío si no
     * le quedan días.
     */
    private Optional<LineaLiquidacion> vacacionesPendientes(Integer empresaId, Long empleadoId, BigDecimal salario,
            LocalDate retiroFecha) {
        BigDecimal saldo = vacaciones.saldoAl(empleadoId, empresaId, retiroFecha).getDiasDisponibles();
        if (saldo == null || saldo.signum() <= 0) return Optional.empty();
        BigDecimal promedio = promedios.promedioMensualVariable(empresaId, empleadoId, retiroFecha.minusYears(1),
                retiroFecha, 360, true);
        BigDecimal valor = FormulasPrestaciones.valorVacaciones(salario, promedio, saldo);
        if (valor.signum() <= 0) return Optional.empty();
        ConceptoNominaEntity c = conceptos.porCodigo(empresaId, "VACACIONES", retiroFecha);
        Traza t = new Traza()
                .paso("Vacaciones pendientes al " + retiroFecha + " compensadas en dinero", "")
                .paso("Saldo de días", saldo)
                .paso("Salario", salario)
                .paso("Promedio variable mensual (sin horas extra)", promedio)
                .paso("Fórmula", "(salario + promedio) ÷ 30 × días")
                .paso("Valor", valor);
        LineaLiquidacion l = LineaLiquidacion.devengado(c, valor, t);
        l.setCantidad(saldo);
        return Optional.of(l);
    }

    /**
     * Desde cuándo se liquida la prestación: el día siguiente a su último pago o lote
     * no anulado; si no hay, el ingreso. Las cesantías consignadas fuera del sistema
     * no se vuelven a pagar.
     */
    private LocalDate desde(Integer empresaId, Long empleadoId, String tipo, LocalDate ingreso,
            NominaRetiroEntity retiro) {
        LocalDate desde = prestaciones
                .findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoNotOrderByFechaHastaDesc(
                        empresaId, empleadoId, tipo, "ANULADA")
                .map(u -> u.getFechaHasta().plusDays(1))
                .orElse(ingreso);
        boolean deCesantias = "CESANTIAS".equals(tipo) || "INTERESES_CESANTIAS".equals(tipo);
        if (deCesantias && retiro.getCesantiasCorteHasta() != null) {
            LocalDate corte = retiro.getCesantiasCorteHasta().plusDays(1);
            if (corte.isAfter(desde)) desde = corte;
        }
        return desde;
    }

    /** Salario del historial a la fecha de retiro; si no hay, el del contrato. */
    private BigDecimal salarioVigente(ContratoLaboralEntity contrato, LocalDate fecha) {
        return historialSalario.findEnFecha(contrato.getId(), fecha)
                .map(h -> h.getSalario())
                .filter(s -> s != null && s.signum() > 0)
                .orElse(contrato.getSalarioBase() != null ? contrato.getSalarioBase() : BigDecimal.ZERO);
    }

    /** Auxilio de transporte si el salario no pasa de 2 SMMLV (modo de nómina completo). */
    private static BigDecimal auxilio(NominaConfigEntity config, BigDecimal salario) {
        if (config == null || !"COMPLETO".equals(config.getModoNomina())) return BigDecimal.ZERO;
        BigDecimal smmlv = config.getSmmlv() != null ? config.getSmmlv() : BigDecimal.ZERO;
        BigDecimal dos = smmlv.multiply(BigDecimal.valueOf(2));
        if (dos.signum() > 0 && salario.compareTo(dos) <= 0)
            return config.getAuxilioTransporte() != null ? config.getAuxilioTransporte() : BigDecimal.ZERO;
        return BigDecimal.ZERO;
    }
}

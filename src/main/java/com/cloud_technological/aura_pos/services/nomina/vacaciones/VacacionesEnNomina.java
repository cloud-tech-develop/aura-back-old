package com.cloud_technological.aura_pos.services.nomina.vacaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina_vacaciones.NominaVacacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_vacaciones.NominaVacacionQueryRepository.EnPeriodo;
import com.cloud_technological.aura_pos.services.nomina.DiasHabiles;
import com.cloud_technological.aura_pos.services.nomina.FormulasPrestaciones;
import com.cloud_technological.aura_pos.services.nomina.PromedioSalarialService;

/**
 * Lleva las vacaciones registradas en el módulo (R7) a la fila de nómina del período
 * como novedades VACACIONES de origen VACACIONES. Idempotente: cada liquidación
 * reemplaza las que puso la anterior.
 *
 * <ul>
 *   <li>TIEMPO: los días del disfrute que caen en el período descuentan salario
 *       (días calendario) y se pagan por días hábiles: (salario + promedio variable
 *       del último año sin horas extra) ÷ 30 × hábiles. Constituyen IBC.</li>
 *   <li>DINERO: se pagan completas en el período de su fecha; no descuentan días
 *       ni constituyen IBC.</li>
 * </ul>
 */
@Component
public class VacacionesEnNomina {

    public static final String ORIGEN = "VACACIONES";
    private static final int TOPE_DIAS_PERIODO = 30;

    private final NominaVacacionQueryRepository queryRepository;
    private final PromedioSalarialService promedios;

    public VacacionesEnNomina(NominaVacacionQueryRepository queryRepository, PromedioSalarialService promedios) {
        this.queryRepository = queryRepository;
        this.promedios = promedios;
    }

    /**
     * Reemplaza en la fila las novedades de vacaciones del módulo por las del período.
     *
     * @param salario salario mensual del contrato con el que se liquida la fila
     */
    public void sincronizar(NominaEntity nomina, PeriodoNominaEntity periodo, BigDecimal salario,
            Integer empresaId) {
        nomina.getNovedades().removeIf(n -> ORIGEN.equals(n.getOrigen()));
        if (nomina.getContrato() == null || periodo == null) return;

        for (EnPeriodo v : queryRepository.enPeriodo(nomina.getContrato().getId(), periodo.getFechaInicio(),
                periodo.getFechaFin())) {
            NominaNovedadEntity nv = "TIEMPO".equals(v.tipo())
                    ? enTiempo(v, periodo, salario, nomina, empresaId)
                    : enDinero(v, salario, nomina, empresaId);
            if (nv == null) continue;
            nv.setNomina(nomina);
            nomina.getNovedades().add(nv);
        }
    }

    private NominaNovedadEntity enTiempo(EnPeriodo v, PeriodoNominaEntity periodo, BigDecimal salario,
            NominaEntity nomina, Integer empresaId) {
        LocalDate fin = v.fechaRegresoAnticipado() != null ? v.fechaRegresoAnticipado().minusDays(1) : v.fechaFin();
        LocalDate desde = v.fechaInicio().isAfter(periodo.getFechaInicio()) ? v.fechaInicio() : periodo.getFechaInicio();
        LocalDate hasta = fin.isBefore(periodo.getFechaFin()) ? fin : periodo.getFechaFin();
        if (hasta.isBefore(desde)) return null;
        int habiles = DiasHabiles.entre(desde, hasta);
        int calendario = (int) Math.min(ChronoUnit.DAYS.between(desde, hasta) + 1, TOPE_DIAS_PERIODO);

        BigDecimal dias = BigDecimal.valueOf(habiles);
        BigDecimal valorDia = valorDia(salario, v.fechaInicio(), nomina, empresaId);
        NominaNovedadEntity nv = base(v, "Vacaciones en tiempo del " + desde + " al " + hasta);
        nv.setFechaInicio(desde);
        nv.setFechaFin(hasta);
        nv.setDias(calendario);
        nv.setAfectaDiasSalario(true);
        nv.setCantidad(dias);
        nv.setValorUnitario(valorDia);
        nv.setValorTotal(valorDia.multiply(dias).setScale(2, RoundingMode.HALF_UP));
        nv.setConstituyeIbc(true);
        return nv;
    }

    private NominaNovedadEntity enDinero(EnPeriodo v, BigDecimal salario, NominaEntity nomina, Integer empresaId) {
        BigDecimal valorDia = valorDia(salario, v.fechaInicio(), nomina, empresaId);
        NominaNovedadEntity nv = base(v, "Vacaciones compensadas en dinero");
        nv.setFechaInicio(v.fechaInicio());
        nv.setFechaFin(v.fechaInicio());
        nv.setDias(0);
        nv.setAfectaDiasSalario(false);
        nv.setCantidad(v.diasHabiles());
        nv.setValorUnitario(valorDia);
        nv.setValorTotal(valorDia.multiply(v.diasHabiles()).setScale(2, RoundingMode.HALF_UP));
        nv.setConstituyeIbc(false);
        return nv;
    }

    /** (Salario + promedio variable del año anterior al inicio, sin horas extra) ÷ 30. */
    private BigDecimal valorDia(BigDecimal salario, LocalDate inicio, NominaEntity nomina, Integer empresaId) {
        BigDecimal promedio = promedios.promedioMensualVariable(empresaId, nomina.getEmpleado().getId(),
                inicio.minusYears(1), inicio.minusDays(1), 360, true);
        return FormulasPrestaciones.valorVacaciones(salario, promedio, BigDecimal.ONE);
    }

    private static NominaNovedadEntity base(EnPeriodo v, String descripcion) {
        NominaNovedadEntity nv = new NominaNovedadEntity();
        nv.setTipo("VACACIONES");
        nv.setDescripcion(descripcion);
        nv.setNumeroAutorizacion("VAC-" + v.id());
        nv.setEsDeduccion(false);
        nv.setNaturaleza("DEVENGADO");
        nv.setOrigen(ORIGEN);
        nv.setEstado("APLICADA");
        return nv;
    }
}

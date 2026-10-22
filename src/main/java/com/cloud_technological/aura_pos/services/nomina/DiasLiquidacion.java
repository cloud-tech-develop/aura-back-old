package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;

/**
 * Días con los que se liquida una nómina.
 *
 * <ul>
 *   <li><b>Días de salario</b>: los del período menos las ausencias que restan
 *       salario (incapacidad, licencias, vacaciones). Se pagan aparte.</li>
 *   <li><b>Días de IBC</b>: los del período menos solo la licencia no remunerada
 *       y la suspensión; el resto de ausencias sigue cotizando (B-05).</li>
 * </ul>
 *
 * <p>Son la "cantidad" del salario, el auxilio y los aportes de salud y pensión:
 * sin ella el desprendible decía el valor pero no por cuántos días.
 */
public final class DiasLiquidacion {

    /** Ausencias que SÍ reducen la base de cotización (IBC). */
    private static final Set<String> AUSENCIAS_QUE_REDUCEN_IBC = Set.of("LICENCIA_NO_REMUNERADA", "SUSPENSION");

    /** Conceptos que se pagan por días de salario. */
    private static final Set<String> POR_DIAS_SALARIO = Set.of("SALARIO", "AUX_TRANSP");

    /** Deducciones que se cotizan por días de IBC. */
    private static final Set<String> POR_DIAS_IBC = Set.of("DED_SALUD", "DED_PENSION");

    private DiasLiquidacion() {
    }

    /** Días del período (30 si no se fijaron). */
    public static int diasPeriodo(NominaEntity nomina) {
        return nomina.getDiasTrabajados() != null ? nomina.getDiasTrabajados() : 30;
    }

    /** Días de salario: período menos las ausencias que restan salario. */
    public static int diasSalario(NominaEntity nomina) {
        return Math.max(0, diasPeriodo(nomina) - diasAusencia(nomina, false));
    }

    /** Días de IBC: período menos la licencia no remunerada y la suspensión. */
    public static int diasIbc(NominaEntity nomina) {
        return Math.max(0, diasPeriodo(nomina) - diasAusencia(nomina, true));
    }

    /**
     * Cantidad (días) que le corresponde a un concepto, o null si no se mide en días.
     *
     * @param codigo código del concepto
     * @param nomina nómina liquidada
     * @return días de salario, días de IBC o null
     */
    public static BigDecimal cantidadDe(String codigo, NominaEntity nomina) {
        if (POR_DIAS_SALARIO.contains(codigo)) return BigDecimal.valueOf(diasSalario(nomina));
        if (POR_DIAS_IBC.contains(codigo)) return BigDecimal.valueOf(diasIbc(nomina));
        return null;
    }

    /**
     * Pone los días en las líneas de salario, auxilio, salud y pensión que no traen cantidad.
     *
     * @param lineas líneas calculadas por el motor
     * @param nomina nómina que se liquida
     */
    public static void asignar(List<LineaLiquidacion> lineas, NominaEntity nomina) {
        for (LineaLiquidacion l : lineas) {
            ConceptoNominaEntity c = l.getConcepto();
            if (l.getCantidad() != null || c == null) continue;
            l.setCantidad(cantidadDe(c.getCodigo(), nomina));
        }
    }

    /**
     * Días de ausencia de las novedades que restan salario.
     *
     * @param soloIbc true para contar solo las que también reducen el IBC
     */
    private static int diasAusencia(NominaEntity nomina, boolean soloIbc) {
        if (nomina.getNovedades() == null) return 0;
        return nomina.getNovedades().stream()
                .filter(n -> Boolean.TRUE.equals(n.getAfectaDiasSalario()))
                .filter(n -> !soloIbc || AUSENCIAS_QUE_REDUCEN_IBC.contains(n.getTipo()))
                .mapToInt(n -> n.getDias() != null ? n.getDias() : 0)
                .sum();
    }
}

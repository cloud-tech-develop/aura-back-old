package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * Una línea calculada, antes de persistirse.
 *
 * <p>Existe separada de {@link NominaDetalleEntity} para que el motor pueda
 * testearse sin base de datos: {@code MotorLiquidacion} devuelve estas líneas
 * y el servicio decide cómo persistirlas.
 */
@Getter
@Setter
public class LineaLiquidacion {

    private final ConceptoNominaEntity concepto;
    private final BigDecimal base;
    private final BigDecimal porcentaje;
    private final BigDecimal valor;
    private final Traza traza;

    private BigDecimal cantidad;
    private NominaNovedadEntity novedad;

    // ── Dimensiones (Fase 4.a). Null = sin distribución. ────────────────────
    private Long proyectoId;
    private Long frenteId;
    private Long centroCostoId;
    private BigDecimal porcentajeDistrib = new BigDecimal("100");

    public LineaLiquidacion(ConceptoNominaEntity concepto, BigDecimal base,
                            BigDecimal porcentaje, BigDecimal valor, Traza traza) {
        this.concepto = concepto;
        this.base = base != null ? base : BigDecimal.ZERO;
        this.porcentaje = porcentaje;
        this.valor = valor != null ? valor : BigDecimal.ZERO;
        this.traza = traza;
    }

    /** Atajo para conceptos sin cálculo (salario, auxilio). */
    public static LineaLiquidacion devengado(ConceptoNominaEntity c, BigDecimal valor, Traza traza) {
        return new LineaLiquidacion(c, BigDecimal.ZERO, null, valor, traza);
    }

    /**
     * Convierte a entidad persistible.
     *
     * <p>{@code valorEmpleado} vs {@code valorEmpleador} se decide por la clase
     * del concepto: lo que deduce el empleado sale de su bolsillo; los aportes
     * y provisiones los paga la empresa.
     */
    public NominaDetalleEntity aEntidad(NominaEntity nomina) {
        NominaDetalleEntity d = new NominaDetalleEntity();
        d.setNomina(nomina);
        d.setConcepto(concepto);
        d.setNovedad(novedad);
        d.setCantidad(cantidad);
        d.setBase(base);
        d.setPorcentaje(porcentaje);
        d.setValor(valor);
        d.setTraza(traza != null ? traza.aJson() : null);
        d.setProyectoId(proyectoId);
        d.setFrenteId(frenteId);
        d.setCentroCostoId(centroCostoId);
        d.setPorcentajeDistrib(porcentajeDistrib);

        switch (concepto.getClase()) {
            case ConceptoNominaEntity.Clase.DEDUCCION -> {
                d.setValorEmpleado(valor);
                d.setValorEmpleador(BigDecimal.ZERO);
            }
            case ConceptoNominaEntity.Clase.APORTE_EMPLEADOR,
                 ConceptoNominaEntity.Clase.PROVISION -> {
                d.setValorEmpleado(BigDecimal.ZERO);
                d.setValorEmpleador(valor);
            }
            default -> {  // DEVENGADO: se le paga al empleado
                d.setValorEmpleado(valor);
                d.setValorEmpleador(BigDecimal.ZERO);
            }
        }
        return d;
    }
}

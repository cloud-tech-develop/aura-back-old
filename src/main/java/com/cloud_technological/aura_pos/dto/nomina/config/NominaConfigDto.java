package com.cloud_technological.aura_pos.dto.nomina.config;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominaConfigDto {
    private Long id;
    private String modoNomina;
    private String periodicidad;
    private String modoLiquidacion;
    private BigDecimal smmlv;
    private BigDecimal auxilioTransporte;
    /** Año de la tabla de ley de donde salen smmlv y auxilio (null = valores propios de la empresa). */
    private Integer parametrosAgno;
    /** Norma que fija esos valores (decreto). */
    private String parametrosNorma;
    private BigDecimal pctSaludEmpleado;
    private BigDecimal pctPensionEmpleado;
    private BigDecimal pctSaludEmpleador;
    private BigDecimal pctPensionEmpleador;
    private BigDecimal pctCajaCompensacion;
    private BigDecimal pctIcbf;
    private BigDecimal pctSena;
    /** Apoyo de sostenimiento del aprendiz SENA como % del SMMLV, por fase (B-10). */
    private BigDecimal aprendizPctLectiva;
    private BigDecimal aprendizPctPractica;
    private Boolean permiteVacacionesAnticipadas;
    /** Asistencia por obra (V243): ¿divide los proyectos en frentes? */
    private Boolean asistenciaUsaFrentes;
    /** ¿Exige soporte (PDF o Excel) para enviar un día a revisión? */
    private Boolean asistenciaExigeSoporte;
    /** Cómo se registran las horas: MANUAL | EXCEL | AMBOS. */
    private String asistenciaRegistro;
    /** ¿Un supervisor aprueba la asistencia antes de la nómina? FALSE = la prenómina la toma al liquidar. */
    private Boolean asistenciaRequiereAprobacion;
}

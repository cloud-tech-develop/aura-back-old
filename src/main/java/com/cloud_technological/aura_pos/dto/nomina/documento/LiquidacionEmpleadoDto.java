package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Fila de la pestaña Liquidación: totales del empleado o la causa por la que no se liquidó. */
@Getter
@Setter
public class LiquidacionEmpleadoDto {
    private Long nominaId;
    private String codigoVinculacion;
    private String empleadoNombre;
    private String empleadoDocumento;
    private Integer diasTrabajados;
    private BigDecimal devengado;
    private BigDecimal deducido;
    private BigDecimal neto;
    /** Aportes del empleador (salud, pensión, ARL, caja, ICBF, SENA). */
    private BigDecimal aportes;
    private boolean liquidada;
    private LocalDateTime liquidadaAt;
    /** Causa del último intento fallido; null si se liquidó o aún no se intenta. */
    private String error;
    /** Estado de la fila: BORRADOR (prenómina), APROBADO (nómina por pagar), PAGADO, ANULADO. */
    private String estado;
    private LocalDateTime fechaPago;
    /** EFECTIVO | TRANSFERENCIA. */
    private String medioPago;
}

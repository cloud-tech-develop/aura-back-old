package com.cloud_technological.aura_pos.dto.contabilidad.declaraciones;

import java.time.LocalDate;

import lombok.Data;
import java.math.BigDecimal;

/** Pago de una declaración (V204): el valor y las cuentas los calcula el servidor. */
@Data
public class RegistrarPagoDeclaracionDto {
    /** IVA | RETENCION | RETEICA */
    private String tipo;
    private LocalDate desde;
    private LocalDate hasta;
    private Long terceroAcreedorId;
    private LocalDate fecha;
    private String numeroFormulario;
    /** EFECTIVO | TRANSFERENCIA | … */
    private String metodoPago;
    private Long cuentaBancariaId;
    /** Cuenta contable de donde sale la plata (medio de pago), si no es una cuenta bancaria. */
    private Long cuentaContableId;
    /** Aproximar el valor a pagar al múltiplo de mil (art. 577 E.T.). Por defecto sí. */
    private Boolean redondearMiles;
    /** Intereses de mora liquidados al presentar tarde (opcional). */
    private BigDecimal intereses;
    /** Sanción por extemporaneidad u otra (opcional). */
    private BigDecimal sancion;
}

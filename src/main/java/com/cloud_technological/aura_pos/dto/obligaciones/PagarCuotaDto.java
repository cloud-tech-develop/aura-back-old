package com.cloud_technological.aura_pos.dto.obligaciones;

import lombok.Data;

/**
 * Origen del dinero al pagar una cuota de una obligación financiera.
 * Todos los campos son opcionales: si no se envía body (o vienen en null), el
 * pago sale de la cuenta bancaria del desembolso, preservando el comportamiento
 * anterior. Permite cancelar el pasivo desde cualquier activo monetario:
 * caja (EFECTIVO) o cualquier cuenta bancaria, no solo la del desembolso.
 */
@Data
public class PagarCuotaDto {
    /** EFECTIVO | TRANSFERENCIA | TARJETA | ... (se normaliza a MAYÚS). */
    private String metodoPago;
    /** Cuenta bancaria DE DONDE sale el dinero (no necesariamente la del desembolso). */
    private Long cuentaBancariaId;
}

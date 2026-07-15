package com.cloud_technological.aura_pos.contabilidad.application.resolucion;

/**
 * Puerto de resolución de la cuenta de un movimiento de dinero:
 * cuenta contable de la cuenta bancaria → (E2: forma de pago) →
 * fallback efectivo→CAJA / resto→BANCOS.
 */
public interface ResolucionCuentaPago {

    Long resolver(Integer empresaId, String metodoPago, Long cuentaBancariaId);
}

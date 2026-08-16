package com.cloud_technological.aura_pos.contabilidad.application.resolucion;

/**
 * Puerto de resolución de la cuenta de un movimiento de dinero:
 * cuenta contable de la cuenta bancaria → (E2: forma de pago) →
 * fallback efectivo→CAJA / resto→BANCOS.
 */
public interface ResolucionCuentaPago {

    Long resolver(Integer empresaId, String metodoPago, Long cuentaBancariaId);

    /**
     * Igual que {@link #resolver}, pero una cuenta elegida a mano manda sobre
     * todo lo demás (V142).
     *
     * <p>Es la salida del administrador que paga o cobra sin caja abierta y sin
     * cuenta bancaria de por medio. Vive aquí y no en cada llamador para que la
     * regla de prioridad tenga una sola definición.
     */
    default Long resolver(Integer empresaId, String metodoPago, Long cuentaBancariaId,
            Long cuentaContableId) {
        return cuentaContableId != null
                ? cuentaContableId
                : resolver(empresaId, metodoPago, cuentaBancariaId);
    }
}

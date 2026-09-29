package com.cloud_technological.aura_pos.utils;

/**
 * Utilidades del medio de pago de un documento.
 *
 * <p>Existe porque la pregunta "¿este pago fue en efectivo?" se hacía en cada
 * módulo con una regla distinta: el cierre de caja compara
 * {@code metodo_pago = 'EFECTIVO'} en SQL crudo, la resolución de cuentas usa
 * {@code contains("EFECTIVO")}, y las validaciones de venta no la hacían. Con
 * criterios distintos, un pago guardado en minúscula exigía turno en un lado y
 * no sumaba al efectivo esperado en el otro — cuadre imposible para el cajero.
 *
 * <p>Regla única: se normaliza a mayúsculas sin espacios y se pregunta si
 * contiene EFECTIVO. {@link #normalizar} debe aplicarse <b>al guardar</b>, para
 * que las consultas SQL que comparan por igualdad exacta sigan funcionando.
 */
public final class MediosPago {

    public static final String EFECTIVO = "EFECTIVO";
    public static final String CREDITO  = "CREDITO";

    private MediosPago() {
    }

    /** Forma canónica que se persiste: mayúsculas y sin espacios sobrantes. */
    public static String normalizar(String metodoPago) {
        return metodoPago == null ? null : metodoPago.trim().toUpperCase();
    }

    /** true si el pago mueve dinero físico y por tanto necesita una caja. */
    public static boolean esEfectivo(String metodoPago) {
        String m = normalizar(metodoPago);
        return m != null && m.contains(EFECTIVO);
    }

    /** true si no hay dinero entregado: queda como cartera, no como pago. */
    public static boolean esCredito(String metodoPago) {
        return CREDITO.equals(normalizar(metodoPago));
    }
}

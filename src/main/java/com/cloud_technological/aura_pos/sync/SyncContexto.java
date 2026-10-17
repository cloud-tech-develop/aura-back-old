package com.cloud_technological.aura_pos.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Marca el hilo mientras la nube reproduce un comando que llegó de un nodo.
 *
 * <p>La operación ya ocurrió en la tienda: la venta se entregó, el turno se
 * abrió. Los servicios la reproducen con los mismos métodos de siempre, pero
 * consultan este contexto para:
 * <ul>
 * <li>conservar la fecha original (el asiento cae en el período correcto aunque
 * la sincronización llegue días después),</li>
 * <li>conservar el prefijo y el consecutivo que el cliente ya tiene impresos,</li>
 * <li>no volver a bloquear por stock, cupo o permisos lo que el nodo ya validó.</li>
 * </ul>
 */
public final class SyncContexto {

    /**
     * Datos de la operación original.
     *
     * @param nodoId      nodo que la hizo
     * @param fecha       fecha y hora en que ocurrió en la tienda
     * @param prefijo     prefijo del documento en el nodo (puede ser null)
     * @param consecutivo consecutivo del documento en el nodo (puede ser null)
     */
    public record Replay(Long nodoId, LocalDateTime fecha, String prefijo, Long consecutivo) {
    }

    private static final ThreadLocal<Replay> ACTUAL = new ThreadLocal<>();

    private SyncContexto() {
    }

    /**
     * Ejecuta la acción marcando el hilo como reproducción.
     *
     * @param replay datos de la operación original
     * @param accion lo que se reproduce
     * @return lo que devuelva la acción
     */
    public static <T> T ejecutar(Replay replay, Supplier<T> accion) {
        Replay anterior = ACTUAL.get();
        ACTUAL.set(replay);
        try {
            return accion.get();
        } finally {
            if (anterior == null) ACTUAL.remove();
            else ACTUAL.set(anterior);
        }
    }

    /**
     * Indica si el hilo está reproduciendo un comando de un nodo.
     *
     * @return true durante la reproducción
     */
    public static boolean enReplay() {
        return ACTUAL.get() != null;
    }

    /**
     * Datos de la reproducción en curso.
     *
     * @return el replay, o null si no hay
     */
    public static Replay actual() {
        return ACTUAL.get();
    }

    /**
     * La hora de la operación: la original si se está reproduciendo, la actual
     * si no.
     *
     * @return fecha y hora a registrar
     */
    public static LocalDateTime ahora() {
        Replay r = ACTUAL.get();
        return r != null && r.fecha() != null ? r.fecha() : LocalDateTime.now();
    }

    /**
     * Igual que {@link #ahora()}, solo la fecha.
     *
     * @return fecha a registrar
     */
    public static LocalDate hoy() {
        return ahora().toLocalDate();
    }
}

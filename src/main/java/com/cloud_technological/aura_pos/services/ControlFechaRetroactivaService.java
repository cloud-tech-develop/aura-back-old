package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;

/**
 * Decide si un documento con fecha anterior puede pagarse desde la caja.
 *
 * <p>Las fases anteriores le dieron al usuario cómo declarar de dónde sale la
 * plata, y al cajero cómo ver qué parte de su arqueo no es suya. Esto es el
 * freno: sin él, nada impide cargar una factura de hace tres semanas a la caja
 * de hoy, y vuelve el descuadre que todo esto vino a evitar.
 *
 * <p><b>Solo aplica a la vía CAJA.</b> Crédito, banco y caja menor no descuadran
 * el arqueo de nadie, así que no piden permiso — ponerles fricción empujaría al
 * usuario de vuelta a "Caja", que es lo contrario de lo que se busca.
 */
public interface ControlFechaRetroactivaService {

    /**
     * Valida el documento y devuelve quién lo autorizó, si hizo falta.
     *
     * <p>Dentro de la ventana de gracia no hay fricción: pagar hoy la factura de
     * ayer es la operación más normal del mundo.
     *
     * @param fechaDocumento  fecha del documento; null se trata como hoy
     * @param saleDeCaja      true solo cuando el pago mueve el arqueo
     * @param motivo          explicación que escribió el usuario, si la hubo
     * @param documento       "compra" o "gasto", para los mensajes
     * @return id del usuario que autorizó, o null si no hizo falta autorización
     * @throws com.cloud_technological.aura_pos.utils.GlobalException si el
     *         documento excede la ventana y no puede o no debe pasar
     */
    Integer validar(Integer empresaId, LocalDate fechaDocumento, boolean saleDeCaja,
            String motivo, Long usuarioId, String documento);

    /**
     * Exige que quien opera tenga el rol autorizador de la empresa.
     *
     * <p>Vive aquí y no en cada llamador para que "quién puede tocar el pasado"
     * tenga una sola definición: la misma que decide si una factura vieja entra
     * a la caja decide si un cierre puede corregirse.
     *
     * @param accion lo que se intenta hacer, para el mensaje de error
     * @throws com.cloud_technological.aura_pos.utils.GlobalException si el rol
     *         actual no es el autorizador
     */
    void exigirRolAutorizador(Integer empresaId, String accion);
}

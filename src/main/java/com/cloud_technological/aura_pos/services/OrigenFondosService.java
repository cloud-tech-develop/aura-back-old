package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;

/**
 * Resuelve <b>de dónde salió o entró la plata</b> de un documento.
 *
 * <p>Antes cada módulo lo deducía del usuario que digitaba: la venta tomaba el
 * turno del vendedor, la compra buscaba el turno del comprador y el abono el
 * que mandara el front. Como el administrador no tiene turno, sus abonos y
 * gastos no caían en ningún cierre de caja, y el gasto además acreditaba CAJA
 * a ciegas aunque se hubiera pagado por banco.
 *
 * <p>El dinero pertenece a una caja o a una cuenta, nunca a un usuario. Por eso
 * el origen se declara explícitamente y este servicio es el único que decide
 * qué cuenta contable se afecta y si hay que registrar movimiento de caja.
 */
public interface OrigenFondosService {

    /** De qué fondo sale o entra el dinero. */
    enum Tipo {
        /** Caja física: exige turno abierto y genera movimiento de caja. */
        CAJA,
        /** Cuenta bancaria de la empresa: afecta su cuenta contable y su saldo. */
        BANCO,
        /** Cuenta contable directa o la parametrizada en la forma de pago. */
        CUENTA_CONTABLE
    }

    /**
     * Lo que declara el documento. Los tres identificadores son excluyentes en
     * la práctica y se evalúan de más explícito a menos:
     * cuenta contable → cuenta bancaria → efectivo (caja) → forma de pago.
     *
     * @param sucursalId necesario para ubicar la caja abierta cuando el pago es
     *                   en efectivo y el documento no trae turno (caso admin).
     * @param documento  descripción corta para los mensajes de error.
     */
    record Solicitud(
            String metodoPago,
            Long turnoCajaId,
            Long cuentaBancariaId,
            Long cuentaContableId,
            Integer sucursalId,
            String documento) {
    }

    /**
     * @param turno turno donde cae el movimiento; puede venir informado aunque
     *              el tipo no sea CAJA (trazabilidad de quién lo registró).
     */
    record OrigenFondos(Tipo tipo, Long cuentaContableId, TurnoCajaEntity turno) {

        /** Solo el efectivo mueve el arqueo; tarjeta y banco no. */
        public boolean generaMovimientoCaja() {
            return tipo == Tipo.CAJA && turno != null;
        }

        public Long turnoId() {
            return turno != null ? turno.getId() : null;
        }
    }

    OrigenFondos resolver(Integer empresaId, Solicitud solicitud);
}

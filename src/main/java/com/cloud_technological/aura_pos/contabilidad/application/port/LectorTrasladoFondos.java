package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección de un traslado de fondos para su generador: cuánto se movió, de
 * qué cuenta a qué cuenta y con qué descripción.
 *
 * <p>Las cuentas llegan ya resueltas: el servicio las fijó al crear el
 * documento con el mismo resolutor que usan compras y gastos. Recalcularlas
 * aquí abriría la puerta a que el asiento use una cuenta distinta de la que se
 * validó, si entre tanto cambió la parametrización del banco o de la caja.
 */
public interface LectorTrasladoFondos {

    TrasladoFondos cargar(Long trasladoId, Integer empresaId);

    record TrasladoFondos(
            LocalDate fecha,
            BigDecimal monto,
            Long cuentaOrigenId,
            Long cuentaDestinoId,
            String concepto,
            String observacion) {
    }
}

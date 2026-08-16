package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyección del obsequio para contabilizarlo. El obsequio no tiene ingreso:
 * solo costo (que sale de inventario) y, si aplica, el IVA que la empresa
 * asume por retirar mercancía sin venderla.
 */
public interface LectorObsequio {

    ObsequioContable cargar(Long obsequioId, Integer empresaId);

    /**
     * @param terceroId  quién recibió (puede ser null)
     * @param generaIva  si se causa el IVA por retiro de inventario
     */
    record ObsequioContable(
            LocalDate fecha,
            String motivo,
            Long terceroId,
            Long centroCostoId,
            boolean generaIva,
            List<LineaObsequio> lineas) {
    }

    /**
     * @param costo la parte que sale del inventario y se va al gasto
     * @param iva   impuesto por retiro, ya calculado sobre el valor comercial
     */
    record LineaObsequio(Long productoId, BigDecimal costo, BigDecimal iva) {
    }
}

package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;
import com.cloud_technological.aura_pos.dto.comprobante.ComprobanteCajaDto;
import com.cloud_technological.aura_pos.entity.ComprobanteCajaEntity;

public interface ComprobanteCajaService {
    List<ComprobanteCajaDto> listar(Integer empresaId, String tipo, String desde, String hasta, int page, int rows);
    ComprobanteCajaDto obtenerPorId(Long id, Integer empresaId);
    ComprobanteCajaEntity generar(Integer empresaId, Integer usuarioId,
            String tipo, String concepto, BigDecimal monto,
            String metodoPago, String entregadoA,
            String origen, Long origenId, Long turnoCajaId);

    /**
     * Crea el comprobante del documento o, si ya existe, lo pone al día.
     *
     * <p>Un documento tiene un solo comprobante durante toda su vida: al editar
     * la compra hay que corregir el que ya se emitió, no emitir uno nuevo — dos
     * comprobantes para un mismo pago es un soporte duplicado. Por eso conserva
     * el número original y solo actualiza monto, concepto y método.
     *
     * @return null si el documento no mueve dinero (monto nulo o cero)
     */
    ComprobanteCajaEntity sincronizarDeDocumento(Integer empresaId, Integer usuarioId,
            String tipo, String concepto, BigDecimal monto,
            String metodoPago, String entregadoA,
            String origen, Long origenId, Long turnoCajaId);

    /**
     * Invalida el comprobante de un documento cuyo pago dejó de existir, sin
     * borrarlo: conserva el número para no dejar huecos en la serie.
     *
     * <p>No falla si el documento nunca tuvo comprobante ni si ya estaba
     * anulado — se llama desde flujos de edición donde ambas cosas son
     * normales.
     */
    void anularDeDocumento(Integer empresaId, String origen, Long origenId, String motivo);
}

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
}

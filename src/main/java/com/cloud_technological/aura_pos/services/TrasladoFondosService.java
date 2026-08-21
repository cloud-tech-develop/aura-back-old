package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.traslado_fondos.CreateTrasladoFondosDto;
import com.cloud_technological.aura_pos.dto.traslado_fondos.TrasladoFondosDto;

/**
 * Mover dinero entre dos bolsillos de la propia empresa.
 *
 * <p>Cubre la constitución y el reembolso de la caja menor, la consignación del
 * efectivo del día al banco y los movimientos entre cuentas bancarias. Los tres
 * son el mismo hecho económico — cambia dónde está la plata, no cuánta hay — y
 * producen el mismo asiento: débito la cuenta destino, crédito la cuenta origen.
 */
public interface TrasladoFondosService {

    TrasladoFondosDto crear(Integer empresaId, Integer usuarioId, CreateTrasladoFondosDto dto);

    TrasladoFondosDto obtener(Long id, Integer empresaId);

    /** @param concepto opcional; null lista todos los conceptos. */
    List<TrasladoFondosDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta,
            String concepto);
}

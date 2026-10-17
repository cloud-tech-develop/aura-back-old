package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.caja.CerrarTurnoDto;
import com.cloud_technological.aura_pos.services.TurnoCajaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.TraductorIds;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Cierre de turno en la tienda. Payload: {@code CerrarTurnoDto}.
 *
 * <p>La nube recalcula el esperado con lo que ya recibió; como la cola sube en
 * orden, para entonces tiene todas las ventas y movimientos del turno.
 */
@Component
@RequiredArgsConstructor
public class TurnoCerrarManejador implements ManejadorComando {

    private final TurnoCajaService turnoService;
    private final TraductorIds ids;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.TURNO_CERRAR;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        CerrarTurnoDto dto;
        try {
            dto = objectMapper.treeToValue(c.getPayload(), CerrarTurnoDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cierre ilegible en el comando: " + e.getMessage(), e);
        }
        Long turnoId = ids.aNube(nodo.getId(), TiposComando.T_TURNO, c.getIdLocal());
        turnoService.cerrar(turnoId, dto, nodo.getEmpresaId());
        return Salida.de(turnoId);
    }
}

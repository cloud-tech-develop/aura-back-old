package com.cloud_technological.aura_pos.sync.nube.manejadores;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.caja.AbrirTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.services.TurnoCajaService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Apertura de turno de caja en la tienda. Payload: {@code AbrirTurnoDto}. */
@Component
@RequiredArgsConstructor
public class TurnoAbrirManejador implements ManejadorComando {

    private final TurnoCajaService turnoService;
    private final SyncNubeQueryRepository repo;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.TURNO_ABRIR;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        AbrirTurnoDto dto;
        try {
            dto = objectMapper.treeToValue(c.getPayload(), AbrirTurnoDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Turno ilegible en el comando: " + e.getMessage(), e);
        }
        TurnoCajaDto turno = turnoService.abrir(dto, nodo.getEmpresaId(), c.getUsuarioId());
        repo.guardarMapa(nodo.getId(), TiposComando.T_TURNO, c.getIdLocal(), turno.getId());
        return Salida.de(turno.getId());
    }
}

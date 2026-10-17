package com.cloud_technological.aura_pos.sync.nube.manejadores;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.nube.ManejadorComando;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Cliente (tercero) creado en la tienda, típicamente en el POS al facturar.
 *
 * <p>Si la nube ya tiene un tercero con ese documento (lo creó otra sede o la
 * oficina mientras la tienda estaba sin internet) se enlaza a ese en vez de
 * duplicarlo. En la siguiente bajada el nodo fusiona su copia con la de la nube.
 */
@Component
@RequiredArgsConstructor
public class TerceroCrearManejador implements ManejadorComando {

    private final ITerceroService terceroService;
    private final SyncNubeQueryRepository repo;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public String tipo() {
        return TiposComando.TERCERO_CREAR;
    }

    /** {@inheritDoc} */
    @Override
    public Salida reproducir(SyncDtos.Comando c, SyncDtos.NodoInfo nodo) {
        CreateTerceroDto dto;
        try {
            dto = objectMapper.treeToValue(c.getPayload(), CreateTerceroDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Tercero ilegible en el comando: " + e.getMessage(), e);
        }
        Optional<Long> existente = repo.terceroPorDocumento(nodo.getEmpresaId(), dto.getNumeroDocumento());
        Long id;
        if (existente.isPresent()) {
            id = existente.get();
        } else {
            TerceroDto creado = terceroService.crear(dto, nodo.getEmpresaId());
            id = creado.getId();
        }
        repo.guardarMapa(nodo.getId(), TiposComando.T_TERCERO, c.getIdLocal(), id);
        return Salida.de(id);
    }
}

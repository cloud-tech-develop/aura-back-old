package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.conceptos_caja.ConceptoCajaDto;
import com.cloud_technological.aura_pos.dto.conceptos_caja.CreateConceptoCajaDto;

public interface ConceptoCajaService {
    List<ConceptoCajaDto> listar(Integer empresaId, String tipo);
    ConceptoCajaDto crear(CreateConceptoCajaDto dto, Integer empresaId);
    ConceptoCajaDto actualizar(Long id, CreateConceptoCajaDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
}

package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.consumo_interno.ConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoTableDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.CreateConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.SaveConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ConsumoInternoService {
    PageImpl<ConsumoInternoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    ConsumoInternoDto obtenerPorId(Long id, Integer empresaId);

    ConsumoInternoDto crear(CreateConsumoInternoDto dto, Integer empresaId, Long usuarioId);

    void anular(Long id, Integer empresaId);

    List<ConceptoConsumoInternoDto> listarConceptos(Integer empresaId);

    ConceptoConsumoInternoDto guardarConcepto(Long id, SaveConceptoConsumoInternoDto dto, Integer empresaId);
}

package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoTableDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CreateCentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.UpdateCentroCostoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CentroCostoService {

    PageImpl<CentroCostoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    CentroCostoTableDto obtenerPorId(Long id, Integer empresaId);

    CentroCostoTableDto crear(CreateCentroCostoDto dto, Integer empresaId, Long usuarioId);

    CentroCostoTableDto actualizar(Long id, UpdateCentroCostoDto dto, Integer empresaId, Long usuarioId);

    void eliminar(Long id, Integer empresaId);

    List<CentroCostoDto> list(Integer empresaId);
}

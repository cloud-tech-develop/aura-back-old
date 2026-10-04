package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.permisos.SubmoduloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.CreateSubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateSubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloDto;

import java.util.List;

public interface SubmoduloService {
    SubmoduloTableDto crear(CreateSubmoduloDto dto);
    SubmoduloTableDto actualizar(Integer id, UpdateSubmoduloDto dto);
    void eliminar(Integer id);
    SubmoduloDto obtenerPorId(Integer id);
    List<SubmoduloTableDto> listarPorModulo(Integer moduloId);
    org.springframework.data.domain.PageImpl<SubmoduloTableDto> paginar(Integer moduloId, String search, int page, int size);
}

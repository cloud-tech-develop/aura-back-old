package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.proyecto.CreateProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateProyectoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ProyectoService {

    PageImpl<ProyectoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);

    ProyectoTableDto obtenerPorId(Long id, Integer empresaId);

    ProyectoTableDto crear(CreateProyectoDto dto, Integer empresaId, Long usuarioId);

    ProyectoTableDto actualizar(Long id, UpdateProyectoDto dto, Integer empresaId, Long usuarioId);

    void eliminar(Long id, Integer empresaId, Long usuarioId);

    List<ProyectoDto> list(Integer empresaId);
}

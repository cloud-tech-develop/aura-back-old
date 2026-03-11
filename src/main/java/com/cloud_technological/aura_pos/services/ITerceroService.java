package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.EstadoCuentaClienteDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTableDto;
import com.cloud_technological.aura_pos.dto.terceros.UpdateTerceroDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ITerceroService {
    PageImpl<TerceroTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    TerceroDto obtenerPorId(Long id, Integer empresaId);
    List<TerceroTableDto> listarClientes(String search, Integer empresaId);
    List<TerceroTableDto> listarProveedores(String search, Integer empresaId);
    List<TerceroTableDto> listarTodos(String search, Integer empresaId);
    TerceroDto crear(CreateTerceroDto dto, Integer empresaId);
    TerceroDto actualizar(Long id, UpdateTerceroDto dto, Integer empresaId);
    void eliminar(Long id, Integer empresaId);
    EstadoCuentaClienteDto obtenerEstadoCuenta(Long clienteId, Integer empresaId,
            String fechaDesde, String fechaHasta);
}

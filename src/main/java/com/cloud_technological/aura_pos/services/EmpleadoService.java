package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface EmpleadoService {
    PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    List<EmpleadoDto> listarVendedores(Integer empresaId);
    EmpleadoDto obtenerPorId(Long id, Integer empresaId);
    EmpleadoDto crear(CreateEmpleadoDto dto, Integer empresaId);
    EmpleadoDto actualizar(Long id, CreateEmpleadoDto dto, Integer empresaId);
    void retirar(Long id, Integer empresaId);
    
    /**
     * Sincroniza los datos del empleado con el usuario vinculado.
     * Actualiza el rol basándose en el cargo del empleado.
     * @param id ID del empleado
     * @param empresaId ID de la empresa
     */
    void sincronizarConUsuario(Long id, Integer empresaId);
}

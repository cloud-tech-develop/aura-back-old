package com.cloud_technological.aura_pos.services;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioFromEmpleadoDto;
import com.cloud_technological.aura_pos.dto.usuarios.UpdateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface UsuarioService {

    PageImpl<UsuarioTableDto> paginar(PageableDto<Object> pageable, Integer empresaId);

    UsuarioDto obtenerPorId(Integer id, Integer empresaId);

    UsuarioDto crear(CreateUsuarioDto dto, Integer empresaId);

    /**
     * Crea un usuario vinculado a un empleado existente. El cargo del empleado
     * se usa para determinar el rol.
     *
     * @param dto DTO con empleadoId, username y password
     * @return UsuarioDto con los datos creados
     */
    UsuarioDto crearDesdeEmpleado(CreateUsuarioFromEmpleadoDto dto, Integer empresaId);

    UsuarioDto actualizar(Integer id, UpdateUsuarioDto dto, Integer empresaId);

    void desactivar(Integer id, Integer empresaId);
}

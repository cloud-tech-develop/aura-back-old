package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioFromEmpleadoDto;
import com.cloud_technological.aura_pos.dto.usuarios.UpdateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioTableDto;
import com.cloud_technological.aura_pos.services.UsuarioService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<UsuarioTableDto>>> paginar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<UsuarioTableDto> result = usuarioService.paginar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioDto>> obtenerPorId(@PathVariable Integer id) {
        Integer empresaId = securityUtils.getEmpresaId();
        UsuarioDto result = usuarioService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Usuario encontrado", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<UsuarioDto>> crear(@Valid @RequestBody CreateUsuarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        UsuarioDto result = usuarioService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Usuario creado exitosamente", false, result), HttpStatus.CREATED);
    }

    /**
     * Crea un usuario vinculado a un empleado existente.
     * El cargo del empleado se usa para determinar el rol.
     * @param dto DTO con empleadoId, username y password
     * @return UsuarioDto con los datos creados
     */
    @PostMapping("/create-from-empleado")
    public ResponseEntity<ApiResponse<UsuarioDto>> crearDesdeEmpleado(@Valid @RequestBody CreateUsuarioFromEmpleadoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        UsuarioDto result = usuarioService.crearDesdeEmpleado(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Usuario creado exitosamente desde empleado", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioDto>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateUsuarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        UsuarioDto result = usuarioService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Usuario actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> desactivar(@PathVariable Integer id) {
        Integer empresaId = securityUtils.getEmpresaId();
        usuarioService.desactivar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Usuario desactivado correctamente", false, true), HttpStatus.OK);
    }
}

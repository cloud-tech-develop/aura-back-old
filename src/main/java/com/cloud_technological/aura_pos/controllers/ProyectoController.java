package com.cloud_technological.aura_pos.controllers;

import java.util.List;

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

import com.cloud_technological.aura_pos.dto.proyecto.CreateProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateProyectoDto;
import com.cloud_technological.aura_pos.services.ProyectoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    @Autowired private ProyectoService service;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ProyectoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ProyectoTableDto> result = service.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProyectoTableDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProyectoTableDto result = service.obtenerPorId(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Proyecto encontrado", false, result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProyectoTableDto>> crear(@Valid @RequestBody CreateProyectoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ProyectoTableDto result = service.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Proyecto creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProyectoTableDto>> actualizar(
            @PathVariable Long id, @Valid @RequestBody UpdateProyectoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ProyectoTableDto result = service.actualizar(id, dto, empresaId, usuarioId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Proyecto actualizado correctamente", false, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        service.eliminar(id, empresaId, usuarioId);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Proyecto eliminado correctamente", false, true));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ProyectoDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "", false, service.list(empresaId)));
    }
}

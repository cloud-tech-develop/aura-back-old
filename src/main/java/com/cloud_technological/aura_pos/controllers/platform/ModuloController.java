package com.cloud_technological.aura_pos.controllers.platform;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.permisos.CreateModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateModuloDto;
import com.cloud_technological.aura_pos.services.ModuloService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;

@RestController
@RequestMapping("/api/platform/modulos")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ModuloController {

    @Autowired
    private ModuloService moduloService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModuloDto>> obtenerPorId(@PathVariable Integer id) {
        ModuloDto result = moduloService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Módulo encontrado", false, result), HttpStatus.OK);
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ModuloTableDto>>> page(@RequestBody PageableDto<Object> pageable) {
        PageImpl<ModuloTableDto> result = moduloService.page(pageable);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ModuloTableDto>>> listar() {
        List<ModuloTableDto> result = moduloService.listar();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ModuloTableDto>> crear(@Valid @RequestBody CreateModuloDto dto) {
        ModuloTableDto result = moduloService.crear(dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Módulo creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ModuloTableDto>> actualizar(@PathVariable Integer id, @Valid @RequestBody UpdateModuloDto dto) {
        ModuloTableDto result = moduloService.actualizar(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Módulo actualizado exitosamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Integer id) {
        moduloService.eliminar(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Módulo eliminado exitosamente", false, null), HttpStatus.OK);
    }
}

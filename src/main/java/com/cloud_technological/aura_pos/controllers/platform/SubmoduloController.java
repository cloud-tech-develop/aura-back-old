package com.cloud_technological.aura_pos.controllers.platform;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.cloud_technological.aura_pos.dto.permisos.CreateSubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateSubmoduloDto;
import com.cloud_technological.aura_pos.services.SubmoduloService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

@RestController
@RequestMapping("/api/platform/submodulos")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SubmoduloController {

    @Autowired
    private SubmoduloService submoduloService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmoduloDto>> obtenerPorId(@PathVariable Integer id) {
        SubmoduloDto result = submoduloService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Submódulo encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/modulo/{moduloId}")
    public ResponseEntity<ApiResponse<List<SubmoduloTableDto>>> listarPorModulo(@PathVariable Integer moduloId) {
        List<SubmoduloTableDto> result = submoduloService.listarPorModulo(moduloId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmoduloTableDto>> crear(@Valid @RequestBody CreateSubmoduloDto dto) {
        SubmoduloTableDto result = submoduloService.crear(dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Submódulo creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmoduloTableDto>> actualizar(@PathVariable Integer id, @Valid @RequestBody UpdateSubmoduloDto dto) {
        SubmoduloTableDto result = submoduloService.actualizar(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Submódulo actualizado exitosamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Integer id) {
        submoduloService.eliminar(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Submódulo eliminado exitosamente", false, null), HttpStatus.OK);
    }
}

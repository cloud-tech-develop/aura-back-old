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
import java.util.List;

import com.cloud_technological.aura_pos.dto.unidad_medida.CreateUnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedida;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaTableDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UpdateUnidadMedidaDto;
import com.cloud_technological.aura_pos.services.UnidadMedidaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadMedidaController {

    @Autowired
    private UnidadMedidaService unidadMedidaService;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<UnidadMedidaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        PageImpl<UnidadMedidaTableDto> result = unidadMedidaService.listar(pageable);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnidadMedidaDto>> obtenerPorId(@PathVariable Long id) {
        UnidadMedidaDto result = unidadMedidaService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Unidad de medida encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UnidadMedidaDto>> crear(@Valid @RequestBody CreateUnidadMedidaDto dto) {
        UnidadMedidaDto result = unidadMedidaService.crear(dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Unidad de medida creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UnidadMedidaDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnidadMedidaDto dto) {
        UnidadMedidaDto result = unidadMedidaService.actualizar(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Unidad de medida actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        unidadMedidaService.eliminar(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Unidad de medida eliminada correctamente", false, true), HttpStatus.OK);
    }
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<UnidadMedida>>> list() {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, 
            unidadMedidaService.list()), HttpStatus.OK);
    }
}
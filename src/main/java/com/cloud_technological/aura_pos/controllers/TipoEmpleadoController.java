package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.tipo_empleado.CreateTipoEmpleadoDto;
import com.cloud_technological.aura_pos.dto.tipo_empleado.TipoEmpleadoDto;
import com.cloud_technological.aura_pos.dto.tipo_empleado.UpdateTipoEmpleadoDto;
import com.cloud_technological.aura_pos.services.TipoEmpleadoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

@RestController
@RequestMapping("/api/tipos-empleado")
public class TipoEmpleadoController {

    @Autowired
    private TipoEmpleadoService tipoEmpleadoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TipoEmpleadoDto>>> findAll() {
        List<TipoEmpleadoDto> tipos = tipoEmpleadoService.findAll();
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipos de empleado obtenidos", false, tipos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TipoEmpleadoDto>> findById(@PathVariable Long id) {
        TipoEmpleadoDto tipo = tipoEmpleadoService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipo de empleado obtenido", false, tipo));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TipoEmpleadoDto>> create(@RequestBody CreateTipoEmpleadoDto dto) {
        TipoEmpleadoDto tipo = tipoEmpleadoService.create(dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipo de empleado creado", false, tipo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TipoEmpleadoDto>> update(@PathVariable Long id, @RequestBody UpdateTipoEmpleadoDto dto) {
        TipoEmpleadoDto tipo = tipoEmpleadoService.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipo de empleado actualizado", false, tipo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tipoEmpleadoService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipo de empleado eliminado", false, null));
    }
}
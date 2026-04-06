package com.cloud_technological.aura_pos.controllers;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.rutas.CreateRutaDto;
import com.cloud_technological.aura_pos.dto.rutas.RutaDto;
import com.cloud_technological.aura_pos.dto.rutas.RutaTableDto;
import com.cloud_technological.aura_pos.dto.rutas.UpdateRutaDto;
import com.cloud_technological.aura_pos.services.RutaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    @Autowired
    private RutaService rutaService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<RutaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<RutaTableDto> result = rutaService.listar(pageable, empresaId);
        if (result.isEmpty()) {
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron rutas");
        }
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Rutas obtenidas", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RutaDto>> obtenerPorId(@PathVariable Long id) {
        try {
            Integer empresaId = securityUtils.getEmpresaId();
            RutaDto ruta = rutaService.findById(id, empresaId);
            return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Ruta obtenida", false, ruta), HttpStatus.OK);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<RutaDto>> crear(@RequestBody CreateRutaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        RutaDto ruta = rutaService.create(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Ruta creada", false, ruta), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RutaDto>> actualizar(@PathVariable Long id, @RequestBody UpdateRutaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        RutaDto ruta = rutaService.update(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Ruta actualizada", false, ruta), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        rutaService.delete(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Ruta eliminada", false, true), HttpStatus.OK);
    }

    @GetMapping("/vendedor/{vendedorId}")
    public ResponseEntity<ApiResponse<List<RutaDto>>> listarPorVendedor(@PathVariable Long vendedorId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<RutaDto> rutas = rutaService.findByVendedorAndActivas(vendedorId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Rutas del vendedor obtenidas", false, rutas), HttpStatus.OK);
    }

    @GetMapping("/validar")
    public ResponseEntity<ApiResponse<RutaDto>> validar(
            @RequestParam Long vendedorId,
            @RequestParam Long localId,
            @RequestParam Integer diaSemana) {
        Integer empresaId = securityUtils.getEmpresaId();
        RutaDto ruta = rutaService.findByVendedorLocalAndDia(vendedorId, localId, diaSemana, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Validación de ruta", false, ruta), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RutaDto>>> listarActivas() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<RutaDto> rutas = rutaService.findAllActivas(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Rutas activas obtenidas", false, rutas), HttpStatus.OK);
    }
}

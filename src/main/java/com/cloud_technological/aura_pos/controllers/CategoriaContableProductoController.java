package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.contabilidad.CategoriaContableProductoDto;
import com.cloud_technological.aura_pos.services.CategoriaContableProductoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** CRUD de categorías contables de producto (E4). */
@RestController
@RequestMapping("/api/contabilidad/categorias-producto")
public class CategoriaContableProductoController {

    @Autowired
    private CategoriaContableProductoService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoriaContableProductoDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.listar(empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoriaContableProductoDto>> crear(
            @Valid @RequestBody CategoriaContableProductoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaContableProductoDto created = service.crear(empresaId, dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Categoría contable creada", false, created),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoriaContableProductoDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaContableProductoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaContableProductoDto updated = service.actualizar(empresaId, id, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Categoría contable actualizada", false, updated));
    }

    /** Crea una categoría nueva copiando las cuentas de esta. */
    @PostMapping("/{id}/copiar")
    public ResponseEntity<ApiResponse<CategoriaContableProductoDto>> copiar(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaContableProductoDto creada = service.copiar(empresaId, id, body.get("nombre"));
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Categoría copiada", false, creada),
                HttpStatus.CREATED);
    }

    /** Siembra la categoría "General" (idempotente). */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<Void>> seed() {
        Integer empresaId = securityUtils.getEmpresaId();
        service.seedDefaults(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Categoría General sembrada", false, null));
    }
}

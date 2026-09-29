package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import java.util.Map;

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

import com.cloud_technological.aura_pos.dto.compras.CreateGastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.services.GastoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.CategoriaGasto;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    @Autowired private GastoService gastoService;
    @Autowired private SecurityUtils securityUtils;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GastoDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Gasto",
                false, gastoService.obtener(id, empresaId)));
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<GastoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Gastos",
                false, gastoService.listar(pageable, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GastoDto>> crear(@RequestBody CreateGastoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        GastoDto result = gastoService.crear(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "Gasto registrado", false, result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GastoDto>> actualizar(
            @PathVariable Long id, @RequestBody CreateGastoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        GastoDto result = gastoService.actualizar(id, dto, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Gasto actualizado", false, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        gastoService.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Gasto eliminado", false, null));
    }

    /**
     * El catálogo de categorías de gasto.
     *
     * <p>Lo expone para que el reporte deje de traducir códigos a mano. El
     * formulario todavía usa su propia constante en el front: migrarlo a este
     * endpoint es lo que cierra la duplicación.
     */
    @GetMapping("/categorias")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categorias() {
        List<Map<String, Object>> lista = java.util.Arrays.stream(CategoriaGasto.values())
                .map(c -> java.util.Map.<String, Object>of(
                        "value", c.codigo(),
                        "label", c.etiqueta(),
                        "deducible", c.deduciblePorDefecto()))
                .toList();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, lista));
    }
}
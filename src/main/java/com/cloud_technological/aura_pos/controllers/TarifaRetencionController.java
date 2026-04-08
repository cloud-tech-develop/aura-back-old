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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.retenciones.CreateTarifaRetencionDto;
import com.cloud_technological.aura_pos.dto.retenciones.RetencionesSugeridasDto;
import com.cloud_technological.aura_pos.dto.retenciones.TarifaRetencionDto;
import com.cloud_technological.aura_pos.services.implementations.TarifaRetencionServiceImpl;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/tarifas-retencion")
public class TarifaRetencionController {

    @Autowired private TarifaRetencionServiceImpl tarifaService;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<TarifaRetencionDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK",
                false, tarifaService.listar(pageable, empresaId)));
    }

    @GetMapping("/todas")
    public ResponseEntity<ApiResponse<List<TarifaRetencionDto>>> listarTodas() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK",
                false, tarifaService.listarTodas(empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TarifaRetencionDto>> crear(
            @Valid @RequestBody CreateTarifaRetencionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "Tarifa creada",
                        false, tarifaService.crear(dto, empresaId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TarifaRetencionDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CreateTarifaRetencionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Tarifa actualizada",
                false, tarifaService.actualizar(id, dto, empresaId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        tarifaService.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Tarifa eliminada",
                false, null));
    }

    @GetMapping("/sugeridas")
    public ResponseEntity<ApiResponse<RetencionesSugeridasDto>> sugeridas(
            @RequestParam Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK",
                false, tarifaService.obtenerSugeridas(terceroId, empresaId)));
    }
}

package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Anular;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Calculo;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Crear;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Detalle;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Fila;
import com.cloud_technological.aura_pos.services.causacion_gasto.CausacionGastoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Causación de gastos y servicios (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §4).
 * Permiso: el de Compras (PermisoRutas).
 */
@RestController
@RequestMapping("/api/causaciones-gasto")
public class CausacionGastoController {

    @Autowired
    private CausacionGastoService service;

    @Autowired
    private SecurityUtils securityUtils;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), msg, false, data));
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<Fila>>> listar(@RequestBody PageableDto<Object> pageable) {
        return ok("Listado exitoso", service.listar(securityUtils.getEmpresaId(), pageable));
    }

    /** Calcula IVA, retenciones y neto sin grabar. */
    @PostMapping("/calcular")
    public ResponseEntity<ApiResponse<Calculo>> calcular(@RequestBody Crear dto) {
        return ok("Cálculo", service.calcular(securityUtils.getEmpresaId(), dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Detalle>> crear(@RequestBody Crear dto) {
        Detalle d = service.crear(securityUtils.getEmpresaId(), usuarioId(), dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Causación " + d.getNumero() + " registrada", false, d), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Detalle>> obtener(@PathVariable Long id) {
        return ok("Causación", service.obtener(securityUtils.getEmpresaId(), id));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Detalle>> anular(@PathVariable Long id, @RequestBody Anular dto) {
        return ok("Causación anulada",
                service.anular(securityUtils.getEmpresaId(), usuarioId(), id, dto != null ? dto.getMotivo() : null));
    }

    private Integer usuarioId() {
        Long u = securityUtils.getUsuarioId();
        return u != null ? u.intValue() : null;
    }
}

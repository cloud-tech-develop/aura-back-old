package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.ordenes_compra.CreateOrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.OrdenCompraTableDto;
import com.cloud_technological.aura_pos.dto.ordenes_compra.RecepcionOrdenDto;
import com.cloud_technological.aura_pos.services.OrdenCompraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/ordenes-compra")
public class OrdenCompraController {

    @Autowired
    private OrdenCompraService ordenCompraService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrdenCompraTableDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                ordenCompraService.listar(empresaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrdenCompraDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                ordenCompraService.obtenerPorId(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrdenCompraDto>> crear(@Valid @RequestBody CreateOrdenCompraDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        OrdenCompraDto saved = ordenCompraService.crear(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Orden de compra creada", false, saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrdenCompraDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CreateOrdenCompraDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Orden actualizada", false,
                ordenCompraService.actualizar(id, dto, empresaId)));
    }

    @PatchMapping("/{id}/enviar")
    public ResponseEntity<ApiResponse<OrdenCompraDto>> enviar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Orden enviada al proveedor", false,
                ordenCompraService.enviar(id, empresaId)));
    }

    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<ApiResponse<OrdenCompraDto>> confirmar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Orden confirmada", false,
                ordenCompraService.confirmar(id, empresaId)));
    }

    @PostMapping("/{id}/recibir")
    public ResponseEntity<ApiResponse<OrdenCompraDto>> recibir(
            @PathVariable Long id,
            @Valid @RequestBody RecepcionOrdenDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        OrdenCompraDto orden = ordenCompraService.recibirMercancia(id, dto, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Recepción registrada", false, orden));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Void>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ordenCompraService.anular(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Orden anulada", false, null));
    }
}

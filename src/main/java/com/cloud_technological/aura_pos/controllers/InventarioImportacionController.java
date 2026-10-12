package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Bodegas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Conteo;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.SaldosInventario;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.services.importacion.InventarioImportacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Importar inventario desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2c): bodegas,
 * saldos iniciales y conteo físico. /validar no graba; /confirmar es todo o nada.
 * Cada ruta exige el permiso de su pantalla (PermisoRutas).
 */
@RestController
@RequestMapping("/api/importacion-inventario")
public class InventarioImportacionController {

    @Autowired
    private InventarioImportacionService service;

    @Autowired
    private SecurityUtils securityUtils;

    private static ResponseEntity<ApiResponse<Resultado>> ok(Resultado r) {
        return ResponseEntity.ok(new ApiResponse<>(200, r.isConfirmado() ? "Importación realizada" : "Validación",
                false, r));
    }

    @PostMapping("/bodegas/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarBodegas(@RequestBody Bodegas req) {
        return ok(service.validarBodegas(securityUtils.getEmpresaId(), securityUtils.getSucursalId(), req));
    }

    @PostMapping("/bodegas/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarBodegas(@RequestBody Bodegas req) {
        return ok(service.confirmarBodegas(securityUtils.getEmpresaId(), securityUtils.getSucursalId(), req));
    }

    @PostMapping("/saldos/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarSaldos(@RequestBody SaldosInventario req) {
        return ok(service.validarSaldos(securityUtils.getEmpresaId(), securityUtils.getSucursalId(), req));
    }

    @PostMapping("/saldos/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarSaldos(@RequestBody SaldosInventario req) {
        return ok(service.confirmarSaldos(securityUtils.getEmpresaId(), securityUtils.getSucursalId(), req));
    }

    @PostMapping("/conteo/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarConteo(@RequestBody Conteo req) {
        return ok(service.validarConteo(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/conteo/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarConteo(@RequestBody Conteo req) {
        return ok(service.confirmarConteo(securityUtils.getEmpresaId(), req));
    }
}

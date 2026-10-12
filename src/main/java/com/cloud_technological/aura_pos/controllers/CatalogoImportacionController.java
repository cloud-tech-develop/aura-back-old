package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Categorias;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Marcas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Precios;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Presentaciones;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Recetas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Productos;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService;
import com.cloud_technological.aura_pos.services.importacion.PreciosRecetasImportacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Importar catálogo desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2). El
 * archivo se lee en el navegador y llega como filas. /validar no graba; /confirmar
 * graba todo o nada. Cada ruta exige el permiso de su pantalla (PermisoRutas).
 */
@RestController
@RequestMapping("/api/importacion-catalogo")
public class CatalogoImportacionController {

    @Autowired
    private CatalogoImportacionService service;

    @Autowired
    private PreciosRecetasImportacionService service2;

    @Autowired
    private SecurityUtils securityUtils;

    private static ResponseEntity<ApiResponse<Resultado>> ok(Resultado r) {
        return ResponseEntity.ok(new ApiResponse<>(200, r.isConfirmado() ? "Importación realizada" : "Validación",
                false, r));
    }

    @PostMapping("/categorias/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarCategorias(@RequestBody Categorias req) {
        return ok(service.validarCategorias(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/categorias/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarCategorias(@RequestBody Categorias req) {
        return ok(service.confirmarCategorias(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/marcas/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarMarcas(@RequestBody Marcas req) {
        return ok(service.validarMarcas(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/marcas/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarMarcas(@RequestBody Marcas req) {
        return ok(service.confirmarMarcas(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/productos/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarProductos(@RequestBody Productos req) {
        return ok(service.validarProductos(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/productos/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarProductos(@RequestBody Productos req) {
        return ok(service.confirmarProductos(securityUtils.getEmpresaId(), req));
    }

    // ── Segunda parte (§2b): precios por lista, presentaciones, recetas ─────

    @PostMapping("/precios/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarPrecios(@RequestBody Precios req) {
        return ok(service2.validarPrecios(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/precios/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarPrecios(@RequestBody Precios req) {
        return ok(service2.confirmarPrecios(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/presentaciones/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarPresentaciones(@RequestBody Presentaciones req) {
        return ok(service2.validarPresentaciones(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/presentaciones/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarPresentaciones(@RequestBody Presentaciones req) {
        return ok(service2.confirmarPresentaciones(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/recetas/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarRecetas(@RequestBody Recetas req) {
        return ok(service2.validarRecetas(securityUtils.getEmpresaId(), req));
    }

    @PostMapping("/recetas/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarRecetas(@RequestBody Recetas req) {
        return ok(service2.confirmarRecetas(securityUtils.getEmpresaId(), req));
    }
}

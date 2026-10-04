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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.config.PermisoInterceptor;
import com.cloud_technological.aura_pos.config.RequerirPermiso;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.AccionEspecialDto;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.BloqueoLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.CambioLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.GuardarExcepciones;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.GuardarPerfil;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.ModoControl;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.NodoArbol;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PerfilDetalle;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PerfilFila;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PermisosDeUsuario;
import com.cloud_technological.aura_pos.services.permisos.PerfilService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Perfiles de permisos de la empresa y excepciones por usuario
 * (docs/PLAN_PERMISOS.md, fase P1). Lo protege el submódulo caja.perfiles.
 */
@RestController
@RequestMapping("/api/perfiles")
public class PerfilController {

    @Autowired
    private PerfilService service;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private PermisoInterceptor control;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PerfilFila>>> listar() {
        return ok(service.listar(securityUtils.getEmpresaId()));
    }

    /**
     * Para elegir el perfil en el formulario de usuario: lo usa quien tenga
     * Usuarios aunque no tenga la pantalla de perfiles.
     */
    @GetMapping("/opciones")
    @RequerirPermiso(claves = { "caja.usuarios", "caja.perfiles" }, accion = "VER")
    public ResponseEntity<ApiResponse<List<PerfilFila>>> opciones() {
        return ok(service.listar(securityUtils.getEmpresaId()).stream().filter(PerfilFila::isActivo).toList());
    }

    /** Submódulos que la empresa tiene activos, para la matriz de permisos. */
    @GetMapping("/arbol")
    @RequerirPermiso(claves = { "caja.usuarios", "caja.perfiles" }, accion = "VER")
    public ResponseEntity<ApiResponse<List<NodoArbol>>> arbol() {
        return ok(service.arbol(securityUtils.getEmpresaId()));
    }

    /** Acciones especiales de los submódulos que la empresa tiene (V192), para la matriz. */
    @GetMapping("/especiales")
    @RequerirPermiso(claves = { "caja.usuarios", "caja.perfiles" }, accion = "VER")
    public ResponseEntity<ApiResponse<List<AccionEspecialDto>>> especiales() {
        return ok(service.especiales(securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PerfilDetalle>> detalle(@PathVariable Long id) {
        return ok(service.detalle(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PerfilDetalle>> crear(@RequestBody GuardarPerfil req) {
        return ok(service.crear(req, securityUtils.getEmpresaId(), usuario()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PerfilDetalle>> actualizar(@PathVariable Long id, @RequestBody GuardarPerfil req) {
        return ok(service.actualizar(id, req, securityUtils.getEmpresaId(), usuario()));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<ApiResponse<PerfilDetalle>> duplicar(@PathVariable Long id) {
        return ok(service.duplicar(id, securityUtils.getEmpresaId(), usuario()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        service.eliminar(id, securityUtils.getEmpresaId(), usuario());
        return ok(null);
    }

    /** Perfil y excepciones de un usuario (pestaña "Permisos" del usuario). */
    @GetMapping("/usuarios/{usuarioId}")
    @RequerirPermiso(claves = { "caja.usuarios", "caja.perfiles" }, accion = "VER")
    public ResponseEntity<ApiResponse<PermisosDeUsuario>> permisosDeUsuario(@PathVariable Integer usuarioId) {
        return ok(service.permisosDeUsuario(usuarioId, securityUtils.getEmpresaId()));
    }

    @PutMapping("/usuarios/{usuarioId}")
    @RequerirPermiso(claves = { "caja.usuarios", "caja.perfiles" }, accion = "EDITAR")
    public ResponseEntity<ApiResponse<PermisosDeUsuario>> guardarExcepciones(@PathVariable Integer usuarioId,
            @RequestBody GuardarExcepciones req) {
        return ok(service.guardarExcepciones(usuarioId, req, securityUtils.getEmpresaId(), usuario(),
                securityUtils.getRol()));
    }

    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<CambioLog>>> historial() {
        return ok(service.historial(securityUtils.getEmpresaId()));
    }

    /** Lo que el back bloqueó o habría bloqueado en los últimos días. */
    @GetMapping("/bloqueos")
    public ResponseEntity<ApiResponse<List<BloqueoLog>>> bloqueos(@RequestParam(required = false) Integer dias) {
        return ok(service.bloqueos(securityUtils.getEmpresaId(), dias));
    }

    @GetMapping("/modo")
    @RequerirPermiso(libre = true)
    public ResponseEntity<ApiResponse<ModoControl>> modo() {
        ModoControl m = new ModoControl();
        m.setModo(control.getModo());
        return ok(m);
    }

    private Integer usuario() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}

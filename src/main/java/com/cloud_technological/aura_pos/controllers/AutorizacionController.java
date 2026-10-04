package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.config.RequerirPermiso;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.AutorizacionDada;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.CodigoGenerado;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.EstadoSolicitud;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Exceso;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.SolicitarAutorizacion;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.SolicitudPendiente;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.UsarCodigo;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.services.permisos.AutorizacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Autorización de un supervisor para pasar el límite de descuento o precio
 * (docs/PLAN_PERMISOS.md, fase P8). La clave del supervisor nunca viaja desde el
 * equipo del cajero: o le dicta un código de un solo uso que generó en su sesión,
 * o aprueba desde su sesión la solicitud remota.
 *
 * <p>Libre en el interceptor: lo usa cualquiera que venda. Lo del supervisor
 * (generar código, bandeja, aprobar, rechazar) lo exige el servicio siempre.
 */
@RestController
@RequestMapping("/api/autorizaciones")
@RequerirPermiso(libre = true)
public class AutorizacionController {

    @Autowired
    private AutorizacionService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Cuánto pasa la venta el límite del usuario (lo mismo que revisará el back al guardar). */
    @PostMapping("/venta/evaluar")
    public ResponseEntity<ApiResponse<Exceso>> evaluarVenta(@RequestBody CreateVentaDto dto) {
        return ok(service.exceso(dto, usuario(), securityUtils.getEmpresaId()));
    }

    // ── Cajero ───────────────────────────────────────────────────────────────

    @PostMapping("/usar-codigo")
    public ResponseEntity<ApiResponse<AutorizacionDada>> usarCodigo(@RequestBody UsarCodigo req) {
        return ok(service.usarCodigo(req, usuario(), securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitudes")
    public ResponseEntity<ApiResponse<EstadoSolicitud>> solicitar(@RequestBody SolicitarAutorizacion req) {
        return ok(service.solicitar(req, usuario(), securityUtils.getEmpresaId()));
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<ApiResponse<EstadoSolicitud>> estado(@PathVariable Long id) {
        return ok(service.estadoSolicitud(id, usuario(), securityUtils.getEmpresaId()));
    }

    // ── Supervisor ───────────────────────────────────────────────────────────

    @PostMapping("/codigo")
    public ResponseEntity<ApiResponse<CodigoGenerado>> generarCodigo() {
        return ok(service.generarCodigo(usuario(), securityUtils.getEmpresaId()));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<SolicitudPendiente>>> pendientes() {
        return ok(service.pendientes(usuario(), securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<ApiResponse<EstadoSolicitud>> aprobar(@PathVariable Long id) {
        return ok(service.aprobar(id, usuario(), securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<ApiResponse<EstadoSolicitud>> rechazar(@PathVariable Long id) {
        return ok(service.rechazar(id, usuario(), securityUtils.getEmpresaId()));
    }

    private Integer usuario() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}

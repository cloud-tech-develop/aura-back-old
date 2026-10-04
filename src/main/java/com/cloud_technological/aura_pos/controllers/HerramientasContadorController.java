package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.contabilidad.VistaPreviaAsientoDto;
import com.cloud_technological.aura_pos.services.implementations.HerramientasContadorService;
import com.cloud_technological.aura_pos.services.implementations.VistaPreviaAsientoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Herramientas del contador (Fase 4): vista previa del asiento, traslado de
 * cuentas y fusión de terceros.
 */
@RestController
@RequestMapping("/api/contabilidad/herramientas")
public class HerramientasContadorController {

    @Autowired private HerramientasContadorService service;
    @Autowired private VistaPreviaAsientoService vistaPrevia;
    @Autowired private SecurityUtils securityUtils;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) {
        return ResponseEntity.ok(new ApiResponse<>(200, msg, false, data));
    }

    // ── Vista previa ────────────────────────────────────────────────────

    @PostMapping("/vista-previa/compra")
    public ResponseEntity<ApiResponse<VistaPreviaAsientoDto>> vistaPreviaCompra(
            @RequestBody VistaPreviaAsientoDto.CompraRequest dto) {
        return ok("Vista previa", vistaPrevia.compra(dto, securityUtils.getEmpresaId()));
    }

    // ── Traslado de cuentas ─────────────────────────────────────────────

    public record TrasladoRequest(Long origenId, Long destinoId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Long terceroId, List<Long> detalleIds, String motivo,
            Boolean conConfiguracion, Boolean origenAgrupadora, Boolean soloConfiguracion) {
    }

    @GetMapping("/traslado-cuentas/vista-previa")
    public ResponseEntity<ApiResponse<HerramientasContadorService.ResumenTraslado>> vistaPreviaTraslado(
            @RequestParam Long origenId, @RequestParam Long destinoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long terceroId) {
        return ok("OK", service.vistaPreviaTraslado(securityUtils.getEmpresaId(), origenId, destinoId, desde, hasta,
                terceroId));
    }

    @PostMapping("/traslado-cuentas")
    public ResponseEntity<ApiResponse<HerramientasContadorService.ResultadoTraslado>> trasladar(
            @RequestBody TrasladoRequest r) {
        var res = service.trasladar(securityUtils.getEmpresaId(), r.origenId(), r.destinoId(), r.desde(), r.hasta(),
                r.terceroId(), r.detalleIds(), r.motivo(), securityUtils.getUsuarioId(),
                Boolean.TRUE.equals(r.conConfiguracion()), Boolean.TRUE.equals(r.origenAgrupadora()),
                Boolean.TRUE.equals(r.soloConfiguracion()));
        String msg = res.lineas() + " movimiento(s) trasladado(s)"
                + (res.configuracion() > 0 ? ", " + res.configuracion() + " registro(s) de configuración" : "")
                + (res.origenAgrupadora() ? "; la cuenta de origen quedó como agrupadora" : "");
        return ok(msg, res);
    }

    @GetMapping("/traslado-cuentas/historial")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> historialTraslados() {
        return ok("OK", service.historialTraslados(securityUtils.getEmpresaId()));
    }

    // ── Fusión de terceros ──────────────────────────────────────────────

    public record FusionRequest(Long origenId, Long destinoId, String motivo) {
    }

    @GetMapping("/fusion-terceros/vista-previa")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> vistaPreviaFusion(
            @RequestParam Long origenId, @RequestParam Long destinoId) {
        return ok("OK", service.vistaPreviaFusion(securityUtils.getEmpresaId(), origenId, destinoId));
    }

    @PostMapping("/fusion-terceros")
    public ResponseEntity<ApiResponse<Integer>> fusionar(@RequestBody FusionRequest r) {
        int n = service.fusionar(securityUtils.getEmpresaId(), r.origenId(), r.destinoId(), r.motivo(),
                securityUtils.getUsuarioId());
        return ok("Terceros fusionados: " + n + " registro(s) movido(s)", n);
    }

    @GetMapping("/fusion-terceros/historial")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> historialFusiones() {
        return ok("OK", service.historialFusiones(securityUtils.getEmpresaId()));
    }
}

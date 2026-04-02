package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.tesoreria.ConciliacionResumenDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CreateMovimientoDto;
import com.cloud_technological.aura_pos.dto.tesoreria.TesoreriaMovimientoDto;
import com.cloud_technological.aura_pos.services.TesoreriaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/tesoreria")
public class TesoreriaController {

    @Autowired
    private TesoreriaService tesoreriaService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Egresos ──────────────────────────────────────────────────────

    @GetMapping("/egresos")
    public ResponseEntity<ApiResponse<List<TesoreriaMovimientoDto>>> listarEgresos(
            @RequestParam(required = false) Long cuentaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                tesoreriaService.listarEgresos(empresaId, cuentaId, desde, hasta)));
    }

    @PostMapping("/egresos")
    public ResponseEntity<ApiResponse<TesoreriaMovimientoDto>> crearEgreso(
            @Valid @RequestBody CreateMovimientoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null ? securityUtils.getUsuarioId().intValue() : null;
        TesoreriaMovimientoDto saved = tesoreriaService.crearEgreso(empresaId, usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Egreso registrado", false, saved));
    }

    // ── Recaudos ─────────────────────────────────────────────────────

    @GetMapping("/recaudos")
    public ResponseEntity<ApiResponse<List<TesoreriaMovimientoDto>>> listarRecaudos(
            @RequestParam(required = false) Long cuentaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                tesoreriaService.listarRecaudos(empresaId, cuentaId, desde, hasta)));
    }

    @PostMapping("/recaudos")
    public ResponseEntity<ApiResponse<TesoreriaMovimientoDto>> crearRecaudo(
            @Valid @RequestBody CreateMovimientoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null ? securityUtils.getUsuarioId().intValue() : null;
        TesoreriaMovimientoDto saved = tesoreriaService.crearRecaudo(empresaId, usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Recaudo registrado", false, saved));
    }

    // ── Anular / Conciliar ───────────────────────────────────────────

    @PatchMapping("/movimientos/{id}/anular")
    public ResponseEntity<ApiResponse<Void>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        tesoreriaService.anular(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Movimiento anulado", false, null));
    }

    @PatchMapping("/movimientos/{id}/conciliar")
    public ResponseEntity<ApiResponse<Void>> conciliar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        tesoreriaService.toggleConciliado(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, null));
    }

    // ── Conciliación ─────────────────────────────────────────────────

    @GetMapping("/conciliacion")
    public ResponseEntity<ApiResponse<List<TesoreriaMovimientoDto>>> listarConciliacion(
            @RequestParam Long cuentaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                tesoreriaService.listarParaConciliacion(empresaId, cuentaId, desde, hasta)));
    }

    @GetMapping("/conciliacion/resumen")
    public ResponseEntity<ApiResponse<ConciliacionResumenDto>> getResumen(
            @RequestParam Long cuentaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                tesoreriaService.getResumen(empresaId, cuentaId, desde, hasta)));
    }

    @PatchMapping("/conciliacion/conciliar-lote")
    public ResponseEntity<ApiResponse<Void>> conciliarLote(@RequestBody List<Long> ids) {
        Integer empresaId = securityUtils.getEmpresaId();
        tesoreriaService.conciliarLote(ids, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Movimientos conciliados", false, null));
    }
}

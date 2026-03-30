package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.cartera.CarteraDashboardDto;
import com.cloud_technological.aura_pos.dto.cartera.ClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateGestionCobroDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateTerceroCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.CuentaVencidaAlertaDto;
import com.cloud_technological.aura_pos.dto.cartera.EdadCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.ValidacionCreditoDto;
import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;
import com.cloud_technological.aura_pos.services.CarteraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/cartera")
public class CarteraController {

    @Autowired
    private CarteraService carteraService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<CarteraDashboardDto>> dashboard() {
        Integer empresaId = securityUtils.getEmpresaId();
        CarteraDashboardDto dto = carteraService.dashboard(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, dto));
    }

    @GetMapping("/edades")
    public ResponseEntity<ApiResponse<List<EdadCarteraDto>>> edades() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
            carteraService.edadesCartera(empresaId)));
    }

    @GetMapping("/alertas")
    public ResponseEntity<ApiResponse<List<CuentaVencidaAlertaDto>>> alertas(
            @RequestParam(defaultValue = "20") int limit) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
            carteraService.alertasVencidas(empresaId, limit)));
    }

    // ── Clientes ──────────────────────────────────────────────────────────────

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<PageImpl<ClienteCarteraDto>>> clientes(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int rows,
            @RequestParam(required = false)    String search) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
            carteraService.listarClientes(empresaId, page, rows, search)));
    }

    // ── Cupo de crédito ───────────────────────────────────────────────────────

    @PostMapping("/credito")
    public ResponseEntity<ApiResponse<TerceroCreditoEntity>> abrirCredito(
            @Valid @RequestBody CreateTerceroCreditoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        TerceroCreditoEntity result = carteraService.abrirCredito(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiResponse<>(201, "Crédito configurado correctamente", false, result));
    }

    @PutMapping("/credito/{id}")
    public ResponseEntity<ApiResponse<TerceroCreditoEntity>> actualizarCredito(
            @PathVariable Long id,
            @Valid @RequestBody CreateTerceroCreditoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        TerceroCreditoEntity result = carteraService.actualizarCredito(id, dto, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Crédito actualizado", false, result));
    }

    @GetMapping("/credito/tercero/{terceroId}")
    public ResponseEntity<ApiResponse<TerceroCreditoEntity>> creditoTercero(
            @PathVariable Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
            carteraService.obtenerCreditoTercero(terceroId, empresaId)));
    }

    // ── Validación POS ────────────────────────────────────────────────────────

    @GetMapping("/validar-venta")
    public ResponseEntity<ApiResponse<ValidacionCreditoDto>> validarVenta(
            @RequestParam Long terceroId,
            @RequestParam BigDecimal monto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
            carteraService.validarVenta(terceroId, monto, empresaId)));
    }

    // ── Score / motor ─────────────────────────────────────────────────────────

    @PostMapping("/score/{terceroId}/recalcular")
    public ResponseEntity<ApiResponse<Void>> recalcularScore(@PathVariable Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        carteraService.recalcularScore(terceroId, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Score recalculado", false, null));
    }

    // ── Gestión de cobros ─────────────────────────────────────────────────────

    @PostMapping("/gestion")
    public ResponseEntity<ApiResponse<Void>> registrarGestion(
            @Valid @RequestBody CreateGestionCobroDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        carteraService.registrarGestion(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiResponse<>(201, "Gestión registrada", false, null));
    }

    // ── Solicitudes de autorización ───────────────────────────────────────────

    @PatchMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<ApiResponse<Void>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        carteraService.aprobarSolicitud(id, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Solicitud aprobada", false, null));
    }

    @PatchMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<ApiResponse<Void>> rechazar(
            @PathVariable Long id,
            @RequestParam String motivo) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        carteraService.rechazarSolicitud(id, motivo, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Solicitud rechazada", false, null));
    }
}

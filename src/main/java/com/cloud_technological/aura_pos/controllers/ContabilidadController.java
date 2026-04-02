package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Needed for auto-service generarDesde* endpoints

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;
import com.cloud_technological.aura_pos.services.AsientoContableService;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.PlanCuentasService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/contabilidad")
public class ContabilidadController {

    @Autowired
    private PlanCuentasService planCuentasService;

    @Autowired
    private AsientoContableService asientoService;

    @Autowired
    private ContabilidadAutoService autoService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Plan de Cuentas ──────────────────────────────────────────────

    @GetMapping("/plan-cuentas")
    public ResponseEntity<ApiResponse<List<PlanCuentaDto>>> listarPlan() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                planCuentasService.listar(empresaId)));
    }

    @PostMapping("/plan-cuentas")
    public ResponseEntity<ApiResponse<PlanCuentaDto>> crearCuenta(
            @Valid @RequestBody CreatePlanCuentaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PlanCuentaDto created = planCuentasService.crear(empresaId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Cuenta creada", false, created));
    }

    @PutMapping("/plan-cuentas/{id}")
    public ResponseEntity<ApiResponse<PlanCuentaDto>> actualizarCuenta(
            @PathVariable Long id,
            @Valid @RequestBody CreatePlanCuentaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Cuenta actualizada", false,
                planCuentasService.actualizar(id, empresaId, dto)));
    }

    @DeleteMapping("/plan-cuentas/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCuenta(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        planCuentasService.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Cuenta desactivada", false, null));
    }

    @PostMapping("/plan-cuentas/seed")
    public ResponseEntity<ApiResponse<Void>> seedPUC() {
        Integer empresaId = securityUtils.getEmpresaId();
        planCuentasService.seedPUC(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "PUC básico cargado", false, null));
    }

    // ── Asientos Contables ───────────────────────────────────────────

    @GetMapping("/asientos")
    public ResponseEntity<ApiResponse<List<AsientoContableTableDto>>> listarAsientos(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String tipoOrigen,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.listar(empresaId, d, h, tipoOrigen, page, rows)));
    }

    @GetMapping("/asientos/{id}")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> obtenerAsiento(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.obtenerConDetalles(id, empresaId)));
    }

    @PostMapping("/asientos")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> crearAsiento(
            @Valid @RequestBody CreateAsientoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto created = asientoService.crear(empresaId, usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Asiento creado", false, created));
    }

    @PatchMapping("/asientos/{id}/anular")
    public ResponseEntity<ApiResponse<Void>> anularAsiento(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        asientoService.anular(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Asiento anulado", false, null));
    }

    // ── Balance General ──────────────────────────────────────────────

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BalanceGeneralDto>> balanceGeneral(
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        String h = (hasta != null && !hasta.isBlank()) ? hasta
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.balanceGeneral(empresaId, h)));
    }

    // ── Estado de Resultados (P&G) ────────────────────────────────────

    @GetMapping("/estado-resultados")
    public ResponseEntity<ApiResponse<EstadoResultadosDto>> estadoResultados(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.estadoResultados(empresaId, d, h)));
    }

    // ── Libro Mayor ──────────────────────────────────────────────────

    @GetMapping("/libro-mayor")
    public ResponseEntity<ApiResponse<List<LibroMayorLineaDto>>> libroMayor(
            @RequestParam Long cuentaId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.libroMayor(empresaId, cuentaId, d, h)));
    }

    // ── Flujo de Caja ────────────────────────────────────────────────

    @GetMapping("/flujo-caja")
    public ResponseEntity<ApiResponse<FlujoCajaDto>> flujoCaja(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.flujoCaja(empresaId, d, h)));
    }

    // ── Asientos automáticos ─────────────────────────────────────────

    @PostMapping("/asientos/generar-desde-venta/{ventaId}")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> generarDesdeVenta(
            @PathVariable Long ventaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto result = autoService.generarDesdeVenta(ventaId, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Asiento generado", false, result));
    }

    @PostMapping("/asientos/generar-desde-compra/{compraId}")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> generarDesdeCompra(
            @PathVariable Long compraId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto result = autoService.generarDesdeCompra(compraId, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Asiento generado", false, result));
    }
}

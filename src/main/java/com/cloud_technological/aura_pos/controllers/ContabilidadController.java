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
import com.cloud_technological.aura_pos.dto.contabilidad.CreateComprobanteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateSaldosInicialesDto;
import com.cloud_technological.aura_pos.dto.contabilidad.DashboardContableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;
import com.cloud_technological.aura_pos.services.AperturaContableService;
import com.cloud_technological.aura_pos.services.AsientoContableService;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.DashboardContableService;
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
    private AperturaContableService aperturaService;

    @Autowired
    private DashboardContableService dashboardService;

    @Autowired
    private com.cloud_technological.aura_pos.services.ReprocesoContableService reprocesoService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Plan de Cuentas ──────────────────────────────────────────────

    @GetMapping("/plan-cuentas")
    public ResponseEntity<ApiResponse<List<PlanCuentaDto>>> listarPlan() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                planCuentasService.listar(empresaId)));
    }

    /**
     * Cuentas que pueden elegirse como origen de un pago. Es la lista del combo
     * "¿de dónde sale la plata?" de compras y gastos: caja, caja menor, bancos.
     */
    @GetMapping("/plan-cuentas/medios-pago")
    public ResponseEntity<ApiResponse<List<PlanCuentaDto>>> listarMediosPago() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                planCuentasService.listarMediosPago(empresaId)));
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

    // ── Comprobantes manuales (CD/CE/RC) ─────────────────────────────────

    @PostMapping("/comprobantes")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> crearComprobante(
            @Valid @RequestBody CreateComprobanteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto created = asientoService.crearComprobante(empresaId, usuarioId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Comprobante creado", false, created));
    }

    @GetMapping("/comprobantes/siguiente")
    public ResponseEntity<ApiResponse<String>> siguienteConsecutivo(
            @RequestParam(defaultValue = "CD") String tipo) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.siguienteConsecutivo(empresaId, tipo)));
    }

    // ── Saldos iniciales / apertura ──────────────────────────────────

    @GetMapping("/saldos-iniciales")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> obtenerApertura() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, aperturaService.obtener(empresaId)));
    }

    /** Líneas propuestas desde un auxiliar: INVENTARIO, ACTIVOS, DIFERIDOS, CARTERA, PROVEEDORES, BANCOS. */
    @GetMapping("/saldos-iniciales/sugerencias")
    public ResponseEntity<ApiResponse<java.util.List<com.cloud_technological.aura_pos.dto.contabilidad.SugerenciaSaldoInicialDto>>>
            sugerencias(@RequestParam String fuente) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, aperturaService.sugerencias(empresaId, fuente)));
    }

    @GetMapping("/saldos-iniciales/sugerencia-bancos")
    public ResponseEntity<ApiResponse<java.util.List<com.cloud_technological.aura_pos.dto.contabilidad.SaldoInicialLineaDto>>>
            sugerenciaBancos() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                aperturaService.sugerirDesdeBancos(empresaId)));
    }

    @PostMapping("/saldos-iniciales")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> guardarApertura(
            @Valid @RequestBody CreateSaldosInicialesDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto created = aperturaService.guardar(dto, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Saldos iniciales cargados", false, created));
    }

    /**
     * Atajo: crea la apertura directamente desde las cuentas bancarias usando el
     * saldo actual de cada una. Un solo tiro para migrar clientes con saldos.
     * POST /api/contabilidad/saldos-iniciales/desde-bancos?fecha=YYYY-MM-DD
     */
    @PostMapping("/saldos-iniciales/desde-bancos")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> guardarAperturaDesdeBancos(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto created = aperturaService.guardarDesdeBancos(empresaId, fecha, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Apertura cargada desde bancos", false, created));
    }

    @DeleteMapping("/saldos-iniciales")
    public ResponseEntity<ApiResponse<Void>> eliminarApertura() {
        Integer empresaId = securityUtils.getEmpresaId();
        aperturaService.eliminar(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Apertura eliminada", false, null));
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

    @GetMapping("/balance-detallado")
    public ResponseEntity<ApiResponse<com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDetalladoDto>> balanceGeneralDetallado(
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        String h = (hasta != null && !hasta.isBlank()) ? hasta
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.balanceGeneralDetallado(empresaId, h)));
    }

    // ── Estado de Resultados (P&G) ────────────────────────────────────

    @GetMapping("/estado-resultados")
    public ResponseEntity<ApiResponse<EstadoResultadosDto>> estadoResultados(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Long centroCostoId,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long frenteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.estadoResultados(empresaId, d, h, centroCostoId, proyectoId, frenteId)));
    }

    // ── Libro Mayor ──────────────────────────────────────────────────

    @GetMapping("/libro-mayor")
    public ResponseEntity<ApiResponse<List<LibroMayorLineaDto>>> libroMayor(
            @RequestParam Long cuentaId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Long centroCostoId,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long frenteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate hoy = LocalDate.now();
        String d = (desde != null && !desde.isBlank()) ? desde : hoy.withDayOfMonth(1).format(fmt);
        String h = (hasta != null && !hasta.isBlank()) ? hasta : hoy.format(fmt);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                asientoService.libroMayor(empresaId, cuentaId, d, h,
                        centroCostoId, proyectoId, frenteId)));
    }

    // ── Flujo de Caja ────────────────────────────────────────────────

    /**
     * Resumen del Centro de Contabilidad (/contabilidad en el front): KPIs del
     * mes y del anterior, serie enero..mes, distribución de gastos y pendientes.
     * Sin parámetros usa el mes en curso.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardContableDto>> dashboard(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate hoy = LocalDate.now();
        int a = anio != null ? anio : hoy.getYear();
        int m = mes != null ? mes : hoy.getMonthValue();
        if (m < 1 || m > 12) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, "El mes debe estar entre 1 y 12", true, null));
        }
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                dashboardService.resumen(empresaId, a, m)));
    }

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

    /**
     * Documentos sin exactamente un asiento vigente (0 = falta en el mayor,
     * más de 1 = duplicado). Por defecto, del año en curso.
     */
    @GetMapping("/asientos/sin-asiento")
    public ResponseEntity<ApiResponse<List<com.cloud_technological.aura_pos.dto.contabilidad.DocumentoSinAsientoDto>>> sinAsiento(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate hoy = LocalDate.now();
        LocalDate d = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : hoy.withDayOfYear(1);
        LocalDate h = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : hoy;
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, reprocesoService.detectar(empresaId, d, h)));
    }

    /** Genera el asiento de un documento que no lo tiene (idempotente). */
    @PostMapping("/asientos/reprocesar")
    public ResponseEntity<ApiResponse<Long>> reprocesar(@RequestBody java.util.Map<String, Object> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null ? securityUtils.getUsuarioId().intValue() : null;
        String tipo = body.get("tipoOrigen") != null ? body.get("tipoOrigen").toString() : null;
        Long id = body.get("origenId") != null ? Long.valueOf(body.get("origenId").toString()) : null;
        Long asientoId = reprocesoService.reprocesar(empresaId, usuarioId, tipo, id);
        return ResponseEntity.ok(new ApiResponse<>(200,
                asientoId != null ? "Asiento generado" : "El documento ya tenía asiento", false, asientoId));
    }

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

    /**
     * Regenera manualmente el asiento de una nómina ya aprobada (útil cuando el
     * asiento automático falló por falta de cuentas y ya se corrigió el PUC).
     */
    @PostMapping("/asientos/generar-desde-nomina/{nominaId}")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> generarDesdeNomina(
            @PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Integer usuarioId = securityUtils.getUsuarioId() != null
                ? securityUtils.getUsuarioId().intValue() : null;
        AsientoContableTableDto result = autoService.generarDesdeNomina(nominaId, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Asiento generado", false, result));
    }
}

package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Ajuste;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cabecera;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.CargarBalance;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Crear;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Reclasificar;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Resumen;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.services.conciliacion_fiscal.ConciliacionFiscalService;
import com.cloud_technological.aura_pos.services.conciliacion_fiscal.RenglonesFiscales;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.cloud_technological.aura_pos.services.conciliacion_fiscal.Formulario110;

/**
 * Conciliación fiscal 2516 / 2517 (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §5).
 * Permiso: el de Declaraciones (PermisoRutas).
 */
@RestController
@RequestMapping("/api/conciliacion-fiscal")
public class ConciliacionFiscalController {

    @Autowired
    private ConciliacionFiscalService service;

    @Autowired
    private SecurityUtils securityUtils;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), msg, false, data));
    }

    private Integer empresa() {
        return securityUtils.getEmpresaId();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Cabecera>>> listar() {
        return ok("Listado", service.listar(empresa()));
    }

    /** Catálogo de renglones (para clasificar cuentas y registrar ajustes). */
    @GetMapping("/renglones")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> renglones() {
        return ok("Renglones", RenglonesFiscales.TODOS.stream()
                .map(r -> Map.of("codigo", r.codigo(), "nombre", r.nombre(),
                        "seccion", r.seccion().name(), "seccionTitulo", r.seccion().titulo))
                .toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Cabecera>> crear(@RequestBody Crear dto) {
        Long u = securityUtils.getUsuarioId();
        return ok("Conciliación creada", service.crear(empresa(), u != null ? u.intValue() : null, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Resumen>> obtener(@PathVariable Long id) {
        return ok("Conciliación", service.obtener(empresa(), id));
    }

    /** Borrador del formulario 110 (renta): los valores que la contabilidad no sabe van en el cuerpo. */
    @PostMapping("/{id}/formulario-110")
    public ResponseEntity<ApiResponse<Formulario110.Resultado>> formulario110(
            @PathVariable Long id,
            @RequestBody(required = false) Formulario110.Manuales manuales) {
        return ok("Formulario 110", service.formulario110(empresa(), id, manuales));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        service.eliminar(empresa(), id);
        return ok("Conciliación eliminada", true);
    }

    @PutMapping("/{id}/tarifa")
    public ResponseEntity<ApiResponse<Cabecera>> tarifa(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        return ok("Tarifa actualizada", service.actualizarTarifa(empresa(), id, body.get("tarifaRenta")));
    }

    @PatchMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<Cabecera>> cerrar(@PathVariable Long id) {
        return ok("Conciliación cerrada", service.cambiarEstado(empresa(), id, true));
    }

    @PatchMapping("/{id}/reabrir")
    public ResponseEntity<ApiResponse<Cabecera>> reabrir(@PathVariable Long id) {
        return ok("Conciliación reabierta", service.cambiarEstado(empresa(), id, false));
    }

    // ── Balance ──

    @GetMapping("/{id}/cuentas")
    public ResponseEntity<ApiResponse<List<Cuenta>>> cuentas(@PathVariable Long id) {
        return ok("Cuentas", service.cuentas(empresa(), id));
    }

    @PostMapping("/{id}/balance/validar")
    public ResponseEntity<ApiResponse<Resultado>> validarBalance(@PathVariable Long id, @RequestBody CargarBalance body) {
        return ok("Validación", service.cargarExcel(empresa(), id, body.getFilas(), false));
    }

    @PostMapping("/{id}/balance/confirmar")
    public ResponseEntity<ApiResponse<Resultado>> confirmarBalance(@PathVariable Long id, @RequestBody CargarBalance body) {
        return ok("Balance cargado", service.cargarExcel(empresa(), id, body.getFilas(), true));
    }

    @PostMapping("/{id}/balance/sistema")
    public ResponseEntity<ApiResponse<Resumen>> balanceSistema(@PathVariable Long id) {
        return ok("Balance traído de la contabilidad", service.cargarSistema(empresa(), id));
    }

    @PutMapping("/{id}/cuentas/{cuentaId}/renglon")
    public ResponseEntity<ApiResponse<Cuenta>> reclasificar(@PathVariable Long id, @PathVariable Long cuentaId,
            @RequestBody Reclasificar body) {
        return ok("Cuenta clasificada", service.reclasificar(empresa(), id, cuentaId, body.getRenglon()));
    }

    // ── Ajustes ──

    @PostMapping("/{id}/ajustes")
    public ResponseEntity<ApiResponse<Ajuste>> agregarAjuste(@PathVariable Long id, @RequestBody Ajuste dto) {
        return ok("Ajuste registrado", service.agregarAjuste(empresa(), id, dto));
    }

    @DeleteMapping("/{id}/ajustes/{ajusteId}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarAjuste(@PathVariable Long id, @PathVariable Long ajusteId) {
        service.eliminarAjuste(empresa(), id, ajusteId);
        return ok("Ajuste eliminado", true);
    }
}

package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.cierre.CierreAnualService;
import com.cloud_technological.aura_pos.entity.CierreAnualEntity;
import com.cloud_technological.aura_pos.entity.DistribucionUtilidadesEntity;
import com.cloud_technological.aura_pos.entity.DividendoPagoEntity;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Cierre anual fiscal (E8): wizard de cierre de ejercicio (provisión de renta
 * digitada por el contador + cierre a 3605 que ya hace el período), traslado
 * 3605→3705 al abrir el año y distribución de utilidades post-asamblea.
 */
@RestController
@RequestMapping("/api/contabilidad/cierre-anual")
public class CierreAnualController {

    @Autowired
    private CierreAnualService service;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Wizard paso 1: provisión de renta ───────────────────────────────

    /** Sugerencia utilidad × tarifa (default 35%); el contador digita el valor real. */
    @GetMapping("/provision-renta/sugerencia")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sugerirProvision(
            @RequestParam int anio,
            @RequestParam(required = false) BigDecimal tarifa) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.sugerirProvisionRenta(empresaId, anio, tarifa)));
    }

    @PostMapping("/provision-renta")
    public ResponseEntity<ApiResponse<CierreAnualEntity>> provisionarRenta(
            @RequestBody ProvisionRentaRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CierreAnualEntity op = service.provisionarRenta(empresaId, usuarioId,
                body.anio(), body.monto(), body.detalle(), body.fecha());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Provisión de renta contabilizada", false, op), HttpStatus.CREATED);
    }

    // ── Apertura de año: traslado 3605 → 3705 ───────────────────────────

    @PostMapping("/traslado")
    public ResponseEntity<ApiResponse<CierreAnualEntity>> trasladarUtilidad(
            @RequestBody TrasladoRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CierreAnualEntity op = service.trasladarUtilidad(empresaId, usuarioId,
                body.anio(), body.fecha());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Traslado a resultados acumulados contabilizado", false, op), HttpStatus.CREATED);
    }

    @GetMapping("/operaciones")
    public ResponseEntity<ApiResponse<List<CierreAnualEntity>>> listarOperaciones() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listarOperaciones(empresaId)));
    }

    // ── Distribución de utilidades ───────────────────────────────────────

    /** Saldos 3705/330505/3105 + reserva sugerida (10%, tope 50% del capital). */
    @GetMapping("/distribucion/sugerencia")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sugerirDistribucion() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.sugerirDistribucion(empresaId)));
    }

    @PostMapping("/distribucion")
    public ResponseEntity<ApiResponse<DistribucionUtilidadesEntity>> distribuir(
            @RequestBody DistribucionRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        DistribucionUtilidadesEntity d = service.distribuir(empresaId, usuarioId,
                body.anio(), body.reservaLegal(), body.dividendos(),
                body.observaciones(), body.fecha());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Distribución de utilidades contabilizada", false, d), HttpStatus.CREATED);
    }

    @GetMapping("/distribuciones")
    public ResponseEntity<ApiResponse<List<DistribucionUtilidadesEntity>>> listarDistribuciones() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listarDistribuciones(empresaId)));
    }

    @GetMapping("/distribuciones/{id}/pagos")
    public ResponseEntity<ApiResponse<List<DividendoPagoEntity>>> listarPagos(
            @PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listarPagos(empresaId, id)));
    }

    @PostMapping("/dividendos/pagos")
    public ResponseEntity<ApiResponse<DividendoPagoEntity>> pagarDividendos(
            @RequestBody PagoDividendoRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        DividendoPagoEntity pago = service.pagarDividendos(empresaId, usuarioId,
                body.distribucionId(), body.monto(), body.metodoPago(),
                body.cuentaBancariaId(), body.terceroId(), body.fecha());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Pago de dividendos contabilizado", false, pago), HttpStatus.CREATED);
    }

    // ── Cuerpos de petición ──────────────────────────────────────────────

    public record ProvisionRentaRequest(int anio, BigDecimal monto, String detalle, LocalDate fecha) {
    }

    public record TrasladoRequest(int anio, LocalDate fecha) {
    }

    public record DistribucionRequest(int anio, BigDecimal reservaLegal, BigDecimal dividendos,
            String observaciones, LocalDate fecha) {
    }

    public record PagoDividendoRequest(Long distribucionId, BigDecimal monto, String metodoPago,
            Long cuentaBancariaId, Long terceroId, LocalDate fecha) {
    }
}

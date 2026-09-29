package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.facturacion.FacturaDto;
import com.cloud_technological.aura_pos.entity.FacturaLogEntity;
import com.cloud_technological.aura_pos.services.FacturaService;
import com.cloud_technological.aura_pos.services.FacturaLogService;
import com.cloud_technological.aura_pos.services.implementations.FacturaRetryService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaService facturaService;
    private final FacturaLogService facturaLogService;
    private final FacturaRetryService facturaRetryService;
    private final com.cloud_technological.aura_pos.services.implementations.FactusTokenService factusTokenService;
    private final com.cloud_technological.aura_pos.utils.SecurityUtils securityUtils;

    @Autowired
    public FacturaController(FacturaService facturaService,
                             FacturaLogService facturaLogService,
                             FacturaRetryService facturaRetryService,
                             com.cloud_technological.aura_pos.services.implementations.FactusTokenService factusTokenService,
                             com.cloud_technological.aura_pos.utils.SecurityUtils securityUtils) {
        this.facturaService = facturaService;
        this.facturaLogService = facturaLogService;
        this.facturaRetryService = facturaRetryService;
        this.factusTokenService = factusTokenService;
        this.securityUtils = securityUtils;
    }

    /**
     * Fuerza la regeneración del token de Factus con las credenciales actuales de
     * la empresa (descarta el token cacheado). Úsalo tras cambiar de cuenta Factus.
     */
    @PostMapping("/token/refrescar")
    public ResponseEntity<java.util.Map<String, Object>> refrescarToken() {
        Integer empresaId = securityUtils.getEmpresaId();
        factusTokenService.forzarRefresco(empresaId);
        return ResponseEntity.ok(java.util.Map.of(
                "ok", true, "message", "Token de Factus regenerado con las credenciales actuales"));
    }

    /**
     * Crea una factura electrónica a partir de una venta existente.
     * POST /api/facturas/desde-venta?ventaId=1&empresaId=1&usuarioId=1
     */
    @PostMapping("/desde-venta")
    public ResponseEntity<FacturaDto> crearDesdeVenta(
            @RequestParam Long ventaId,
            @RequestParam Integer empresaId,
            @RequestParam Integer usuarioId) {
        FacturaDto factura = facturaService.crearDesdeVenta(ventaId, empresaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(factura);
    }

    /**
     * Obtiene el historial de logs de una factura específica.
     * GET /api/facturas/{facturaId}/logs
     */
    @GetMapping("/{facturaId}/logs")
    public ResponseEntity<List<FacturaLogEntity>> obtenerLogs(@PathVariable Long facturaId) {
        List<FacturaLogEntity> logs = facturaLogService.obtenerPorFactura(facturaId);
        return ResponseEntity.ok(logs);
    }

    /**
     * Reintenta manualmente el flujo de una factura en estado PENDIENTE.
     * Lee el metadata del último log PENDIENTE y re-ejecuta el action correspondiente.
     * POST /api/facturas/{facturaId}/reintentar
     */
    @PostMapping("/{facturaId}/reintentar")
    public ResponseEntity<String> reintentar(@PathVariable Long facturaId) {
        boolean ejecutado = facturaRetryService.reintentar(facturaId);
        if (ejecutado) {
            return ResponseEntity.ok("Reintento ejecutado exitosamente para factura " + facturaId);
        } else {
            throw new GlobalException(HttpStatus.NOT_FOUND,
                "No se encontró un log PENDIENTE con payload de reintento para la factura " + facturaId);
        }
    }
}

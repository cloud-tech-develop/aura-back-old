package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.correo.CorreoDtos;
import com.cloud_technological.aura_pos.services.correo.CobroCorreoService;
import com.cloud_technological.aura_pos.services.correo.CorreoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * Envío de documentos por correo (docs/PLAN_CUENTA_COBRO_CORREO.md). Cada grupo de
 * rutas exige el permiso de su pantalla (PermisoRutas): /facturas → Facturas de
 * venta, /cotizaciones → Cotizaciones, /nomina → Liquidación de nómina, /cartera → Cartera. "enviar"
 * exige editar; las consultas, ver.
 */
@RestController
@RequestMapping("/api/correos")
@RequiredArgsConstructor
public class CorreoController {

    private final CorreoService service;
    private final CobroCorreoService cobros;
    private final SecurityUtils securityUtils;

    // ── Factura y cuenta de cobro ─────────────────────────────────────────

    @GetMapping("/facturas/{id}/sugerencia")
    public ResponseEntity<ApiResponse<CorreoDtos.Sugerencia>> sugerenciaFactura(@PathVariable Long id) {
        return ok("Sugerencia", service.sugerenciaFactura(securityUtils.getEmpresaId(), id));
    }

    @GetMapping("/facturas/{id}")
    public ResponseEntity<ApiResponse<List<CorreoDtos.Fila>>> historialFactura(@PathVariable Long id) {
        return ok("Correos de la factura", service.historialFactura(securityUtils.getEmpresaId(), id));
    }

    @PostMapping("/facturas/{id}/enviar")
    public ResponseEntity<ApiResponse<CorreoDtos.Fila>> enviarFactura(@PathVariable Long id,
            @Valid @RequestBody CorreoDtos.Enviar dto) {
        return ok("Correo enviado", service.enviarFactura(securityUtils.getEmpresaId(), id, dto, actor()));
    }

    // ── Cotización ───────────────────────────────────────────────────────

    @GetMapping("/cotizaciones/{id}/sugerencia")
    public ResponseEntity<ApiResponse<CorreoDtos.Sugerencia>> sugerenciaCotizacion(@PathVariable Long id) {
        return ok("Sugerencia", service.sugerenciaCotizacion(securityUtils.getEmpresaId(), id));
    }

    @GetMapping("/cotizaciones/{id}")
    public ResponseEntity<ApiResponse<List<CorreoDtos.Fila>>> historialCotizacion(@PathVariable Long id) {
        return ok("Correos de la cotización", service.historialCotizacion(securityUtils.getEmpresaId(), id));
    }

    @PostMapping("/cotizaciones/{id}/enviar")
    public ResponseEntity<ApiResponse<CorreoDtos.Fila>> enviarCotizacion(@PathVariable Long id,
            @Valid @RequestBody CorreoDtos.Enviar dto) {
        return ok("Correo enviado", service.enviarCotizacion(securityUtils.getEmpresaId(), id, dto, actor()));
    }

    // ── Comprobantes de nómina en masa ──────────────────────────────────

    @GetMapping("/nomina/sugerencia")
    public ResponseEntity<ApiResponse<CorreoDtos.Sugerencia>> sugerenciaNomina() {
        return ok("Sugerencia", service.sugerenciaNomina(securityUtils.getEmpresaId()));
    }

    /** Encola una tanda (máx. 50); las siguientes tandas mandan el loteId de la primera. */
    @PostMapping("/nomina/lote/enviar")
    public ResponseEntity<ApiResponse<CorreoDtos.LoteEncolado>> encolarNomina(@Valid @RequestBody CorreoDtos.Lote dto) {
        return ok("Comprobantes en cola", service.encolarNomina(securityUtils.getEmpresaId(), dto, actor()));
    }

    @GetMapping("/nomina/lote/{loteId}")
    public ResponseEntity<ApiResponse<CorreoDtos.ResumenLote>> resumenLote(@PathVariable String loteId) {
        return ok("Avance del envío", service.resumenLote(securityUtils.getEmpresaId(), loteId));
    }

    // ── Cobro de cartera (estado de cuenta adjunto, lo arma el servidor) ─

    /** Recordatorio a todos los clientes con facturas vencidas: asunto y mensaje propuestos. */
    @GetMapping("/cartera/vencidos/sugerencia")
    public ResponseEntity<ApiResponse<CorreoDtos.Sugerencia>> sugerenciaCobroMasivo() {
        return ok("Sugerencia", cobros.sugerenciaMasiva(securityUtils.getEmpresaId()));
    }

    @PostMapping("/cartera/vencidos/enviar")
    public ResponseEntity<ApiResponse<CorreoDtos.LoteEncolado>> cobrarVencidos(
            @Valid @RequestBody CorreoDtos.CobroMasivo dto) {
        return ok("Recordatorios en cola", cobros.encolarVencidos(securityUtils.getEmpresaId(), dto, actor()));
    }

    @GetMapping("/cartera/lote/{loteId}")
    public ResponseEntity<ApiResponse<CorreoDtos.ResumenLote>> resumenLoteCobro(@PathVariable String loteId) {
        return ok("Avance del envío", service.resumenLote(securityUtils.getEmpresaId(), loteId));
    }

    @GetMapping("/cartera/{terceroId}/sugerencia")
    public ResponseEntity<ApiResponse<CorreoDtos.Sugerencia>> sugerenciaCobro(@PathVariable Long terceroId) {
        return ok("Sugerencia", cobros.sugerencia(securityUtils.getEmpresaId(), terceroId));
    }

    @GetMapping("/cartera/{terceroId}")
    public ResponseEntity<ApiResponse<List<CorreoDtos.Fila>>> historialCobro(@PathVariable Long terceroId) {
        return ok("Cobros enviados", cobros.historial(securityUtils.getEmpresaId(), terceroId));
    }

    /** Recordatorio de pago a un cliente con su estado de cuenta adjunto. */
    @PostMapping("/cartera/{terceroId}/enviar")
    public ResponseEntity<ApiResponse<CorreoDtos.Fila>> enviarCobro(@PathVariable Long terceroId,
            @Valid @RequestBody CorreoDtos.Enviar dto) {
        return ok("Correo enviado", cobros.enviar(securityUtils.getEmpresaId(), terceroId, dto, actor()));
    }

    private Integer actor() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

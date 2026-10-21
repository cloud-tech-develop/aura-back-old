package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.CertificadoRetencionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.RegistrarPagoDeclaracionDto;
import com.cloud_technological.aura_pos.services.implementations.DeclaracionPagoService;
import com.cloud_technological.aura_pos.services.DeclaracionesService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.ParametrosIcaDto;
import java.util.Map;

/** Borradores de declaraciones leídos del mayor. */
@RestController
@RequestMapping("/api/contabilidad/declaraciones")
public class DeclaracionesController {

    @Autowired
    private DeclaracionesService service;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private DeclaracionPagoService pagos;

    /** IVA (formulario 300): el período lo elige el usuario (bimestral o cuatrimestral). */
    @GetMapping("/iva")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto>> iva(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Borrador IVA", false,
                service.iva(securityUtils.getEmpresaId(), desde, hasta)));
    }

    /** Retención en la fuente (formulario 350): mensual. */
    @GetMapping("/retencion")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto>> retencion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Borrador retención", false,
                service.retencion(securityUtils.getEmpresaId(), desde, hasta)));
    }

    /**
     * Certificados de retención por tercero: tipo FUENTE, IVA o ICA. Sin
     * terceroId trae a todos los terceros a los que se les retuvo en el período.
     */
    @GetMapping("/certificados")
    public ResponseEntity<ApiResponse<CertificadoRetencionDto>> certificados(
            @RequestParam String tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long terceroId) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Certificados de retención", false,
                service.certificados(securityUtils.getEmpresaId(), tipo, desde, hasta, terceroId)));
    }

    /** Industria y comercio (municipal): la tarifa y las deducciones las escribe el contador. */
    @PostMapping("/ica")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto>> ica(
            @RequestBody ParametrosIcaDto p) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Borrador ICA", false,
                service.ica(securityUtils.getEmpresaId(), p)));
    }

    /** Causa el ICA del borrador: nota contable DB 511505 · CR 2412 a nombre del municipio. */
    @PostMapping("/ica/causar")
    public ResponseEntity<ApiResponse<String>> causarIca(
            @RequestBody ParametrosIcaDto p) {
        String numero = service.causarIca(securityUtils.getEmpresaId(), usuarioId(), p);
        return ResponseEntity.ok(new ApiResponse<>(200, "ICA causado en la nota " + numero, false, numero));
    }

    /** Registra el pago de la declaración a un acreedor (V204): cierra las cuentas del impuesto. */
    @PostMapping("/pagos")
    public ResponseEntity<ApiResponse<BorradorDeclaracionDto.PorPagar>> registrarPago(
            @RequestBody RegistrarPagoDeclaracionDto dto) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Pago registrado", false,
                pagos.registrar(securityUtils.getEmpresaId(), usuarioId(), dto)));
    }

    /** Anula el pago: el contraasiento sale con fecha de hoy. */
    @PatchMapping("/pagos/{id}/anular")
    public ResponseEntity<ApiResponse<Void>> anularPago(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        pagos.anular(securityUtils.getEmpresaId(), usuarioId(), id, body != null ? body.get("motivo") : null);
        return ResponseEntity.ok(new ApiResponse<>(200, "Pago anulado", false, null));
    }

    private Integer usuarioId() {
        Long u = securityUtils.getUsuarioId();
        return u != null ? u.intValue() : null;
    }
}

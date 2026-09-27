package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.documento_soporte.DocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.EmitirDocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.PreviaDocumentoSoporteDto;
import com.cloud_technological.aura_pos.services.DocumentoSoporteService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Documento soporte electrónico (Factus v2) desde compras y gastos. */
@RestController
@RequestMapping("/api/documentos-soporte")
public class DocumentoSoporteController {

    @Autowired
    private DocumentoSoporteService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/previa")
    public ResponseEntity<ApiResponse<PreviaDocumentoSoporteDto>> previa(
            @RequestParam String origenTipo, @RequestParam Long origenId) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.previa(securityUtils.getEmpresaId(), origenTipo, origenId)));
    }

    /**
     * Siempre 200 con el documento: si la DIAN lo rechaza, el estado es
     * RECHAZADO y el mensaje de error viene en el cuerpo para mostrarlo.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DocumentoSoporteDto>> emitir(
            @Valid @RequestBody EmitirDocumentoSoporteDto dto) {
        Long usuarioId = securityUtils.getUsuarioId();
        DocumentoSoporteDto res = service.emitir(securityUtils.getEmpresaId(),
                usuarioId != null ? usuarioId.intValue() : null, dto);
        String msg = "ACEPTADO".equals(res.getEstado())
                ? "Documento soporte aceptado por la DIAN"
                : "La DIAN rechazó el documento soporte";
        return ResponseEntity.ok(new ApiResponse<>(200, msg, false, res));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentoSoporteDto>>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listar(securityUtils.getEmpresaId(), desde, hasta)));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<ApiResponse<Map<String, String>>> pdf(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                Map.of("pdfBase64", service.pdf(securityUtils.getEmpresaId(), id))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentoSoporteDto>> descartar(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Intento descartado", false,
                service.descartar(securityUtils.getEmpresaId(), id)));
    }
}

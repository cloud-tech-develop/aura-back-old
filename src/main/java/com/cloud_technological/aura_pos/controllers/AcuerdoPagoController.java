package com.cloud_technological.aura_pos.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoPagoDto;
import com.cloud_technological.aura_pos.dto.cartera.acuerdo.CreateAcuerdoPagoDto;
import com.cloud_technological.aura_pos.services.implementations.AcuerdoPagoService;
import com.cloud_technological.aura_pos.services.implementations.CuentaPdfService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Acuerdos de pago en cuotas (C4). */
@RestController
@RequestMapping("/api/cartera/acuerdos")
public class AcuerdoPagoController {

    @Autowired
    private AcuerdoPagoService service;

    @Autowired
    private CuentaPdfService pdfService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<PageImpl<AcuerdoPagoDto>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listar(securityUtils.getEmpresaId(), estado, search, page, rows)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AcuerdoPagoDto>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.obtener(id, securityUtils.getEmpresaId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AcuerdoPagoDto>> crear(@RequestBody CreateAcuerdoPagoDto dto) {
        AcuerdoPagoDto creado = service.crear(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Acuerdo " + creado.getNumero() + " registrado", false, creado), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<AcuerdoPagoDto>> anular(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        AcuerdoPagoDto anulado = service.anular(id, body != null ? body.get("motivo") : null,
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(200, "Acuerdo anulado", false, anulado));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = pdfService.generarAcuerdoPago(id, securityUtils.getEmpresaId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("acuerdo_pago_" + id + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}

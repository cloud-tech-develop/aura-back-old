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

import com.cloud_technological.aura_pos.dto.cartera.ficha.FichaClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.CreateReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaTableDto;
import com.cloud_technological.aura_pos.services.ReciboCajaService;
import com.cloud_technological.aura_pos.services.implementations.CuentaPdfService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Recibos de caja multi-factura y ficha del cliente de cartera. */
@RestController
@RequestMapping("/api/cartera")
public class ReciboCajaController {

    @Autowired
    private ReciboCajaService service;

    @Autowired
    private CuentaPdfService pdfService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/clientes/{terceroId}/ficha")
    public ResponseEntity<ApiResponse<FichaClienteCarteraDto>> ficha(@PathVariable Long terceroId) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.ficha(terceroId, securityUtils.getEmpresaId())));
    }

    @GetMapping("/recibos")
    public ResponseEntity<ApiResponse<PageImpl<ReciboCajaTableDto>>> listar(
            @RequestParam(required = false) Long terceroId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listar(securityUtils.getEmpresaId(), terceroId, estado, search, page, rows)));
    }

    @GetMapping("/recibos/{id}")
    public ResponseEntity<ApiResponse<ReciboCajaDto>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.obtener(id, securityUtils.getEmpresaId())));
    }

    @PostMapping("/recibos")
    public ResponseEntity<ApiResponse<ReciboCajaDto>> crear(@RequestBody CreateReciboCajaDto dto) {
        ReciboCajaDto creado = service.crear(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Recibo " + creado.getNumero() + " registrado", false, creado), HttpStatus.CREATED);
    }

    @PostMapping("/recibos/{id}/anular")
    public ResponseEntity<ApiResponse<ReciboCajaDto>> anular(@PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ReciboCajaDto anulado = service.anular(id, body != null ? body.get("motivo") : null,
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(200, "Recibo anulado", false, anulado));
    }

    @GetMapping("/recibos/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = pdfService.generarReciboCajaCartera(id, securityUtils.getEmpresaId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("recibo_caja_" + id + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}

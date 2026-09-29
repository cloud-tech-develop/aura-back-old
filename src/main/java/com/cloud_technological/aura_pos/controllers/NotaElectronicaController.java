package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.factus.CrearNotaCreditoDto;
import com.cloud_technological.aura_pos.dto.factus.NotaElectronicaEstadoDto;
import com.cloud_technological.aura_pos.entity.NotaElectronicaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.NotaElectronicaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.FactusNotaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Notas crédito/débito electrónicas (Factus v1). Pertenece a facturación de
 * ventas: las notas referencian facturas de venta.
 */
@RestController
@RequestMapping("/api/nota-electronica")
public class NotaElectronicaController {

    private final FactusNotaService notaService;
    private final NotaElectronicaJPARepository notaRepo;
    private final SecurityUtils securityUtils;

    public NotaElectronicaController(FactusNotaService notaService,
                                     NotaElectronicaJPARepository notaRepo,
                                     SecurityUtils securityUtils) {
        this.notaService = notaService;
        this.notaRepo = notaRepo;
        this.securityUtils = securityUtils;
    }

    /** Busca facturas emitidas para referenciarlas en la nota (N°, CUFE, cliente). */
    @GetMapping("/facturas")
    public ResponseEntity<ApiResponse<JsonNode>> buscarFacturas(
            @RequestParam(required = false) String search) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = notaService.buscarFacturas(empresaId, search);
        return ok("Facturas encontradas", data);
    }

    /**
     * Prefill de la nota: CUFE + ítems con IVA. Intenta el detalle de Factus por
     * {@code billId}; si no, la venta local por {@code numero}.
     */
    @GetMapping("/factura-detalle")
    public ResponseEntity<ApiResponse<com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto>> facturaDetalle(
            @RequestParam(required = false) Long billId,
            @RequestParam String numero) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Detalle de factura", notaService.prefill(empresaId, billId, numero));
    }

    /** Crea y valida una nota crédito ante la DIAN. */
    @PostMapping("/credito")
    public ResponseEntity<ApiResponse<JsonNode>> crearCredito(@RequestBody CrearNotaCreditoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = notaService.crearNotaCredito(empresaId, dto);
        return ok("Nota crédito procesada", data);
    }

    /** Crea y valida una nota débito ante la DIAN (mismo payload). */
    @PostMapping("/debito")
    public ResponseEntity<ApiResponse<JsonNode>> crearDebito(@RequestBody CrearNotaCreditoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = notaService.crearNotaDebito(empresaId, dto);
        return ok("Nota débito procesada", data);
    }

    /** Listado local de notas emitidas. */
    @GetMapping("/local")
    public ResponseEntity<ApiResponse<List<NotaElectronicaEstadoDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<NotaElectronicaEstadoDto> data = notaRepo.findByEmpresaIdOrderByIdDesc(empresaId)
                .stream().map(this::aDto).toList();
        return ok("Notas electrónicas", data);
    }

    /** Detalle de una nota. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaElectronicaEstadoDto>> ver(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaElectronicaEstadoDto data = notaRepo.findByIdAndEmpresaId(id, empresaId)
                .map(this::aDto)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nota no encontrada"));
        return ok("Nota electrónica", data);
    }

    /** PDF de la nota (Base64) desde Factus. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<ApiResponse<String>> pdf(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("PDF de la nota", notaService.descargarPdf(empresaId, id));
    }

    /** Elimina la nota en Factus (solo si NO está validada por la DIAN) y localmente. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        notaService.eliminar(empresaId, id);
        return ok("Nota eliminada", null);
    }

    /** Reenvía la nota por correo al cliente. */
    @PostMapping("/{id}/reenviar-correo")
    public ResponseEntity<ApiResponse<Void>> reenviarCorreo(
            @PathVariable Long id,
            @RequestParam(required = false) String email) {
        Integer empresaId = securityUtils.getEmpresaId();
        notaService.reenviarCorreo(empresaId, id, email);
        return ok("Correo enviado al cliente", null);
    }

    private NotaElectronicaEstadoDto aDto(NotaElectronicaEntity ne) {
        NotaElectronicaEstadoDto d = new NotaElectronicaEstadoDto();
        d.setId(ne.getId());
        d.setTipo(ne.getTipo());
        d.setReferenceCode(ne.getReferenceCode());
        d.setBillId(ne.getBillId());
        d.setNumero(ne.getNumero());
        d.setCude(ne.getCude());
        d.setEstado(ne.getEstado());
        d.setCustomizationId(ne.getCustomizationId());
        d.setCorrectionConceptCode(ne.getCorrectionConceptCode());
        d.setTotal(ne.getTotal());
        d.setTieneXml(ne.getXml() != null && !ne.getXml().isBlank());
        d.setCreatedAt(ne.getCreatedAt());
        return d;
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

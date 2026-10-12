package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.CalculoInteres;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.CandidatoCastigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Castigar;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Castigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.DeterioroSugerido;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.FacturarInteres;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresMora;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Politica;
import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;
import com.cloud_technological.aura_pos.services.cartera.CastigoCarteraService;
import com.cloud_technological.aura_pos.services.cartera.InteresMoraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/** Cartera C5 (intereses de mora) y C9 (deterioro y castigo). */
@RestController
@RequestMapping("/api/cartera")
@RequiredArgsConstructor
public class CarteraMoraCastigoController {

    private final InteresMoraService interesService;
    private final CastigoCarteraService castigoService;
    private final SecurityUtils securityUtils;

    private Integer usuarioId() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return ResponseEntity.ok(new ApiResponse<>(200, mensaje, false, data));
    }

    // ── Política ─────────────────────────────────────────────────────────────

    @GetMapping("/politica")
    public ResponseEntity<ApiResponse<Politica>> politica() {
        return ok("OK", interesService.politica(securityUtils.getEmpresaId()));
    }

    @PutMapping("/politica")
    public ResponseEntity<ApiResponse<Politica>> guardarPolitica(@RequestBody Politica dto) {
        return ok("Política de cartera guardada",
                interesService.guardarPolitica(securityUtils.getEmpresaId(), usuarioId(), dto));
    }

    // ── Intereses de mora ────────────────────────────────────────────────────

    @GetMapping("/intereses/calculo")
    public ResponseEntity<ApiResponse<CalculoInteres>> calcular(
            @RequestParam(required = false) Long terceroId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ok("OK", interesService.calcular(securityUtils.getEmpresaId(), terceroId, fecha));
    }

    @GetMapping("/intereses/cliente/{terceroId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumenCliente(@PathVariable Long terceroId) {
        return ok("OK", interesService.resumenCliente(securityUtils.getEmpresaId(), terceroId));
    }

    @GetMapping("/intereses")
    public ResponseEntity<ApiResponse<PageImpl<InteresMora>>> listarIntereses(
            @RequestParam(required = false) Long terceroId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows) {
        return ok("OK", interesService.listar(securityUtils.getEmpresaId(), terceroId, search, page, rows));
    }

    @GetMapping("/intereses/{id}")
    public ResponseEntity<ApiResponse<InteresMora>> obtenerInteres(@PathVariable Long id) {
        return ok("OK", interesService.obtener(securityUtils.getEmpresaId(), id));
    }

    @PostMapping("/intereses")
    public ResponseEntity<ApiResponse<InteresMora>> facturar(@RequestBody FacturarInteres dto) {
        InteresMora im = interesService.facturar(securityUtils.getEmpresaId(), usuarioId(), dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Intereses " + im.getNumero() + " facturados en la cuenta " + im.getNumeroCuenta(), false, im),
                HttpStatus.CREATED);
    }

    @PostMapping("/intereses/{id}/anular")
    public ResponseEntity<ApiResponse<InteresMora>> anularInteres(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ok("Cobro de intereses anulado", interesService.anular(securityUtils.getEmpresaId(), usuarioId(), id,
                body != null ? body.get("motivo") : null));
    }

    // ── Deterioro ────────────────────────────────────────────────────────────

    @GetMapping("/deterioro")
    public ResponseEntity<ApiResponse<DeterioroSugerido>> deterioro() {
        return ok("OK", castigoService.deterioroSugerido(securityUtils.getEmpresaId()));
    }

    @PostMapping("/deterioro/proponer")
    public ResponseEntity<ApiResponse<DeterioroCalculoEntity>> proponerDeterioro() {
        return ok("Propuesta de deterioro creada en borrador: el contador la aprueba en Revisión de comprobantes",
                castigoService.proponerDeterioro(securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    // ── Castigo ──────────────────────────────────────────────────────────────

    @GetMapping("/castigos/candidatos")
    public ResponseEntity<ApiResponse<List<CandidatoCastigo>>> candidatos(
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) Long terceroId) {
        return ok("OK", castigoService.candidatos(securityUtils.getEmpresaId(), dias, terceroId));
    }

    @GetMapping("/castigos")
    public ResponseEntity<ApiResponse<PageImpl<Castigo>>> listarCastigos(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows) {
        return ok("OK", castigoService.listar(securityUtils.getEmpresaId(), search, page, rows));
    }

    @PostMapping("/castigos")
    public ResponseEntity<ApiResponse<List<Castigo>>> castigar(@RequestBody Castigar dto) {
        List<Castigo> hechos = castigoService.castigar(securityUtils.getEmpresaId(), usuarioId(), dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                hechos.size() == 1 ? "Factura castigada" : hechos.size() + " facturas castigadas", false, hechos),
                HttpStatus.CREATED);
    }

    @PostMapping("/castigos/{id}/anular")
    public ResponseEntity<ApiResponse<Castigo>> anularCastigo(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ok("Castigo anulado: la factura vuelve a la cartera", castigoService.anular(
                securityUtils.getEmpresaId(), usuarioId(), id, body != null ? body.get("motivo") : null));
    }
}

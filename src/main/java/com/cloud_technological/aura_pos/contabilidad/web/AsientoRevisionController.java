package com.cloud_technological.aura_pos.contabilidad.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.revision.AsientoRevisionService;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Bandeja de revisión del contador (E3): comprobantes pendientes,
 * aprobación individual/masiva y descuadrados (red de seguridad).
 */
@RestController
@RequestMapping("/api/contabilidad/asientos")
public class AsientoRevisionController {

    @Autowired
    private AsientoRevisionService service;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.contabilidad.ContabilidadPostingLogJPARepository postingLogRepo;

    /**
     * Posting log (E3): qué se contabilizó y qué falló. Filtro opcional
     * {@code ?estado=ERROR} para ver solo los fallos a reprocesar.
     */
    @GetMapping("/posting-log")
    public ResponseEntity<ApiResponse<List<com.cloud_technological.aura_pos.entity.ContabilidadPostingLogEntity>>> postingLog(
            @RequestParam(required = false) String estado) {
        Integer empresaId = securityUtils.getEmpresaId();
        var logs = estado != null && !estado.isBlank()
                ? postingLogRepo.findTop200ByEmpresaIdAndEstadoOrderByCreatedAtDesc(empresaId, estado.toUpperCase())
                : postingLogRepo.findTop200ByEmpresaIdOrderByCreatedAtDesc(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, logs));
    }

    /** Comprobantes en BORRADOR esperando aprobación del contador. */
    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<AsientoContableTableDto>>> pendientes() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.pendientes(empresaId)));
    }

    /** Red de seguridad: asientos con débito ≠ crédito (debería estar vacío). */
    @GetMapping("/descuadrados")
    public ResponseEntity<ApiResponse<List<AsientoContableTableDto>>> descuadrados() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.descuadrados(empresaId)));
    }

    /** Aprueba un borrador: BORRADOR → CONTABILIZADO (período abierto). */
    @PostMapping("/{id}/contabilizar")
    public ResponseEntity<ApiResponse<AsientoContableTableDto>> contabilizar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        AsientoContableTableDto result = service.contabilizar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Comprobante contabilizado", false, result));
    }

    /**
     * Aprueba en bloque los borradores del rango de fechas (yyyy-MM-dd) y,
     * opcionalmente, de un solo tipo de origen.
     */
    @PostMapping("/contabilizar-masivo")
    public ResponseEntity<ApiResponse<Integer>> contabilizarMasivo(
            @RequestBody Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate desde = LocalDate.parse(body.get("desde"));
        LocalDate hasta = LocalDate.parse(body.get("hasta"));
        int total = service.contabilizarMasivo(empresaId, desde, hasta, body.get("tipoOrigen"));
        return ResponseEntity.ok(new ApiResponse<>(200,
                total + " comprobante(s) contabilizado(s)", false, total));
    }
}

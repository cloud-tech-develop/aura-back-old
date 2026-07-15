package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaExcelExporter;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaService;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;
import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaErrorEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLineaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.ExogenaQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Información exógena DIAN (E11): catálogo de formatos, mapeos cuenta→
 * concepto por empresa, validador previo, lotes versionados (generar,
 * revisar, aprobar) y export Excel para el prevalidador.
 */
@RestController
@RequestMapping("/api/contabilidad/exogena")
public class ExogenaController {

    @Autowired
    private ExogenaService service;

    @Autowired
    private ExogenaExcelExporter exporter;

    @Autowired
    private ExogenaLineaJPARepository lineaRepo;

    @Autowired
    private ExogenaQueryRepository queryRepo;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Catálogo ─────────────────────────────────────────────────────────

    @GetMapping("/formatos")
    public ResponseEntity<ApiResponse<List<ExogenaFormatoEntity>>> formatos() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.formatos()));
    }

    @GetMapping("/formatos/{id}/conceptos")
    public ResponseEntity<ApiResponse<List<ExogenaConceptoEntity>>> conceptos(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.conceptos(id)));
    }

    // ── Mapeos ───────────────────────────────────────────────────────────

    @GetMapping("/mapeos")
    public ResponseEntity<ApiResponse<List<ExogenaMapeoCuentaEntity>>> mapeos(
            @RequestParam(required = false) Long formatoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.mapeos(empresaId, formatoId)));
    }

    @PostMapping("/mapeos")
    public ResponseEntity<ApiResponse<ExogenaMapeoCuentaEntity>> crearMapeo(
            @RequestBody MapeoRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        ExogenaMapeoCuentaEntity m = service.crearMapeo(empresaId, body.conceptoId(),
                body.cuentaDesde(), body.cuentaHasta(), body.tipoValor());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Mapeo creado", false, m), HttpStatus.CREATED);
    }

    @DeleteMapping("/mapeos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarMapeo(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminarMapeo(empresaId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Mapeo eliminado", false, null));
    }

    /** Restaura los mapeos default sobre el PUC seed (solo si no hay ninguno). */
    @PostMapping("/mapeos/seed")
    public ResponseEntity<ApiResponse<Void>> seedMapeos() {
        Integer empresaId = securityUtils.getEmpresaId();
        service.seedDefaults(empresaId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Mapeos default sembrados", false, null));
    }

    // ── Validador previo ─────────────────────────────────────────────────

    @GetMapping("/validar")
    public ResponseEntity<ApiResponse<List<ExogenaErrorEntity>>> validar(
            @RequestParam int anio, @RequestParam Long formatoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.validar(empresaId, anio, formatoId)));
    }

    // ── Lotes ────────────────────────────────────────────────────────────

    @PostMapping("/lotes")
    public ResponseEntity<ApiResponse<ExogenaLoteEntity>> generar(@RequestBody GenerarRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ExogenaLoteEntity lote = service.generar(empresaId, usuarioId,
                body.formatoId(), body.anio(), body.cuantiaMenorUmbral());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Lote generado", false, lote), HttpStatus.CREATED);
    }

    @GetMapping("/lotes")
    public ResponseEntity<ApiResponse<List<ExogenaLoteEntity>>> lotes(
            @RequestParam(required = false) Integer anio) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.lotes(empresaId, anio)));
    }

    @GetMapping("/lotes/{id}/lineas")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> lineas(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.lineas(empresaId, id)));
    }

    @GetMapping("/lotes/{id}/errores")
    public ResponseEntity<ApiResponse<List<ExogenaErrorEntity>>> errores(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.errores(empresaId, id)));
    }

    @PostMapping("/lotes/{id}/aprobar")
    public ResponseEntity<ApiResponse<ExogenaLoteEntity>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Lote aprobado y bloqueado", false,
                service.aprobar(empresaId, usuarioId, id)));
    }

    /** Excel con las columnas del prevalidador DIAN. */
    @GetMapping("/lotes/{id}/export")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ExogenaLoteEntity lote = service.lote(empresaId, id);
        ExogenaFormatoEntity formato = service.formatos().stream()
                .filter(f -> f.getId().equals(lote.getFormatoId()))
                .findFirst().orElseThrow();
        List<ExogenaLineaEntity> lineas = lineaRepo.findByLoteIdOrderByConceptoIdAscValorDesc(id);
        Map<Long, ExogenaConceptoEntity> conceptos = service.conceptos(lote.getFormatoId()).stream()
                .collect(Collectors.toMap(ExogenaConceptoEntity::getId, Function.identity()));
        Map<Long, TerceroExogenaDto> terceros = queryRepo.terceros(empresaId,
                        lineas.stream().map(ExogenaLineaEntity::getTerceroId)
                                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(TerceroExogenaDto::id, Function.identity()));

        byte[] excel = exporter.exportar(formato, lote, lineas, conceptos, terceros);
        String nombre = "exogena-" + formato.getCodigo() + "-" + lote.getAnio()
                + "-v" + lote.getVersion() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ── Cuerpos de petición ──────────────────────────────────────────────

    public record MapeoRequest(Long conceptoId, String cuentaDesde, String cuentaHasta,
            String tipoValor) {
    }

    public record GenerarRequest(Long formatoId, int anio, BigDecimal cuantiaMenorUmbral) {
    }
}

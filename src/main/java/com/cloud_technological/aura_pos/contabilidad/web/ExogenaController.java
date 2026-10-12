package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaExcelExporter;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaService;
import com.cloud_technological.aura_pos.entity.ExogenaColumnaEntity;
import com.cloud_technological.aura_pos.entity.ExogenaConceptoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaErrorEntity;
import com.cloud_technological.aura_pos.entity.ExogenaFormatoEntity;
import com.cloud_technological.aura_pos.entity.ExogenaLoteEntity;
import com.cloud_technological.aura_pos.entity.ExogenaMapeoCuentaEntity;
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
    private SecurityUtils securityUtils;

    // ── Catálogo ─────────────────────────────────────────────────────────

    /** Formatos que reporta la empresa según su tipo de persona (natural: sin el 1010). */
    @GetMapping("/formatos")
    public ResponseEntity<ApiResponse<List<ExogenaFormatoEntity>>> formatos() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.formatos(securityUtils.getEmpresaId())));
    }

    /** Columnas de valor del formato, en el orden del prevalidador. */
    @GetMapping("/formatos/{id}/columnas")
    public ResponseEntity<ApiResponse<List<ExogenaColumnaEntity>>> columnas(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.columnas(id)));
    }

    /** Tipo de persona del informante: NATURAL | JURIDICA. */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, String>>> config() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                Map.of("tipoPersona", service.tipoPersona(securityUtils.getEmpresaId()))));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, String>>> cambiarConfig(@RequestBody Map<String, String> body) {
        String tp = service.cambiarTipoPersona(securityUtils.getEmpresaId(), body.get("tipoPersona"));
        return ResponseEntity.ok(new ApiResponse<>(200, "Tipo de persona guardado", false, Map.of("tipoPersona", tp)));
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
                body.cuentaDesde(), body.cuentaHasta(), body.tipoValor(), body.columna());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Mapeo creado", false, m), HttpStatus.CREATED);
    }

    @DeleteMapping("/mapeos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarMapeo(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminarMapeo(empresaId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Mapeo eliminado", false, null));
    }

    /** Agrega los mapeos default que falten (no toca ni borra los de la empresa). */
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

    /** Genera de una vez los lotes del año de todos los formatos que reporta la empresa. */
    @PostMapping("/lotes/todos")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> generarTodos(@RequestBody GenerarRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Lotes generados", false,
                service.generarTodos(empresaId, securityUtils.getUsuarioId(), body.anio())));
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

    /** Excel del lote con las columnas del prevalidador DIAN. */
    @GetMapping("/lotes/{id}/export")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) {
        ExogenaExcelExporter.Hoja hoja = service.hoja(securityUtils.getEmpresaId(), id);
        return excel(exporter.exportar(hoja), "exogena-" + hoja.formato().getCodigo() + "-"
                + hoja.lote().getAnio() + "-v" + hoja.lote().getVersion() + ".xlsx");
    }

    /** Excel del año: una hoja por formato con la última versión de su lote. */
    @GetMapping("/anio/{anio}/export")
    public ResponseEntity<byte[]> exportarAnio(@PathVariable int anio) {
        return excel(exporter.exportar(service.hojasDelAnio(securityUtils.getEmpresaId(), anio)),
                "exogena-" + anio + ".xlsx");
    }

    private static ResponseEntity<byte[]> excel(byte[] contenido, String nombre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(contenido);
    }

    // ── Cuerpos de petición ──────────────────────────────────────────────

    public record MapeoRequest(Long conceptoId, String cuentaDesde, String cuentaHasta,
            String tipoValor, String columna) {
    }

    public record GenerarRequest(Long formatoId, int anio, BigDecimal cuantiaMenorUmbral) {
    }
}

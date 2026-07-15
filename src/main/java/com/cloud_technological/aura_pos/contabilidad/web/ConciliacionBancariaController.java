package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.conciliacion.ConciliacionBancariaService;
import com.cloud_technological.aura_pos.dto.contabilidad.MovimientoLibroDto;
import com.cloud_technological.aura_pos.entity.ExtractoBancarioEntity;
import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Conciliación bancaria (E9): extractos por cuenta y período, importación
 * CSV, matching sugerido, confirmación línea a línea, ajustes (comisiones,
 * GMF, intereses) y cierre del extracto.
 */
@RestController
@RequestMapping("/api/contabilidad/conciliacion")
public class ConciliacionBancariaController {

    @Autowired
    private ConciliacionBancariaService service;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Extractos ────────────────────────────────────────────────────────

    @PostMapping("/extractos")
    public ResponseEntity<ApiResponse<ExtractoBancarioEntity>> crear(
            @RequestBody CrearExtractoRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ExtractoBancarioEntity e = service.crear(empresaId, usuarioId,
                body.cuentaBancariaId(), body.periodo(), body.saldoInicial(), body.saldoFinal());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Extracto creado", false, e), HttpStatus.CREATED);
    }

    @GetMapping("/extractos")
    public ResponseEntity<ApiResponse<List<ExtractoBancarioEntity>>> listar(
            @RequestParam(required = false) Long cuentaBancariaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listar(empresaId, cuentaBancariaId)));
    }

    @GetMapping("/extractos/{id}")
    public ResponseEntity<ApiResponse<ExtractoBancarioEntity>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.obtener(empresaId, id)));
    }

    /** Elimina un extracto creado por error (solo ABIERTO y sin ajustes). */
    @DeleteMapping("/extractos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(empresaId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Extracto eliminado", false, null));
    }

    @GetMapping("/extractos/{id}/lineas")
    public ResponseEntity<ApiResponse<List<ExtractoLineaEntity>>> lineas(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.lineas(empresaId, id)));
    }

    // ── Importación ──────────────────────────────────────────────────────

    /** Acepta CSV crudo (fecha;descripción;valor) y/o líneas ya estructuradas. */
    @PostMapping("/extractos/{id}/lineas")
    public ResponseEntity<ApiResponse<List<ExtractoLineaEntity>>> importar(
            @PathVariable Long id, @RequestBody ImportarRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ConciliacionBancariaService.LineaImportada> lineas = body.lineas() != null
                ? body.lineas().stream().map(l -> new ConciliacionBancariaService.LineaImportada(
                        l.fecha(), l.descripcion(), l.valor())).toList()
                : null;
        List<ExtractoLineaEntity> guardadas = service.importar(empresaId, id, body.csv(), lineas);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                guardadas.size() + " líneas importadas", false, guardadas), HttpStatus.CREATED);
    }

    // ── Matching y conciliación línea a línea ────────────────────────────

    /** Candidatos del libro por línea pendiente (valor exacto, fecha ±3 días). */
    @GetMapping("/extractos/{id}/sugerencias")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> sugerencias(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.sugerencias(empresaId, id)));
    }

    /** Columna "libro" de la pantalla de dos columnas. */
    @GetMapping("/extractos/{id}/movimientos-libro")
    public ResponseEntity<ApiResponse<List<MovimientoLibroDto>>> movimientosLibro(
            @PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.movimientosLibro(empresaId, id)));
    }

    @PostMapping("/extractos/{id}/lineas/{lineaId}/conciliar")
    public ResponseEntity<ApiResponse<ExtractoLineaEntity>> conciliar(
            @PathVariable Long id, @PathVariable Long lineaId,
            @RequestBody ConciliarRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Línea conciliada", false,
                service.conciliar(empresaId, id, lineaId, body.asientoDetalleId())));
    }

    @PostMapping("/extractos/{id}/lineas/{lineaId}/desconciliar")
    public ResponseEntity<ApiResponse<ExtractoLineaEntity>> desconciliar(
            @PathVariable Long id, @PathVariable Long lineaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Línea devuelta a pendiente", false,
                service.desconciliar(empresaId, id, lineaId)));
    }

    /** Contabiliza la línea sin registro en el libro: GASTO_BANCARIO | GMF | INTERES. */
    @PostMapping("/extractos/{id}/lineas/{lineaId}/ajuste")
    public ResponseEntity<ApiResponse<ExtractoLineaEntity>> registrarAjuste(
            @PathVariable Long id, @PathVariable Long lineaId,
            @RequestBody AjusteRequest body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Ajuste registrado", false,
                service.registrarAjuste(empresaId, usuarioId, id, lineaId, body.tipo())));
    }

    // ── Resumen y cierre ─────────────────────────────────────────────────

    @GetMapping("/extractos/{id}/resumen")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumen(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.resumen(empresaId, id)));
    }

    @PostMapping("/extractos/{id}/cerrar")
    public ResponseEntity<ApiResponse<ExtractoBancarioEntity>> cerrar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "Extracto conciliado", false,
                service.cerrar(empresaId, id)));
    }

    // ── Cuerpos de petición ──────────────────────────────────────────────

    public record CrearExtractoRequest(Long cuentaBancariaId, String periodo,
            BigDecimal saldoInicial, BigDecimal saldoFinal) {
    }

    public record ImportarRequest(String csv, List<LineaRequest> lineas) {
    }

    public record LineaRequest(LocalDate fecha, String descripcion, BigDecimal valor) {
    }

    public record ConciliarRequest(Long asientoDetalleId) {
    }

    public record AjusteRequest(String tipo) {
    }
}

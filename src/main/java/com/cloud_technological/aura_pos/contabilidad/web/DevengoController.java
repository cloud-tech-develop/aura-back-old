package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.CausacionService;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.DeterioroService;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.DiferidoService;
import com.cloud_technological.aura_pos.entity.CausacionProgramadaEntity;
import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Devengo (E6): causaciones programadas, amortización de diferidos a demanda
 * y deterioro de cartera por edades.
 */
@RestController
@RequestMapping("/api/contabilidad/devengo")
public class DevengoController {

    @Autowired
    private CausacionService causacionService;

    @Autowired
    private DiferidoService diferidoService;

    @Autowired
    private DeterioroService deterioroService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Causaciones programadas ─────────────────────────────────────────

    @GetMapping("/causaciones")
    public ResponseEntity<ApiResponse<List<CausacionProgramadaEntity>>> listarCausaciones() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                causacionService.listar(empresaId)));
    }

    @PostMapping("/causaciones")
    public ResponseEntity<ApiResponse<CausacionProgramadaEntity>> crearCausacion(
            @RequestBody CausacionProgramadaEntity dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CausacionProgramadaEntity created = causacionService.crear(empresaId, dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Causación creada", false, created),
                HttpStatus.CREATED);
    }

    /** Genera las causaciones pendientes del mes (además del job diario). */
    @PostMapping("/causaciones/generar")
    public ResponseEntity<ApiResponse<Integer>> generarCausaciones() {
        int total = causacionService.generar(LocalDate.now());
        return ResponseEntity.ok(new ApiResponse<>(200,
                total + " causación(es) generada(s) en borrador", false, total));
    }

    // ── Diferidos ───────────────────────────────────────────────────────

    /** Corre la amortización del mes actual (además del job del día 1). */
    @PostMapping("/diferidos/amortizar")
    public ResponseEntity<ApiResponse<Integer>> amortizarDiferidos() {
        int total = diferidoService.amortizar(LocalDate.now());
        return ResponseEntity.ok(new ApiResponse<>(200,
                total + " cuota(s) de diferido amortizada(s)", false, total));
    }

    // ── Deterioro de cartera ────────────────────────────────────────────

    /** Vista previa sin persistir: body = { "31": 5, "91": 20, "181": 50 }. */
    @PostMapping("/deterioro/calcular")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calcularDeterioro(
            @RequestBody Map<String, BigDecimal> tramos) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                deterioroService.calcular(empresaId, aTramos(tramos))));
    }

    /** Persiste la propuesta y genera su asiento en BORRADOR. */
    @PostMapping("/deterioro/proponer")
    public ResponseEntity<ApiResponse<DeterioroCalculoEntity>> proponerDeterioro(
            @RequestBody Map<String, BigDecimal> tramos) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        DeterioroCalculoEntity propuesta = deterioroService.proponer(
                empresaId, usuarioId, aTramos(tramos));
        return ResponseEntity.ok(new ApiResponse<>(200,
                "Propuesta de deterioro en borrador", false, propuesta));
    }

    private Map<Integer, BigDecimal> aTramos(Map<String, BigDecimal> body) {
        Map<Integer, BigDecimal> tramos = new TreeMap<>();
        body.forEach((dias, pct) -> tramos.put(Integer.valueOf(dias), pct));
        return tramos;
    }
}

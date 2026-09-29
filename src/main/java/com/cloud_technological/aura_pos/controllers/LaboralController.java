package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.cloud_technological.aura_pos.dto.laboral.CalendarioDiaDto;
import com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto;
import com.cloud_technological.aura_pos.services.implementations.CalendarioLaboralService;
import com.cloud_technological.aura_pos.services.implementations.LaboralConfigService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/laboral")
public class LaboralController {

    @Autowired private LaboralConfigService jornadaService;
    @Autowired private CalendarioLaboralService calendarioService;
    @Autowired private SecurityUtils securityUtils;

    // ── Jornada / recargos por vigencia ──────────────────────────────────────
    @GetMapping("/jornada")
    public ResponseEntity<ApiResponse<List<JornadaConfigDto>>> listarJornada() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                jornadaService.listar(empresaId)));
    }

    @GetMapping("/jornada/vigente")
    public ResponseEntity<ApiResponse<JornadaConfigDto>> jornadaVigente(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                jornadaService.vigente(empresaId, fecha)));
    }

    @PostMapping("/jornada")
    public ResponseEntity<ApiResponse<JornadaConfigDto>> guardarJornada(@RequestBody JornadaConfigDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Configuración guardada", false,
                jornadaService.guardar(dto, empresaId, usuarioId)));
    }

    @DeleteMapping("/jornada/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarJornada(@PathVariable Long id) {
        jornadaService.eliminar(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Eliminada", false, true));
    }

    // ── Calendario laboral ───────────────────────────────────────────────────
    @GetMapping("/calendario")
    public ResponseEntity<ApiResponse<List<CalendarioDiaDto>>> listarCalendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                calendarioService.listar(empresaId, desde, hasta)));
    }

    @PostMapping("/calendario")
    public ResponseEntity<ApiResponse<CalendarioDiaDto>> guardarDia(@RequestBody CalendarioDiaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Día guardado", false,
                calendarioService.guardar(dto, empresaId, usuarioId)));
    }

    @DeleteMapping("/calendario/{id}")
    public ResponseEntity<ApiResponse<Boolean>> anularDia(@PathVariable Long id) {
        calendarioService.anular(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Día anulado", false, true));
    }

    @PostMapping("/calendario/cargar-festivos/{anio}")
    public ResponseEntity<ApiResponse<Integer>> cargarFestivos(@PathVariable int anio) {
        int creados = calendarioService.cargarFestivos(anio, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "Festivos " + anio + " cargados: " + creados, false, creados));
    }
}

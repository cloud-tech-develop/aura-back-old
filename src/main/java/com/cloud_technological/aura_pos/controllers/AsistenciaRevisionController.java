package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaDiaDto;
import com.cloud_technological.aura_pos.dto.asistencia.CrearIncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.CrearPeriodoAsistenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.IncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.PeriodoAsistenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.RevisarIncidenciaDto;
import com.cloud_technological.aura_pos.services.AsistenciaIncidenciaService;
import com.cloud_technological.aura_pos.services.AsistenciaService;
import com.cloud_technological.aura_pos.services.PeriodoAsistenciaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/asistencia/revision")
public class AsistenciaRevisionController {

    @Autowired
    private AsistenciaIncidenciaService incidenciaService;

    @Autowired
    private PeriodoAsistenciaService periodoService;

    @Autowired
    private AsistenciaService asistenciaService;

    @Autowired
    private SecurityUtils securityUtils;

    // ─── Incidencias ──────────────────────────────────────────────────────────

    @PostMapping("/incidencias/generar/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<IncidenciaDto>>> generar(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Incidencias generadas", false,
                        incidenciaService.generarDesdeDia(empleadoId, fecha, empresaId)), HttpStatus.OK);
    }

    @GetMapping("/incidencias/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<IncidenciaDto>>> listarIncidencias(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Incidencias obtenidas", false,
                        incidenciaService.listar(empleadoId, desde, hasta, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/incidencias")
    public ResponseEntity<ApiResponse<IncidenciaDto>> crearIncidencia(@RequestBody CrearIncidenciaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Incidencia creada", false,
                        incidenciaService.crearManual(dto, empresaId, usuarioId)), HttpStatus.OK);
    }

    @PutMapping("/incidencias/{id}/revisar")
    public ResponseEntity<ApiResponse<IncidenciaDto>> revisar(
            @PathVariable Long id, @RequestBody RevisarIncidenciaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Incidencia revisada", false,
                        incidenciaService.revisar(id, dto, empresaId, usuarioId)), HttpStatus.OK);
    }

    // ─── Aprobación del día ───────────────────────────────────────────────────

    @PutMapping("/dias/{diaId}/aprobar")
    public ResponseEntity<ApiResponse<AsistenciaDiaDto>> aprobarDia(@PathVariable Long diaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Día aprobado", false,
                        asistenciaService.aprobarDia(diaId, empresaId, usuarioId)), HttpStatus.OK);
    }

    @PutMapping("/dias/{diaId}/rechazar")
    public ResponseEntity<ApiResponse<AsistenciaDiaDto>> rechazarDia(
            @PathVariable Long diaId, @RequestBody(required = false) Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        String observacion = body != null ? body.get("observacion") : null;
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Día rechazado", false,
                        asistenciaService.rechazarDia(diaId, observacion, empresaId, usuarioId)), HttpStatus.OK);
    }

    // ─── Período de asistencia ────────────────────────────────────────────────

    @GetMapping("/periodos")
    public ResponseEntity<ApiResponse<List<PeriodoAsistenciaDto>>> listarPeriodos() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Períodos obtenidos", false,
                        periodoService.listar(empresaId)), HttpStatus.OK);
    }

    @PostMapping("/periodos")
    public ResponseEntity<ApiResponse<PeriodoAsistenciaDto>> crearPeriodo(
            @RequestBody CrearPeriodoAsistenciaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período creado", false,
                        periodoService.crear(dto, empresaId, usuarioId)), HttpStatus.OK);
    }

    @PutMapping("/periodos/{id}/cerrar")
    public ResponseEntity<ApiResponse<PeriodoAsistenciaDto>> cerrarPeriodo(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período en revisión", false,
                        periodoService.cerrar(id, empresaId, usuarioId)), HttpStatus.OK);
    }

    @PutMapping("/periodos/{id}/aprobar")
    public ResponseEntity<ApiResponse<PeriodoAsistenciaDto>> aprobarPeriodo(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período aprobado", false,
                        periodoService.aprobar(id, empresaId, usuarioId)), HttpStatus.OK);
    }

    @PutMapping("/periodos/{id}/enviar-nomina")
    public ResponseEntity<ApiResponse<PeriodoAsistenciaDto>> enviarANomina(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período enviado a nómina", false,
                        periodoService.enviarANomina(id, empresaId)), HttpStatus.OK);
    }
}

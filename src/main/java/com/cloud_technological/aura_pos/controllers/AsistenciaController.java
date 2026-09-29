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

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaDiaDto;
import com.cloud_technological.aura_pos.dto.asistencia.CreateMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia.MarcajeDto;
import com.cloud_technological.aura_pos.services.AsistenciaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/asistencia")
public class AsistenciaController {

    @Autowired
    private AsistenciaService asistenciaService;

    @Autowired
    private SecurityUtils securityUtils;

    // ─── Marcajes ─────────────────────────────────────────────────────────────

    @PostMapping("/marcajes")
    public ResponseEntity<ApiResponse<MarcajeDto>> registrarMarcaje(@RequestBody CreateMarcajeDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Marcaje registrado", false,
                        asistenciaService.registrarMarcaje(dto, empresaId, usuarioId)), HttpStatus.OK);
    }

    @GetMapping("/marcajes/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<MarcajeDto>>> listarMarcajes(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Marcajes obtenidos", false,
                        asistenciaService.listarMarcajes(empleadoId, fecha, empresaId)), HttpStatus.OK);
    }

    @DeleteMapping("/marcajes/{id}")
    public ResponseEntity<ApiResponse<Void>> anularMarcaje(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        asistenciaService.anularMarcaje(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Marcaje anulado", false, null), HttpStatus.OK);
    }

    // ─── Consolidación diaria ─────────────────────────────────────────────────

    @PostMapping("/consolidar/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<AsistenciaDiaDto>> consolidarDia(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Día consolidado", false,
                        asistenciaService.consolidarDia(empleadoId, fecha, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/consolidar/rango")
    public ResponseEntity<ApiResponse<List<AsistenciaDiaDto>>> consolidarRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Rango consolidado", false,
                        asistenciaService.consolidarRango(desde, hasta, empresaId)), HttpStatus.OK);
    }

    @GetMapping("/dias/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<AsistenciaDiaDto>>> listarDias(
            @PathVariable Long empleadoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Asistencia obtenida", false,
                        asistenciaService.listarDias(empleadoId, desde, hasta, empresaId)), HttpStatus.OK);
    }
}

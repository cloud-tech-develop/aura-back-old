package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.asistencia.CreateEmpleadoTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.CreateTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.EmpleadoTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.TurnoDto;
import com.cloud_technological.aura_pos.services.EmpleadoTurnoService;
import com.cloud_technological.aura_pos.services.TurnoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/asistencia/turnos")
public class TurnoController {

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private EmpleadoTurnoService empleadoTurnoService;

    @Autowired
    private SecurityUtils securityUtils;

    // ─── Turnos maestros ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<TurnoDto>>> listar(
            @RequestParam(defaultValue = "false") boolean soloActivos) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<TurnoDto> result = turnoService.listar(empresaId, soloActivos);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turnos obtenidos", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TurnoDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turno encontrado", false,
                        turnoService.obtener(id, empresaId)), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TurnoDto>> crear(@RequestBody CreateTurnoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turno creado", false,
                        turnoService.crear(dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TurnoDto>> actualizar(
            @PathVariable Long id, @RequestBody CreateTurnoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turno actualizado", false,
                        turnoService.actualizar(id, dto, empresaId)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        turnoService.eliminar(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turno desactivado", false, null), HttpStatus.OK);
    }

    // ─── Asignación empleado ↔ turno ──────────────────────────────────────────

    @GetMapping("/asignaciones/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<EmpleadoTurnoDto>>> asignacionesPorEmpleado(
            @PathVariable Long empleadoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Asignaciones obtenidas", false,
                        empleadoTurnoService.listarPorEmpleado(empleadoId, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/asignaciones")
    public ResponseEntity<ApiResponse<EmpleadoTurnoDto>> asignar(
            @RequestBody CreateEmpleadoTurnoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Turno asignado", false,
                        empleadoTurnoService.asignar(dto, empresaId)), HttpStatus.OK);
    }

    @DeleteMapping("/asignaciones/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarAsignacion(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        empleadoTurnoService.eliminar(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Asignación eliminada", false, null), HttpStatus.OK);
    }
}

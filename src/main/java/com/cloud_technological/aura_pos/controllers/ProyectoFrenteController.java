package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.proyecto.AsignarFrenteTurnoDto;
import com.cloud_technological.aura_pos.dto.proyecto.AsignarTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.CreateFrenteDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTurnoDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateFrenteDto;
import com.cloud_technological.aura_pos.services.ProyectoFrenteService;
import com.cloud_technological.aura_pos.services.implementations.FrenteTurnoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
public class ProyectoFrenteController {

    @Autowired private ProyectoFrenteService service;
    @Autowired private FrenteTurnoService frenteTurnoService;
    @Autowired private SecurityUtils securityUtils;

    // ── Frentes ──────────────────────────────────────────────────────────────

    @GetMapping("/api/proyectos/{proyectoId}/frentes")
    public ResponseEntity<ApiResponse<List<FrenteTableDto>>> listarFrentes(@PathVariable Long proyectoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.listarPorProyecto(proyectoId, empresaId)));
    }

    @PostMapping("/api/proyectos/{proyectoId}/frentes")
    public ResponseEntity<ApiResponse<FrenteTableDto>> crearFrente(
            @PathVariable Long proyectoId, @Valid @RequestBody CreateFrenteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        FrenteTableDto result = service.crearFrente(proyectoId, dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Frente creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @GetMapping("/api/frentes/{id}")
    public ResponseEntity<ApiResponse<FrenteTableDto>> obtenerFrente(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Frente encontrado", false,
                service.obtenerFrente(id, empresaId)));
    }

    @PutMapping("/api/frentes/{id}")
    public ResponseEntity<ApiResponse<FrenteTableDto>> actualizarFrente(
            @PathVariable Long id, @Valid @RequestBody UpdateFrenteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Frente actualizado correctamente", false,
                service.actualizarFrente(id, dto, empresaId, usuarioId)));
    }

    @DeleteMapping("/api/frentes/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarFrente(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        service.eliminarFrente(id, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Frente eliminado correctamente", false, true));
    }

    // ── Trabajadores del frente ──────────────────────────────────────────────

    @GetMapping("/api/frentes/{frenteId}/trabajadores")
    public ResponseEntity<ApiResponse<List<FrenteTrabajadorDto>>> listarTrabajadores(@PathVariable Long frenteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.listarTrabajadores(frenteId, empresaId)));
    }

    @PostMapping("/api/frentes/{frenteId}/trabajadores")
    public ResponseEntity<ApiResponse<Boolean>> asignarTrabajador(
            @PathVariable Long frenteId, @Valid @RequestBody AsignarTrabajadorDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        service.asignarTrabajador(frenteId, dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Trabajador asignado correctamente", false, true),
                HttpStatus.CREATED);
    }

    @DeleteMapping("/api/frentes/{frenteId}/trabajadores/{empleadoId}")
    public ResponseEntity<ApiResponse<Boolean>> retirarTrabajador(
            @PathVariable Long frenteId, @PathVariable Long empleadoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        service.retirarTrabajador(frenteId, empleadoId, empresaId, usuarioId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Trabajador retirado correctamente", false, true));
    }

    // ── Turno del frente (vigencias) ─────────────────────────────────────────

    @GetMapping("/api/frentes/{frenteId}/turnos")
    public ResponseEntity<ApiResponse<List<FrenteTurnoDto>>> listarTurnos(@PathVariable Long frenteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                frenteTurnoService.listar(frenteId, empresaId)));
    }

    @PostMapping("/api/frentes/{frenteId}/turnos")
    public ResponseEntity<ApiResponse<FrenteTurnoDto>> asignarTurno(
            @PathVariable Long frenteId, @Valid @RequestBody AsignarFrenteTurnoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Turno asignado al frente", false, frenteTurnoService.asignar(frenteId, dto, empresaId, usuarioId)),
                HttpStatus.CREATED);
    }

    @DeleteMapping("/api/frentes/turnos/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarTurno(@PathVariable Long id) {
        frenteTurnoService.eliminar(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Asignación eliminada", false, true));
    }
}

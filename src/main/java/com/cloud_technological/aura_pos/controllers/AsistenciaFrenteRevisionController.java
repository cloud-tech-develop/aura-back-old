package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.AsistenciaFrenteTableDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.PreliquidacionFrenteItemDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisarDetallesDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionAccionDto;
import com.cloud_technological.aura_pos.dto.asistencia_frente.RevisionFilterDto;
import com.cloud_technological.aura_pos.services.AsistenciaRevisionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Revisión/aprobación de la asistencia por PROYECTO/FRENTE (distinto del módulo por turnos). */
@RestController
@RequestMapping("/api/asistencia/frente-revision")
public class AsistenciaFrenteRevisionController {

    @Autowired private AsistenciaRevisionService service;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<AsistenciaFrenteTableDto>>> listar(
            @RequestBody RevisionFilterDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.listar(filtro, empresaId)));
    }

    @GetMapping("/preliquidacion")
    public ResponseEntity<ApiResponse<List<PreliquidacionFrenteItemDto>>> preliquidacion(
            @RequestParam Long periodoId,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long frenteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.preliquidacion(periodoId, proyectoId, frenteId, empresaId)));
    }

    @GetMapping("/{asistenciaId}")
    public ResponseEntity<ApiResponse<AsistenciaFrenteDto>> obtener(@PathVariable Long asistenciaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.obtenerPorId(asistenciaId, empresaId)));
    }

    @PostMapping("/{asistenciaId}/detalles")
    public ResponseEntity<ApiResponse<Boolean>> revisarDetalles(
            @PathVariable Long asistenciaId, @RequestBody RevisarDetallesDto dto) {
        service.revisarDetalles(asistenciaId, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Revisión guardada", false, true));
    }

    @PostMapping("/{asistenciaId}/aprobar")
    public ResponseEntity<ApiResponse<Boolean>> aprobar(
            @PathVariable Long asistenciaId, @RequestBody(required = false) RevisionAccionDto dto) {
        service.aprobar(asistenciaId, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Asistencia aprobada", false, true));
    }

    @PostMapping("/{asistenciaId}/rechazar")
    public ResponseEntity<ApiResponse<Boolean>> rechazar(
            @PathVariable Long asistenciaId, @RequestBody(required = false) RevisionAccionDto dto) {
        service.rechazar(asistenciaId, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Asistencia rechazada", false, true));
    }

    @PostMapping("/{asistenciaId}/enviar-nomina")
    public ResponseEntity<ApiResponse<Integer>> enviarNomina(@PathVariable Long asistenciaId) {
        int generadas = service.enviarNomina(asistenciaId,
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "Enviado a nómina: " + generadas + " novedad(es) generada(s)", false, generadas));
    }

    @PostMapping("/{asistenciaId}/solicitar-correccion")
    public ResponseEntity<ApiResponse<Boolean>> solicitarCorreccion(
            @PathVariable Long asistenciaId, @RequestBody(required = false) RevisionAccionDto dto) {
        service.solicitarCorreccion(asistenciaId, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Corrección solicitada", false, true));
    }
}

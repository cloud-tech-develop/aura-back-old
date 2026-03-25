package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.nomina.AddNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaTableDto;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/nomina")
public class NominaController {

    @Autowired
    private NominaService nominaService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<NominaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<NominaTableDto> result = nominaService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros de nómina");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NominaDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina encontrada", false, result),
                HttpStatus.OK);
    }

    /**
     * Liquidar nómina de un empleado específico para un período.
     * Si ya existe en BORRADOR, la recalcula.
     */
    @PostMapping("/liquidar/{periodoId}/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<NominaDto>> liquidar(
            @PathVariable Long periodoId,
            @PathVariable Long empleadoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.liquidar(periodoId, empleadoId, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina liquidada exitosamente", false, result),
                HttpStatus.OK);
    }

    /**
     * Liquidar nómina de TODOS los empleados activos para un período.
     */
    @PostMapping("/liquidar/{periodoId}/todos")
    public ResponseEntity<ApiResponse<Void>> liquidarPeriodoCompleto(@PathVariable Long periodoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        nominaService.liquidarPeriodoCompleto(periodoId, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Período liquidado para todos los empleados activos", false, null),
                HttpStatus.OK);
    }

    @PostMapping("/{nominaId}/novedades")
    public ResponseEntity<ApiResponse<NominaDto>> agregarNovedad(
            @PathVariable Long nominaId,
            @RequestBody AddNovedadDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.agregarNovedad(nominaId, dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedad agregada y nómina recalculada", false, result),
                HttpStatus.OK);
    }

    @DeleteMapping("/{nominaId}/novedades/{novedadId}")
    public ResponseEntity<ApiResponse<NominaDto>> eliminarNovedad(
            @PathVariable Long nominaId,
            @PathVariable Long novedadId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.eliminarNovedad(nominaId, novedadId, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Novedad eliminada y nómina recalculada", false, result),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<NominaDto>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.aprobar(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina aprobada", false, result),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<NominaDto>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaDto result = nominaService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina anulada", false, result),
                HttpStatus.OK);
    }
}

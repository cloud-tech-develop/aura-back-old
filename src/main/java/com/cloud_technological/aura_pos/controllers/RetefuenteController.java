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
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.retefuente.RetefuenteDtos.CambiarProcedimientoDto;
import com.cloud_technological.aura_pos.dto.nomina.retefuente.RetefuenteDtos.CreateDeduccionDto;
import com.cloud_technological.aura_pos.dto.nomina.retefuente.RetefuenteDtos.DeduccionDto;
import com.cloud_technological.aura_pos.services.implementations.ContratoLaboralService;
import com.cloud_technological.aura_pos.services.implementations.DeduccionRentaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Retención en la fuente: deducciones del empleado y procedimiento (Fase 4.5).
 */
@RestController
@RequestMapping("/api")
public class RetefuenteController {

    @Autowired
    private DeduccionRentaService deduccionService;

    @Autowired
    private ContratoLaboralService contratoService;

    @Autowired
    private SecurityUtils securityUtils;

    /** Deducciones y rentas exentas de un contrato. */
    @GetMapping("/retefuente/deducciones/{contratoId}")
    public ResponseEntity<ApiResponse<List<DeduccionDto>>> deducciones(@PathVariable Long contratoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Deducciones consultadas", deduccionService.listar(contratoId, empresaId));
    }

    /**
     * Registra una deducción.
     *
     * <p>DEPENDIENTES no lleva valor: es siempre 10% del ingreso topado a 32 UVT,
     * lo calcula el motor. Si llega un monto, se ignora.
     */
    @PostMapping("/retefuente/deducciones")
    public ResponseEntity<ApiResponse<DeduccionDto>> crear(@RequestBody CreateDeduccionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        DeduccionDto data = deduccionService.crear(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Deducción registrada", false, data),
                HttpStatus.CREATED);
    }

    @DeleteMapping("/retefuente/deducciones/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        deduccionService.eliminar(id, empresaId);
        return ok("Deducción eliminada", null);
    }

    /** Cambia el procedimiento de retefuente del contrato ('1' | '2'). */
    @PutMapping("/contrato/{id}/procedimiento-retefuente")
    public ResponseEntity<ApiResponse<Void>> cambiarProcedimiento(
            @PathVariable Long id,
            @RequestBody CambiarProcedimientoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        contratoService.cambiarProcedimientoRetefuente(id, empresaId, dto.getProcedimiento());
        return ok("Procedimiento actualizado", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

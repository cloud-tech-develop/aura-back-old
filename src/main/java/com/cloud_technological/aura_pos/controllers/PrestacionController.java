package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.CrearPrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.PrestacionDto;
import com.cloud_technological.aura_pos.services.PrestacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/prestaciones")
public class PrestacionController {

    @Autowired
    private PrestacionService prestacionService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestaciones obtenidas", false,
                        prestacionService.listar(empresaId)), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PrestacionDto>> crear(@RequestBody CrearPrestacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación calculada", false,
                        prestacionService.crear(dto, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/liquidacion-definitiva")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> liquidacionDefinitiva(
            @RequestBody com.cloud_technological.aura_pos.dto.nomina.prestacion.LiquidacionDefinitivaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidación definitiva calculada", false,
                        prestacionService.liquidacionDefinitiva(dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<PrestacionDto>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación aprobada", false,
                        prestacionService.aprobar(id, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<ApiResponse<PrestacionDto>> pagar(
            @PathVariable Long id,
            @RequestBody(required = false) PagoNominaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación pagada", false,
                        prestacionService.pagar(id, dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<PrestacionDto>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación anulada", false,
                        prestacionService.anular(id, empresaId)), HttpStatus.OK);
    }
}

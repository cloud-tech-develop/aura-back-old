package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/empresa")
public class EmpresaController {

    @Autowired
    private IEmpresaService empresaService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<EmpresaDto>> obtenerEmpresaActual() {
        Integer empresaId = securityUtils.getEmpresaId();
        Long sucursalId = securityUtils.getSucursalId();
        Long usuarioId = securityUtils.getUsuarioId();
        EmpresaDto result = empresaService.obtenerEmpresaActual(empresaId, sucursalId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Empresa obtenida exitosamente", false, result),
                HttpStatus.OK);
    }
}

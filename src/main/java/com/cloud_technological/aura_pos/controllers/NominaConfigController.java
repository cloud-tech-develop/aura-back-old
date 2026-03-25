package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.config.NominaConfigDto;
import com.cloud_technological.aura_pos.dto.nomina.config.UpdateNominaConfigDto;
import com.cloud_technological.aura_pos.services.NominaConfigService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/nomina/config")
public class NominaConfigController {

    @Autowired
    private NominaConfigService nominaConfigService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<NominaConfigDto>> obtener() {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaConfigDto result = nominaConfigService.obtener(empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Configuración de nómina", false, result),
                HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NominaConfigDto>> guardar(@RequestBody UpdateNominaConfigDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaConfigDto result = nominaConfigService.guardar(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Configuración guardada", false, result),
                HttpStatus.OK);
    }
}

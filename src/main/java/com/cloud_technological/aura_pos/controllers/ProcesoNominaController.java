package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.proceso.ProcesoNominaDto;
import com.cloud_technological.aura_pos.services.implementations.ProcesoNominaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Polling de procesos asíncronos de nómina (Fase 7).
 *
 * <p>El front consulta este endpoint cada 2-3 s tras lanzar una liquidación,
 * hasta que {@code estado} deja de estar en curso.
 */
@RestController
@RequestMapping("/api/proceso")
public class ProcesoNominaController {

    @Autowired
    private ProcesoNominaService procesoService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcesoNominaDto>> consultar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ProcesoNominaDto dto = ProcesoNominaDto.de(procesoService.consultar(id, empresaId));
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Proceso consultado", false, dto),
                HttpStatus.OK);
    }
}

package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.entity.AuditoriaNominaAsistenciaEntity;
import com.cloud_technological.aura_pos.services.AuditoriaNominaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/nomina/auditoria")
public class AuditoriaNominaController {

    @Autowired
    private AuditoriaNominaService auditoriaService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditoriaNominaAsistenciaEntity>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Auditoría obtenida", false,
                        auditoriaService.listar(empresaId)), HttpStatus.OK);
    }

    @GetMapping("/{entidad}/{entidadId}")
    public ResponseEntity<ApiResponse<List<AuditoriaNominaAsistenciaEntity>>> listarPorEntidad(
            @PathVariable String entidad, @PathVariable Long entidadId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Auditoría de la entidad", false,
                        auditoriaService.listarPorEntidad(empresaId, entidad, entidadId)), HttpStatus.OK);
    }
}

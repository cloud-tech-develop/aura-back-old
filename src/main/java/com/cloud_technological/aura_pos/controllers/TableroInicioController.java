package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.PuestaEnMarchaDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.ResumenFinancieroDto;
import com.cloud_technological.aura_pos.dto.empresas.TableroInicioDtos.TableroComercialDto;
import com.cloud_technological.aura_pos.services.empresa.TableroInicioService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Tableros de inicio por línea de uso (docs/PLAN_PERFIL_EMPRESA.md). */
@RestController
@RequestMapping("/api/tablero")
public class TableroInicioController {

    @Autowired
    private TableroInicioService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Disponible (caja y bancos) y cartera por cobrar y por pagar. */
    @GetMapping("/financiero")
    public ResponseEntity<ApiResponse<ResumenFinancieroDto>> financiero() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.financiero(securityUtils.getEmpresaId())));
    }

    @GetMapping("/comercial")
    public ResponseEntity<ApiResponse<TableroComercialDto>> comercial() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.comercial(securityUtils.getEmpresaId())));
    }

    /** Lo que le falta configurar a la empresa para operar. */
    @GetMapping("/puesta-en-marcha")
    public ResponseEntity<ApiResponse<PuestaEnMarchaDto>> puestaEnMarcha() {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.puestaEnMarcha(securityUtils.getEmpresaId())));
    }
}

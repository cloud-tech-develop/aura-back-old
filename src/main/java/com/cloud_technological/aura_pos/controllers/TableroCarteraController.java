package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroCarteraDto;
import com.cloud_technological.aura_pos.services.implementations.TableroCarteraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/cartera/tablero")
public class TableroCarteraController {

    @Autowired
    private TableroCarteraService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<TableroCarteraDto>> tablero(@RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.tablero(securityUtils.getEmpresaId(), meses)));
    }
}

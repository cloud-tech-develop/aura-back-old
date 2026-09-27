package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Registrar;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Reporte;
import com.cloud_technological.aura_pos.services.implementations.CarritoAbandonadoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Carritos del POS vaciados sin vender. */
@RestController
@RequestMapping("/api/pos/carritos-abandonados")
public class CarritoAbandonadoController {

    @Autowired
    private CarritoAbandonadoService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Lo llama el POS al vaciar un carrito o cerrar una orden con productos. */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> registrar(@RequestBody Registrar req) {
        Long usuarioId = securityUtils.getUsuarioId();
        service.registrar(securityUtils.getEmpresaId(), usuarioId != null ? usuarioId.intValue() : null, req);
        return ResponseEntity.ok(new ApiResponse<>(200, "Registrado", false, null));
    }

    @GetMapping("/reporte")
    public ResponseEntity<ApiResponse<Reporte>> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer minutosMinimos,
            @RequestParam(required = false) Integer sucursalId) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.reporte(securityUtils.getEmpresaId(), desde, hasta, minutosMinimos, sucursalId)));
    }
}

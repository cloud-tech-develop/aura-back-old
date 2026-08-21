package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.caja.SupervisionRetroactivaDto;
import com.cloud_technological.aura_pos.services.SupervisionRetroactivaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Panel de supervisión: qué entró a las cajas sin ser del turno.
 *
 * <p>Documentos viejos autorizados a mano, pagos cuyo documento es de otro día,
 * cajas que el sistema dedujo, y correcciones sobre arqueos cerrados.
 */
@RestController
@RequestMapping("/api/caja/supervision-retroactiva")
public class SupervisionRetroactivaController {

    @Autowired
    private SupervisionRetroactivaService supervisionService;

    @Autowired
    private SecurityUtils securityUtils;

    /** Sin fechas, el mes en curso. */
    @GetMapping
    public ResponseEntity<ApiResponse<SupervisionRetroactivaDto>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                supervisionService.listar(empresaId, desde, hasta)));
    }
}

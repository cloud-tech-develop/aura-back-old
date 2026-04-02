package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cloud_technological.aura_pos.dto.comprobante.ComprobanteCajaDto;
import com.cloud_technological.aura_pos.services.ComprobanteCajaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/comprobantes-caja")
public class ComprobanteCajaController {

    @Autowired private ComprobanteCajaService service;
    @Autowired private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ComprobanteCajaDto>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int rows,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        String d = desde != null ? desde : "2000-01-01";
        String h = hasta  != null ? hasta  : java.time.LocalDate.now().toString();
        List<ComprobanteCajaDto> data = service.listar(empresaId, tipo, d, h, page, rows);
        return ResponseEntity.ok(new ApiResponse<>(200, "Comprobantes obtenidos", false, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ComprobanteCajaDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, service.obtenerPorId(id, empresaId)));
    }
}

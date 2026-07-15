package com.cloud_technological.aura_pos.contabilidad.web;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.reportes.EstadosFinancierosService;
import com.cloud_technological.aura_pos.dto.contabilidad.CambioPatrimonioLineaDto;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Estados financieros NIIF (E10): estado de cambios en el patrimonio y estado
 * de flujos de efectivo (método indirecto). Fechas en formato yyyy-MM-dd.
 */
@RestController
@RequestMapping("/api/contabilidad/eeff")
public class EstadosFinancierosController {

    @Autowired
    private EstadosFinancierosService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/cambios-patrimonio")
    public ResponseEntity<ApiResponse<List<CambioPatrimonioLineaDto>>> cambiosPatrimonio(
            @RequestParam String desde, @RequestParam String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.cambiosPatrimonio(empresaId, desde, hasta)));
    }

    @GetMapping("/flujo-efectivo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> flujoEfectivo(
            @RequestParam String desde, @RequestParam String hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.flujoEfectivo(empresaId, desde, hasta)));
    }
}

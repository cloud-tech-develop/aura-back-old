package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.certificado.CertificadoIngresosDto;
import com.cloud_technological.aura_pos.dto.nomina.certificado.DesprendibleDto;
import com.cloud_technological.aura_pos.services.implementations.CertificadoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Desprendible y certificados de nómina (Fase 10).
 *
 * <p>Se apoyan en {@code nomina_detalle} con su {@code traza} (Fase 3): sin ese
 * desglose no hay nada que explicar. El PDF lo arma el front a partir de estos
 * DTOs (mismo enfoque que ya usa en otros documentos).
 */
@RestController
@RequestMapping("/api/certificado")
public class CertificadoController {

    @Autowired
    private CertificadoService certificadoService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Desprendible de una nómina, con la traza de cada línea.
     *
     * <p>A diferencia de los certificados anuales, este SÍ se regenera: refleja
     * una nómina que ya está congelada.
     */
    @GetMapping("/desprendible/{nominaId}")
    public ResponseEntity<ApiResponse<DesprendibleDto>> desprendible(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        DesprendibleDto dto = certificadoService.desprendible(nominaId, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Desprendible generado", false, dto),
                HttpStatus.OK);
    }

    /**
     * Certificado de ingresos y retenciones (formato 220) de un año.
     *
     * <p>Se archiva: si se pide dos veces, devuelve el mismo documento. Es un
     * requisito legal, no un bug de caché.
     */
    @GetMapping("/ingresos/{terceroId}/{agno}")
    public ResponseEntity<ApiResponse<CertificadoIngresosDto>> ingresos(
            @PathVariable Long terceroId,
            @PathVariable Integer agno) {
        Integer empresaId = securityUtils.getEmpresaId();
        CertificadoIngresosDto dto = certificadoService.certificadoIngresos(terceroId, agno, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Certificado de ingresos", false, dto),
                HttpStatus.OK);
    }
}

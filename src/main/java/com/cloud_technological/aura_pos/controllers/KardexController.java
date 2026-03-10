package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexResumenDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexTableDto;
import com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@RestController
@RequestMapping("/api/kardex")
public class KardexController {
    @Autowired
    private KardexQueryRepository kardexRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<KardexTableDto>>> listar(
            @RequestBody KardexFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<KardexTableDto> result = kardexRepository.listar(filtro, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron movimientos");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Kardex consultado", false, result), HttpStatus.OK);
    }

    @GetMapping("/resumen/{productoId}")
    public ResponseEntity<ApiResponse<List<KardexResumenDto>>> resumenStock(
            @PathVariable Long productoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<KardexResumenDto> result = kardexRepository.resumenStockPorProducto(productoId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }
}

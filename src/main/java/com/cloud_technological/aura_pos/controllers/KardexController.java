package com.cloud_technological.aura_pos.controllers;

import java.util.Arrays;
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

import com.cloud_technological.aura_pos.dto.kardex.KardexDetalleLineaDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteLineaDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexResumenDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexTableDto;
import com.cloud_technological.aura_pos.dto.kardex.TipoMovimientoDto;
import com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;
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

    /**
     * El catálogo de tipos de movimiento.
     *
     * <p>Lo consume el filtro del front, que antes mantenía su propia lista
     * hardcodeada con 9 de los 17 tipos: merma, obsequio, devolución y reconteo
     * salían en la tabla pero no se podían filtrar.
     */
    @GetMapping("/tipos-movimiento")
    public ResponseEntity<ApiResponse<List<TipoMovimientoDto>>> tiposMovimiento() {
        List<TipoMovimientoDto> tipos = Arrays.stream(TipoMovimientoInventario.values())
                .map(TipoMovimientoDto::de)
                .toList();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, tipos));
    }

    /** Reporte agrupado: una fila por producto con lo que entró y salió. */
    @PostMapping("/reporte")
    public ResponseEntity<ApiResponse<PageImpl<KardexReporteLineaDto>>> reporte(
            @RequestBody KardexReporteFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<KardexReporteLineaDto> result = kardexRepository.reporte(filtro, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron movimientos");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Reporte generado", false, result));
    }

    /** Kardex clásico: los movimientos en orden cronológico con saldo corrido. */
    @PostMapping("/reporte/detalle")
    public ResponseEntity<ApiResponse<PageImpl<KardexDetalleLineaDto>>> detalle(
            @RequestBody KardexFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        if (filtro.getProductoId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El kardex detallado es de un producto: indique cuál.");
        }
        PageImpl<KardexDetalleLineaDto> result = kardexRepository.detalle(filtro, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron movimientos");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Kardex consultado", false, result));
    }
}

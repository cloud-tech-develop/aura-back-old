package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionTableDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CreateCotizacionDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.cloud_technological.aura_pos.services.CotizacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    @Autowired
    private CotizacionService cotizacionService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CotizacionTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CotizacionTableDto> result = cotizacionService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CotizacionDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CotizacionDto result = cotizacionService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Cotización encontrada", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CotizacionDto>> crear(
            @Valid @RequestBody CreateCotizacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CotizacionDto result = cotizacionService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Cotización creada exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        cotizacionService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Cotización anulada correctamente", false, true),
                HttpStatus.OK);
    }

    @GetMapping("/{id}/convertir")
    public ResponseEntity<ApiResponse<CotizacionDto>> convertirAVenta(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CotizacionDto result = cotizacionService.convertirAVenta(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Cotización lista para convertir", false, result),
                HttpStatus.OK);
    }
}

package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.inventario.CreateLoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.services.implementations.LoteService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {
    @Autowired
    private LoteService loteService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<LoteTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<LoteTableDto> result = loteService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LoteDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        LoteDto result = loteService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Lote encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/por-vencer")
    public ResponseEntity<ApiResponse<List<LoteTableDto>>> listarPorVencer() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<LoteTableDto> result = loteService.listarPorVencer(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @GetMapping("/disponibles/{productoId}/{sucursalId}")
    public ResponseEntity<ApiResponse<List<LoteTableDto>>> listarDisponibles(
            @PathVariable Long productoId,
            @PathVariable Long sucursalId) {
        List<LoteTableDto> result = loteService.listarDisponiblesPorProducto(productoId, sucursalId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<LoteDto>> crear(@Valid @RequestBody CreateLoteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        LoteDto result = loteService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Lote creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        loteService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Lote eliminado correctamente", false, true), HttpStatus.OK);
    }
}

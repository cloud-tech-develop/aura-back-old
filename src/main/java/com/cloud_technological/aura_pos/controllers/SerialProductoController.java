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

import com.cloud_technological.aura_pos.dto.inventario.CreateSerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoTableDto;
import com.cloud_technological.aura_pos.services.SerialProductoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/seriales")
public class SerialProductoController {
    @Autowired
    private SerialProductoService serialService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<SerialProductoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<SerialProductoTableDto> result = serialService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SerialProductoDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        SerialProductoDto result = serialService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Serial encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/disponibles/{productoId}/{sucursalId}")
    public ResponseEntity<ApiResponse<List<SerialProductoTableDto>>> listarDisponibles(
            @PathVariable Long productoId,
            @PathVariable Long sucursalId) {
        List<SerialProductoTableDto> result = serialService.listarDisponiblesPorProducto(productoId, sucursalId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<SerialProductoDto>> crear(@Valid @RequestBody CreateSerialProductoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        SerialProductoDto result = serialService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Serial creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        serialService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Serial eliminado correctamente", false, true), HttpStatus.OK);
    }
}

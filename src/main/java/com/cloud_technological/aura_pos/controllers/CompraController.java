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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.compras.CompraAcreditableDto;
import com.cloud_technological.aura_pos.dto.compras.CompraAcreditableItemDto;
import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.services.CompraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    @Autowired
    private CompraService compraService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CompraTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CompraTableDto> result = compraService.listar(pageable, empresaId);
        if (result.isEmpty()) {
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        }
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    /**
     * Facturas del proveedor sobre las que se puede emitir una nota crédito.
     * Las consume el selector del formulario: una NC siempre corrige una
     * factura concreta, nunca al proveedor en abstracto.
     *
     * <p>
     * Paginado y con búsqueda en el servidor — el proveedor puede tener miles
     * de facturas. El proveedor y la sucursal van en {@code params}.
     *
     * <p>
     * A diferencia del listado general de compras, una página vacía NO es un
     * 206: el selector la pide en cada tecleo y un error por "no hay
     * resultados" lo dejaría en blanco sin poder seguir buscando.
     */
    @PostMapping("/acreditables/page")
    public ResponseEntity<ApiResponse<PageImpl<CompraAcreditableDto>>> acreditables(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CompraAcreditableDto> result = compraService.facturasAcreditables(pageable, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(),
                "Listado exitoso", false, result), HttpStatus.OK);
    }

    /**
     * Lo que queda por acreditar de cada producto de una factura de compra.
     */
    @GetMapping("/{id}/acreditable")
    public ResponseEntity<ApiResponse<java.util.List<CompraAcreditableItemDto>>> itemsAcreditables(
            @PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        var result = compraService.itemsAcreditables(empresaId, id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(),
                "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompraDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CompraDto result = compraService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Compra encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CompraDto>> crear(@Valid @RequestBody CreateCompraDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CompraDto result = compraService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Compra registrada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompraDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody CreateCompraDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CompraDto result = compraService.actualizar(id, dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Compra actualizada correctamente", false, result), HttpStatus.OK);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        compraService.anular(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Compra anulada correctamente", false, true), HttpStatus.OK);
    }
}

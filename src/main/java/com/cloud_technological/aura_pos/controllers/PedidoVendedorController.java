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

import com.cloud_technological.aura_pos.dto.pedidos_vendedor.CreatePedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorPageableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorTableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.RegistrarCobroPedidoDto;
import com.cloud_technological.aura_pos.services.PedidoVendedorService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/pedidos-vendedor")
public class PedidoVendedorController {

    @Autowired
    private PedidoVendedorService pedidoVendedorService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<PedidoVendedorTableDto>>> listar(
            @RequestBody PedidoVendedorPageableDto pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<PedidoVendedorTableDto> result = pedidoVendedorService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PedidoVendedorDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        PedidoVendedorDto result = pedidoVendedorService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Pedido encontrado", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PedidoVendedorDto>> crear(
            @Valid @RequestBody CreatePedidoVendedorDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        PedidoVendedorDto result = pedidoVendedorService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Pedido creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/despachar")
    public ResponseEntity<ApiResponse<Boolean>> despachar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        pedidoVendedorService.despachar(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Pedido despachado correctamente", false, true),
                HttpStatus.OK);
    }

    @PatchMapping("/{id}/cobrar")
    public ResponseEntity<ApiResponse<Boolean>> registrarCobro(
            @PathVariable Long id,
            @Valid @RequestBody RegistrarCobroPedidoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        pedidoVendedorService.registrarCobro(id, dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Cobro registrado correctamente", false, true),
                HttpStatus.OK);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        pedidoVendedorService.anular(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Pedido anulado correctamente", false, true),
                HttpStatus.OK);
    }
}

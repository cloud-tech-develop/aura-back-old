package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.CreatePrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.DescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioVolumenTableDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdateDescuentoClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioClienteDto;
import com.cloud_technological.aura_pos.dto.precios_dinamicos.UpdatePrecioVolumenDto;
import com.cloud_technological.aura_pos.services.PrecioDinamicoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api")
public class PrecioDinamicoController {

    @Autowired
    private PrecioDinamicoService precioDinamicoService;

    @Autowired
    private SecurityUtils securityUtils;

    // ========== PRECIOS CLIENTE ==========

    @PostMapping("/precios-cliente/page")
    public ResponseEntity<ApiResponse<PageImpl<PrecioClienteTableDto>>> listarPreciosCliente(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<PrecioClienteTableDto> result = precioDinamicoService.listarPreciosCliente(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/precios-cliente/{id}")
    public ResponseEntity<ApiResponse<PrecioClienteDto>> obtenerPrecioClientePorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioClienteDto result = precioDinamicoService.obtenerPrecioClientePorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio especial encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/precios-cliente/cliente/{terceroId}")
    public ResponseEntity<ApiResponse<List<PrecioClienteTableDto>>> listarPreciosClientePorTercero(
            @PathVariable Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<PrecioClienteTableDto> result = precioDinamicoService.listarPreciosClientePorTercero(empresaId, terceroId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/precios-cliente")
    public ResponseEntity<ApiResponse<PrecioClienteDto>> crearPrecioCliente(
            @Valid @RequestBody CreatePrecioClienteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioClienteDto result = precioDinamicoService.crearPrecioCliente(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Precio especial creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/precios-cliente/{id}")
    public ResponseEntity<ApiResponse<PrecioClienteDto>> actualizarPrecioCliente(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePrecioClienteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioClienteDto result = precioDinamicoService.actualizarPrecioCliente(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio especial actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/precios-cliente/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarPrecioCliente(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        precioDinamicoService.eliminarPrecioCliente(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio especial eliminado correctamente", false, true), HttpStatus.OK);
    }

    // ========== DESCUENTOS CLIENTE ==========

    @PostMapping("/descuentos-cliente/page")
    public ResponseEntity<ApiResponse<PageImpl<DescuentoClienteDto>>> listarDescuentosCliente(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<DescuentoClienteDto> result = precioDinamicoService.listarDescuentosCliente(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/descuentos-cliente/{id}")
    public ResponseEntity<ApiResponse<DescuentoClienteDto>> obtenerDescuentoClientePorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        DescuentoClienteDto result = precioDinamicoService.obtenerDescuentoClientePorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Descuento encontrado", false, result), HttpStatus.OK);
    }

    @PostMapping("/descuentos-cliente")
    public ResponseEntity<ApiResponse<DescuentoClienteDto>> crearDescuentoCliente(
            @Valid @RequestBody CreateDescuentoClienteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        DescuentoClienteDto result = precioDinamicoService.crearDescuentoCliente(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Descuento creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/descuentos-cliente/{id}")
    public ResponseEntity<ApiResponse<DescuentoClienteDto>> actualizarDescuentoCliente(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDescuentoClienteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        DescuentoClienteDto result = precioDinamicoService.actualizarDescuentoCliente(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Descuento actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/descuentos-cliente/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarDescuentoCliente(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        precioDinamicoService.eliminarDescuentoCliente(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Descuento eliminado correctamente", false, true), HttpStatus.OK);
    }

    // ========== PRECIOS VOLUMEN ==========

    @PostMapping("/precios-volumen/page")
    public ResponseEntity<ApiResponse<PageImpl<PrecioVolumenTableDto>>> listarPreciosVolumen(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<PrecioVolumenTableDto> result = precioDinamicoService.listarPreciosVolumen(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/precios-volumen/{id}")
    public ResponseEntity<ApiResponse<PrecioVolumenDto>> obtenerPrecioVolumenPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioVolumenDto result = precioDinamicoService.obtenerPrecioVolumenPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio por volumen encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/precios-volumen/producto/{productoPresentacionId}")
    public ResponseEntity<ApiResponse<List<PrecioVolumenTableDto>>> listarPreciosVolumenPorProducto(
            @PathVariable Long productoPresentacionId) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<PrecioVolumenTableDto> result = precioDinamicoService.listarPreciosVolumenPorProducto(empresaId, productoPresentacionId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/precios-volumen")
    public ResponseEntity<ApiResponse<PrecioVolumenDto>> crearPrecioVolumen(
            @Valid @RequestBody CreatePrecioVolumenDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioVolumenDto result = precioDinamicoService.crearPrecioVolumen(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Precio por volumen creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/precios-volumen/{id}")
    public ResponseEntity<ApiResponse<PrecioVolumenDto>> actualizarPrecioVolumen(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePrecioVolumenDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        PrecioVolumenDto result = precioDinamicoService.actualizarPrecioVolumen(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio por volumen actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/precios-volumen/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarPrecioVolumen(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        precioDinamicoService.eliminarPrecioVolumen(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio por volumen eliminado correctamente", false, true), HttpStatus.OK);
    }

    // ========== CÁLCULO DE PRECIOS ==========

    @GetMapping("/calculo-precios")
    public ResponseEntity<ApiResponse<BigDecimal>> calcularPrecio(
            @RequestParam Long terceroId,
            @RequestParam Long productoPresentacionId,
            @RequestParam Integer cantidad,
            @RequestParam BigDecimal precioBase) {
        Integer empresaId = securityUtils.getEmpresaId();
        BigDecimal result = precioDinamicoService.calcularPrecio(
                empresaId, terceroId, productoPresentacionId, cantidad, precioBase);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Precio calculado", false, result), HttpStatus.OK);
    }
}

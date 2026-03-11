package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.EstadoCuentaClienteDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTableDto;
import com.cloud_technological.aura_pos.dto.terceros.UpdateTerceroDto;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.services.implementations.EstadoCuentaPdfService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/terceros")
@RequiredArgsConstructor
public class TerceroController {

    @Autowired
    private ITerceroService terceroService;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private EstadoCuentaPdfService estadoCuentaPdfService;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<TerceroTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<TerceroTableDto> result = terceroService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TerceroDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        TerceroDto result = terceroService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Tercero encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<TerceroTableDto>>> listarClientes(
            @RequestParam(defaultValue = "") String search) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<TerceroTableDto> result = terceroService.listarClientes(search, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @GetMapping("/proveedores")
    public ResponseEntity<ApiResponse<List<TerceroTableDto>>> listarProveedores(
            @RequestParam(defaultValue = "") String search) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<TerceroTableDto> result = terceroService.listarProveedores(search, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @GetMapping("/todos")
    public ResponseEntity<ApiResponse<List<TerceroTableDto>>> listarTodos(
            @RequestParam(defaultValue = "") String search) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<TerceroTableDto> result = terceroService.listarTodos(search, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<TerceroDto>> crear(@Valid @RequestBody CreateTerceroDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        TerceroDto result = terceroService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Tercero creado exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TerceroDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTerceroDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        dto.setId(id);
        TerceroDto result = terceroService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Tercero actualizado correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        terceroService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Tercero eliminado correctamente", false, true), HttpStatus.OK);
    }

    /**
     * Estado de cuenta del cliente: historial de ventas, abonos y saldos.
     * GET /api/terceros/{id}/estado-cuenta?fechaDesde=2025-01-01&fechaHasta=2025-12-31
     */
    @GetMapping("/{id}/estado-cuenta")
    public ResponseEntity<ApiResponse<EstadoCuentaClienteDto>> obtenerEstadoCuenta(
            @PathVariable Long id,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        EstadoCuentaClienteDto result = terceroService.obtenerEstadoCuenta(id, empresaId, fechaDesde, fechaHasta);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Estado de cuenta generado", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}/estado-cuenta/pdf")
    public ResponseEntity<byte[]> descargarEstadoCuentaPdf(
            @PathVariable Long id,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        EstadoCuentaClienteDto estado = terceroService.obtenerEstadoCuenta(id, empresaId, fechaDesde, fechaHasta);
        byte[] pdf = estadoCuentaPdfService.generar(estado, empresaId, fechaDesde, fechaHasta);

        String nombreCliente = estado.getNombreCliente()
                .replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ ]", "").trim().replace(" ", "_");
        String filename = "estado_cuenta_" + nombreCliente + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}

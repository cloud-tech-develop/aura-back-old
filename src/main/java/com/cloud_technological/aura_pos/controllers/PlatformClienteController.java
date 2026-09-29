package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.super_admin.ClienteDto;
import com.cloud_technological.aura_pos.dto.super_admin.ClientesResumenDto;
import com.cloud_technological.aura_pos.dto.super_admin.GuardarSuscripcionDto;
import com.cloud_technological.aura_pos.dto.super_admin.RegistrarPagoDto;
import com.cloud_technological.aura_pos.dto.super_admin.SuscripcionPagoDto;
import com.cloud_technological.aura_pos.services.implementations.ClienteSuscripcionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformClienteController {

    @Autowired private ClienteSuscripcionService service;

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<ClienteDto>>> listar() {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, service.listar()));
    }

    @GetMapping("/clientes/resumen")
    public ResponseEntity<ApiResponse<ClientesResumenDto>> resumen() {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Resumen obtenido", false, service.resumen()));
    }

    @PutMapping("/clientes/{empresaId}/suscripcion")
    public ResponseEntity<ApiResponse<ClienteDto>> guardarSuscripcion(
            @PathVariable Integer empresaId, @Valid @RequestBody GuardarSuscripcionDto dto) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Membresía guardada", false,
                service.guardarSuscripcion(empresaId, dto)));
    }

    @GetMapping("/clientes/{empresaId}/pagos")
    public ResponseEntity<ApiResponse<List<SuscripcionPagoDto>>> pagos(@PathVariable Integer empresaId) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, service.pagos(empresaId)));
    }

    @PostMapping("/clientes/{empresaId}/pagos")
    public ResponseEntity<ApiResponse<ClienteDto>> registrarPago(
            @PathVariable Integer empresaId, @Valid @RequestBody RegistrarPagoDto dto) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Pago registrado", false,
                service.registrarPago(empresaId, dto)), HttpStatus.CREATED);
    }

    @DeleteMapping("/clientes/pagos/{pagoId}")
    public ResponseEntity<ApiResponse<Boolean>> eliminarPago(@PathVariable Long pagoId) {
        service.eliminarPago(pagoId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Pago eliminado", false, true));
    }
}

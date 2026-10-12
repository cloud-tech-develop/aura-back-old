package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateFamiliarDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.ExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.FamiliarDto;
import com.cloud_technological.aura_pos.services.EmpleadoHojaVidaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Hoja de vida del empleado ({@code recursos-humanos.empleados}): cuentas
 * bancarias, familiares e historial laboral.
 */
@RestController
@RequestMapping("/api/empleados/{empleadoId}")
public class EmpleadoHojaVidaController {

    private final EmpleadoHojaVidaService service;
    private final SecurityUtils securityUtils;

    public EmpleadoHojaVidaController(EmpleadoHojaVidaService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    // ── Cuentas bancarias ────────────────────────────────────────────────────

    @GetMapping("/cuentas-bancarias")
    public ResponseEntity<ApiResponse<List<CuentaBancariaDto>>> cuentas(@PathVariable Long empleadoId) {
        return ok("Cuentas", service.cuentas(empleadoId, securityUtils.getEmpresaId()));
    }

    @PostMapping("/cuentas-bancarias")
    public ResponseEntity<ApiResponse<List<CuentaBancariaDto>>> crearCuenta(@PathVariable Long empleadoId,
            @Valid @RequestBody CreateCuentaBancariaDto dto) {
        return ok("Cuenta agregada", service.crearCuenta(empleadoId, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/cuentas-bancarias/{id}")
    public ResponseEntity<ApiResponse<List<CuentaBancariaDto>>> actualizarCuenta(@PathVariable Long empleadoId,
            @PathVariable Long id, @Valid @RequestBody CreateCuentaBancariaDto dto) {
        return ok("Cuenta actualizada", service.actualizarCuenta(empleadoId, id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/cuentas-bancarias/{id}")
    public ResponseEntity<ApiResponse<List<CuentaBancariaDto>>> eliminarCuenta(@PathVariable Long empleadoId,
            @PathVariable Long id) {
        return ok("Cuenta quitada", service.eliminarCuenta(empleadoId, id, securityUtils.getEmpresaId()));
    }

    // ── Familiares ───────────────────────────────────────────────────────────

    @GetMapping("/familiares")
    public ResponseEntity<ApiResponse<List<FamiliarDto>>> familiares(@PathVariable Long empleadoId) {
        return ok("Familiares", service.familiares(empleadoId, securityUtils.getEmpresaId()));
    }

    @PostMapping("/familiares")
    public ResponseEntity<ApiResponse<List<FamiliarDto>>> crearFamiliar(@PathVariable Long empleadoId,
            @Valid @RequestBody CreateFamiliarDto dto) {
        return ok("Familiar agregado", service.crearFamiliar(empleadoId, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/familiares/{id}")
    public ResponseEntity<ApiResponse<List<FamiliarDto>>> actualizarFamiliar(@PathVariable Long empleadoId,
            @PathVariable Long id, @Valid @RequestBody CreateFamiliarDto dto) {
        return ok("Familiar actualizado", service.actualizarFamiliar(empleadoId, id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/familiares/{id}")
    public ResponseEntity<ApiResponse<List<FamiliarDto>>> eliminarFamiliar(@PathVariable Long empleadoId,
            @PathVariable Long id) {
        return ok("Familiar quitado", service.eliminarFamiliar(empleadoId, id, securityUtils.getEmpresaId()));
    }

    // ── Historial laboral ────────────────────────────────────────────────────

    @GetMapping("/experiencia")
    public ResponseEntity<ApiResponse<List<ExperienciaDto>>> experiencia(@PathVariable Long empleadoId) {
        return ok("Historial laboral", service.experiencia(empleadoId, securityUtils.getEmpresaId()));
    }

    @PostMapping("/experiencia")
    public ResponseEntity<ApiResponse<List<ExperienciaDto>>> crearExperiencia(@PathVariable Long empleadoId,
            @Valid @RequestBody CreateExperienciaDto dto) {
        return ok("Experiencia agregada", service.crearExperiencia(empleadoId, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/experiencia/{id}")
    public ResponseEntity<ApiResponse<List<ExperienciaDto>>> actualizarExperiencia(@PathVariable Long empleadoId,
            @PathVariable Long id, @Valid @RequestBody CreateExperienciaDto dto) {
        return ok("Experiencia actualizada",
                service.actualizarExperiencia(empleadoId, id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/experiencia/{id}")
    public ResponseEntity<ApiResponse<List<ExperienciaDto>>> eliminarExperiencia(@PathVariable Long empleadoId,
            @PathVariable Long id) {
        return ok("Experiencia quitada", service.eliminarExperiencia(empleadoId, id, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

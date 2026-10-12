package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.nomina.VacacionesSaldoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.FiltroVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RegistrarVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.ResultadoColectivasDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.RetornoAnticipadoDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionesColectivasDto;
import com.cloud_technological.aura_pos.services.NominaVacacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Vacaciones de las vinculaciones ({@code recursos-humanos.vacaciones}). */
@RestController
@RequestMapping("/api/nomina/vacaciones")
public class NominaVacacionController {

    private final NominaVacacionService service;
    private final SecurityUtils securityUtils;

    public NominaVacacionController(NominaVacacionService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<VacacionDto>>> listar(
            @RequestBody PageableDto<FiltroVacacionDto> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VacacionDto>> obtener(@PathVariable Long id) {
        return ok("Vacaciones", service.obtener(id, securityUtils.getEmpresaId()));
    }

    /** Saldo de días de la vinculación a hoy. */
    @GetMapping("/saldo/{contratoId}")
    public ResponseEntity<ApiResponse<VacacionesSaldoDto>> saldo(@PathVariable Long contratoId) {
        return ok("Saldo", service.saldo(contratoId, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VacacionDto>> registrar(@Valid @RequestBody RegistrarVacacionDto dto) {
        VacacionDto v = service.registrar(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Vacaciones registradas", false, v),
                HttpStatus.CREATED);
    }

    @PostMapping("/colectivas")
    public ResponseEntity<ApiResponse<ResultadoColectivasDto>> colectivas(
            @Valid @RequestBody VacacionesColectivasDto dto) {
        return ok("Vacaciones colectivas", service.colectivas(dto, securityUtils.getEmpresaId(),
                securityUtils.getUsuarioId()));
    }

    @PutMapping("/{id}/retorno")
    public ResponseEntity<ApiResponse<VacacionDto>> retorno(@PathVariable Long id,
            @Valid @RequestBody RetornoAnticipadoDto dto) {
        return ok("Regreso registrado", service.retornoAnticipado(id, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<VacacionDto>> anular(@PathVariable Long id) {
        return ok("Vacaciones anuladas", service.anular(id, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

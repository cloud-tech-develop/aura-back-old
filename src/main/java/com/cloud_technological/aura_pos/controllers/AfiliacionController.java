package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.AfiliacionDto;
import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.AfiliarDto;
import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.EntidadDto;
import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.TipoCotizanteDto;
import org.springframework.web.bind.annotation.PutMapping;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.services.implementations.AfiliacionService;
import com.cloud_technological.aura_pos.services.implementations.ContratoLaboralService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Afiliaciones a seguridad social por contrato (Fase 5.5).
 *
 * <p>Bloquea PILA: sin saber a qué EPS/AFP/CCF/ARL está afiliado cada quien no
 * hay planilla que generar. La entidad se elige del catálogo nacional, nunca se
 * digita el código: si cada cliente lo digitara, divergirían y PILA se rechaza.
 */
@RestController
@RequestMapping("/api/afiliacion")
public class AfiliacionController {

    @Autowired
    private AfiliacionService afiliacionService;

    @Autowired
    private ContratoLaboralService contratoService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Entidades disponibles de un tipo: EPS | AFP | CCF | ARL.
     *
     * <p>Son terceros con ese rol (V120): se crean en Terceros con su código
     * UGPP. Si el selector sale vacío, es que no hay ninguna creada aún.
     */
    @GetMapping("/catalogo/{tipo}")
    public ResponseEntity<ApiResponse<List<EntidadDto>>> catalogo(@PathVariable String tipo) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<EntidadDto> data = afiliacionService.entidadesDe(tipo.toUpperCase(), empresaId);
        return ok("Entidades consultadas", data);
    }

    /** Afiliaciones de un contrato (vigentes e históricas). */
    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<ApiResponse<List<AfiliacionDto>>> porContrato(@PathVariable Long contratoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        // Valida pertenencia del contrato a la empresa antes de listar.
        contratoService.obtener(contratoId, empresaId);
        return ok("Afiliaciones consultadas", afiliacionService.listarAfiliaciones(contratoId));
    }

    /**
     * Afilia (o traslada) un contrato a una entidad.
     *
     * <p>Si ya había una afiliación vigente del mismo tipo, se cierra: eso es un
     * traslado, y PILA lo deriva de las fechas. Devuelve la lista actualizada.
     */
    @PostMapping("/contrato/{contratoId}")
    public ResponseEntity<ApiResponse<List<AfiliacionDto>>> afiliar(
            @PathVariable Long contratoId,
            @RequestBody AfiliarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ContratoLaboralEntity contrato = contratoService.obtener(contratoId, empresaId);
        LocalDate desde = dto.getDesde() != null ? dto.getDesde() : LocalDate.now();
        String tipo = dto.getTipo() != null ? dto.getTipo().toUpperCase() : null;
        afiliacionService.afiliar(contrato, dto.getEntidadId(), empresaId, tipo, desde);
        return ok("Afiliación registrada", afiliacionService.listarAfiliaciones(contratoId));
    }

    /** Fija el tipo de cotizante (código UGPP) del contrato. También lo exige PILA. */
    @PutMapping("/contrato/{contratoId}/tipo-cotizante")
    public ResponseEntity<ApiResponse<Void>> tipoCotizante(
            @PathVariable Long contratoId,
            @RequestBody TipoCotizanteDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        contratoService.cambiarTipoCotizante(contratoId, empresaId,
                dto.getTipoCotizante(), dto.getSubtipoCotizante());
        return ok("Tipo de cotizante actualizado", null);
    }

    /**
     * Problemas que impedirían generar PILA para el contrato en una fecha.
     *
     * <p>Conviene mostrarlo antes de liquidar, no al generar la planilla con 200
     * empleados. Lista vacía = listo.
     */
    @GetMapping("/validar/{contratoId}")
    public ResponseEntity<ApiResponse<List<String>>> validar(
            @PathVariable Long contratoId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Integer empresaId = securityUtils.getEmpresaId();
        ContratoLaboralEntity contrato = contratoService.obtener(contratoId, empresaId);
        LocalDate f = fecha != null ? fecha : LocalDate.now();
        return ok("Validación de PILA", afiliacionService.validarParaPila(contrato, f));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

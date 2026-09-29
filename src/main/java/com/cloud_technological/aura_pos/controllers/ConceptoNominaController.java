package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.CreateConceptoDto;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.services.implementations.ConceptoNominaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Catálogo de conceptos de nómina (Fase 3).
 *
 * <p>Reemplaza a las tarifas compiladas. Un cambio de ley pasa de ser código
 * nuevo + release a ser una fila.
 *
 * <p><b>Cambiar una tarifa no edita el concepto: crea una versión nueva con su
 * vigencia.</b> Es lo que permite reliquidar un período viejo con las tarifas
 * de su momento.
 */
@RestController
@RequestMapping("/api/concepto")
public class ConceptoNominaController {

    @Autowired
    private ConceptoNominaService conceptoService;

    @Autowired
    private SecurityUtils securityUtils;

    /**
     * Conceptos vigentes en una fecha.
     *
     * <p>Devuelve los globales de ley más los propios de la empresa, ya
     * resuelta la precedencia: si la empresa personalizó un código, gana el suyo.
     *
     * @param fecha por defecto, hoy
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ConceptoDto>>> vigentes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate f = fecha != null ? fecha : LocalDate.now();

        List<ConceptoNominaEntity> vigentes = conceptoService.vigentesPara(empresaId, f);

        // Qué códigos tiene personalizados la empresa: el front lo usa para
        // marcar cuáles se pueden editar y cuáles son de ley.
        Set<String> personalizados = vigentes.stream()
                .filter(c -> !c.esGlobal())
                .map(ConceptoNominaEntity::getCodigo)
                .collect(Collectors.toSet());

        List<ConceptoDto> result = vigentes.stream()
                .map(c -> aDto(c, personalizados))
                .toList();

        return ok("Listado exitoso", result);
    }

    /**
     * Alta de un concepto propio.
     *
     * <p>Responde 409 si la vigencia se solapa con otra versión del mismo
     * código: dos conceptos vigentes a la vez harían que el resultado dependa
     * del orden de la query.
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ConceptoDto>> crear(@RequestBody CreateConceptoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();

        ConceptoNominaEntity c = new ConceptoNominaEntity();
        // Siempre de la empresa: los globales los mantiene el proveedor.
        c.setEmpresaId(empresaId);
        c.setCodigo(dto.getCodigo());
        c.setNombre(dto.getNombre());
        c.setClase(dto.getClase());
        c.setConstituyeIbc(dto.getConstituyeIbc() == null || dto.getConstituyeIbc());
        c.setBase(dto.getBase());
        c.setPorcentaje(dto.getPorcentaje());
        c.setValorFijo(dto.getValorFijo());
        c.setVigenteDesde(dto.getVigenteDesde());
        c.setVigenteHasta(dto.getVigenteHasta());
        c.setCodigoDian(dto.getCodigoDian());
        c.setOrden(dto.getOrden() != null ? dto.getOrden() : 100);
        c.setActivo(Boolean.TRUE);

        // crear() valida coherencia base↔valor y que no haya solapes.
        ConceptoNominaEntity guardado = conceptoService.crear(c);

        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Concepto creado exitosamente",
                        false, aDto(guardado, Set.of())),
                HttpStatus.CREATED);
    }

    /** Opciones de los enums, para que el front no las duplique. */
    @GetMapping("/opciones")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> opciones() {
        return ok("Consulta exitosa", Map.of(
                "clase", List.of(
                        ConceptoNominaEntity.Clase.DEVENGADO,
                        ConceptoNominaEntity.Clase.DEDUCCION,
                        ConceptoNominaEntity.Clase.APORTE_EMPLEADOR,
                        ConceptoNominaEntity.Clase.PROVISION),
                "base", List.of(
                        ConceptoNominaEntity.Base.SALARIO,
                        ConceptoNominaEntity.Base.SALARIO_MAS_AUXILIO,
                        ConceptoNominaEntity.Base.IBC,
                        ConceptoNominaEntity.Base.DEVENGADO_TOTAL,
                        ConceptoNominaEntity.Base.FIJO,
                        ConceptoNominaEntity.Base.MANUAL)));
    }

    private ConceptoDto aDto(ConceptoNominaEntity c, Set<String> personalizados) {
        ConceptoDto d = new ConceptoDto();
        d.setId(c.getId());
        d.setEmpresaId(c.getEmpresaId());
        d.setCodigo(c.getCodigo());
        d.setNombre(c.getNombre());
        d.setClase(c.getClase());
        d.setConstituyeIbc(c.getConstituyeIbc());
        d.setBase(c.getBase());
        d.setPorcentaje(c.getPorcentaje());
        d.setValorFijo(c.getValorFijo());
        d.setVigenteDesde(c.getVigenteDesde());
        d.setVigenteHasta(c.getVigenteHasta());
        d.setCodigoDian(c.getCodigoDian());
        d.setOrden(c.getOrden());
        d.setActivo(c.getActivo());
        d.setEsGlobal(c.esGlobal());
        d.setPersonalizado(personalizados.contains(c.getCodigo()));
        return d;
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

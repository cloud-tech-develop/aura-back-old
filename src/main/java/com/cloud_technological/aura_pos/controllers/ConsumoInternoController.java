package com.cloud_technological.aura_pos.controllers;

import java.util.List;

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

import com.cloud_technological.aura_pos.dto.consumo_interno.ConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoTableDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.CreateConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.SaveConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.services.ConsumoInternoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/consumos-internos")
public class ConsumoInternoController {

    @Autowired
    private ConsumoInternoService consumoInternoService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ConsumoInternoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ConsumoInternoTableDto> result = consumoInternoService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/conceptos")
    public ResponseEntity<ApiResponse<List<ConceptoConsumoInternoDto>>> listarConceptos() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ConceptoConsumoInternoDto> result = consumoInternoService.listarConceptos(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }

    @PostMapping("/conceptos")
    public ResponseEntity<ApiResponse<ConceptoConsumoInternoDto>> crearConcepto(
            @Valid @RequestBody SaveConceptoConsumoInternoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ConceptoConsumoInternoDto result = consumoInternoService.guardarConcepto(null, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Concepto creado", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/conceptos/{id}")
    public ResponseEntity<ApiResponse<ConceptoConsumoInternoDto>> actualizarConcepto(@PathVariable Long id,
            @Valid @RequestBody SaveConceptoConsumoInternoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ConceptoConsumoInternoDto result = consumoInternoService.guardarConcepto(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Concepto actualizado", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConsumoInternoDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ConsumoInternoDto result = consumoInternoService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Consumo interno encontrado", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ConsumoInternoDto>> crear(@Valid @RequestBody CreateConsumoInternoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        ConsumoInternoDto result = consumoInternoService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Consumo interno registrado", false, result), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        consumoInternoService.anular(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Consumo interno anulado", false, true), HttpStatus.OK);
    }
}

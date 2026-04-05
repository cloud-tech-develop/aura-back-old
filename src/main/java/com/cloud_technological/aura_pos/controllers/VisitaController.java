package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.visitas.ConfirmarLlegadaDto;
import com.cloud_technological.aura_pos.dto.visitas.CreateVisitaConfirmadaDto;
import com.cloud_technological.aura_pos.dto.visitas.CreateVisitaDto;
import com.cloud_technological.aura_pos.dto.visitas.VisitaDto;
import com.cloud_technological.aura_pos.dto.visitas.VisitaTableDto;
import com.cloud_technological.aura_pos.services.VisitaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/visitas")
public class VisitaController {

    @Autowired
    private VisitaService visitaService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<VisitaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<VisitaTableDto> result = visitaService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron visitas");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Visitas obtenidas", false, result), HttpStatus.OK);
    }

    @GetMapping("/hoy")
    public ResponseEntity<ApiResponse<List<VisitaTableDto>>> getVisitasDelDia() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<VisitaTableDto> visitas = visitaService.getVisitasDelDia(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Visitas del día obtenidas", false, visitas), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VisitaDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        VisitaDto visita = visitaService.findById(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Visita obtenida", false, visita), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VisitaDto>> crear(@RequestBody CreateVisitaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        VisitaDto visita = visitaService.create(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Visita creada", false, visita), HttpStatus.CREATED);
    }

    @PostMapping("/create-confirmada")
    public ResponseEntity<ApiResponse<VisitaDto>> crearConfirmada(@RequestBody CreateVisitaConfirmadaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        VisitaDto visita = visitaService.createConfirmada(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Visita creada y confirmada", false, visita), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ApiResponse<VisitaDto>> confirmarLlegada(@PathVariable Long id, @RequestBody ConfirmarLlegadaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        VisitaDto visita = visitaService.confirmarLlegada(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Llegada confirmada", false, visita), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> cancelar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        visitaService.cancelar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Visita cancelada", false, true), HttpStatus.OK);
    }
}
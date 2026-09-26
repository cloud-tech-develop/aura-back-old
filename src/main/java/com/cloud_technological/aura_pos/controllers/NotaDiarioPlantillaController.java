package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.cloud_technological.aura_pos.dto.contabilidad.notas.GenerarDesdePlantillaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioPlantillaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioPlantillaDto;
import com.cloud_technological.aura_pos.services.NotaDiarioPlantillaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Plantillas de notas contables (y las recurrentes que generan un borrador cada mes). */
@RestController
@RequestMapping("/api/contabilidad/notas/plantillas")
public class NotaDiarioPlantillaController {

    @Autowired
    private NotaDiarioPlantillaService service;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotaDiarioPlantillaDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, service.listar(empresaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaDiarioPlantillaDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false, service.obtener(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotaDiarioPlantillaDto>> crear(@Valid @RequestBody SaveNotaDiarioPlantillaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioPlantillaDto result = service.crear(empresaId, usuarioId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Plantilla guardada", false, result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaDiarioPlantillaDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody SaveNotaDiarioPlantillaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Plantilla actualizada", false,
                service.actualizar(id, empresaId, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Plantilla eliminada", false, null));
    }

    /** Crea una nota en borrador con las líneas de la plantilla. */
    @PostMapping("/{id}/generar")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> generar(@PathVariable Long id,
            @Valid @RequestBody GenerarDesdePlantillaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.generar(id, empresaId, usuarioId(), dto.getFecha());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Borrador creado desde la plantilla", false, result));
    }

    private Integer usuarioId() {
        return securityUtils.getUsuarioId() != null ? securityUtils.getUsuarioId().intValue() : null;
    }
}

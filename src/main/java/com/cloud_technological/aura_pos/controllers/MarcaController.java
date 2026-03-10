package com.cloud_technological.aura_pos.controllers;

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
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.cloud_technological.aura_pos.dto.marcas.CreateMarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaTableDto;
import com.cloud_technological.aura_pos.dto.marcas.UpdateMarcaDto;
import com.cloud_technological.aura_pos.services.MarcaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    @Autowired
    private MarcaService marcaService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<MarcaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<MarcaTableDto> result = marcaService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MarcaTableDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        MarcaTableDto result = marcaService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Marca encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MarcaTableDto>> crear(@Valid @RequestBody CreateMarcaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        MarcaTableDto result = marcaService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Marca creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MarcaTableDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMarcaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        MarcaTableDto result = marcaService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Marca actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        marcaService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Marca eliminada correctamente", false, true), HttpStatus.OK);
    }
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<MarcaDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, marcaService.list(empresaId)), HttpStatus.OK);
    }
}
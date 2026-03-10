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
import com.cloud_technological.aura_pos.dto.categorias.CategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaTableDto;
import com.cloud_technological.aura_pos.dto.categorias.CreateCategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.UpdateCategoriaDto;
import com.cloud_technological.aura_pos.services.CategoriaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CategoriaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CategoriaTableDto> result = categoriaService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoriaTableDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaTableDto result = categoriaService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Categoría encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoriaTableDto>> crear(@Valid @RequestBody CreateCategoriaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaTableDto result = categoriaService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Categoría creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoriaTableDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoriaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CategoriaTableDto result = categoriaService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Categoría actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        categoriaService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Categoría eliminada correctamente", false, true), HttpStatus.OK);
    }
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<CategoriaDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<CategoriaDto> result = categoriaService.list(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, result), HttpStatus.OK);
    }
}
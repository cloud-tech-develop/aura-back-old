package com.cloud_technological.aura_pos.controllers;

import java.util.List;

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

import com.cloud_technological.aura_pos.dto.lista_precios.CreateListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateListaPreciosDto;
import com.cloud_technological.aura_pos.services.ListaPreciosService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/listas-precios")
public class ListaPreciosController {

    @Autowired
    private ListaPreciosService listaPreciosService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<ListaPreciosTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<ListaPreciosTableDto> result = listaPreciosService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ListaPreciosDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ListaPreciosDto result = listaPreciosService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Lista encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ListaPreciosDto>> crear(@Valid @RequestBody CreateListaPreciosDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ListaPreciosDto result = listaPreciosService.crear(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Lista creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ListaPreciosDto>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UpdateListaPreciosDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ListaPreciosDto result = listaPreciosService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Lista actualizada correctamente", false, result), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        listaPreciosService.eliminar(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Lista eliminada correctamente", false, true), HttpStatus.OK);
    }
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ListaPreciosDto>>> list() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, listaPreciosService.list(empresaId)), HttpStatus.OK);
    }
}
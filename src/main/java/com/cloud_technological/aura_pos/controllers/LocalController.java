package com.cloud_technological.aura_pos.controllers;

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

import com.cloud_technological.aura_pos.dto.locales.CreateLocalDto;
import com.cloud_technological.aura_pos.dto.locales.LocalDto;
import com.cloud_technological.aura_pos.dto.locales.LocalTableDto;
import com.cloud_technological.aura_pos.dto.locales.UpdateLocalDto;
import com.cloud_technological.aura_pos.services.LocalService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/locales")
public class LocalController {

    @Autowired
    private LocalService localService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<LocalTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<LocalTableDto> result = localService.listar(pageable, empresaId);
        if (result.isEmpty()) {
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron locales");
        }
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Locales obtenidos", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocalDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDto local = localService.findById(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Local obtenido", false, local), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<LocalDto>> crear(@RequestBody CreateLocalDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDto local = localService.create(dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Local creado", false, local), HttpStatus.CREATED);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<ApiResponse<LocalDto>> actualizar(@PathVariable Long id, @RequestBody UpdateLocalDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDto local = localService.update(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Local actualizado", false, local), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        localService.delete(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Local eliminado", false, true), HttpStatus.OK);
    }
}

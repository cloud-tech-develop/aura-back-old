package com.cloud_technological.aura_pos.controllers.platform;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.permisos.PermisosEmpresaDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdatePermisosDto;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

@RestController
@RequestMapping("/api/platform/empresas")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PermisoEmpresaController {

    @Autowired
    private PermisoService permisoService;

    @GetMapping("/{id}/permisos")
    public ResponseEntity<ApiResponse<PermisosEmpresaDto>> obtenerPermisos(@PathVariable Integer id) {
        PermisosEmpresaDto result = permisoService.obtenerPermisosPorEmpresa(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Permisos obtenidos", false, result), HttpStatus.OK);
    }

    @PutMapping("/{id}/permisos")
    public ResponseEntity<ApiResponse<PermisosEmpresaDto>> actualizarPermisos(
            @PathVariable Integer id, 
            @RequestBody UpdatePermisosDto dto) {
        PermisosEmpresaDto result = permisoService.actualizarPermisos(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Permisos actualizados", false, result), HttpStatus.OK);
    }
}

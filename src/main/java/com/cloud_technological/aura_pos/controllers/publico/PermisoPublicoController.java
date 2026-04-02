package com.cloud_technological.aura_pos.controllers.publico;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.services.PermisoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Controlador público para obtener permisos de empresa HU-032: Endpoint Público
 * de Permisos
 */
@RestController
@RequestMapping("/api/public")
public class PermisoPublicoController {

    @Autowired
    private PermisoService permisoService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/empresas/{nit}/permisos")
    public ResponseEntity<ApiResponse<List<ModuloPermisoDto>>> obtenerPermisosPublicos(@PathVariable String nit) {
        List<ModuloPermisoDto> result = permisoService.obtenerPermisosPublicos(nit);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Permisos obtenidos", false, result), HttpStatus.OK);
    }

    @GetMapping("/empresas/permisos")
    public ResponseEntity<ApiResponse<List<ModuloPermisoDto>>> obtenerModulosPorEmpresa() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ModuloPermisoDto> result = permisoService.obtenerModulosPorEmpresa(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Módulos obtenidos", false, result), HttpStatus.OK);
    }
}

package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Permisos por perfil del usuario que hace la petición (docs/PLAN_PERMISOS.md). */
@RestController
@RequestMapping("/api/permisos")
public class PermisoUsuarioController {

    @Autowired
    private PermisoUsuarioService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Lo que el usuario logueado ve y puede hacer: {@code "modulo.submodulo" → acciones}. */
    @GetMapping("/mios")
    public ResponseEntity<ApiResponse<PermisosUsuarioDto>> mios() {
        Long usuarioId = securityUtils.getUsuarioId();
        PermisosUsuarioDto data = service.efectivos(usuarioId != null ? usuarioId.intValue() : null);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}

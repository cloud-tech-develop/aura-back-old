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

    @Autowired
    private com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository empresaRepo;

    /** Lo que el usuario logueado ve y puede hacer: {@code "modulo.submodulo" → acciones}. */
    @GetMapping("/mios")
    public ResponseEntity<ApiResponse<PermisosUsuarioDto>> mios() {
        Long usuarioId = securityUtils.getUsuarioId();
        PermisosUsuarioDto data = service.efectivos(usuarioId != null ? usuarioId.intValue() : null);
        // Firma contable (PLAN_FIRMA_CONTABLE §9): su administrador, en su propia firma.
        String rol = securityUtils.getRol();
        Integer empresaId = securityUtils.getEmpresaId();
        data.setAdminDeFirma(empresaId != null && securityUtils.getVinculoId() == null
                && rol != null && ("ADMIN".equalsIgnoreCase(rol) || "SUPER_ADMIN".equalsIgnoreCase(rol))
                && empresaRepo.findById(empresaId).map(e -> Boolean.TRUE.equals(e.getEsFirmaContable())).orElse(false));
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}

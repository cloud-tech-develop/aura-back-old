package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Filtro;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Pagina;
import com.cloud_technological.aura_pos.repositories.auditoria.BitacoraQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Bitácora de auditoría (docs/PLAN_PERMISOS.md, fase P7): quién anuló, editó,
 * autorizó o cambió qué y cuándo. La protege el submódulo caja.bitacora.
 */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    @Autowired
    private BitacoraQueryRepository query;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<Pagina>> pagina(@RequestBody Filtro filtro) {
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                query.pagina(securityUtils.getEmpresaId(), filtro != null ? filtro : new Filtro())));
    }
}

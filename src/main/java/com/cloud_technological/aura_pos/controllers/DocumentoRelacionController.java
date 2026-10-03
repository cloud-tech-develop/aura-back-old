package com.cloud_technological.aura_pos.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.documento.DocumentoRelacionDtos.Relacionados;
import com.cloud_technological.aura_pos.services.implementations.DocumentoRelacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Cadena documental: de dónde viene y a dónde fue un documento (fase D0). */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoRelacionController {

    @Autowired
    private DocumentoRelacionService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Relaciones hacia atrás (origen) y hacia adelante (destino) del documento. */
    @GetMapping("/{tipo}/{id}/relacionados")
    public ResponseEntity<ApiResponse<Relacionados>> relacionados(
            @PathVariable String tipo, @PathVariable Long id) {
        Relacionados data = service.relacionados(securityUtils.getEmpresaId(), tipo, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false, data));
    }
}

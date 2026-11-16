package com.cloud_technological.aura_pos.controllers.platform;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ResponderSolicitudDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitudPlataformaDto;
import com.cloud_technological.aura_pos.services.nube.NubeEspacioService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Panel de plataforma: solicitudes de más espacio en la nube. La plataforma ve
 * cuotas y uso, nunca los documentos de las empresas.
 */
@RestController
@RequestMapping("/api/platform/nube")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformNubeController {

    private final NubeEspacioService service;
    private final SecurityUtils securityUtils;

    public PlatformNubeController(NubeEspacioService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/solicitudes-espacio")
    public ResponseEntity<ApiResponse<List<SolicitudPlataformaDto>>> pendientes() {
        return ok("Solicitudes de espacio", service.pendientes());
    }

    @GetMapping("/solicitudes-espacio/pendientes")
    public ResponseEntity<ApiResponse<Integer>> contarPendientes() {
        return ok("Pendientes", service.contarPendientes());
    }

    /** POST …/{id}/aprobar { "cuotaMb": 1024, "observacion": "..." } — sin cuotaMb, la solicitada. */
    @PostMapping("/solicitudes-espacio/{id}/aprobar")
    public ResponseEntity<ApiResponse<Void>> aprobar(@PathVariable Long id,
            @RequestBody(required = false) ResponderSolicitudDto dto) {
        service.aprobar(id, dto, actor());
        return ok("Espacio ampliado", null);
    }

    /** POST …/{id}/rechazar { "observacion": "motivo" } */
    @PostMapping("/solicitudes-espacio/{id}/rechazar")
    public ResponseEntity<ApiResponse<Void>> rechazar(@PathVariable Long id, @RequestBody ResponderSolicitudDto dto) {
        service.rechazar(id, dto, actor());
        return ok("Solicitud rechazada", null);
    }

    private Integer actor() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data));
    }
}

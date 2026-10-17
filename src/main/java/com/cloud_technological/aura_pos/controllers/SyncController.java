package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.config.RequerirPermiso;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.sync.local.ClienteNube;
import com.cloud_technological.aura_pos.sync.local.SyncLocalService;
import com.cloud_technological.aura_pos.sync.nube.SyncNubeService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Modo híbrido (docs/MODO_HIBRIDO.md).
 *
 * <ul>
 * <li>{@code /push} y {@code /pull}: los llama el nodo local. No usan sesión de
 * usuario sino el token del nodo (cabecera {@code X-Sync-Token}); por eso están
 * abiertos en SecurityConfig y se validan aquí.</li>
 * <li>{@code /nodos}: el administrador registra los servidores locales (nube).</li>
 * <li>{@code /local/**}: estado y cola del servidor local (nodo).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private static final Set<String> ROLES_ADMIN = Set.of("ADMIN", "SUPER_ADMIN");

    @Autowired
    private SyncNubeService nubeService;

    @Autowired
    private SyncLocalService localService;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Nodo → nube ────────────────────────────────────────────────────

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<SyncDtos.PushRespuesta>> push(
            @RequestHeader(value = ClienteNube.HEADER_TOKEN, required = false) String token,
            @RequestBody SyncDtos.PushPeticion peticion) {
        SyncDtos.NodoInfo nodo = nubeService.autenticar(token);
        return ok("Comandos procesados", nubeService.push(nodo, peticion));
    }

    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<SyncDtos.PullRespuesta>> pull(
            @RequestHeader(value = ClienteNube.HEADER_TOKEN, required = false) String token,
            @RequestParam(defaultValue = "false") boolean saldos) {
        SyncDtos.NodoInfo nodo = nubeService.autenticar(token);
        return ok("Catálogo", nubeService.pull(nodo, saldos));
    }

    // ── Administración de nodos (nube) ─────────────────────────────────

    @GetMapping("/nodos")
    public ResponseEntity<ApiResponse<List<SyncDtos.NodoInfo>>> nodos() {
        exigirAdmin();
        return ok("Nodos", nubeService.nodos(securityUtils.getEmpresaId()));
    }

    @PostMapping("/nodos")
    public ResponseEntity<ApiResponse<SyncDtos.NodoCreado>> crearNodo(@RequestBody SyncDtos.NodoNuevo dto) {
        exigirAdmin();
        return ok("Nodo registrado. Copie el token: no se vuelve a mostrar",
                nubeService.crearNodo(securityUtils.getEmpresaId(), dto));
    }

    @PatchMapping("/nodos/{id}/activo")
    public ResponseEntity<ApiResponse<Boolean>> cambiarActivo(@PathVariable Long id, @RequestParam boolean valor) {
        exigirAdmin();
        nubeService.cambiarActivo(id, securityUtils.getEmpresaId(), valor);
        return ok(valor ? "Nodo activado" : "Nodo desactivado", true);
    }

    // ── Servidor local ─────────────────────────────────────────────────

    /** Lo consulta el front para el indicador en línea / sin conexión. */
    @RequerirPermiso(libre = true)
    @GetMapping("/local/estado")
    public ResponseEntity<ApiResponse<SyncDtos.EstadoLocal>> estado() {
        return ok("Estado", localService.estado());
    }

    @GetMapping("/local/cola")
    public ResponseEntity<ApiResponse<List<SyncDtos.FilaCola>>> cola(@RequestParam(defaultValue = "100") int limite) {
        exigirAdmin();
        return ok("Cola", localService.cola(limite));
    }

    @PostMapping("/local/sincronizar")
    public ResponseEntity<ApiResponse<SyncDtos.EstadoLocal>> sincronizar() {
        return ok("Sincronización intentada", localService.sincronizarAhora());
    }

    @PostMapping("/local/cola/{id}/descartar")
    public ResponseEntity<ApiResponse<Boolean>> descartar(@PathVariable Long id) {
        exigirAdmin();
        localService.descartar(id);
        return ok("Comando descartado", true);
    }

    private void exigirAdmin() {
        String rol = securityUtils.getRol();
        if (rol == null || !ROLES_ADMIN.contains(rol))
            throw new GlobalException(HttpStatus.FORBIDDEN, "Solo un administrador gestiona la sincronización");
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

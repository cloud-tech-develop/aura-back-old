package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.firma_contable.CrearGestionadoDto;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.EmpresaDisponible;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.SolicitudFila;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.VinculoFila;
import com.cloud_technological.aura_pos.services.firma_contable.GestionadoService;
import com.cloud_technological.aura_pos.services.firma_contable.FirmaContableService;
import com.cloud_technological.aura_pos.services.firma_contable.FirmaContableService.AccesoDelCliente;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Firma contable desde las empresas (docs/PLAN_FIRMA_CONTABLE.md):
 * <ul>
 * <li>F4 — el administrador de la firma decide qué clientes atiende cada usuario.</li>
 * <li>F5 — el administrador del cliente ve qué firma tiene acceso y puede quitarlo.</li>
 * </ul>
 * Solo administradores (ADMIN o SUPER_ADMIN) de la empresa de la sesión: un usuario de
 * la firma trabajando en un cliente tiene rol CONTADOR_EXTERNO y no pasa.
 */
@RestController
@RequestMapping("/api/firma")
public class FirmaContableController {

    @Autowired
    private FirmaContableService service;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private GestionadoService gestionados;

    // ── F4: la firma ─────────────────────────────────────────────────────

    /** Clientes de la firma (vacío si la empresa no es firma). */
    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<VinculoFila>>> clientes() {
        exigirAdministrador();
        return ok("Clientes de la firma", service.clientesDeLaFirma(securityUtils.getEmpresaId()));
    }

    /** Clientes (vínculos) que atiende un usuario de la firma. */
    @GetMapping("/usuarios/{usuarioId}/clientes")
    public ResponseEntity<ApiResponse<List<Long>>> clientesDelUsuario(@PathVariable Integer usuarioId) {
        exigirAdministrador();
        return ok("Clientes del usuario", service.clientesDelUsuario(securityUtils.getEmpresaId(), usuarioId));
    }

    /** Deja al usuario atendiendo exactamente esos clientes: {@code {"vinculoIds": [..]}}. */
    @PutMapping("/usuarios/{usuarioId}/clientes")
    public ResponseEntity<ApiResponse<List<Long>>> asignarClientes(@PathVariable Integer usuarioId,
            @RequestBody Map<String, List<Long>> body) {
        exigirAdministrador();
        List<Long> ids = body != null ? body.get("vinculoIds") : null;
        return ok("Clientes asignados", service.asignarClientes(securityUtils.getEmpresaId(), usuarioId, ids, actor()));
    }

    // ── F5: el cliente ───────────────────────────────────────────────────

    /** Firma contable con acceso a esta empresa y sus usuarios que entran; null si no tiene. */
    @GetMapping("/acceso")
    public ResponseEntity<ApiResponse<AccesoDelCliente>> acceso() {
        exigirAdministrador();
        return ok("Firma contable con acceso", service.accesoDelCliente(securityUtils.getEmpresaId()));
    }

    /** Quita el acceso de la firma: sus sesiones en esta empresa se cortan. */
    @DeleteMapping("/acceso")
    public ResponseEntity<ApiResponse<Boolean>> quitarAcceso() {
        exigirAdministrador();
        service.quitarFirma(securityUtils.getEmpresaId(), actor());
        return ok("Se quitó el acceso de la firma contable", true);
    }

    // ── Clientes gestionados (la firma los crea; §9 del plan) ─────────────────

    /** Cupo, cuántos gestionados lleva y todos sus clientes. */
    @GetMapping("/gestionados")
    public ResponseEntity<ApiResponse<GestionadoService.ResumenFirma>> resumenGestionados() {
        exigirAdministrador();
        return ok("Clientes de la firma", gestionados.resumen(securityUtils.getEmpresaId()));
    }

    /** Módulos que la firma puede activarle a un gestionado. */
    @GetMapping("/gestionados/paquetes")
    public ResponseEntity<ApiResponse<List<GestionadoService.PaqueteDto>>> paquetes() {
        exigirAdministrador();
        return ok("Paquetes", gestionados.paquetes());
    }

    @PostMapping("/gestionados")
    public ResponseEntity<ApiResponse<VinculoFila>> crearGestionado(
            @javax.validation.Valid @RequestBody CrearGestionadoDto dto) {
        exigirAdministrador();
        return ok("Cliente gestionado creado", gestionados.crear(securityUtils.getEmpresaId(), dto, actor()));
    }

    @GetMapping("/gestionados/{clienteId}/paquetes")
    public ResponseEntity<ApiResponse<List<String>>> paquetesDe(@PathVariable Integer clienteId) {
        exigirAdministrador();
        return ok("Paquetes del cliente", gestionados.paquetesDe(securityUtils.getEmpresaId(), clienteId));
    }

    @PutMapping("/gestionados/{clienteId}/paquetes")
    public ResponseEntity<ApiResponse<List<String>>> cambiarPaquetes(@PathVariable Integer clienteId,
            @RequestBody Map<String, List<String>> body) {
        exigirAdministrador();
        return ok("Módulos actualizados", gestionados.cambiarPaquetes(securityUtils.getEmpresaId(), clienteId,
                body != null ? body.get("paquetes") : null, actor()));
    }

    // ── Solicitudes cliente → firma (§9 del plan) ────────────────────────────

    /** El cliente busca la firma por NIT. */
    @GetMapping("/buscar")
    public ResponseEntity<ApiResponse<EmpresaDisponible>> buscarFirma(@RequestParam String nit) {
        exigirAdministrador();
        return ok("Firma contable", service.buscarFirmaPorNit(securityUtils.getEmpresaId(), nit));
    }

    /** La última solicitud de esta empresa (pendiente o respondida); null si nunca pidió. */
    @GetMapping("/solicitud")
    public ResponseEntity<ApiResponse<SolicitudFila>> miSolicitud() {
        exigirAdministrador();
        return ok("Solicitud", service.ultimaSolicitud(securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitud")
    public ResponseEntity<ApiResponse<SolicitudFila>> solicitar(@RequestBody Map<String, Integer> body) {
        exigirAdministrador();
        Integer firmaId = body != null ? body.get("firmaEmpresaId") : null;
        if (firmaId == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la firma contable");
        return ok("Solicitud enviada", service.solicitar(securityUtils.getEmpresaId(), firmaId, actor()));
    }

    @DeleteMapping("/solicitud")
    public ResponseEntity<ApiResponse<Boolean>> cancelarSolicitud() {
        exigirAdministrador();
        service.cancelarSolicitud(securityUtils.getEmpresaId(), actor());
        return ok("Solicitud cancelada", true);
    }

    /** Solicitudes que recibió la firma y están pendientes. */
    @GetMapping("/solicitudes")
    public ResponseEntity<ApiResponse<List<SolicitudFila>>> solicitudesRecibidas() {
        exigirAdministrador();
        return ok("Solicitudes", service.solicitudesPendientes(securityUtils.getEmpresaId()));
    }

    @PostMapping("/solicitudes/{id}/aceptar")
    public ResponseEntity<ApiResponse<Boolean>> aceptar(@PathVariable Long id) {
        exigirAdministrador();
        service.responderSolicitud(securityUtils.getEmpresaId(), id, true, null, actor());
        return ok("Solicitud aceptada: el cliente ya aparece entre sus clientes", true);
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<ApiResponse<Boolean>> rechazar(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        exigirAdministrador();
        service.responderSolicitud(securityUtils.getEmpresaId(), id, false, body != null ? body.get("motivo") : null,
                actor());
        return ok("Solicitud rechazada", true);
    }

    private void exigirAdministrador() {
        String rol = securityUtils.getRol();
        boolean admin = rol != null && ("ADMIN".equalsIgnoreCase(rol) || "SUPER_ADMIN".equalsIgnoreCase(rol));
        if (!admin || securityUtils.getVinculoId() != null)
            throw new GlobalException(HttpStatus.FORBIDDEN, "Solo un administrador de la empresa puede hacer esto");
    }

    private Integer actor() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

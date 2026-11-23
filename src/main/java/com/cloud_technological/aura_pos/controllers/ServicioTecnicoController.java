package com.cloud_technological.aura_pos.controllers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.AnularAbono;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.CambiarEstado;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteRapido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConfigDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Entregar;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.FotoContenido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarConfig;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarItem;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarOrden;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Indicadores;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Nota;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenDetalle;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenFila;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Pago;
import com.cloud_technological.aura_pos.services.servicio_tecnico.ServicioOrdenService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Servicio técnico (docs/PLAN_SERVICIO_TECNICO.md). Permisos en PermisoRutas:
 * {@code servicio-tecnico.ordenes} y {@code servicio-tecnico.configuracion}.
 */
@RestController
@RequestMapping("/api/servicio-tecnico")
public class ServicioTecnicoController {

    @Autowired private ServicioOrdenService service;
    @Autowired private SecurityUtils securityUtils;

    @PostMapping("/ordenes/page")
    public ResponseEntity<ApiResponse<PageImpl<OrdenFila>>> listar(@RequestBody PageableDto<Map<String, Object>> pageable) {
        return ok(service.listar(pageable, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @GetMapping("/ordenes/indicadores")
    public ResponseEntity<ApiResponse<Indicadores>> indicadores() {
        return ok(service.indicadores(securityUtils.getEmpresaId()));
    }

    @GetMapping("/ordenes/{id}")
    public ResponseEntity<ApiResponse<OrdenDetalle>> obtener(@PathVariable Long id) {
        return ok(service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/ordenes")
    public ResponseEntity<ApiResponse<OrdenDetalle>> crear(@Valid @RequestBody GuardarOrden dto) {
        OrdenDetalle d = service.crear(dto, securityUtils.getEmpresaId(), securityUtils.getSucursalId(),
                securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Orden " + d.getCodigo() + " creada",
                false, d), HttpStatus.CREATED);
    }

    @PutMapping("/ordenes/{id}")
    public ResponseEntity<ApiResponse<OrdenDetalle>> actualizar(@PathVariable Long id, @Valid @RequestBody GuardarOrden dto) {
        return ok(service.actualizar(id, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/ordenes/{id}/estado")
    public ResponseEntity<ApiResponse<OrdenDetalle>> cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstado dto) {
        return ok(service.cambiarEstado(id, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/ordenes/{id}/notas")
    public ResponseEntity<ApiResponse<OrdenDetalle>> agregarNota(@PathVariable Long id, @Valid @RequestBody Nota dto) {
        return ok(service.agregarNota(id, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/ordenes/{id}/items")
    public ResponseEntity<ApiResponse<OrdenDetalle>> agregarItem(@PathVariable Long id,
            @Valid @RequestBody GuardarItem dto) {
        return ok(service.agregarItem(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/ordenes/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<OrdenDetalle>> eliminarItem(@PathVariable Long id, @PathVariable Long itemId) {
        return ok(service.eliminarItem(id, itemId, securityUtils.getEmpresaId()));
    }

    @PostMapping(value = "/ordenes/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OrdenDetalle>> subirFoto(@PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(required = false, defaultValue = "RECIBIDO") String momento) {
        return ok(service.subirFoto(id, archivo, momento, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    /** Contenido de la foto (sale solo por aquí, después de revisar la empresa). */
    @GetMapping("/ordenes/{id}/fotos/{fotoId}")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @PathVariable Long fotoId) {
        FotoContenido f = service.foto(id, fotoId, securityUtils.getEmpresaId());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.mime()));
        h.setContentDisposition(ContentDisposition.inline().filename(f.nombre(), StandardCharsets.UTF_8).build());
        h.setCacheControl(CacheControl.noStore());
        h.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(f.bytes(), h, HttpStatus.OK);
    }

    @DeleteMapping("/ordenes/{id}/fotos/{fotoId}")
    public ResponseEntity<ApiResponse<OrdenDetalle>> eliminarFoto(@PathVariable Long id, @PathVariable Long fotoId) {
        return ok(service.eliminarFoto(id, fotoId, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    // ── Abonos y entrega ─────────────────────────────────────────────────

    @PostMapping("/ordenes/{id}/abonos")
    public ResponseEntity<ApiResponse<OrdenDetalle>> registrarAbono(@PathVariable Long id, @Valid @RequestBody Pago dto) {
        return ok(service.registrarAbono(id, dto, securityUtils.getEmpresaId(), sucursal(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/ordenes/{id}/abonos/{abonoId}/anular")
    public ResponseEntity<ApiResponse<OrdenDetalle>> anularAbono(@PathVariable Long id, @PathVariable Long abonoId,
            @Valid @RequestBody AnularAbono dto) {
        return ok(service.anularAbono(id, abonoId, dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/ordenes/{id}/entregar")
    public ResponseEntity<ApiResponse<OrdenDetalle>> entregar(@PathVariable Long id, @Valid @RequestBody Entregar dto) {
        return ok(service.entregar(id, dto, securityUtils.getEmpresaId(), sucursal(), securityUtils.getUsuarioId()));
    }

    private Integer sucursal() {
        Long s = securityUtils.getSucursalId();
        return s != null ? s.intValue() : null;
    }

    /** Cliente nuevo desde la recepción (sin exigir el permiso de Terceros). */
    @PostMapping("/clientes")
    public ResponseEntity<ApiResponse<ClienteDto>> crearCliente(@Valid @RequestBody ClienteRapido dto) {
        return ok(service.crearCliente(dto, securityUtils.getEmpresaId()));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<ConfigDto>> config() {
        return ok(service.config(securityUtils.getEmpresaId()));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<ConfigDto>> guardarConfig(@Valid @RequestBody GuardarConfig dto) {
        return ok(service.guardarConfig(dto, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "", false, data));
    }
}

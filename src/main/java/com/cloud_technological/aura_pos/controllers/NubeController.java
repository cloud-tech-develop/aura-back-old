package com.cloud_technological.aura_pos.controllers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.cloud_technological.aura_pos.config.RequerirPermiso;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ActualizarArchivoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ActualizarCarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ArchivoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.CarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ContenidoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.CrearCarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.DescargaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.EventoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.PapeleraDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitarEspacioDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.UsoDto;
import com.cloud_technological.aura_pos.services.nube.NubeContexto;
import com.cloud_technological.aura_pos.services.nube.NubeService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Nube de documentos de la empresa del token (docs/PLAN_NUBE_DOCUMENTOS.md §6).
 *
 * <p>La empresa, el usuario y la firma salen SOLO del token: ningún endpoint
 * recibe una empresa. Los permisos los revisa el servicio en cada llamada.
 */
@RestController
@RequestMapping("/api/nube")
public class NubeController {

    private final NubeService service;
    private final SecurityUtils securityUtils;

    public NubeController(NubeService service, SecurityUtils securityUtils) {
        this.service = service;
        this.securityUtils = securityUtils;
    }

    // ── Carpetas ─────────────────────────────────────────────────────────────

    /** GET /api/nube/contenido?carpetaId= — sin carpetaId, la raíz. */
    @GetMapping("/contenido")
    public ResponseEntity<ApiResponse<ContenidoDto>> contenido(@RequestParam(required = false) Long carpetaId,
            HttpServletRequest req) {
        return ok("Contenido", service.contenido(ctx(req), carpetaId));
    }

    @PostMapping("/carpetas")
    public ResponseEntity<ApiResponse<CarpetaDto>> crearCarpeta(@RequestBody CrearCarpetaDto dto, HttpServletRequest req) {
        return ok("Carpeta creada", service.crearCarpeta(ctx(req), dto));
    }

    @PutMapping("/carpetas/{id}")
    public ResponseEntity<ApiResponse<CarpetaDto>> actualizarCarpeta(@PathVariable Long id,
            @RequestBody ActualizarCarpetaDto dto, HttpServletRequest req) {
        return ok("Carpeta actualizada", service.actualizarCarpeta(ctx(req), id, dto));
    }

    @DeleteMapping("/carpetas/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCarpeta(@PathVariable Long id, HttpServletRequest req) {
        service.eliminarCarpeta(ctx(req), id);
        return ok("Carpeta enviada a la papelera", null);
    }

    // ── Archivos ─────────────────────────────────────────────────────────────

    /** POST /api/nube/archivos (multipart: archivos[], carpetaId opcional). */
    @PostMapping(value = "/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ArchivoDto>>> subir(@RequestParam(required = false) Long carpetaId,
            @RequestPart("archivos") List<MultipartFile> archivos, HttpServletRequest req) {
        List<ArchivoDto> r = service.subir(ctx(req), carpetaId, archivos);
        return ok(r.size() == 1 ? "Archivo subido" : r.size() + " archivos subidos", r);
    }

    /**
     * GET /api/nube/archivos/{id}/descargar?vista=true
     *
     * <p>Siempre como adjunto, con nosniff y CSP sandbox: aunque alguien lograra
     * colar un HTML, el navegador no lo ejecuta. La vista previa la arma el front
     * desde un blob, nunca abriendo una URL.
     */
    @GetMapping("/archivos/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean vista, HttpServletRequest req) {
        DescargaDto d = service.descargar(ctx(req), id, vista);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(d.mime()));
        h.setContentLength(d.contenido().length);
        h.setContentDisposition(ContentDisposition.attachment().filename(d.nombre(), StandardCharsets.UTF_8).build());
        h.setCacheControl(CacheControl.noStore());
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Content-Security-Policy", "sandbox; default-src 'none'");
        h.set("Referrer-Policy", "no-referrer");
        return new ResponseEntity<>(d.contenido(), h, HttpStatus.OK);
    }

    @PutMapping("/archivos/{id}")
    public ResponseEntity<ApiResponse<ArchivoDto>> actualizarArchivo(@PathVariable Long id,
            @RequestBody ActualizarArchivoDto dto, HttpServletRequest req) {
        return ok("Archivo actualizado", service.actualizarArchivo(ctx(req), id, dto));
    }

    @DeleteMapping("/archivos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarArchivo(@PathVariable Long id, HttpServletRequest req) {
        service.eliminarArchivo(ctx(req), id);
        return ok("Archivo enviado a la papelera", null);
    }

    // ── Papelera ─────────────────────────────────────────────────────────────

    @GetMapping("/papelera")
    @RequerirPermiso(accion = "ANULAR")
    public ResponseEntity<ApiResponse<PapeleraDto>> papelera(HttpServletRequest req) {
        return ok("Papelera", service.papelera(ctx(req)));
    }

    /** POST /api/nube/papelera/{tipo}/{id}/restaurar — tipo: carpeta | archivo. */
    @PostMapping("/papelera/{tipo}/{id}/restaurar")
    @RequerirPermiso(accion = "ANULAR")
    public ResponseEntity<ApiResponse<Void>> restaurar(@PathVariable String tipo, @PathVariable Long id,
            HttpServletRequest req) {
        service.restaurar(ctx(req), tipo, id);
        return ok("Restaurado", null);
    }

    /** DELETE /api/nube/papelera/{tipo}/{id} — borra para siempre (solo administradores). */
    @DeleteMapping("/papelera/{tipo}/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarDefinitivo(@PathVariable String tipo, @PathVariable Long id,
            HttpServletRequest req) {
        service.eliminarDefinitivo(ctx(req), tipo, id);
        return ok("Eliminado definitivamente", null);
    }

    // ── Espacio, términos y bitácora ─────────────────────────────────────────

    @GetMapping("/uso")
    public ResponseEntity<ApiResponse<UsoDto>> uso(HttpServletRequest req) {
        return ok("Uso del espacio", service.uso(ctx(req)));
    }

    /** POST /api/nube/terminos { "version": "1" } */
    @PostMapping("/terminos")
    public ResponseEntity<ApiResponse<UsoDto>> aceptarTerminos(@RequestBody Map<String, String> cuerpo,
            HttpServletRequest req) {
        return ok("Términos aceptados", service.aceptarTerminos(ctx(req), cuerpo == null ? null : cuerpo.get("version")));
    }

    /** POST /api/nube/solicitudes-espacio { "cuotaSolicitadaMb": 1024 } — solo administradores. */
    @PostMapping("/solicitudes-espacio")
    public ResponseEntity<ApiResponse<UsoDto>> solicitarEspacio(@RequestBody SolicitarEspacioDto dto,
            HttpServletRequest req) {
        return ok("Solicitud enviada: Aura te contactará con el valor", service.solicitarEspacio(ctx(req), dto));
    }

    @GetMapping("/eventos")
    public ResponseEntity<ApiResponse<List<EventoDto>>> eventos(@RequestParam(required = false) Long archivoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            HttpServletRequest req) {
        return ok("Bitácora", service.eventos(ctx(req), archivoId, desde, hasta));
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** Contexto SOLO con datos del token y de la conexión. */
    private NubeContexto ctx(HttpServletRequest req) {
        Long usuario = securityUtils.getUsuarioId();
        return new NubeContexto(securityUtils.getEmpresaId(), usuario == null ? null : usuario.intValue(),
                securityUtils.getFirmaId(), ipDe(req), req.getHeader(HttpHeaders.USER_AGENT));
    }

    /** IP real detrás del proxy de Dokploy (primera de X-Forwarded-For). */
    private static String ipDe(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data));
    }
}

package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.AnularNotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.ImportarLineasDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.ImportarLineasResultadoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioSoporteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.ReversarNotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioFiltroDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.services.NotaDiarioService;
import com.cloud_technological.aura_pos.services.implementations.NotaDiarioPdfService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Notas contables (comprobante de diario CD) que elabora el contador. */
@RestController
@RequestMapping("/api/contabilidad/notas")
public class NotaDiarioController {

    @Autowired
    private NotaDiarioService service;

    @Autowired
    private NotaDiarioPdfService pdfService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<NotaDiarioTableDto>>> listar(
            @RequestBody PageableDto<NotaDiarioFiltroDto> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<NotaDiarioTableDto> result = service.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.obtener(id, empresaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotaDiarioDto>> crear(@Valid @RequestBody SaveNotaDiarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.crear(empresaId, usuarioId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(HttpStatus.CREATED.value(),
                mensajeGuardado(result), false, result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody SaveNotaDiarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.actualizar(id, empresaId, usuarioId(), dto);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), mensajeGuardado(result), false, result));
    }

    @PostMapping("/{id}/contabilizar")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> contabilizar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.contabilizar(id, empresaId, usuarioId());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), mensajeGuardado(result), false, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminar(id, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Borrador eliminado", false, null));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> anular(@PathVariable Long id,
            @Valid @RequestBody AnularNotaDiarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.anular(id, empresaId, usuarioId(), dto.getMotivo());
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "Nota " + result.getNumeroComprobante() + " anulada", false, result));
    }

    @PostMapping("/{id}/reversar")
    public ResponseEntity<ApiResponse<NotaDiarioDto>> reversar(@PathVariable Long id,
            @Valid @RequestBody ReversarNotaDiarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto result = service.reversar(id, empresaId, usuarioId(), dto.getFecha(), dto.getConcepto());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Reversión " + result.getNumeroComprobante() + " contabilizada", false, result));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioDto nota = service.obtener(id, empresaId);
        byte[] pdf = pdfService.generar(nota, empresaId);
        String nombre = (nota.getNumeroComprobante() != null ? nota.getNumeroComprobante() : "borrador-" + id)
                + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(nombre).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping("/{id}/soportes")
    public ResponseEntity<ApiResponse<NotaDiarioSoporteDto>> subirSoporte(@PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Integer empresaId = securityUtils.getEmpresaId();
        NotaDiarioSoporteDto result = service.subirSoporte(id, empresaId, usuarioId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(HttpStatus.CREATED.value(),
                "Soporte adjuntado", false, result));
    }

    @DeleteMapping("/{id}/soportes/{soporteId}")
    public ResponseEntity<ApiResponse<Void>> eliminarSoporte(@PathVariable Long id, @PathVariable Long soporteId) {
        Integer empresaId = securityUtils.getEmpresaId();
        service.eliminarSoporte(id, soporteId, empresaId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Soporte quitado", false, null));
    }

    /** Interpreta líneas pegadas desde Excel; no guarda nada. */
    @PostMapping("/importar-lineas")
    public ResponseEntity<ApiResponse<ImportarLineasResultadoDto>> importarLineas(
            @Valid @RequestBody ImportarLineasDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "OK", false,
                service.importarLineas(empresaId, dto.getTexto())));
    }

    private Integer usuarioId() {
        return securityUtils.getUsuarioId() != null ? securityUtils.getUsuarioId().intValue() : null;
    }

    private static String mensajeGuardado(NotaDiarioDto nota) {
        return "CONTABILIZADO".equals(nota.getEstado())
                ? "Nota " + nota.getNumeroComprobante() + " contabilizada"
                : "Borrador guardado";
    }
}

package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.PageImpl;
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
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarNovedadDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CandidatoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DetalleLiquidacionDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.EmpleadoDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.FiltroDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.LiquidacionEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.LiquidarDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.NovedadDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.PagarDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProcesoDocumentoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProyeccionContableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.QuitarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ResultadoAgregarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.proceso.ProcesoNominaDto;
import com.cloud_technological.aura_pos.services.NominaDocumentoCicloService;
import com.cloud_technological.aura_pos.services.NominaDocumentoContabilidadService;
import com.cloud_technological.aura_pos.services.NominaDocumentoEmpleadoService;
import com.cloud_technological.aura_pos.services.NominaDocumentoLiquidacionService;
import com.cloud_technological.aura_pos.services.NominaDocumentoNovedadService;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.services.nomina.prestacion.CesantiasExcelExporter;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Prenóminas y nóminas por documento ({@code recursos-humanos.prenomina}). */
@RestController
@RequestMapping("/api/nomina/documentos")
public class NominaDocumentoController {

    private final NominaDocumentoService service;
    private final NominaDocumentoEmpleadoService empleados;
    private final NominaDocumentoLiquidacionService liquidacion;
    private final NominaDocumentoCicloService ciclo;
    private final NominaDocumentoContabilidadService contabilidad;
    private final NominaDocumentoNovedadService novedades;
    private final CesantiasExcelExporter cesantiasExcel;
    private final SecurityUtils securityUtils;

    public NominaDocumentoController(NominaDocumentoService service, NominaDocumentoEmpleadoService empleados,
            NominaDocumentoLiquidacionService liquidacion, NominaDocumentoCicloService ciclo,
            NominaDocumentoContabilidadService contabilidad, NominaDocumentoNovedadService novedades,
            CesantiasExcelExporter cesantiasExcel, SecurityUtils securityUtils) {
        this.service = service;
        this.empleados = empleados;
        this.liquidacion = liquidacion;
        this.ciclo = ciclo;
        this.contabilidad = contabilidad;
        this.novedades = novedades;
        this.cesantiasExcel = cesantiasExcel;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<DocumentoNominaDto>>> listar(
            @RequestBody PageableDto<FiltroDocumentoNominaDto> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> obtener(@PathVariable Long id) {
        return ok("Documento de nómina", service.obtener(id, securityUtils.getEmpresaId()));
    }

    /** Crea una prenómina preliminar (encabezado). */
    @PostMapping("/prenomina")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> crearPrenomina(
            @Valid @RequestBody CreateDocumentoNominaDto dto) {
        DocumentoNominaDto r = service.crearPrenomina(dto, securityUtils.getEmpresaId(),
                securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Prenómina creada", false, r),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> actualizar(@PathVariable Long id,
            @Valid @RequestBody CreateDocumentoNominaDto dto) {
        return ok("Actualizado", service.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    /** Prenómina preliminar, o nómina (reversa el asiento de cada empleado). */
    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> anular(@PathVariable Long id) {
        return ok("Anulado", ciclo.anular(id, securityUtils.getEmpresaId()));
    }

    // ── Ciclo prenómina → nómina ─────────────────────────────────────────────

    @PostMapping("/{id}/confirmar-prenomina")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> confirmarPrenomina(@PathVariable Long id) {
        return ok("Prenómina confirmada", ciclo.confirmarPrenomina(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/reabrir")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> reabrir(@PathVariable Long id) {
        return ok("Prenómina reabierta", ciclo.reabrirPrenomina(id, securityUtils.getEmpresaId()));
    }

    /** Genera la nómina desde la prenómina confirmada; devuelve la nómina creada. */
    @PostMapping("/{id}/generar-nomina")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> generarNomina(@PathVariable Long id) {
        DocumentoNominaDto r = ciclo.generarNomina(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Nómina generada", false, r),
                HttpStatus.CREATED);
    }

    /** Paga empleados de la nómina confirmada (los indicados o todos los pendientes). */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> pagar(@PathVariable Long id,
            @RequestBody PagarDocumentoDto dto) {
        return ok("Nómina pagada", ciclo.pagar(id, dto, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/confirmar-nomina")
    public ResponseEntity<ApiResponse<DocumentoNominaDto>> confirmarNomina(@PathVariable Long id) {
        return ok("Nómina confirmada", ciclo.confirmarNomina(id, securityUtils.getEmpresaId()));
    }

    // ── Pestaña Empleados ────────────────────────────────────────────────────

    @GetMapping("/{id}/empleados")
    public ResponseEntity<ApiResponse<List<EmpleadoDocumentoDto>>> listarEmpleados(@PathVariable Long id) {
        return ok("Empleados", empleados.listar(id, securityUtils.getEmpresaId()));
    }

    /** Vinculaciones vigentes en el período con la causa por la que no entrarían. */
    @GetMapping("/{id}/candidatos")
    public ResponseEntity<ApiResponse<List<CandidatoNominaDto>>> candidatos(@PathVariable Long id) {
        return ok("Candidatos", empleados.candidatos(id, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/empleados")
    public ResponseEntity<ApiResponse<ResultadoAgregarEmpleadosDto>> agregarEmpleados(@PathVariable Long id,
            @Valid @RequestBody AgregarEmpleadosDto dto) {
        return ok("Empleados agregados", empleados.agregar(id, dto, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/empleados/quitar")
    public ResponseEntity<ApiResponse<Boolean>> quitarEmpleados(@PathVariable Long id,
            @Valid @RequestBody QuitarEmpleadosDto dto) {
        empleados.quitar(id, dto.getNominaIds(), securityUtils.getEmpresaId());
        return ok("Empleados quitados", true);
    }

    // ── Pestaña Novedades ────────────────────────────────────────────────────

    @GetMapping("/{id}/novedades")
    public ResponseEntity<ApiResponse<List<NovedadDocumentoDto>>> listarNovedades(@PathVariable Long id) {
        return ok("Novedades", novedades.listar(id, securityUtils.getEmpresaId()));
    }

    /** Agrega una novedad; el empleado queda sin liquidar hasta reliquidarlo. */
    @PostMapping("/{id}/novedades")
    public ResponseEntity<ApiResponse<List<NovedadDocumentoDto>>> agregarNovedad(@PathVariable Long id,
            @Valid @RequestBody AgregarNovedadDocumentoDto dto) {
        return ok("Novedad agregada", novedades.agregar(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}/novedades/{novedadId}")
    public ResponseEntity<ApiResponse<List<NovedadDocumentoDto>>> eliminarNovedad(@PathVariable Long id,
            @PathVariable Long novedadId) {
        return ok("Novedad eliminada", novedades.eliminar(id, novedadId, securityUtils.getEmpresaId()));
    }

    // ── Liquidación y traza ──────────────────────────────────────────────────

    /** Lanza la liquidación en segundo plano; 202 con el proceso para consultar su avance. */
    @PostMapping("/{id}/liquidar")
    public ResponseEntity<ApiResponse<ProcesoNominaDto>> liquidar(@PathVariable Long id,
            @RequestBody(required = false) LiquidarDocumentoDto dto) {
        ProcesoNominaDto proceso = ProcesoNominaDto.de(liquidacion.liquidar(id, dto == null ? List.of() : dto.getNominaIds(),
                dto != null && dto.isSoloPendientes(),
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.ACCEPTED.value(), "Liquidación en proceso", false,
                proceso), HttpStatus.ACCEPTED);
    }

    @GetMapping("/{id}/liquidacion")
    public ResponseEntity<ApiResponse<List<LiquidacionEmpleadoDto>>> liquidacion(@PathVariable Long id) {
        return ok("Liquidación", liquidacion.liquidacion(id, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}/liquidacion/{nominaId}/detalle")
    public ResponseEntity<ApiResponse<List<DetalleLiquidacionDto>>> detalle(@PathVariable Long id,
            @PathVariable Long nominaId) {
        return ok("Detalle", liquidacion.detalle(id, nominaId, securityUtils.getEmpresaId()));
    }

    /** Proyección del asiento con lo liquidado (pestaña Contabilidad); no guarda nada. */
    @GetMapping("/{id}/contabilidad")
    public ResponseEntity<ApiResponse<ProyeccionContableDto>> contabilidad(@PathVariable Long id) {
        return ok("Proyección contable", contabilidad.proyeccion(id, securityUtils.getEmpresaId()));
    }

    /** Consignación de cesantías por fondo (nómina de cesantías confirmada), en Excel. */
    @GetMapping("/{id}/cesantias-excel")
    public ResponseEntity<byte[]> cesantiasExcel(@PathVariable Long id) {
        byte[] excel = cesantiasExcel.excel(id, securityUtils.getEmpresaId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consignacion_cesantias_" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/{id}/traza")
    public ResponseEntity<ApiResponse<List<ProcesoDocumentoDto>>> traza(@PathVariable Long id) {
        return ok("Traza", liquidacion.traza(id, securityUtils.getEmpresaId()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

package com.cloud_technological.aura_pos.controllers;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ActualizarConfiguracionDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.ConfiguracionEmpresaDto;
import com.cloud_technological.aura_pos.dto.empresas.ConfiguracionEmpresaDtos.LineaUsoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogDetalleDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogGrupoDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogPageParamsDto;
import com.cloud_technological.aura_pos.dto.error_log.ErrorLogTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.CreateEmpresaResponseDto;
import com.cloud_technological.aura_pos.dto.super_admin.DashboardPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaPlataformaDto;
import com.cloud_technological.aura_pos.dto.super_admin.EmpresaTableDto;
import com.cloud_technological.aura_pos.dto.super_admin.UpdateEmpresaPlataformaDto;
import com.cloud_technological.aura_pos.repositories.firma_contable.ActivacionElectronicaQueryRepository.SolicitudActivacionFila;
import com.cloud_technological.aura_pos.services.EmpresaPlataformaService;
import com.cloud_technological.aura_pos.services.ErrorLogService;
import com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService;
import com.cloud_technological.aura_pos.services.correo.AccesoCorreoService;
import com.cloud_technological.aura_pos.services.correo.AccesoCorreoService.EnvioAcceso;
import com.cloud_technological.aura_pos.services.firma_contable.ActivacionElectronicaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    @Autowired
    private EmpresaPlataformaService empresaService;

    @Autowired
    private ErrorLogService errorLogService;

    @Autowired
    private ConfiguracionEmpresaService configuracionService;

    @Autowired
    private ActivacionElectronicaService activacionElectronica;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private AccesoCorreoService accesoCorreo;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardPlataformaDto>> dashboard() {
        DashboardPlataformaDto result = empresaService.dashboard();
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Dashboard obtenido", false, result), HttpStatus.OK);
    }

    @PostMapping("/empresas/page")
    public ResponseEntity<ApiResponse<PageImpl<EmpresaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        PageImpl<EmpresaTableDto> result = empresaService.listar(pageable);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/empresas/{id}")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> obtenerPorId(@PathVariable Integer id) {
        EmpresaPlataformaDto result = empresaService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/empresas")
    public ResponseEntity<ApiResponse<CreateEmpresaResponseDto>> crear(
            @Valid @RequestBody CreateEmpresaPlataformaDto dto) {
        CreateEmpresaResponseDto result = empresaService.crear(dto);
        // Ya creada (transacción confirmada): el administrador recibe su acceso por correo.
        // Si el correo falla, la empresa queda creada y se puede reenviar desde el panel.
        EnvioAcceso envio = accesoCorreo.enviarAccesoInicial(result.getEmpresa().getId(), result.getEmailAdmin(),
                result.getPasswordTemporal(), result.getResetLink(), actor());
        result.setCorreoEnviado(envio.enviado());
        result.setCorreoError(envio.error());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Empresa creada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PutMapping("/empresas/{id}")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateEmpresaPlataformaDto dto) {
        EmpresaPlataformaDto result = empresaService.actualizar(id, dto);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa actualizada exitosamente", false, result), HttpStatus.OK);
    }

    /** Nuevo enlace para que el administrador cree su contraseña, enviado a su correo. */
    @PostMapping("/empresas/{id}/reenviar-acceso")
    public ResponseEntity<ApiResponse<EnvioAcceso>> reenviarAcceso(@PathVariable Integer id) {
        EnvioAcceso r = accesoCorreo.reenviarAcceso(id, actor());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(),
                r.enviado() ? "Acceso enviado a " + r.email() : "No se pudo enviar el correo", false, r), HttpStatus.OK);
    }

    @PatchMapping("/empresas/{id}/suspender")
    public ResponseEntity<ApiResponse<Boolean>> suspender(@PathVariable Integer id) {
        empresaService.suspender(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa suspendida correctamente", false, true), HttpStatus.OK);
    }

    @PatchMapping("/empresas/{id}/activar")
    public ResponseEntity<ApiResponse<Boolean>> activar(@PathVariable Integer id) {
        empresaService.activar(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Empresa activada correctamente", false, true), HttpStatus.OK);
    }

    // ─── Líneas de uso (docs/PLAN_PERFIL_EMPRESA.md) ──────────

    /** Catálogo de líneas con los submódulos que cada una trae marcados en el árbol. */
    @GetMapping("/lineas-uso")
    public ResponseEntity<ApiResponse<List<LineaUsoDto>>> lineasUso() {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Líneas de uso", false,
                configuracionService.catalogo()), HttpStatus.OK);
    }

    @GetMapping("/empresas/{id}/configuracion")
    public ResponseEntity<ApiResponse<ConfiguracionEmpresaDto>> configuracion(@PathVariable Integer id) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Configuración obtenida", false,
                configuracionService.obtener(id)), HttpStatus.OK);
    }

    @PutMapping("/empresas/{id}/configuracion")
    public ResponseEntity<ApiResponse<ConfiguracionEmpresaDto>> actualizarConfiguracion(@PathVariable Integer id,
            @RequestBody ActualizarConfiguracionDto dto) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Líneas de uso actualizadas", false,
                configuracionService.actualizar(id, dto)), HttpStatus.OK);
    }

    // ─── Firma contable (docs/PLAN_FIRMA_CONTABLE.md) ─────────

    /** Firmas contables activas, para el buscador de crear/editar empresa. */
    @GetMapping("/firmas-contables")
    public ResponseEntity<ApiResponse<java.util.List<com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.EmpresaDisponible>>> firmasContables(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String texto) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Firmas contables", false,
                empresaService.firmasContables(texto)), HttpStatus.OK);
    }

    /** Cambia o quita ({@code firmaEmpresaId} null) la firma contable que atiende a la empresa. */
    @PutMapping("/empresas/{id}/firma-contable")
    public ResponseEntity<ApiResponse<EmpresaPlataformaDto>> asignarFirmaContable(@PathVariable Integer id,
            @RequestBody java.util.Map<String, Integer> body) {
        Integer firmaId = body != null ? body.get("firmaEmpresaId") : null;
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(),
                firmaId == null ? "Firma contable quitada" : "Firma contable asignada", false,
                empresaService.asignarFirmaContable(id, firmaId)), HttpStatus.OK);
    }

    /** Vuelve a correr el arranque (PUC y configuración por defecto); no duplica nada. */
    @PostMapping("/empresas/{id}/arranque")
    public ResponseEntity<ApiResponse<ConfiguracionEmpresaDto>> reintentarArranque(@PathVariable Integer id) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Arranque ejecutado", false,
                configuracionService.reintentarArranque(id)), HttpStatus.OK);
    }

    // ─── Activación electrónica de gestionados (docs/PLAN_FIRMA_CONTABLE.md §10) ──

    /** Solicitudes de las firmas que esperan las credenciales de Factus, la más antigua primero. */
    @GetMapping("/solicitudes-electronica")
    public ResponseEntity<ApiResponse<List<SolicitudActivacionFila>>> solicitudesElectronica() {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Solicitudes", false,
                activacionElectronica.pendientes()), HttpStatus.OK);
    }

    /** Cuántas hay pendientes (campana del panel). */
    @GetMapping("/solicitudes-electronica/pendientes")
    public ResponseEntity<ApiResponse<Integer>> solicitudesElectronicaPendientes() {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Pendientes", false,
                activacionElectronica.contarPendientes()), HttpStatus.OK);
    }

    /** Marca atendida a mano (la empresa ya debe tener la facturación electrónica activa). */
    @PostMapping("/solicitudes-electronica/{id}/atender")
    public ResponseEntity<ApiResponse<Boolean>> atenderSolicitudElectronica(@PathVariable Long id) {
        activacionElectronica.atender(id, actor());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Solicitud atendida", false, true),
                HttpStatus.OK);
    }

    @PostMapping("/solicitudes-electronica/{id}/rechazar")
    public ResponseEntity<ApiResponse<Boolean>> rechazarSolicitudElectronica(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        activacionElectronica.rechazar(id, body != null ? body.get("motivo") : null, actor());
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Solicitud rechazada", false, true),
                HttpStatus.OK);
    }

    private Integer actor() {
        Long id = securityUtils.getUsuarioId();
        return id != null ? id.intValue() : null;
    }

    // ─── Error Logs ───────────────────────────────────────────

    @PostMapping("/error-logs/page")
    public ResponseEntity<ApiResponse<PageImpl<ErrorLogTableDto>>> errorLogs(
            @RequestBody PageableDto<ErrorLogPageParamsDto> pageable) {
        var result = errorLogService.listar(pageable);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @PostMapping("/error-logs/grupos")
    public ResponseEntity<ApiResponse<PageImpl<ErrorLogGrupoDto>>> errorLogGrupos(
            @RequestBody PageableDto<ErrorLogPageParamsDto> pageable) {
        var result = errorLogService.listarGrupos(pageable);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Grupos obtenidos", false, result), HttpStatus.OK);
    }

    @GetMapping("/error-logs/{id}")
    public ResponseEntity<ApiResponse<ErrorLogDetalleDto>> errorLogDetalle(@PathVariable Long id) {
        ErrorLogDetalleDto result = errorLogService.obtenerPorId(id);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Detalle obtenido", false, result), HttpStatus.OK);
    }
}
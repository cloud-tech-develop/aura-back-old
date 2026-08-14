package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.CambiarSalarioDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.CentroCostoDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.ContratoDetalleDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.ContratoDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.ContratoTableDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.CreateContratoDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.CreateRenovacionDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.RenovacionDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.SalarioHistorialDto;
import com.cloud_technological.aura_pos.dto.nomina.contrato.ContratoDtos.TerminarContratoDto;
import com.cloud_technological.aura_pos.entity.ContratoCentroCostoEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.ContratoRenovacionEntity;
import com.cloud_technological.aura_pos.entity.ContratoSalarioHistorialEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.services.implementations.ContratoLaboralService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Contratos laborales (Fase 2).
 *
 * <p>Antes el contrato estaba embebido en el empleado. Ahora es su propia
 * entidad: una persona puede tener varios contratos, y el salario tiene
 * historial.
 *
 * <p><b>Cambiar el salario NO es un update</b> — ver {@link #cambiarSalario}.
 */
@RestController
@RequestMapping("/api/contrato")
public class ContratoController {

    @Autowired
    private ContratoLaboralService contratoService;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private SecurityUtils securityUtils;

    // ── Consultas ───────────────────────────────────────────────────────────

    /** Cargos ya usados en la empresa: alimentan el autocompletar del form. */
    @GetMapping("/cargos")
    public ResponseEntity<ApiResponse<List<String>>> cargos() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Cargos obtenidos", contratoService.cargosUsados(empresaId));
    }

    /** Contratos activos de un empleado. Puede haber varios (multi-vínculo). */
    @GetMapping("/empleado/{empleadoId}")
    public ResponseEntity<ApiResponse<List<ContratoTableDto>>> porEmpleado(@PathVariable Long empleadoId) {
        List<ContratoTableDto> result = contratoService.activosDe(empleadoId).stream()
                .map(this::aTabla)
                .toList();
        return ok("Listado exitoso", result);
    }

    /** Detalle con historial salarial y renovaciones. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContratoDetalleDto>> obtener(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ContratoLaboralEntity c = contratoService.obtener(id, empresaId);

        ContratoDetalleDto d = new ContratoDetalleDto();
        d.setContrato(aDto(c));
        d.setHistorialSalarios(aHistorial(contratoService.historialDe(id)));
        d.setRenovaciones(contratoService.renovacionesDe(id).stream().map(this::aRenovacion).toList());
        d.setCentrosCosto(aCentrosCosto(c.getCentrosCosto()));
        return ok("Consulta exitosa", d);
    }

    @GetMapping("/{id}/historial-salarios")
    public ResponseEntity<ApiResponse<List<SalarioHistorialDto>>> historial(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        contratoService.obtener(id, empresaId);   // valida pertenencia a la empresa
        return ok("Consulta exitosa", aHistorial(contratoService.historialDe(id)));
    }

    /** Contratos a término fijo que vencen pronto. Alimenta las alertas. */
    @GetMapping("/por-vencer")
    public ResponseEntity<ApiResponse<List<ContratoTableDto>>> porVencer(
            @RequestParam(defaultValue = "30") int dias) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ContratoTableDto> result = contratoService.porVencer(empresaId, dias).stream()
                .map(this::aTabla)
                .toList();
        return ok("Listado exitoso", result);
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<ContratoDto>> crear(@RequestBody CreateContratoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        ContratoLaboralEntity c = new ContratoLaboralEntity();
        c.setEmpresa(empresa);
        c.setEmpleado(empleado);
        c.setTipoContrato(dto.getTipoContrato());
        c.setCargo(dto.getCargo());
        c.setFechaInicio(dto.getFechaInicio());
        c.setFechaFin(dto.getFechaFin());
        c.setSalarioBase(dto.getSalarioBase());
        c.setEsSalarioIntegral(Boolean.TRUE.equals(dto.getEsSalarioIntegral()));
        c.setFase(dto.getFase());
        c.setPeriodicidad(dto.getPeriodicidad());
        c.setEsPrincipal(dto.getEsPrincipal() == null || dto.getEsPrincipal());
        c.setProcedimientoRetefuente(
                dto.getProcedimientoRetefuente() != null ? dto.getProcedimientoRetefuente() : "1");
        c.setObservacion(dto.getObservacion());
        c.setNivelRiesgoArl(dto.getNivelRiesgoArl());
        c.setEstado("ACTIVO");

        // crear() valida coherencia de fechas (FIJO exige fin, INDEFINIDO no lo
        // admite), que no haya dos principales, y abre el historial salarial.
        ContratoLaboralEntity guardado = contratoService.crear(c);

        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Contrato creado exitosamente",
                        false, aDto(guardado)),
                HttpStatus.CREATED);
    }

    /**
     * Edita el contrato (corrección de errores). No cambia el salario: eso pasa
     * por {@code PUT /{id}/salario} para preservar el histórico.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContratoDto>> editar(
            @PathVariable Long id,
            @RequestBody CreateContratoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();

        ContratoLaboralEntity datos = new ContratoLaboralEntity();
        datos.setTipoContrato(dto.getTipoContrato());
        datos.setCargo(dto.getCargo());
        datos.setFechaInicio(dto.getFechaInicio());
        datos.setFechaFin(dto.getFechaFin());
        datos.setEsSalarioIntegral(Boolean.TRUE.equals(dto.getEsSalarioIntegral()));
        datos.setFase(dto.getFase());
        datos.setPeriodicidad(dto.getPeriodicidad());
        datos.setEsPrincipal(dto.getEsPrincipal() == null || dto.getEsPrincipal());
        datos.setProcedimientoRetefuente(
                dto.getProcedimientoRetefuente() != null ? dto.getProcedimientoRetefuente() : "1");
        datos.setNivelRiesgoArl(dto.getNivelRiesgoArl());
        datos.setObservacion(dto.getObservacion());

        ContratoLaboralEntity guardado = contratoService.editar(id, empresaId, datos);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Contrato actualizado", false, aDto(guardado)),
                HttpStatus.OK);
    }

    // ── Salario ─────────────────────────────────────────────────────────────

    /**
     * Cambia el salario preservando el histórico.
     *
     * <p><b>No es un update.</b> Cierra la vigencia anterior y abre una nueva.
     * Por eso el body exige {@code fechaDesde} y {@code motivo}: sin la fecha no
     * se pueden liquidar retroactivos ni calcular la bandera {@code vsp} de PILA.
     */
    @PutMapping("/{id}/salario")
    public ResponseEntity<ApiResponse<ContratoDto>> cambiarSalario(@PathVariable Long id,
                                                                   @RequestBody CambiarSalarioDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = usuarioActual();

        if (dto.getFechaDesde() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Indique desde cuándo rige el nuevo salario");
        }

        ContratoLaboralEntity c = contratoService.cambiarSalario(
                id, empresaId, dto.getNuevoSalario(), dto.getFechaDesde(), dto.getMotivo(), usuarioId);
        return ok("Salario actualizado. Se conservó el histórico.", aDto(c));
    }

    // ── Terminación ─────────────────────────────────────────────────────────

    /**
     * Termina el contrato.
     *
     * <p>{@code causaRetiro} determina si hay indemnización y cómo se calcula
     * (Fase 8). No es un campo descriptivo.
     */
    @PutMapping("/{id}/terminar")
    public ResponseEntity<ApiResponse<ContratoDto>> terminar(@PathVariable Long id,
                                                             @RequestBody TerminarContratoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        if (dto.getFechaFin() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de terminación es obligatoria");
        }
        ContratoLaboralEntity c = contratoService.terminar(
                id, empresaId, dto.getFechaFin(), dto.getCausaRetiro());
        return ok("Contrato terminado", aDto(c));
    }

    // ── Renovación ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/renovacion")
    public ResponseEntity<ApiResponse<RenovacionDto>> renovar(@PathVariable Long id,
                                                              @RequestBody CreateRenovacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        ContratoRenovacionEntity r = contratoService.renovar(
                id, empresaId, dto.getFechaInicial(), dto.getFechaFinal(),
                dto.getDescripcion(), usuarioActual());
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Contrato renovado", false, aRenovacion(r)),
                HttpStatus.CREATED);
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────

    private ContratoDto aDto(ContratoLaboralEntity c) {
        ContratoDto d = new ContratoDto();
        d.setId(c.getId());
        if (c.getEmpleado() != null) {
            d.setEmpleadoId(c.getEmpleado().getId());
            d.setEmpleadoNombre(c.getEmpleado().getNombreCompletoResuelto());
            d.setEmpleadoDocumento(c.getEmpleado().getNumeroDocumentoResuelto());
        }
        d.setTipoContrato(c.getTipoContrato());
        d.setCargo(c.getCargo());
        d.setFechaInicio(c.getFechaInicio());
        d.setFechaFin(c.getFechaFin());
        d.setSalarioBase(c.getSalarioBase());
        d.setEsSalarioIntegral(c.getEsSalarioIntegral());
        d.setFase(c.getFase());
        d.setPeriodicidad(c.getPeriodicidad());
        d.setEsPrincipal(c.getEsPrincipal());
        d.setProcedimientoRetefuente(c.getProcedimientoRetefuente());
        d.setPorcentajeFijoRetencion(c.getPorcentajeFijoRetencion());
        d.setEstado(c.getEstado());
        d.setCausaRetiro(c.getCausaRetiro());
        d.setObservacion(c.getObservacion());
        d.setTipoCotizante(c.getTipoCotizante());
        d.setSubtipoCotizante(c.getSubtipoCotizante());
        d.setNivelRiesgoArl(c.getNivelRiesgoArl());
        d.setTarifaArl(c.getTarifaArl());
        d.setCreatedAt(c.getCreatedAt());
        d.setUpdatedAt(c.getUpdatedAt());
        return d;
    }

    private ContratoTableDto aTabla(ContratoLaboralEntity c) {
        ContratoTableDto t = new ContratoTableDto();
        t.setId(c.getId());
        if (c.getEmpleado() != null) {
            t.setEmpleadoId(c.getEmpleado().getId());
            t.setEmpleadoNombre(c.getEmpleado().getNombreCompletoResuelto());
        }
        t.setTipoContrato(c.getTipoContrato());
        t.setCargo(c.getCargo());
        t.setFechaInicio(c.getFechaInicio());
        t.setFechaFin(c.getFechaFin());
        t.setSalarioBase(c.getSalarioBase());
        t.setEsPrincipal(c.getEsPrincipal());
        t.setEstado(c.getEstado());
        // Null si es indefinido: no hay vencimiento que contar.
        t.setDiasParaVencer(c.getFechaFin() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), c.getFechaFin())
                : null);
        return t;
    }

    /**
     * Historial salarial con la variación entre versiones.
     *
     * <p>Llega ordenado de más nuevo a más viejo, así que el "anterior" de cada
     * fila es el siguiente del listado.
     */
    private List<SalarioHistorialDto> aHistorial(List<ContratoSalarioHistorialEntity> hs) {
        List<SalarioHistorialDto> out = new ArrayList<>();
        for (int i = 0; i < hs.size(); i++) {
            ContratoSalarioHistorialEntity h = hs.get(i);
            SalarioHistorialDto d = new SalarioHistorialDto();
            d.setId(h.getId());
            d.setSalario(h.getSalario());
            d.setFechaDesde(h.getFechaDesde());
            d.setFechaHasta(h.getFechaHasta());
            d.setMotivo(h.getMotivo());
            d.setCreatedAt(h.getCreatedAt());

            if (i + 1 < hs.size()) {
                BigDecimal anterior = hs.get(i + 1).getSalario();
                if (anterior != null && anterior.signum() > 0) {
                    d.setVariacionPorcentaje(h.getSalario().subtract(anterior)
                            .multiply(new BigDecimal("100"))
                            .divide(anterior, 2, RoundingMode.HALF_UP));
                }
            }
            out.add(d);
        }
        return out;
    }

    private RenovacionDto aRenovacion(ContratoRenovacionEntity r) {
        RenovacionDto d = new RenovacionDto();
        d.setId(r.getId());
        d.setFechaInicial(r.getFechaInicial());
        d.setFechaFinal(r.getFechaFinal());
        d.setDescripcion(r.getDescripcion());
        d.setCreatedAt(r.getCreatedAt());
        return d;
    }

    private List<CentroCostoDto> aCentrosCosto(List<ContratoCentroCostoEntity> ccs) {
        if (ccs == null) return List.of();
        return ccs.stream().map(cc -> {
            CentroCostoDto d = new CentroCostoDto();
            d.setId(cc.getId());
            d.setCentroCostoId(cc.getCentroCostoId());
            d.setPorcentaje(cc.getPorcentaje());
            return d;
        }).toList();
    }

    private Long usuarioActual() {
        try {
            return securityUtils.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}

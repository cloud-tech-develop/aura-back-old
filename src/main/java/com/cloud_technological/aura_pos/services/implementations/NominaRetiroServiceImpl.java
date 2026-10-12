package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.FiltroRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RegistrarRetiroDto;
import com.cloud_technological.aura_pos.dto.nomina.retiro.RetiroDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.NominaCatalogoEntity;
import com.cloud_technological.aura_pos.entity.NominaRetiroEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_retiros.NominaRetiroJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_retiros.NominaRetiroQueryRepository;
import com.cloud_technological.aura_pos.services.NominaDocumentoEmpleadoService;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.services.NominaRetiroService;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.services.nomina.comprobante.ClaseComprobanteNomina;
import com.cloud_technological.aura_pos.services.nomina.catalogos.TipoCatalogoNomina;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.JsonMapas;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaRetiroService}. */
@Service
public class NominaRetiroServiceImpl implements NominaRetiroService {

    private static final String VIGENTE = "VIGENTE";
    private static final String ANULADO = "ANULADO";

    private final NominaRetiroJPARepository jpaRepository;
    private final NominaRetiroQueryRepository queryRepository;
    private final ContratoLaboralService contratos;
    private final EmpleadoJPARepository empleados;
    private final NominaCatalogoJPARepository catalogos;
    private final NominaTipoComprobanteService tiposComprobante;
    private final NominaDocumentoService documentos;
    private final NominaDocumentoEmpleadoService empleadosDocumento;

    public NominaRetiroServiceImpl(NominaRetiroJPARepository jpaRepository,
            NominaRetiroQueryRepository queryRepository,
            ContratoLaboralService contratos,
            EmpleadoJPARepository empleados,
            NominaCatalogoJPARepository catalogos,
            NominaTipoComprobanteService tiposComprobante,
            NominaDocumentoService documentos,
            NominaDocumentoEmpleadoService empleadosDocumento) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.contratos = contratos;
        this.empleados = empleados;
        this.catalogos = catalogos;
        this.tiposComprobante = tiposComprobante;
        this.documentos = documentos;
        this.empleadosDocumento = empleadosDocumento;
    }

    @Override
    public PageImpl<RetiroDto> listar(PageableDto<FiltroRetiroDto> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public RetiroDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Retiro no encontrado"));
    }

    @Override
    @Transactional
    public RetiroDto registrar(RegistrarRetiroDto dto, Integer empresaId, Long usuarioId) {
        ContratoLaboralEntity contrato = contratos.obtener(dto.getContratoId(), empresaId);
        if (!"ACTIVO".equals(contrato.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La vinculación está " + contrato.getEstado().toLowerCase() + ": no se puede retirar.");
        if (queryRepository.tieneRetiroVigente(contrato.getId()))
            throw new GlobalException(HttpStatus.CONFLICT, "La vinculación ya tiene un retiro registrado.");
        if (dto.getFechaRetiro().isBefore(contrato.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha de retiro no puede ser anterior al ingreso (" + contrato.getFechaInicio() + ").");
        NominaCatalogoEntity motivo = motivo(dto.getMotivoId(), empresaId);

        NominaRetiroEntity r = new NominaRetiroEntity();
        r.setEmpresaId(empresaId);
        r.setContratoId(contrato.getId());
        r.setEmpleadoId(contrato.getEmpleado().getId());
        r.setFechaRetiro(dto.getFechaRetiro());
        r.setMotivoId(motivo.getId());
        r.setIndemniza(Boolean.TRUE.equals(JsonMapas.deJson(motivo.getAtributos()).get("indemniza")));
        // Se guarda antes de terminar: terminar() la sobrescribe con la fecha de retiro.
        r.setFechaFinPactada(contrato.getFechaFin());
        r.setCesantiasCorteHasta(dto.getCesantiasCorteHasta());
        r.setObservacion(dto.getObservacion() == null || dto.getObservacion().isBlank()
                ? null : dto.getObservacion().trim());
        r.setEstado(VIGENTE);
        r.setUsuarioId(usuarioId);
        r.setCreatedAt(LocalDateTime.now());

        // Termina la vinculación: PILA reporta RET por su fecha de fin.
        contratos.terminar(contrato.getId(), empresaId, dto.getFechaRetiro(), motivo.getCodigo());
        if (!queryRepository.tieneOtraVinculacionActiva(r.getEmpleadoId(), contrato.getId()))
            marcarEmpleado(r.getEmpleadoId(), empresaId, false, r);

        Long id = jpaRepository.saveAndFlush(r).getId();
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public RetiroDto anular(Long id, Integer empresaId) {
        NominaRetiroEntity r = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Retiro no encontrado"));
        if (ANULADO.equals(r.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El retiro ya está anulado.");
        RetiroDto actual = obtener(id, empresaId);
        if (actual.getDocumentoId() != null)
            throw new GlobalException(HttpStatus.CONFLICT, "El retiro ya tiene la liquidación "
                    + actual.getDocumentoNumero() + ". Anúlela primero para poder anular el retiro.");

        contratos.reactivar(r.getContratoId(), empresaId, r.getFechaFinPactada());
        marcarEmpleado(r.getEmpleadoId(), empresaId, true, r);
        r.setEstado(ANULADO);
        r.setAnuladoAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(r);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto generarLiquidacion(Long id, Integer empresaId, Long usuarioId) {
        NominaRetiroEntity r = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Retiro no encontrado"));
        if (ANULADO.equals(r.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El retiro está anulado.");
        RetiroDto actual = obtener(id, empresaId);
        if (actual.getDocumentoId() != null)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El retiro ya tiene la liquidación " + actual.getDocumentoNumero() + ".");

        TipoComprobanteTableDto tipo = tiposComprobante.activos(empresaId).stream()
                .filter(t -> ClaseComprobanteNomina.LIQUIDACION_DEFINITIVA.name().equals(t.getClase()))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Cree (o active) un tipo de comprobante de clase Liquidación definitiva."));
        ContratoLaboralEntity contrato = contratos.obtener(r.getContratoId(), empresaId);

        CreateDocumentoNominaDto dto = new CreateDocumentoNominaDto();
        dto.setTipoComprobanteId(tipo.getId());
        dto.setFecha(LocalDate.now());
        dto.setFechaDesde(desdeSalario(r, contrato));
        dto.setFechaHasta(r.getFechaRetiro());
        dto.setFechaLiquidacion(r.getFechaRetiro());
        dto.setSimulacion(false);
        dto.setDescripcion("Liquidación definitiva — " + actual.getEmpleadoNombre()
                + (actual.getCodigoVinculacion() != null ? " (" + actual.getCodigoVinculacion() + ")" : ""));
        DocumentoNominaDto doc = documentos.crearPrenomina(dto, empresaId, usuarioId);
        empleadosDocumento.agregarRetirado(doc.getId(), contrato.getId(), r.getEmpleadoId(),
                contrato.getSalarioBase(), empresaId);

        r.setDocumentoId(doc.getId());
        r.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(r);
        return documentos.obtener(doc.getId(), empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /**
     * Desde cuándo se liquida el salario de la definitiva: el día siguiente al último
     * período pagado; si nunca se le pagó, el inicio del mes del retiro (o el ingreso,
     * si fue después). Nunca después de la fecha de retiro.
     */
    private LocalDate desdeSalario(NominaRetiroEntity r, ContratoLaboralEntity contrato) {
        LocalDate desde = queryRepository.salarioPagadoHasta(contrato.getId(), null)
                .map(d -> d.plusDays(1))
                .orElseGet(() -> {
                    LocalDate inicioMes = r.getFechaRetiro().withDayOfMonth(1);
                    return contrato.getFechaInicio().isAfter(inicioMes) ? contrato.getFechaInicio() : inicioMes;
                });
        return desde.isAfter(r.getFechaRetiro()) ? r.getFechaRetiro() : desde;
    }

    /** El motivo debe ser del catálogo de motivos de retiro de la empresa. */
    private NominaCatalogoEntity motivo(Long motivoId, Integer empresaId) {
        return catalogos.findByIdAndEmpresaId(motivoId, empresaId)
                .filter(m -> m.getDeletedAt() == null && TipoCatalogoNomina.MOTIVO_RETIRO.name().equals(m.getTipo()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El motivo de retiro no es válido."));
    }

    /** Retirado: inactivo con su fecha de retiro. Reactivado: activo y sin fecha de retiro. */
    private void marcarEmpleado(Long empleadoId, Integer empresaId, boolean activo, NominaRetiroEntity r) {
        EmpleadoEntity e = empleados.findByIdAndEmpresaId(empleadoId, empresaId).orElse(null);
        if (e == null) return;
        e.setActivo(activo);
        e.setFechaRetiro(activo ? null : r.getFechaRetiro());
        e.setUpdatedAt(LocalDateTime.now());
        empleados.save(e);
    }
}

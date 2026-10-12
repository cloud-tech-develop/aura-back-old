package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.FiltroDocumentoNominaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaDocumentoEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.mappers.NominaDocumentoMapper;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoQueryRepository;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaDocumentoService}. */
@Service
public class NominaDocumentoServiceImpl implements NominaDocumentoService {

    static final String PRENOMINA = "PRENOMINA";
    static final String PRELIMINAR = "PRELIMINAR";
    static final String ANULADO = "ANULADO";

    private final NominaDocumentoJPARepository jpaRepository;
    private final NominaDocumentoQueryRepository queryRepository;
    private final PeriodoNominaJPARepository periodoRepository;
    private final NominaTipoComprobanteService tiposComprobante;
    private final NominaDocumentoMapper mapper;

    public NominaDocumentoServiceImpl(NominaDocumentoJPARepository jpaRepository,
            NominaDocumentoQueryRepository queryRepository,
            PeriodoNominaJPARepository periodoRepository,
            NominaTipoComprobanteService tiposComprobante,
            NominaDocumentoMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.periodoRepository = periodoRepository;
        this.tiposComprobante = tiposComprobante;
        this.mapper = mapper;
    }

    @Override
    public PageImpl<DocumentoNominaDto> listar(PageableDto<FiltroDocumentoNominaDto> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public DocumentoNominaDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
    }

    @Override
    @Transactional
    public DocumentoNominaDto crearPrenomina(CreateDocumentoNominaDto dto, Integer empresaId, Long usuarioId) {
        exigirTipoActivo(dto.getTipoComprobanteId(), empresaId);
        validarFechas(dto);

        NominaDocumentoEntity d = new NominaDocumentoEntity();
        mapper.copiarEncabezado(dto, d);
        completar(d, dto);
        d.setEmpresaId(empresaId);
        d.setClaseDocumento(PRENOMINA);
        d.setEstado(PRELIMINAR);
        d.setNumero(queryRepository.siguienteNumero(empresaId, dto.getTipoComprobanteId(), PRENOMINA));
        d.setPeriodoId(periodoPara(empresaId, dto.getFechaDesde(), dto.getFechaHasta()));
        d.setUsuarioId(usuarioId);
        d.setCreatedAt(LocalDateTime.now());
        return obtener(jpaRepository.save(d).getId(), empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto actualizar(Long id, CreateDocumentoNominaDto dto, Integer empresaId) {
        NominaDocumentoEntity d = buscarPreliminar(id, empresaId);
        validarFechas(dto);
        boolean cambiaTipo = !Objects.equals(d.getTipoComprobanteId(), dto.getTipoComprobanteId());
        boolean cambiaPeriodo = !d.getFechaDesde().equals(dto.getFechaDesde())
                || !d.getFechaHasta().equals(dto.getFechaHasta());
        if ((cambiaTipo || cambiaPeriodo) && obtener(id, empresaId).getEmpleados() > 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El documento ya tiene empleados: el tipo de comprobante y el período no se cambian. "
                    + "Quítelos primero o cree otra prenómina.");
        if (cambiaTipo) {
            exigirTipoActivo(dto.getTipoComprobanteId(), empresaId);
            // El número es del tipo: al cambiarlo se toma el siguiente del nuevo tipo.
            d.setNumero(queryRepository.siguienteNumero(empresaId, dto.getTipoComprobanteId(), d.getClaseDocumento()));
        }

        mapper.copiarEncabezado(dto, d);
        completar(d, dto);
        if (cambiaPeriodo) d.setPeriodoId(periodoPara(empresaId, dto.getFechaDesde(), dto.getFechaHasta()));
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        jpaRepository.saveAndFlush(d);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto anular(Long id, Integer empresaId) {
        NominaDocumentoEntity d = buscarPreliminar(id, empresaId);
        d.setEstado(ANULADO);
        d.setUpdatedAt(LocalDateTime.now());
        // saveAndFlush: obtener() relee con SQL y debe ver el cambio dentro de esta transacción.
        jpaRepository.saveAndFlush(d);
        return obtener(id, empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** El documento de la empresa en estado preliminar; 404 o 409 en otro caso. */
    private NominaDocumentoEntity buscarPreliminar(Long id, Integer empresaId) {
        NominaDocumentoEntity d = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));
        if (!"PRENOMINA".equals(d.getClaseDocumento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina no se modifica: los cambios se hacen en su prenómina.");
        if (!PRELIMINAR.equals(d.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El documento está " + d.getEstado().toLowerCase() + " y no se modifica.");
        return d;
    }

    /** El tipo de comprobante debe ser de la empresa y estar activo. */
    private void exigirTipoActivo(Long tipoId, Integer empresaId) {
        TipoComprobanteTableDto tipo = tiposComprobante.obtener(tipoId, empresaId);
        if (!Boolean.TRUE.equals(tipo.getActivo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El tipo de comprobante " + tipo.getNombre() + " está inactivo.");
    }

    /** Hasta ≥ desde, y el pago no antes de que empiece el período. */
    private static void validarFechas(CreateDocumentoNominaDto dto) {
        if (dto.getFechaHasta().isBefore(dto.getFechaDesde()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha final del período no puede ser anterior a la inicial.");
        if (dto.getFechaPago() != null && dto.getFechaPago().isBefore(dto.getFechaDesde()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La fecha de pago no puede ser anterior al inicio del período.");
    }

    /** Valores por defecto que el mapper no pone. */
    private static void completar(NominaDocumentoEntity d, CreateDocumentoNominaDto dto) {
        if (d.getFechaLiquidacion() == null) d.setFechaLiquidacion(dto.getFechaHasta());
        d.setSimulacion(Boolean.TRUE.equals(dto.getSimulacion()));
        d.setDescripcion(dto.getDescripcion() == null || dto.getDescripcion().isBlank()
                ? null : dto.getDescripcion().trim());
    }

    /**
     * Período con ese mismo rango de fechas: el motor, PILA y la nómina electrónica
     * siguen leyendo el período. Si no existe, se crea abierto.
     */
    private Long periodoPara(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return queryRepository.periodoConRango(empresaId, desde, hasta).orElseGet(() -> {
            EmpresaEntity empresa = new EmpresaEntity();
            empresa.setId(empresaId);
            PeriodoNominaEntity p = new PeriodoNominaEntity();
            p.setEmpresa(empresa);
            p.setFechaInicio(desde);
            p.setFechaFin(hasta);
            p.setEstado("ABIERTO");
            p.setCreatedAt(LocalDateTime.now());
            return periodoRepository.save(p).getId();
        });
    }
}

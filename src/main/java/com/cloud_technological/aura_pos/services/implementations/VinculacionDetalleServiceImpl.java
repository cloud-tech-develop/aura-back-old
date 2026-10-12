package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CentroCostoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateNovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.CreateVinculacionConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.NovedadPermitidaDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDetalleDtos.VinculacionConceptoDto;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.VinculacionConceptoEntity;
import com.cloud_technological.aura_pos.entity.VinculacionNovedadPermitidaEntity;
import com.cloud_technological.aura_pos.mappers.VinculacionDetalleMapper;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionConceptoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionDetalleQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_vinculaciones.VinculacionNovedadPermitidaJPARepository;
import com.cloud_technological.aura_pos.services.VinculacionDetalleService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link VinculacionDetalleService}. */
@Service
public class VinculacionDetalleServiceImpl implements VinculacionDetalleService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ContratoLaboralService contratos;
    private final VinculacionDetalleQueryRepository queryRepository;
    private final VinculacionConceptoJPARepository conceptoRepository;
    private final VinculacionNovedadPermitidaJPARepository novedadRepository;
    private final VinculacionDetalleMapper mapper;

    public VinculacionDetalleServiceImpl(ContratoLaboralService contratos,
            VinculacionDetalleQueryRepository queryRepository,
            VinculacionConceptoJPARepository conceptoRepository,
            VinculacionNovedadPermitidaJPARepository novedadRepository,
            VinculacionDetalleMapper mapper) {
        this.contratos = contratos;
        this.queryRepository = queryRepository;
        this.conceptoRepository = conceptoRepository;
        this.novedadRepository = novedadRepository;
        this.mapper = mapper;
    }

    // ── Centros de costo ─────────────────────────────────────────────────────

    @Override
    public List<CentroCostoVinculacionDto> centrosCosto(Long vinculacionId, Integer empresaId) {
        contratos.obtener(vinculacionId, empresaId);
        return queryRepository.centrosCosto(vinculacionId);
    }

    @Override
    @Transactional
    public List<CentroCostoVinculacionDto> guardarCentrosCosto(Long vinculacionId,
            List<CentroCostoVinculacionDto> centros, Integer empresaId) {
        editable(vinculacionId, empresaId);
        List<CentroCostoVinculacionDto> lista = centros != null ? centros : List.of();
        Set<Long> vistos = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;
        for (CentroCostoVinculacionDto c : lista) {
            if (!vistos.add(c.getCentroCostoId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Hay un centro de costo repetido.");
            if (!queryRepository.centroCostoValido(c.getCentroCostoId(), empresaId))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Un centro de costo no es válido (inactivo, de otra empresa o no admite movimientos).");
            suma = suma.add(c.getPorcentaje());
        }
        if (!lista.isEmpty() && suma.compareTo(CIEN) != 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los porcentajes deben sumar 100 %; hoy suman " + suma.stripTrailingZeros().toPlainString() + " %.");
        queryRepository.reemplazarCentrosCosto(vinculacionId, lista);
        return queryRepository.centrosCosto(vinculacionId);
    }

    // ── Conceptos ────────────────────────────────────────────────────────────

    @Override
    public List<VinculacionConceptoDto> conceptos(Long vinculacionId, Integer empresaId) {
        contratos.obtener(vinculacionId, empresaId);
        return queryRepository.conceptos(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<VinculacionConceptoDto> crearConcepto(Long vinculacionId, CreateVinculacionConceptoDto dto,
            Integer empresaId) {
        ContratoLaboralEntity c = editable(vinculacionId, empresaId);
        validarConcepto(c, dto, empresaId, null);
        VinculacionConceptoEntity e = new VinculacionConceptoEntity();
        mapper.actualizarConcepto(dto, e);
        e.setContratoId(vinculacionId);
        e.setCreatedAt(LocalDateTime.now());
        conceptoRepository.save(e);
        return queryRepository.conceptos(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<VinculacionConceptoDto> actualizarConcepto(Long vinculacionId, Long id,
            CreateVinculacionConceptoDto dto, Integer empresaId) {
        ContratoLaboralEntity c = editable(vinculacionId, empresaId);
        VinculacionConceptoEntity e = concepto(vinculacionId, id);
        validarConcepto(c, dto, empresaId, id);
        mapper.actualizarConcepto(dto, e);
        e.setUpdatedAt(LocalDateTime.now());
        conceptoRepository.save(e);
        return queryRepository.conceptos(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<VinculacionConceptoDto> eliminarConcepto(Long vinculacionId, Long id, Integer empresaId) {
        editable(vinculacionId, empresaId);
        VinculacionConceptoEntity e = concepto(vinculacionId, id);
        e.setDeletedAt(LocalDateTime.now());
        conceptoRepository.save(e);
        return queryRepository.conceptos(vinculacionId, empresaId);
    }

    /** Concepto existente, con algún valor, dentro de la vinculación y sin cruces. */
    private void validarConcepto(ContratoLaboralEntity c, CreateVinculacionConceptoDto dto, Integer empresaId,
            Long excluirId) {
        if (!queryRepository.conceptoExiste(dto.getConceptoCodigo(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El concepto " + dto.getConceptoCodigo() + " no existe.");
        if (dto.getValor() == null && dto.getPorcentaje() == null && dto.getCantidad() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el valor, el porcentaje o la cantidad.");
        if (dto.getFechaHasta() != null && dto.getFechaHasta().isBefore(dto.getFechaDesde()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha hasta no puede ser anterior a la fecha desde.");
        if (dto.getFechaDesde().isBefore(c.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El concepto no puede empezar antes del inicio de la vinculación (" + c.getFechaInicio() + ").");
        if (queryRepository.conceptoSeCruza(c.getId(), dto.getConceptoCodigo(), dto.getFechaDesde(),
                dto.getFechaHasta(), excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El concepto ya está vigente en esas fechas; cierre la vigencia anterior primero.");
    }

    private VinculacionConceptoEntity concepto(Long vinculacionId, Long id) {
        return conceptoRepository.findByIdAndContratoId(id, vinculacionId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Concepto no encontrado"));
    }

    // ── Novedades permitidas ─────────────────────────────────────────────────

    @Override
    public List<NovedadPermitidaDto> novedadesPermitidas(Long vinculacionId, Integer empresaId) {
        contratos.obtener(vinculacionId, empresaId);
        return queryRepository.novedadesPermitidas(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<NovedadPermitidaDto> crearNovedad(Long vinculacionId, CreateNovedadPermitidaDto dto,
            Integer empresaId) {
        editable(vinculacionId, empresaId);
        validarNovedad(vinculacionId, dto, empresaId, null);
        VinculacionNovedadPermitidaEntity e = new VinculacionNovedadPermitidaEntity();
        mapper.actualizarNovedad(dto, e);
        e.setContratoId(vinculacionId);
        e.setRequiereAutorizacion(Boolean.TRUE.equals(dto.getRequiereAutorizacion()));
        e.setCreatedAt(LocalDateTime.now());
        novedadRepository.save(e);
        return queryRepository.novedadesPermitidas(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<NovedadPermitidaDto> actualizarNovedad(Long vinculacionId, Long id, CreateNovedadPermitidaDto dto,
            Integer empresaId) {
        editable(vinculacionId, empresaId);
        VinculacionNovedadPermitidaEntity e = novedad(vinculacionId, id);
        validarNovedad(vinculacionId, dto, empresaId, id);
        mapper.actualizarNovedad(dto, e);
        e.setRequiereAutorizacion(Boolean.TRUE.equals(dto.getRequiereAutorizacion()));
        e.setUpdatedAt(LocalDateTime.now());
        novedadRepository.save(e);
        return queryRepository.novedadesPermitidas(vinculacionId, empresaId);
    }

    @Override
    @Transactional
    public List<NovedadPermitidaDto> eliminarNovedad(Long vinculacionId, Long id, Integer empresaId) {
        editable(vinculacionId, empresaId);
        novedadRepository.delete(novedad(vinculacionId, id));
        return queryRepository.novedadesPermitidas(vinculacionId, empresaId);
    }

    private void validarNovedad(Long vinculacionId, CreateNovedadPermitidaDto dto, Integer empresaId, Long excluirId) {
        if (!queryRepository.conceptoExiste(dto.getConceptoCodigo(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El concepto " + dto.getConceptoCodigo() + " no existe.");
        if (queryRepository.novedadDuplicada(vinculacionId, dto.getConceptoCodigo(), excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ese concepto ya está permitido en la vinculación.");
    }

    private VinculacionNovedadPermitidaEntity novedad(Long vinculacionId, Long id) {
        return novedadRepository.findByIdAndContratoId(id, vinculacionId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Novedad permitida no encontrada"));
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** La vinculación de la empresa; 409 si está retirada (solo consulta). */
    private ContratoLaboralEntity editable(Long vinculacionId, Integer empresaId) {
        ContratoLaboralEntity c = contratos.obtener(vinculacionId, empresaId);
        if ("TERMINADO".equals(c.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "La vinculación está retirada y no se modifica.");
        return c;
    }
}

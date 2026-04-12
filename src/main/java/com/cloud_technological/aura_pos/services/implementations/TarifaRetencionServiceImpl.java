package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.retenciones.CreateTarifaRetencionDto;
import com.cloud_technological.aura_pos.dto.retenciones.RetencionesSugeridasDto;
import com.cloud_technological.aura_pos.dto.retenciones.TarifaRetencionDto;
import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.retenciones.TarifaRetencionJPARepository;
import com.cloud_technological.aura_pos.repositories.retenciones.TarifaRetencionQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class TarifaRetencionServiceImpl {

    @Autowired private TarifaRetencionJPARepository tarifaJPARepository;
    @Autowired private TarifaRetencionQueryRepository tarifaQueryRepository;
    @Autowired private TerceroJPARepository terceroJPARepository;

    // ── CRUD ────────────────────────────────────────────────────────────────────

    public PageImpl<TarifaRetencionDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return tarifaQueryRepository.listar(pageable, empresaId);
    }

    public List<TarifaRetencionDto> listarTodas(Integer empresaId) {
        return tarifaJPARepository.findByEmpresaIdAndActivoTrueOrderByTipoAscConceptoAsc(empresaId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public TarifaRetencionDto crear(CreateTarifaRetencionDto dto, Integer empresaId) {
        if (tarifaJPARepository.existsByEmpresaIdAndTipoAndConcepto(empresaId, dto.getTipo(), dto.getConcepto()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe una tarifa de " + dto.getTipo() + " con el concepto '" + dto.getConcepto() + "'");

        TarifaRetencionEntity entity = fromDto(dto, empresaId);
        return toDto(tarifaJPARepository.save(entity));
    }

    @Transactional
    public TarifaRetencionDto actualizar(Long id, CreateTarifaRetencionDto dto, Integer empresaId) {
        TarifaRetencionEntity entity = tarifaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tarifa no encontrada"));

        if (tarifaJPARepository.existsByEmpresaIdAndTipoAndConceptoAndIdNot(empresaId, dto.getTipo(), dto.getConcepto(), id))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe otra tarifa de " + dto.getTipo() + " con ese concepto");

        entity.setTipo(dto.getTipo());
        entity.setConcepto(dto.getConcepto());
        entity.setCodigoConcepto(dto.getCodigoConcepto());
        entity.setTarifaNatural(dto.getTarifaNatural());
        entity.setTarifaJuridica(dto.getTarifaJuridica());
        entity.setBaseMinima(dto.getBaseMinima() != null ? dto.getBaseMinima() : BigDecimal.ZERO);
        entity.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        return toDto(tarifaJPARepository.save(entity));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        TarifaRetencionEntity entity = tarifaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tarifa no encontrada"));
        entity.setActivo(false);
        tarifaJPARepository.save(entity);
    }

    // ── Sugeridas ────────────────────────────────────────────────────────────────

    /**
     * Devuelve los porcentajes de retención sugeridos para un tercero,
     * basado en su tipoPersona (NATURAL / JURIDICA) y la primera tarifa activa
     * de cada tipo (RETEFUENTE, RETEIVA, RETEICA).
     */
    public RetencionesSugeridasDto obtenerSugeridas(Long terceroId, Integer empresaId) {
        TerceroEntity tercero = terceroJPARepository.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        boolean esNatural = !"JURIDICA".equalsIgnoreCase(tercero.getTipoPersona());

        BigDecimal refPct = primeraTarifa("RETEFUENTE", empresaId, esNatural);
        BigDecimal rivPct = primeraTarifa("RETEIVA",    empresaId, esNatural);
        BigDecimal ricPct = primeraTarifa("RETEICA",    empresaId, esNatural);

        String refConcepto = primerConcepto("RETEFUENTE", empresaId);
        String rivConcepto = primerConcepto("RETEIVA",    empresaId);
        String ricConcepto = primerConcepto("RETEICA",    empresaId);

        return RetencionesSugeridasDto.builder()
                .terceroId(terceroId)
                .tipoPersona(tercero.getTipoPersona() != null ? tercero.getTipoPersona() : "NATURAL")
                .retefuentePct(refPct)
                .retefuenteConcepto(refConcepto)
                .reteivaPct(rivPct)
                .reteivaConcepto(rivConcepto)
                .reteicaPct(ricPct)
                .reteicaConcepto(ricConcepto)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private BigDecimal primeraTarifa(String tipo, Integer empresaId, boolean esNatural) {
        return tarifaJPARepository.findByEmpresaIdAndTipoAndActivoTrue(empresaId, tipo)
                .stream().findFirst()
                .map(t -> esNatural ? t.getTarifaNatural() : t.getTarifaJuridica())
                .orElse(BigDecimal.ZERO);
    }

    private String primerConcepto(String tipo, Integer empresaId) {
        return tarifaJPARepository.findByEmpresaIdAndTipoAndActivoTrue(empresaId, tipo)
                .stream().findFirst()
                .map(TarifaRetencionEntity::getConcepto)
                .orElse(null);
    }

    private TarifaRetencionEntity fromDto(CreateTarifaRetencionDto dto, Integer empresaId) {
        TarifaRetencionEntity e = new TarifaRetencionEntity();
        e.setEmpresaId(empresaId);
        e.setTipo(dto.getTipo());
        e.setConcepto(dto.getConcepto());
        e.setCodigoConcepto(dto.getCodigoConcepto());
        e.setTarifaNatural(dto.getTarifaNatural());
        e.setTarifaJuridica(dto.getTarifaJuridica());
        e.setBaseMinima(dto.getBaseMinima() != null ? dto.getBaseMinima() : BigDecimal.ZERO);
        e.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        return e;
    }

    private TarifaRetencionDto toDto(TarifaRetencionEntity e) {
        TarifaRetencionDto dto = new TarifaRetencionDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresaId());
        dto.setTipo(e.getTipo());
        dto.setConcepto(e.getConcepto());
        dto.setCodigoConcepto(e.getCodigoConcepto());
        dto.setTarifaNatural(e.getTarifaNatural());
        dto.setTarifaJuridica(e.getTarifaJuridica());
        dto.setBaseMinima(e.getBaseMinima());
        dto.setActivo(e.getActivo());
        return dto;
    }
}

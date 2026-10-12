package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CreateNominaCargoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.NominaCargoTableDto;
import com.cloud_technological.aura_pos.entity.NominaCargoEntity;
import com.cloud_technological.aura_pos.mappers.NominaCargoMapper;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_cargos.NominaCargoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoQueryRepository;
import com.cloud_technological.aura_pos.services.NominaCargoService;
import com.cloud_technological.aura_pos.services.nomina.catalogos.TipoCatalogoNomina;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaCargoService}. */
@Service
public class NominaCargoServiceImpl implements NominaCargoService {

    private final NominaCargoJPARepository jpaRepository;
    private final NominaCargoQueryRepository queryRepository;
    private final NominaCatalogoQueryRepository catalogos;
    private final NominaCargoMapper mapper;

    public NominaCargoServiceImpl(NominaCargoJPARepository jpaRepository,
            NominaCargoQueryRepository queryRepository,
            NominaCatalogoQueryRepository catalogos,
            NominaCargoMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.catalogos = catalogos;
        this.mapper = mapper;
    }

    @Override
    public PageImpl<NominaCargoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public List<NominaCargoTableDto> activos(Integer empresaId) {
        return queryRepository.activos(empresaId);
    }

    @Override
    public NominaCargoTableDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cargo no encontrado"));
    }

    @Override
    @Transactional
    public NominaCargoTableDto crear(CreateNominaCargoDto dto, Integer empresaId) {
        String codigo = normalizarCodigo(dto.getCodigo());
        validar(dto, codigo, empresaId, null);

        NominaCargoEntity entity = mapper.toEntity(dto);
        entity.setEmpresaId(empresaId);
        completar(entity, dto, codigo);
        entity.setCreatedAt(LocalDateTime.now());
        return obtener(jpaRepository.save(entity).getId(), empresaId);
    }

    @Override
    @Transactional
    public NominaCargoTableDto actualizar(Long id, CreateNominaCargoDto dto, Integer empresaId) {
        NominaCargoEntity entity = buscar(id, empresaId);
        String codigo = normalizarCodigo(dto.getCodigo());
        validar(dto, codigo, empresaId, id);

        mapper.updateEntityFromDto(dto, entity);
        completar(entity, dto, codigo);
        entity.setUpdatedAt(LocalDateTime.now());
        jpaRepository.save(entity);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        NominaCargoEntity entity = buscar(id, empresaId);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        jpaRepository.save(entity);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** El cargo de la empresa, sin borrar; 404 en otro caso. */
    private NominaCargoEntity buscar(Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cargo no encontrado"));
    }

    /**
     * Reglas del formulario: código único, nivel y tipo del catálogo correcto y rango
     * salarial coherente.
     */
    private void validar(CreateNominaCargoDto dto, String codigo, Integer empresaId, Long excluirId) {
        if (queryRepository.existeCodigo(empresaId, codigo, excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un cargo con el código " + codigo + ".");
        if (dto.getNivelId() != null
                && !catalogos.esDelCatalogo(dto.getNivelId(), empresaId, TipoCatalogoNomina.NIVEL_CARGO.name()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nivel de cargo no es válido.");
        if (dto.getTipoCargoId() != null
                && !catalogos.esDelCatalogo(dto.getTipoCargoId(), empresaId, TipoCatalogoNomina.TIPO_CARGO.name()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El tipo de cargo no es válido.");
        validarRangoSalarial(dto.getSalarioMinimo(), dto.getSalarioReferencia(), dto.getSalarioMaximo());
    }

    /** Ningún valor negativo y mínimo ≤ referencia ≤ máximo (con los que vengan). */
    private static void validarRangoSalarial(BigDecimal minimo, BigDecimal referencia, BigDecimal maximo) {
        for (BigDecimal v : new BigDecimal[] { minimo, referencia, maximo })
            if (v != null && v.signum() < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Los salarios no pueden ser negativos.");
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El salario mínimo no puede ser mayor que el máximo.");
        if (referencia != null && minimo != null && referencia.compareTo(minimo) < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El salario de referencia es menor que el mínimo del cargo.");
        if (referencia != null && maximo != null && referencia.compareTo(maximo) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El salario de referencia es mayor que el máximo del cargo.");
    }

    /** Campos que el mapper no normaliza. */
    private static void completar(NominaCargoEntity entity, CreateNominaCargoDto dto, String codigo) {
        entity.setCodigo(codigo);
        entity.setNombre(dto.getNombre().trim());
        entity.setActivo(dto.getActivo() == null || dto.getActivo());
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase();
    }
}

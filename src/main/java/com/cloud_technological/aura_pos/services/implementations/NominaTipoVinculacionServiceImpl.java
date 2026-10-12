package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.CreateTipoVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.TipoVinculacionTableDto;
import com.cloud_technological.aura_pos.entity.NominaTipoVinculacionEntity;
import com.cloud_technological.aura_pos.mappers.NominaTipoVinculacionMapper;
import com.cloud_technological.aura_pos.repositories.nomina_tipos_vinculacion.NominaTipoVinculacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_tipos_vinculacion.NominaTipoVinculacionQueryRepository;
import com.cloud_technological.aura_pos.services.NominaTipoVinculacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaTipoVinculacionService}. */
@Service
public class NominaTipoVinculacionServiceImpl implements NominaTipoVinculacionService {

    /** Contratos que por ley tienen fecha de terminación. */
    private static final Set<String> CONTRATOS_CON_FIN = Set.of("FIJO", "OBRA_LABOR", "APRENDIZAJE");

    /** Tipos iniciales: código, nombre, contrato, remuneración, frecuencia, exige fin. */
    private record Semilla(String codigo, String nombre, String contrato, String remuneracion,
            String frecuencia, boolean exigeFin) {
    }

    private static final List<Semilla> SEMILLA = List.of(
            new Semilla("PLANTA", "Planta (término indefinido)", "INDEFINIDO", "MENSUAL", "MENSUAL", false),
            new Semilla("FIJO", "Término fijo", "FIJO", "MENSUAL", "MENSUAL", true),
            new Semilla("OBRA", "Obra o labor", "OBRA_LABOR", "MENSUAL", "MENSUAL", true),
            new Semilla("INTEGRAL", "Salario integral", "INDEFINIDO", "INTEGRAL", "MENSUAL", false),
            new Semilla("APRENDIZ", "Aprendiz SENA", "APRENDIZAJE", "MENSUAL", "MENSUAL", true),
            new Semilla("JORNAL", "Por días (jornal)", "OBRA_LABOR", "JORNAL", "QUINCENAL", true));

    private final NominaTipoVinculacionJPARepository jpaRepository;
    private final NominaTipoVinculacionQueryRepository queryRepository;
    private final NominaTipoVinculacionMapper mapper;

    public NominaTipoVinculacionServiceImpl(NominaTipoVinculacionJPARepository jpaRepository,
            NominaTipoVinculacionQueryRepository queryRepository,
            NominaTipoVinculacionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PageImpl<TipoVinculacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        asegurarSemilla(empresaId);
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    @Transactional
    public List<TipoVinculacionTableDto> activos(Integer empresaId) {
        asegurarSemilla(empresaId);
        return queryRepository.activos(empresaId);
    }

    @Override
    public TipoVinculacionTableDto obtener(Long id, Integer empresaId) {
        return mapper.toTableDto(buscar(id, empresaId));
    }

    @Override
    @Transactional
    public TipoVinculacionTableDto crear(CreateTipoVinculacionDto dto, Integer empresaId) {
        String codigo = normalizarCodigo(dto.getCodigo());
        validar(dto, codigo, empresaId, null);

        NominaTipoVinculacionEntity entity = mapper.toEntity(dto);
        entity.setEmpresaId(empresaId);
        completar(entity, dto, codigo);
        entity.setCreatedAt(LocalDateTime.now());
        return mapper.toTableDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public TipoVinculacionTableDto actualizar(Long id, CreateTipoVinculacionDto dto, Integer empresaId) {
        NominaTipoVinculacionEntity entity = buscar(id, empresaId);
        String codigo = normalizarCodigo(dto.getCodigo());
        validar(dto, codigo, empresaId, id);

        mapper.updateEntityFromDto(dto, entity);
        completar(entity, dto, codigo);
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toTableDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        NominaTipoVinculacionEntity entity = buscar(id, empresaId);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        jpaRepository.save(entity);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private NominaTipoVinculacionEntity buscar(Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de vinculación no encontrado"));
    }

    /** Código único y coherencia contrato ↔ fecha de fin. */
    private void validar(CreateTipoVinculacionDto dto, String codigo, Integer empresaId, Long excluirId) {
        if (queryRepository.existeCodigo(empresaId, codigo, excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe un tipo de vinculación con el código " + codigo + ".");
        if (dto.getTipoContrato() != null && CONTRATOS_CON_FIN.contains(dto.getTipoContrato())
                && Boolean.FALSE.equals(dto.getExigeFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un contrato a término fijo, por obra o de aprendizaje siempre tiene fecha de terminación.");
    }

    /** Campos que el mapper no normaliza. */
    private static void completar(NominaTipoVinculacionEntity entity, CreateTipoVinculacionDto dto, String codigo) {
        entity.setCodigo(codigo);
        entity.setNombre(dto.getNombre().trim());
        entity.setExigeFechaFin(Boolean.TRUE.equals(dto.getExigeFechaFin()));
        entity.setUnicaPorEmpleado(dto.getUnicaPorEmpleado() == null || dto.getUnicaPorEmpleado());
        entity.setActivo(dto.getActivo() == null || dto.getActivo());
    }

    /** Siembra los tipos iniciales la primera vez que la empresa abre el módulo. */
    private void asegurarSemilla(Integer empresaId) {
        if (queryRepository.tieneElementos(empresaId)) return;
        for (Semilla s : SEMILLA)
            queryRepository.sembrar(empresaId, s.codigo(), s.nombre(), s.contrato(), s.remuneracion(),
                    s.frecuencia(), s.exigeFin());
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase();
    }
}

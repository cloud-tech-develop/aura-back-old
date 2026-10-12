package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.CreateTipoComprobanteDto;
import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.entity.NominaTipoComprobanteEntity;
import com.cloud_technological.aura_pos.mappers.NominaTipoComprobanteMapper;
import com.cloud_technological.aura_pos.repositories.nomina_tipos_comprobante.NominaTipoComprobanteJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_tipos_comprobante.NominaTipoComprobanteQueryRepository;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.services.nomina.comprobante.ClaseComprobanteNomina;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaTipoComprobanteService}. */
@Service
public class NominaTipoComprobanteServiceImpl implements NominaTipoComprobanteService {

    /** Tipos iniciales: código, nombre, clase, prefijo, frecuencia. */
    private record Semilla(String codigo, String nombre, ClaseComprobanteNomina clase, String prefijo,
            String frecuencia) {
    }

    private static final List<Semilla> SEMILLA = List.of(
            new Semilla("NOM", "Nómina", ClaseComprobanteNomina.PERIODICO, "NOM", "MENSUAL"),
            new Semilla("PRIMA", "Prima de servicios", ClaseComprobanteNomina.PRIMA, "PRI", null),
            new Semilla("CES", "Cesantías", ClaseComprobanteNomina.CESANTIAS, "CES", null),
            new Semilla("INTCES", "Intereses de cesantías", ClaseComprobanteNomina.INTERESES_CESANTIAS, "ICE", null),
            new Semilla("LIQ", "Liquidación definitiva", ClaseComprobanteNomina.LIQUIDACION_DEFINITIVA, "LIQ", null),
            new Semilla("RETRO", "Retroactivo", ClaseComprobanteNomina.RETROACTIVO, "RET", null));

    private final NominaTipoComprobanteJPARepository jpaRepository;
    private final NominaTipoComprobanteQueryRepository queryRepository;
    private final NominaTipoComprobanteMapper mapper;

    public NominaTipoComprobanteServiceImpl(NominaTipoComprobanteJPARepository jpaRepository,
            NominaTipoComprobanteQueryRepository queryRepository,
            NominaTipoComprobanteMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PageImpl<TipoComprobanteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        asegurarSemilla(empresaId);
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    @Transactional
    public List<TipoComprobanteTableDto> activos(Integer empresaId) {
        asegurarSemilla(empresaId);
        return queryRepository.activos(empresaId);
    }

    @Override
    public TipoComprobanteTableDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de comprobante no encontrado"));
    }

    @Override
    @Transactional
    public TipoComprobanteTableDto crear(CreateTipoComprobanteDto dto, Integer empresaId) {
        validar(dto, empresaId, null);

        NominaTipoComprobanteEntity entity = mapper.toEntity(dto);
        entity.setEmpresaId(empresaId);
        completar(entity, dto);
        entity.setCreatedAt(LocalDateTime.now());
        Long id = jpaRepository.save(entity).getId();
        queryRepository.reemplazarTiposVinculacion(id, tiposVinculacion(dto));
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public TipoComprobanteTableDto actualizar(Long id, CreateTipoComprobanteDto dto, Integer empresaId) {
        NominaTipoComprobanteEntity entity = buscar(id, empresaId);
        validar(dto, empresaId, id);

        mapper.updateEntityFromDto(dto, entity);
        completar(entity, dto);
        entity.setUpdatedAt(LocalDateTime.now());
        jpaRepository.save(entity);
        queryRepository.reemplazarTiposVinculacion(id, tiposVinculacion(dto));
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        NominaTipoComprobanteEntity entity = buscar(id, empresaId);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        jpaRepository.save(entity);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** El tipo de la empresa, sin borrar; 404 en otro caso. */
    private NominaTipoComprobanteEntity buscar(Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tipo de comprobante no encontrado"));
    }

    /**
     * Código y prefijo únicos, frecuencia obligatoria en los periódicos y tipos de
     * vinculación de la empresa.
     */
    private void validar(CreateTipoComprobanteDto dto, Integer empresaId, Long excluirId) {
        String codigo = normalizar(dto.getCodigo());
        String prefijo = normalizar(dto.getPrefijo());
        if (queryRepository.existe(empresaId, "codigo", codigo, excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe un tipo de comprobante con el código " + codigo + ".");
        if (queryRepository.existe(empresaId, "prefijo", prefijo, excluirId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El prefijo " + prefijo + " ya lo usa otro tipo de comprobante.");
        if (ClaseComprobanteNomina.valueOf(dto.getClase()).esPeriodica() && dto.getFrecuencia() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Una nómina periódica necesita su frecuencia (mensual, quincenal o semanal).");
        List<Long> tipos = tiposVinculacion(dto);
        if (queryRepository.contarTiposVinculacion(empresaId, tipos) != tipos.size())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Algún tipo de vinculación no es válido.");
    }

    /** Campos que el mapper no normaliza. */
    private static void completar(NominaTipoComprobanteEntity entity, CreateTipoComprobanteDto dto) {
        entity.setCodigo(normalizar(dto.getCodigo()));
        entity.setPrefijo(normalizar(dto.getPrefijo()));
        entity.setNombre(dto.getNombre().trim());
        // Solo los periódicos llevan frecuencia.
        if (!ClaseComprobanteNomina.valueOf(dto.getClase()).esPeriodica()) entity.setFrecuencia(null);
        entity.setActivo(dto.getActivo() == null || dto.getActivo());
    }

    /** Tipos de vinculación sin repetidos ni nulos. */
    private static List<Long> tiposVinculacion(CreateTipoComprobanteDto dto) {
        if (dto.getTiposVinculacionIds() == null) return List.of();
        return dto.getTiposVinculacionIds().stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Siembra los tipos iniciales la primera vez que la empresa abre el módulo. */
    private void asegurarSemilla(Integer empresaId) {
        if (queryRepository.tieneElementos(empresaId)) return;
        for (Semilla s : SEMILLA)
            queryRepository.sembrar(empresaId, s.codigo(), s.nombre(), s.clase().name(), s.prefijo(),
                    s.frecuencia());
    }

    private static String normalizar(String valor) {
        return valor.trim().toUpperCase();
    }
}

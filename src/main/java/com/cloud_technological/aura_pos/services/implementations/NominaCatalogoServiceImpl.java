package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.catalogos.CreateNominaCatalogoDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.NominaCatalogoTableDto;
import com.cloud_technological.aura_pos.dto.nomina.catalogos.TipoCatalogoNominaDto;
import com.cloud_technological.aura_pos.entity.NominaCatalogoEntity;
import com.cloud_technological.aura_pos.mappers.NominaCatalogoMapper;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_catalogos.NominaCatalogoQueryRepository;
import com.cloud_technological.aura_pos.services.NominaCatalogoService;
import com.cloud_technological.aura_pos.services.nomina.catalogos.AtributosCatalogoValidator;
import com.cloud_technological.aura_pos.services.nomina.catalogos.CatalogoNominaSemilla;
import com.cloud_technological.aura_pos.services.nomina.catalogos.TipoCatalogoNomina;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.JsonMapas;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Implementación de {@link NominaCatalogoService}. Todos los catálogos simples
 * comparten tabla, reglas y semilla; lo que cambia entre ellos lo declara
 * {@link TipoCatalogoNomina}.
 */
@Service
public class NominaCatalogoServiceImpl implements NominaCatalogoService {

    private final NominaCatalogoJPARepository jpaRepository;
    private final NominaCatalogoQueryRepository queryRepository;
    private final NominaCatalogoMapper mapper;
    private final AtributosCatalogoValidator atributos;

    public NominaCatalogoServiceImpl(NominaCatalogoJPARepository jpaRepository,
            NominaCatalogoQueryRepository queryRepository,
            NominaCatalogoMapper mapper,
            AtributosCatalogoValidator atributos) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.mapper = mapper;
        this.atributos = atributos;
    }

    @Override
    public List<TipoCatalogoNominaDto> tipos() {
        return Arrays.stream(TipoCatalogoNomina.values()).map(NominaCatalogoServiceImpl::aDto).toList();
    }

    @Override
    public TipoCatalogoNominaDto tipo(String ruta) {
        return aDto(resolver(ruta));
    }

    @Override
    @Transactional
    public PageImpl<NominaCatalogoTableDto> listar(String ruta, PageableDto<Object> pageable, Integer empresaId) {
        TipoCatalogoNomina tipo = resolver(ruta);
        asegurarSemilla(tipo, empresaId);
        return queryRepository.listar(pageable, empresaId, tipo.name());
    }

    @Override
    @Transactional
    public List<NominaCatalogoTableDto> activos(String ruta, Integer empresaId) {
        TipoCatalogoNomina tipo = resolver(ruta);
        asegurarSemilla(tipo, empresaId);
        return queryRepository.activos(empresaId, tipo.name());
    }

    @Override
    public NominaCatalogoTableDto obtener(String ruta, Long id, Integer empresaId) {
        return aTableDto(buscar(resolver(ruta), id, empresaId));
    }

    @Override
    @Transactional
    public NominaCatalogoTableDto crear(String ruta, CreateNominaCatalogoDto dto, Integer empresaId) {
        TipoCatalogoNomina tipo = resolver(ruta);
        String codigo = normalizarCodigo(dto.getCodigo());
        if (queryRepository.existeCodigo(empresaId, tipo.name(), codigo, null))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe el código " + codigo + " en " + tipo.getNombre().toLowerCase() + ".");

        NominaCatalogoEntity entity = mapper.toEntity(dto);
        entity.setEmpresaId(empresaId);
        entity.setTipo(tipo.name());
        entity.setCodigo(codigo);
        entity.setNombre(dto.getNombre().trim());
        entity.setAtributos(atributos.aJson(atributos.validar(tipo, dto.getAtributos())));
        entity.setActivo(dto.getActivo() == null || dto.getActivo());
        entity.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        entity.setCreatedAt(LocalDateTime.now());
        return aTableDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public NominaCatalogoTableDto actualizar(String ruta, Long id, CreateNominaCatalogoDto dto, Integer empresaId) {
        TipoCatalogoNomina tipo = resolver(ruta);
        NominaCatalogoEntity entity = buscar(tipo, id, empresaId);
        String codigo = normalizarCodigo(dto.getCodigo());
        if (queryRepository.existeCodigo(empresaId, tipo.name(), codigo, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe el código " + codigo + " en " + tipo.getNombre().toLowerCase() + ".");

        mapper.updateEntityFromDto(dto, entity);
        entity.setCodigo(codigo);
        entity.setNombre(dto.getNombre().trim());
        entity.setAtributos(atributos.aJson(atributos.validar(tipo, dto.getAtributos())));
        entity.setActivo(dto.getActivo() == null || dto.getActivo());
        entity.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        entity.setUpdatedAt(LocalDateTime.now());
        return aTableDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(String ruta, Long id, Integer empresaId) {
        NominaCatalogoEntity entity = buscar(resolver(ruta), id, empresaId);
        if (queryRepository.enUsoPorCargos(id))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Hay cargos que usan " + entity.getNombre() + ": desactívelo en lugar de eliminarlo.");
        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        jpaRepository.save(entity);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** El tipo de catálogo de la URL; 404 si no existe. */
    private static TipoCatalogoNomina resolver(String ruta) {
        return TipoCatalogoNomina.deRuta(ruta)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Catálogo de nómina no encontrado: " + ruta));
    }

    /** El elemento de la empresa y de ese catálogo; 404 en cualquier otro caso. */
    private NominaCatalogoEntity buscar(TipoCatalogoNomina tipo, Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .filter(e -> e.getDeletedAt() == null && tipo.name().equals(e.getTipo()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Elemento no encontrado"));
    }

    /**
     * Siembra los valores iniciales la primera vez que la empresa abre el catálogo.
     * Si la empresa los borró todos, no los vuelve a crear mientras quede alguno.
     */
    private void asegurarSemilla(TipoCatalogoNomina tipo, Integer empresaId) {
        if (queryRepository.tieneElementos(empresaId, tipo.name())) return;
        List<CatalogoNominaSemilla.Elemento> semilla = CatalogoNominaSemilla.de(tipo);
        for (int i = 0; i < semilla.size(); i++) {
            CatalogoNominaSemilla.Elemento e = semilla.get(i);
            Map<String, Object> validos = atributos.validar(tipo, e.atributos());
            queryRepository.sembrar(empresaId, tipo.name(), e.codigo(), e.nombre(), atributos.aJson(validos), i + 1);
        }
    }

    private NominaCatalogoTableDto aTableDto(NominaCatalogoEntity entity) {
        NominaCatalogoTableDto dto = mapper.toTableDto(entity);
        dto.setAtributos(JsonMapas.deJson(entity.getAtributos()));
        return dto;
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase();
    }

    private static TipoCatalogoNominaDto aDto(TipoCatalogoNomina t) {
        return new TipoCatalogoNominaDto(t.getRuta(), t.getNombre(), t.getDescripcion(), t.getAtributos());
    }
}

package com.cloud_technological.aura_pos.repositories.nomina_catalogos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.catalogos.NominaCatalogoTableDto;
import com.cloud_technological.aura_pos.utils.JsonMapas;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de los catálogos de nómina (listados, selects, validaciones y semilla). */
@Repository
@RequiredArgsConstructor
public class NominaCatalogoQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS = """
            SELECT c.id, c.tipo, c.codigo, c.nombre, c.descripcion, c.orden, c.activo, c.atributos,
                   COUNT(*) OVER() AS total_rows
              FROM nomina_catalogo c
            """;

    /**
     * Listado paginado de un catálogo, con búsqueda por código o nombre.
     *
     * @param pageable  página, filas y texto de búsqueda
     * @param empresaId empresa
     * @param tipo      tipo de catálogo (nombre del enum)
     * @return página de elementos ordenados por orden y código
     */
    public PageImpl<NominaCatalogoTableDto> listar(PageableDto<Object> pageable, Integer empresaId, String tipo) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(COLUMNAS).append("""
             WHERE c.empresa_id = :empresaId AND c.tipo = :tipo AND c.deleted_at IS NULL
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipo);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(c.nombre) LIKE :search OR LOWER(c.codigo) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY c.orden, c.codigo OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<NominaCatalogoTableDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Elementos activos de un catálogo, para los selects de otras pantallas.
     *
     * @param empresaId empresa
     * @param tipo      tipo de catálogo
     * @return elementos activos ordenados
     */
    public List<NominaCatalogoTableDto> activos(Integer empresaId, String tipo) {
        return jdbc.query(COLUMNAS + """
                 WHERE c.empresa_id = :empresaId AND c.tipo = :tipo AND c.activo = TRUE AND c.deleted_at IS NULL
                 ORDER BY c.orden, c.nombre
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipo), this::mapear);
    }

    /**
     * ¿Ya existe el código en el catálogo? (sin contar el elemento que se edita).
     *
     * @param excluirId id a ignorar (null al crear)
     */
    public boolean existeCodigo(Integer empresaId, String tipo, String codigo, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_catalogo
                 WHERE empresa_id = :empresaId AND tipo = :tipo AND LOWER(codigo) = LOWER(:codigo)
                   AND deleted_at IS NULL AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipo)
                .addValue("codigo", codigo).addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    /**
     * ¿El catálogo ya tiene elementos (activos o no)? Si no, se le siembran los iniciales.
     */
    public boolean tieneElementos(Integer empresaId, String tipo) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_catalogo
                 WHERE empresa_id = :empresaId AND tipo = :tipo AND deleted_at IS NULL
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipo), Long.class);
        return n != null && n > 0;
    }

    /**
     * ¿El elemento es del catálogo indicado, de la empresa y está vigente? Valida los
     * ids de catálogo que llegan en los formularios (cargos, vinculaciones).
     *
     * @param tipo nombre del tipo de catálogo (p. ej. GRUPO_NOMINA)
     */
    public boolean esDelCatalogo(Long catalogoId, Integer empresaId, String tipo) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_catalogo
                 WHERE id = :id AND empresa_id = :empresaId AND tipo = :tipo AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", catalogoId).addValue("empresaId", empresaId)
                .addValue("tipo", tipo), Long.class);
        return n != null && n > 0;
    }

    /**
     * ¿Algún cargo usa el elemento (como nivel o tipo de cargo)? Se consulta antes de borrar.
     */
    public boolean enUsoPorCargos(Long id) {
        Long n = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM nomina_cargo
                         WHERE (nivel_id = :id OR tipo_cargo_id = :id) AND deleted_at IS NULL)
                     + (SELECT COUNT(*) FROM contrato_laboral
                         WHERE (grupo_nomina_id = :id OR calendario_id = :id OR riesgo_arl_id = :id)
                           AND deleted_at IS NULL)
                """, new MapSqlParameterSource("id", id), Long.class);
        return n != null && n > 0;
    }

    /**
     * Inserta un elemento inicial si el código no existe (dos peticiones simultáneas
     * pueden intentar sembrar a la vez).
     */
    public void sembrar(Integer empresaId, String tipo, String codigo, String nombre, String atributosJson, int orden) {
        jdbc.update("""
                INSERT INTO nomina_catalogo (empresa_id, tipo, codigo, nombre, atributos, orden, activo, created_at)
                SELECT :empresaId, :tipo, :codigo, :nombre, :atributos, :orden, TRUE, now()
                 WHERE NOT EXISTS (SELECT 1 FROM nomina_catalogo
                                    WHERE empresa_id = :empresaId AND tipo = :tipo AND codigo = :codigo
                                      AND deleted_at IS NULL)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipo", tipo)
                .addValue("codigo", codigo).addValue("nombre", nombre).addValue("atributos", atributosJson)
                .addValue("orden", orden));
    }

    private NominaCatalogoTableDto mapear(ResultSet rs, int fila) throws SQLException {
        NominaCatalogoTableDto dto = new NominaCatalogoTableDto();
        dto.setId(rs.getLong("id"));
        dto.setTipo(rs.getString("tipo"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setOrden(rs.getInt("orden"));
        dto.setActivo(rs.getBoolean("activo"));
        dto.setAtributos(JsonMapas.deJson(rs.getString("atributos")));
        dto.setTotalRows(rs.getLong("total_rows"));
        return dto;
    }
}

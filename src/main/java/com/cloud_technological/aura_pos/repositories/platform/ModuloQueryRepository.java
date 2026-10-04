package com.cloud_technological.aura_pos.repositories.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.permisos.ModuloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloPermisoDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ModuloQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PageImpl<ModuloTableDto> listar(PageableDto<Object> pageable) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT 
                id, nombre, codigo, descripcion, activo, orden,
                COUNT(*) OVER() AS total_rows
            FROM modulos
            WHERE 1=1
            """);
        
        MapSqlParameterSource params = new MapSqlParameterSource();
        
        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(nombre) LIKE :search 
                OR LOWER(codigo) LIKE :search
                OR LOWER(descripcion) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }
        
        sql.append(" ORDER BY orden ASC, id ASC LIMIT :limit OFFSET :offset ");
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        
        List<ModuloTableDto> list = jdbc.query(sql.toString(), params, 
                new BeanPropertyRowMapper<>(ModuloTableDto.class));
        
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ModuloTableDto> listarAll() {
        String sql = """
            SELECT id, nombre, codigo, descripcion, activo, orden
            FROM modulos
            ORDER BY orden ASC, id ASC
            """;
        
        return jdbc.query(sql, new BeanPropertyRowMapper<>(ModuloTableDto.class));
    }

    public ModuloTableDto obtenerPorId(Integer id) {
        String sql = """
            SELECT id, nombre, codigo, descripcion, activo, orden
            FROM modulos
            WHERE id = :id
            """;
        
        return jdbc.query(sql, Map.of("id", id), (rs, rowNum) -> ModuloTableDto.builder()
                .id(rs.getInt("id"))
                .nombre(rs.getString("nombre"))
                .codigo(rs.getString("codigo"))
                .descripcion(rs.getString("descripcion"))
                .activo(rs.getBoolean("activo"))
                .orden(rs.getInt("orden"))
                .build())
                .stream().findFirst().orElse(null);
    }

    public List<SubmoduloTableDto> listarSubmodulosPorModulo(Integer moduloId) {
        String sql = """
            SELECT 
                s.id, s.modulo_id, m.nombre as modulo_nombre, 
                s.nombre, s.codigo, s.descripcion, s.activo, s.orden
            FROM submodulos s
            JOIN modulos m ON s.modulo_id = m.id
            WHERE s.modulo_id = :moduloId
            ORDER BY s.orden ASC, s.id ASC
            """;
        
        return jdbc.query(sql, Map.of("moduloId", moduloId), (rs, rowNum) -> SubmoduloTableDto.builder()
                .id(rs.getInt("id"))
                .moduloId(rs.getInt("modulo_id"))
                .moduloNombre(rs.getString("modulo_nombre"))
                .nombre(rs.getString("nombre"))
                .codigo(rs.getString("codigo"))
                .descripcion(rs.getString("descripcion"))
                .activo(rs.getBoolean("activo"))
                .orden(rs.getInt("orden"))
                .build());
    }

    public List<SubmoduloTableDto> listarSubmodulos(PageableDto<Object> pageable) {
        long page = pageable.getPage();
        long size = pageable.getRows();
        long offset = page * size;
        
        String sql = """
            SELECT 
                s.id, s.modulo_id, m.nombre as modulo_nombre, 
                s.nombre, s.codigo, s.descripcion, s.activo, s.orden,
                COUNT(*) OVER() AS total_rows
            FROM submodulos s
            JOIN modulos m ON s.modulo_id = m.id
            ORDER BY s.orden ASC, s.id ASC
            LIMIT :limit OFFSET :offset
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("limit", size);
        params.put("offset", offset);
        
        return jdbc.query(sql, params, (rs, rowNum) -> SubmoduloTableDto.builder()
                .id(rs.getInt("id"))
                .moduloId(rs.getInt("modulo_id"))
                .moduloNombre(rs.getString("modulo_nombre"))
                .nombre(rs.getString("nombre"))
                .codigo(rs.getString("codigo"))
                .descripcion(rs.getString("descripcion"))
                .activo(rs.getBoolean("activo"))
                .orden(rs.getInt("orden"))
                .totalRows(rs.getInt("total_rows"))
                .build());
    }

    public SubmoduloTableDto obtenerSubmoduloPorId(Integer id) {
        String sql = """
            SELECT s.id, s.modulo_id, m.nombre as modulo_nombre, 
                   s.nombre, s.codigo, s.descripcion, s.activo, s.orden
            FROM submodulos s
            JOIN modulos m ON s.modulo_id = m.id
            WHERE s.id = :id
            """;
        
        return jdbc.query(sql, Map.of("id", id), (rs, rowNum) -> SubmoduloTableDto.builder()
                .id(rs.getInt("id"))
                .moduloId(rs.getInt("modulo_id"))
                .moduloNombre(rs.getString("modulo_nombre"))
                .nombre(rs.getString("nombre"))
                .codigo(rs.getString("codigo"))
                .descripcion(rs.getString("descripcion"))
                .activo(rs.getBoolean("activo"))
                .orden(rs.getInt("orden"))
                .build())
                .stream().findFirst().orElse(null);
    }

    /**
     * Módulos y submódulos con lo que la empresa tiene activo, en orden de menú y
     * con el árbol del tercer nivel (padreId / esGrupo): primero cada grupo y
     * enseguida sus pantallas. Con empresaId null trae el catálogo completo (todo
     * en false), para elegir los módulos al crear una empresa.
     */
    public List<ModuloPermisoDto> listarPermisosPorEmpresa(Integer empresaId) {
        String sql = """
            SELECT
                m.id AS modulo_id, m.codigo AS modulo_codigo, m.nombre AS modulo_nombre,
                COALESCE(em.activo, false) AS modulo_activo,
                s.id AS submodulo_id, s.codigo AS submodulo_codigo, s.nombre AS submodulo_nombre,
                COALESCE(es.activo, false) AS submodulo_activo,
                s.padre_id,
                EXISTS (SELECT 1 FROM submodulos h WHERE h.padre_id = s.id AND h.deleted_at IS NULL) AS es_grupo
            FROM modulos m
            LEFT JOIN submodulos s ON m.id = s.modulo_id AND s.deleted_at IS NULL AND s.activo = true
            LEFT JOIN submodulos pa ON pa.id = s.padre_id
            LEFT JOIN empresa_modulo em ON m.id = em.modulo_id AND em.empresa_id = :empresaId
            LEFT JOIN empresa_submodulo es ON s.id = es.submodulo_id AND es.empresa_id = :empresaId
            WHERE m.activo = true AND m.deleted_at IS NULL
            ORDER BY m.orden ASC, m.id ASC,
                     COALESCE(pa.orden, s.orden) ASC, COALESCE(pa.id, s.id) ASC,
                     CASE WHEN s.padre_id IS NULL THEN 0 ELSE 1 END, s.orden ASC, s.id ASC
            """;
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);

        // LinkedHashMap: conserva el orden de menú que trae la consulta.
        Map<Integer, ModuloPermisoDto> modulosMap = new java.util.LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            Integer moduloId = rs.getInt("modulo_id");
            ModuloPermisoDto modulo = modulosMap.get(moduloId);
            if (modulo == null) {
                modulo = ModuloPermisoDto.builder()
                        .moduloId(moduloId)
                        .moduloCodigo(rs.getString("modulo_codigo"))
                        .moduloNombre(rs.getString("modulo_nombre"))
                        .activo(rs.getBoolean("modulo_activo"))
                        .submodulos(new ArrayList<>())
                        .build();
                modulosMap.put(moduloId, modulo);
            }
            int submoduloId = rs.getInt("submodulo_id");
            if (!rs.wasNull() && submoduloId != 0) {
                // padre_id es bigint: el driver no lo convierte a Integer con getObject(.., Integer.class).
                Object padreRaw = rs.getObject("padre_id");
                Integer padre = padreRaw != null ? ((Number) padreRaw).intValue() : null;
                modulo.getSubmodulos().add(SubmoduloPermisoDto.builder()
                        .submoduloId(submoduloId)
                        .submoduloCodigo(rs.getString("submodulo_codigo"))
                        .submoduloNombre(rs.getString("submodulo_nombre"))
                        .activo(rs.getBoolean("submodulo_activo"))
                        .padreId(padre)
                        .esGrupo(rs.getBoolean("es_grupo"))
                        .build());
            }
        });
        return new ArrayList<>(modulosMap.values());
    }

    /** Submódulos de un módulo, paginados y con búsqueda, con su grupo padre. */
    public PageImpl<SubmoduloTableDto> paginarSubmodulos(Integer moduloId, String search, int page, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT s.id, s.modulo_id, m.nombre AS modulo_nombre, s.nombre, s.codigo, s.descripcion,
                   s.activo, s.orden, s.padre_id, pa.nombre AS padre_nombre,
                   EXISTS (SELECT 1 FROM submodulos h WHERE h.padre_id = s.id AND h.deleted_at IS NULL) AS es_grupo,
                   COUNT(*) OVER() AS total_rows
            FROM submodulos s
            JOIN modulos m ON m.id = s.modulo_id
            LEFT JOIN submodulos pa ON pa.id = s.padre_id
            WHERE s.modulo_id = :moduloId AND s.deleted_at IS NULL
            """);
        MapSqlParameterSource p = new MapSqlParameterSource("moduloId", moduloId);
        String q = search != null ? search.trim().toLowerCase() : "";
        if (!q.isEmpty()) {
            sql.append("""
                AND (LOWER(s.nombre) LIKE :q OR LOWER(s.codigo) LIKE :q
                     OR LOWER(COALESCE(s.descripcion, '')) LIKE :q OR LOWER(COALESCE(pa.nombre, '')) LIKE :q)
                """);
            p.addValue("q", "%" + q + "%");
        }
        // Orden de árbol: cada grupo seguido de sus pantallas.
        sql.append("""
            ORDER BY COALESCE(pa.orden, s.orden), COALESCE(pa.id, s.id),
                     CASE WHEN s.padre_id IS NULL THEN 0 ELSE 1 END, s.orden, s.id
            LIMIT :limit OFFSET :offset
            """);
        p.addValue("limit", size);
        p.addValue("offset", page * size);
        List<SubmoduloTableDto> list = jdbc.query(sql.toString(), p, (rs, i) -> SubmoduloTableDto.builder()
                .id(rs.getInt("id"))
                .moduloId(rs.getInt("modulo_id"))
                .moduloNombre(rs.getString("modulo_nombre"))
                .nombre(rs.getString("nombre"))
                .codigo(rs.getString("codigo"))
                .descripcion(rs.getString("descripcion"))
                .activo(rs.getBoolean("activo"))
                .orden(rs.getInt("orden"))
                .padreId((Long) rs.getObject("padre_id", Long.class))
                .padreNombre(rs.getString("padre_nombre"))
                .esGrupo(rs.getBoolean("es_grupo"))
                .totalRows(rs.getInt("total_rows"))
                .build());
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, Math.max(size, 1)), total);
    }

    /** ¿Ya existe ese código en el módulo? (la unicidad es por módulo, no global). */
    public boolean codigoSubmoduloEnUso(Integer moduloId, String codigo, Integer exceptoId) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM submodulos
            WHERE modulo_id = :moduloId AND codigo = :codigo AND deleted_at IS NULL
              AND (CAST(:excepto AS INTEGER) IS NULL OR id <> :excepto)
            """, new MapSqlParameterSource().addValue("moduloId", moduloId).addValue("codigo", codigo)
                .addValue("excepto", exceptoId), Integer.class);
        return n != null && n > 0;
    }

    /** Hijos de un submódulo (para no anidar más de un nivel de grupos). */
    public int hijosDeSubmodulo(Integer submoduloId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM submodulos WHERE padre_id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", submoduloId), Integer.class);
        return n != null ? n : 0;
    }
}

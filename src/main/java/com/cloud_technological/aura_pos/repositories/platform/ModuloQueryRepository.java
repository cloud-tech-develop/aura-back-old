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

    public List<ModuloPermisoDto> listarPermisosPorEmpresa(Integer empresaId) {
        String sql = """
            SELECT 
                m.id as modulo_id, m.codigo as modulo_codigo, m.nombre as modulo_nombre,
                COALESCE(em.activo, false) as modulo_activo,
                s.id as submodulo_id, s.codigo as submodulo_codigo, s.nombre as submodulo_nombre,
                COALESCE(es.activo, false) as submodulo_activo
            FROM modulos m
            LEFT JOIN submodulos s ON m.id = s.modulo_id
            LEFT JOIN empresa_modulo em ON m.id = em.modulo_id AND em.empresa_id = :empresaId
            LEFT JOIN empresa_submodulo es ON s.id = es.submodulo_id AND es.empresa_id = :empresaId
            WHERE m.activo = true
            ORDER BY m.orden ASC, m.id ASC, s.orden ASC, s.id ASC
            """;
        
        List<Map<String, Object>> rows = jdbc.query(sql, Map.of("empresaId", empresaId), (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("modulo_id", rs.getInt("modulo_id"));
            row.put("modulo_codigo", rs.getString("modulo_codigo"));
            row.put("modulo_nombre", rs.getString("modulo_nombre"));
            row.put("modulo_activo", rs.getBoolean("modulo_activo"));
            row.put("submodulo_id", rs.getInt("submodulo_id"));
            row.put("submodulo_codigo", rs.getString("submodulo_codigo"));
            row.put("submodulo_nombre", rs.getString("submodulo_nombre"));
            row.put("submodulo_activo", rs.getBoolean("submodulo_activo"));
            return row;
        });
        
        // Agrupar por modulo
        Map<Integer, ModuloPermisoDto> modulosMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Integer moduloId = (Integer) row.get("modulo_id");
            Integer submoduloId = (Integer) row.get("submodulo_id");
            
            ModuloPermisoDto modulo = modulosMap.get(moduloId);
            if (modulo == null) {
                modulo = ModuloPermisoDto.builder()
                        .moduloId(moduloId)
                        .moduloCodigo((String) row.get("modulo_codigo"))
                        .moduloNombre((String) row.get("modulo_nombre"))
                        .activo((Boolean) row.get("modulo_activo"))
                        .submodulos(new ArrayList<>())
                        .build();
                modulosMap.put(moduloId, modulo);
            }
            
            if (submoduloId != null && submoduloId != 0) {
                SubmoduloPermisoDto submodulo = SubmoduloPermisoDto.builder()
                        .submoduloId(submoduloId)
                        .submoduloCodigo((String) row.get("submodulo_codigo"))
                        .submoduloNombre((String) row.get("submodulo_nombre"))
                        .activo((Boolean) row.get("submodulo_activo"))
                        .build();
                modulo.getSubmodulos().add(submodulo);
            }
        }
        
        return new ArrayList<>(modulosMap.values());
    }
}

package com.cloud_technological.aura_pos.repositories.reglas_descuento;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ReglaDescuentoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ReglaDescuentoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                r.id,
                r.nombre,
                c.nombre AS categoria_nombre,
                p.nombre AS producto_nombre,
                r.tipo_descuento,
                r.valor,
                r.fecha_inicio,
                r.fecha_fin,
                r.activo,
                COUNT(*) OVER() AS total_rows
            FROM regla_descuento r
            LEFT JOIN categoria c ON r.categoria_id = c.id
            LEFT JOIN producto p ON r.producto_id = p.id
            WHERE r.empresa_id = :empresaId
            AND r.activo = true
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(r.nombre) LIKE :search
                OR LOWER(c.nombre) LIKE :search
                OR LOWER(p.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY r.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ReglaDescuentoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ReglaDescuentoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeNombre(String nombre, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM regla_descuento
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND activo = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeNombreExcluyendo(String nombre, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM regla_descuento
            WHERE LOWER(nombre) = LOWER(:nombre)
            AND empresa_id = :empresaId
            AND id != :id
            AND activo = true
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("nombre", nombre);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    // Este método lo usará el motor de ventas para buscar descuentos aplicables
    public List<ReglaDescuentoDto> buscarReglasAplicables(Long productoId, Long categoriaId, Integer empresaId) {
        String sql = """
            SELECT
                r.id,
                r.nombre,
                r.tipo_descuento,
                r.valor,
                r.fecha_inicio,
                r.fecha_fin,
                r.dias_semana,
                r.hora_inicio,
                r.hora_fin
            FROM regla_descuento r
            WHERE r.empresa_id = :empresaId
            AND r.activo = true
            AND (r.producto_id = :productoId OR r.categoria_id = :categoriaId OR 
                (r.producto_id IS NULL AND r.categoria_id IS NULL))
            AND (r.fecha_inicio IS NULL OR r.fecha_inicio <= NOW())
            AND (r.fecha_fin IS NULL OR r.fecha_fin >= NOW())
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("productoId", productoId);
        params.addValue("categoriaId", categoriaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ReglaDescuentoDto.class));
    }
}
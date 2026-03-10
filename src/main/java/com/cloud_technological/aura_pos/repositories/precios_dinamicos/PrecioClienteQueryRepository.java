package com.cloud_technological.aura_pos.repositories.precios_dinamicos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.precios_dinamicos.PrecioClienteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class PrecioClienteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<PrecioClienteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        Long page = pageable.getPage() != null ? pageable.getPage() : 0L;
        Long size = pageable.getRows() != null ? pageable.getRows() : 10L;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pc.id,
                pc.tercero_id,
                t.nombre AS tercero_nombre,
                t.documento AS tercero_documento,
                pc.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                p.nombre AS producto_nombre,
                pc.precio_especial,
                pc.activo,
                COUNT(*) OVER() AS total_rows
            FROM precio_cliente pc
            INNER JOIN tercero t ON pc.tercero_id = t.id
            INNER JOIN producto_presentacion pres ON pc.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE pc.empresa_id = :empresaId
            AND pc.deleted_at IS NULL
            AND t.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(t.nombre) LIKE :search
                OR LOWER(t.documento) LIKE :search
                OR LOWER(p.nombre) LIKE :search
                OR LOWER(pres.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY pc.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<PrecioClienteTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(PrecioClienteTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page.intValue(), size.intValue()), total);
    }

    public List<PrecioClienteTableDto> listarPorCliente(Integer empresaId, Long terceroId) {
        String sql = """
            SELECT
                pc.id,
                pc.tercero_id,
                t.nombre AS tercero_nombre,
                t.documento AS tercero_documento,
                pc.producto_presentacion_id,
                pres.nombre AS producto_presentacion_nombre,
                p.nombre AS producto_nombre,
                pc.precio_especial,
                pc.activo
            FROM precio_cliente pc
            INNER JOIN tercero t ON pc.tercero_id = t.id
            INNER JOIN producto_presentacion pres ON pc.producto_presentacion_id = pres.id
            INNER JOIN producto p ON pres.producto_id = p.id
            WHERE pc.empresa_id = :empresaId
            AND pc.tercero_id = :terceroId
            AND pc.activo = true
            AND pc.deleted_at IS NULL
            AND t.deleted_at IS NULL
            ORDER BY p.nombre, pres.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("terceroId", terceroId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(PrecioClienteTableDto.class));
    }
}

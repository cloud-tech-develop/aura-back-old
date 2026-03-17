package com.cloud_technological.aura_pos.repositories.reconteo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reconteo.ReconteoDetalleResponseDto;
import com.cloud_technological.aura_pos.dto.reconteo.ReconteoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ReconteoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ReconteoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                r.id,
                s.nombre AS sucursal_nombre,
                r.estado,
                r.tipo,
                r.fecha_inicio,
                r.fecha_cierre,
                COUNT(rd.id) AS total_productos,
                COUNT(CASE WHEN rd.stock_contado IS NOT NULL
                           AND rd.stock_contado <> rd.stock_sistema THEN 1 END) AS diferencias_encontradas,
                COUNT(*) OVER() AS total_rows
            FROM reconteos r
            INNER JOIN sucursal s ON r.sucursal_id = s.id
            LEFT JOIN reconteo_detalles rd ON rd.reconteo_id = r.id
            WHERE r.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(s.nombre) LIKE :search OR LOWER(r.estado) LIKE :search OR LOWER(r.tipo) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" GROUP BY r.id, s.nombre, r.estado, r.tipo, r.fecha_inicio, r.fecha_cierre ");
        sql.append(" ORDER BY r.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ReconteoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ReconteoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ReconteoDetalleResponseDto> obtenerDetalles(Long reconteoId) {
        String sql = """
            SELECT
                rd.id,
                rd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                rd.lote_id,
                l.codigo_lote,
                rd.stock_sistema,
                rd.stock_contado,
                CASE WHEN rd.stock_contado IS NOT NULL
                     THEN rd.stock_contado - rd.stock_sistema
                     ELSE NULL END AS diferencia,
                rd.ajuste_aplicado
            FROM reconteo_detalles rd
            INNER JOIN producto p ON rd.producto_id = p.id
            LEFT JOIN lote l ON rd.lote_id = l.id
            WHERE rd.reconteo_id = :reconteoId
            ORDER BY p.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("reconteoId", reconteoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ReconteoDetalleResponseDto.class));
    }
}

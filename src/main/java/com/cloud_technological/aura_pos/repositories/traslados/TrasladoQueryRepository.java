package com.cloud_technological.aura_pos.repositories.traslados;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.traslados.TrasladoDetalleDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class TrasladoQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<TrasladoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                t.id,
                so.nombre AS sucursal_origen_nombre,
                sd.nombre AS sucursal_destino_nombre,
                t.fecha,
                t.estado,
                COUNT(*) OVER() AS total_rows
            FROM traslado t
            INNER JOIN sucursal so ON t.sucursal_origen_id = so.id
            INNER JOIN sucursal sd ON t.sucursal_destino_id = sd.id
            WHERE t.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(so.nombre) LIKE :search
                OR LOWER(sd.nombre) LIKE :search
                OR LOWER(t.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY t.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<TrasladoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(TrasladoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<TrasladoDetalleDto> obtenerDetalles(Long trasladoId) {
        String sql = """
            SELECT
                td.id,
                td.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                td.lote_id,
                l.codigo_lote,
                td.cantidad,
                td.costo_unitario
            FROM traslado_detalle td
            INNER JOIN producto p ON td.producto_id = p.id
            LEFT JOIN lote l ON td.lote_id = l.id
            WHERE td.traslado_id = :trasladoId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("trasladoId", trasladoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(TrasladoDetalleDto.class));
    }

}

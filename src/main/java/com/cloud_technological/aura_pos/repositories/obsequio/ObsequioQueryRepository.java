package com.cloud_technological.aura_pos.repositories.obsequio;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.obsequio.ObsequioDetalleDto;
import com.cloud_technological.aura_pos.dto.obsequio.ObsequioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ObsequioQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ObsequioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                o.id,
                s.nombre AS sucursal_nombre,
                COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS tercero_nombre,
                o.fecha,
                o.motivo,
                o.costo_total,
                o.iva_total,
                o.estado,
                COUNT(*) OVER() AS total_rows
            FROM obsequio o
            INNER JOIN sucursal s ON o.sucursal_id = s.id
            LEFT  JOIN tercero  t ON o.tercero_id  = t.id
            WHERE o.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(s.nombre) LIKE :search
                OR LOWER(o.motivo) LIKE :search
                OR LOWER(o.estado) LIKE :search
                OR LOWER(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos)))) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY o.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ObsequioTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ObsequioTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ObsequioDetalleDto> obtenerDetalles(Long obsequioId) {
        return jdbcTemplate.query("""
            SELECT
                od.id,
                od.producto_id,
                p.nombre AS producto_nombre,
                od.lote_id,
                l.codigo_lote,
                od.cantidad,
                od.costo_unitario,
                od.base_comercial_unitaria,
                od.iva_valor
            FROM obsequio_detalle od
            INNER JOIN producto p ON od.producto_id = p.id
            LEFT  JOIN lote     l ON od.lote_id     = l.id
            WHERE od.obsequio_id = :obsequioId
            ORDER BY od.id
            """,
            new MapSqlParameterSource("obsequioId", obsequioId),
            new BeanPropertyRowMapper<>(ObsequioDetalleDto.class));
    }
}

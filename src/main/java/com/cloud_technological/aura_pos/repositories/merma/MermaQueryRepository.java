package com.cloud_technological.aura_pos.repositories.merma;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.merma.MermaDetalleDto;
import com.cloud_technological.aura_pos.dto.merma.MermaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class MermaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<MermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                s.nombre AS sucursal_nombre,
                mm.nombre AS motivo_nombre,
                m.fecha,
                m.costo_total,
                m.estado,
                COUNT(*) OVER() AS total_rows
            FROM merma m
            INNER JOIN sucursal s ON m.sucursal_id = s.id
            INNER JOIN motivo_merma mm ON m.motivo_id = mm.id
            WHERE m.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(s.nombre) LIKE :search
                OR LOWER(mm.nombre) LIKE :search
                OR LOWER(m.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<MermaTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(MermaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<MermaDetalleDto> obtenerDetalles(Long mermaId) {
        String sql = """
            SELECT
                md.id,
                md.producto_id,
                p.nombre AS producto_nombre,
                md.lote_id,
                l.codigo_lote,
                md.cantidad,
                md.costo_unitario
            FROM merma_detalle md
            INNER JOIN producto p ON md.producto_id = p.id
            LEFT JOIN lote l ON md.lote_id = l.id
            WHERE md.merma_id = :mermaId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("mermaId", mermaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(MermaDetalleDto.class));
    }
}

package com.cloud_technological.aura_pos.repositories.cotizaciones;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionDetalleDto;
import com.cloud_technological.aura_pos.dto.cotizaciones.CotizacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CotizacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<CotizacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                c.id,
                c.numero,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS tercero_nombre,
                t.numero_documento AS tercero_documento,
                c.fecha,
                c.fecha_vencimiento,
                c.total,
                c.estado,
                COUNT(*) OVER() AS total_rows
            FROM cotizacion c
            LEFT JOIN tercero t ON c.tercero_id = t.id
            WHERE c.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(c.numero) LIKE :search
                OR LOWER(c.estado) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(t.numero_documento) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CotizacionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CotizacionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<CotizacionDetalleDto> obtenerDetalles(Long cotizacionId) {
        String sql = """
            SELECT
                cd.id,
                cd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                cd.descripcion,
                cd.cantidad,
                cd.precio_unitario,
                cd.iva_porcentaje,
                cd.descuento_valor,
                cd.subtotal
            FROM cotizacion_detalle cd
            INNER JOIN producto p ON cd.producto_id = p.id
            WHERE cd.cotizacion_id = :cotizacionId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("cotizacionId", cotizacionId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CotizacionDetalleDto.class));
    }

    public Long obtenerSiguienteConsecutivo(Integer empresaId) {
        try {
            String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(numero, 5) AS BIGINT)), 0) + 1
                FROM cotizacion
                WHERE empresa_id = :empresaId
            """;
            MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
            return jdbcTemplate.queryForObject(sql, params, Long.class);
        } catch (Exception e) {
            return 1L;
        }
    }
}

package com.cloud_technological.aura_pos.repositories.compras;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.compras.CompraDetalleDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CompraQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<CompraTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                c.id,
                c.numero_compra,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS proveedor_nombre,
                s.nombre AS sucursal_nombre,
                c.fecha,
                c.total,
                c.estado,
                COUNT(*) OVER() AS total_rows
            FROM compra c
            INNER JOIN tercero t ON c.proveedor_id = t.id
            INNER JOIN sucursal s ON c.sucursal_id = s.id
            WHERE c.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(c.numero_compra) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CompraTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CompraTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<CompraDetalleDto> obtenerDetalles(Long compraId) {
        String sql = """
            SELECT
                cd.id,
                cd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                cd.cantidad,
                cd.costo_unitario,
                cd.impuesto_valor,
                cd.subtotal_linea,
                cd.descuento_pct,
                cd.descuento_valor,
                cd.precio_venta1,
                cd.precio_venta2,
                cd.precio_venta3
            FROM compra_detalle cd
            INNER JOIN producto p ON cd.producto_id = p.id
            WHERE cd.compra_id = :compraId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("compraId", compraId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CompraDetalleDto.class));
    }
}

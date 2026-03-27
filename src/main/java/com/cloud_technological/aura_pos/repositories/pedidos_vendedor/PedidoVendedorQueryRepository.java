package com.cloud_technological.aura_pos.repositories.pedidos_vendedor;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorDetalleDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorTableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorPageableDto;

@Repository
public class PedidoVendedorQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<PedidoVendedorTableDto> listar(PedidoVendedorPageableDto pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage() : 0;
        int size = pageable.getRows() != null ? pageable.getRows() : 15;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        String estado = pageable.getEstado() != null ? pageable.getEstado().trim() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                pv.id,
                pv.numero_pedido,
                COALESCE(NULLIF(tv.razon_social, ''), CONCAT(tv.nombres, ' ', tv.apellidos)) AS vendedor_nombre,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS cliente_nombre,
                pv.estado,
                pv.total,
                pv.created_at,
                COUNT(*) OVER() AS total_rows
            FROM pedido_vendedor pv
            INNER JOIN usuario u ON pv.vendedor_id = u.id
            LEFT JOIN tercero tv ON u.tercero_id = tv.id
            LEFT JOIN tercero t ON pv.cliente_id = t.id
            WHERE pv.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(pv.numero_pedido) LIKE :search
                OR LOWER(tv.nombres) LIKE :search
                OR LOWER(tv.apellidos) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        if (!estado.isEmpty()) {
            sql.append(" AND pv.estado = :estado ");
            params.addValue("estado", estado);
        }

        sql.append(" ORDER BY pv.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<PedidoVendedorTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(PedidoVendedorTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<PedidoVendedorDetalleDto> obtenerDetalles(Long pedidoId) {
        String sql = """
            SELECT
                pvd.id,
                pvd.producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                pvd.cantidad,
                pvd.precio_unitario,
                pvd.descuento_valor,
                pvd.impuesto_valor,
                pvd.subtotal_linea
            FROM pedido_vendedor_detalle pvd
            INNER JOIN producto p ON pvd.producto_id = p.id
            WHERE pvd.pedido_vendedor_id = :pedidoId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("pedidoId", pedidoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(PedidoVendedorDetalleDto.class));
    }
}

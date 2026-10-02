package com.cloud_technological.aura_pos.repositories.inventario;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecturas del costo promedio (el JPA solo guarda; ver regla del proyecto). */
@Repository
public class CostoPromedioQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Bloquea la fila del producto hasta el fin de la transacción y devuelve su
     * costo actual. Dos compras del mismo producto a la vez se mezclarían sobre
     * el mismo promedio y una pisaría a la otra.
     */
    public BigDecimal bloquearYLeerCosto(Long productoId) {
        List<BigDecimal> r = jdbc.queryForList(
                "SELECT costo FROM producto WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", productoId), BigDecimal.class);
        if (r.isEmpty()) return null;
        return r.get(0) != null ? r.get(0) : BigDecimal.ZERO;
    }

    /**
     * Existencia total del producto en todas las bodegas. El promedio es por
     * empresa (el producto ya es de una sola empresa), no por bodega: un
     * traslado no cambia el costo.
     */
    public BigDecimal stockTotal(Long productoId) {
        BigDecimal r = jdbc.queryForObject(
                "SELECT COALESCE(SUM(stock_actual), 0) FROM inventario WHERE producto_id = :id",
                new MapSqlParameterSource("id", productoId), BigDecimal.class);
        return r != null ? r : BigDecimal.ZERO;
    }
}

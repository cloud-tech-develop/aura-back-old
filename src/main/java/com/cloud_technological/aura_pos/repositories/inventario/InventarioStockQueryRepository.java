package com.cloud_technological.aura_pos.repositories.inventario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bloqueo de filas de inventario (el JPA solo guarda; ver regla del proyecto). */
@Repository
public class InventarioStockQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Bloquea el saldo (bodega, producto) hasta el fin de la transacción y
     * devuelve su id, o null si todavía no existe la fila.
     */
    public Long bloquear(Long bodegaId, Long productoId) {
        List<Long> r = jdbc.queryForList(
                "SELECT id FROM inventario WHERE bodega_id = :b AND producto_id = :p FOR UPDATE",
                new MapSqlParameterSource().addValue("b", bodegaId).addValue("p", productoId),
                Long.class);
        return r.isEmpty() ? null : r.get(0);
    }
}

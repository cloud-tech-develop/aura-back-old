package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas del recargo por forma de pago (V188). */
@Repository
public class RecargoFormaPagoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Id del producto de servicio que lleva el recargo en la venta, si ya existe. */
    public Long productoRecargoId(Integer empresaId, String sku) {
        List<Long> ids = jdbc.queryForList("""
                SELECT p.id
                FROM producto p
                WHERE p.empresa_id = :empresaId
                  AND p.sku = :sku
                  AND p.deleted_at IS NULL
                ORDER BY p.id
                LIMIT 1
                """,
                new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("sku", sku),
                Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Unidad para un servicio: la de "unidad" si existe, si no la primera activa. */
    public Long unidadServicioId() {
        List<Long> ids = jdbc.queryForList("""
                SELECT u.id
                FROM unidad_medida u
                WHERE u.activo = true
                ORDER BY CASE WHEN LOWER(u.abreviatura) IN ('und', 'un', 'u', 'unid') THEN 0
                              WHEN LOWER(u.nombre) LIKE 'unidad%' THEN 1
                              WHEN LOWER(u.nombre) LIKE 'servicio%' THEN 2
                              ELSE 3 END,
                         u.id
                LIMIT 1
                """, new MapSqlParameterSource(), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }
}

package com.cloud_technological.aura_pos.repositories.inventario_consumo;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;

@Repository
public class InventarioConsumoComponenteQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /** Componentes consumidos por cada línea del documento, agrupados por línea. */
    public Map<Long, List<ConsumoComponenteDto>> porDetalle(String origen, Collection<Long> detalleIds) {
        if (detalleIds == null || detalleIds.isEmpty()) {
            return Map.of();
        }

        List<ConsumoComponenteDto> consumos = jdbcTemplate.query("""
            SELECT
                c.detalle_id,
                c.producto_hijo_id                  AS producto_id,
                p.nombre                            AS producto_nombre,
                p.sku                               AS producto_sku,
                um.abreviatura                      AS unidad_abreviatura,
                c.cantidad,
                c.costo_unitario,
                ROUND(c.cantidad * c.costo_unitario, 2) AS costo_total
            FROM inventario_consumo_componente c
            INNER JOIN producto      p  ON p.id  = c.producto_hijo_id
            LEFT  JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
            WHERE c.origen = :origen
              AND c.detalle_id IN (:detalleIds)
            ORDER BY c.detalle_id, c.id
            """,
            new MapSqlParameterSource()
                    .addValue("origen", origen)
                    .addValue("detalleIds", detalleIds),
            new BeanPropertyRowMapper<>(ConsumoComponenteDto.class));

        return consumos.stream().collect(Collectors.groupingBy(
                ConsumoComponenteDto::getDetalleId, LinkedHashMap::new, Collectors.toList()));
    }
}

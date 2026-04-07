package com.cloud_technological.aura_pos.repositories.periodo_contable;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.periodo_contable.PeriodoContableTableDto;

@Repository
public class PeriodoContableQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<PeriodoContableTableDto> listar(Integer empresaId) {
        String sql = """
            SELECT
                p.id,
                p.anio,
                p.mes,
                p.estado,
                p.fecha_apertura,
                p.fecha_cierre,
                p.observaciones,
                p.created_at,
                COUNT(a.id) AS total_asientos
            FROM periodo_contable p
            LEFT JOIN asiento_contable a
                   ON a.periodo_contable_id = p.id
                  AND a.estado != 'ANULADO'
            WHERE p.empresa_id = :empresaId
            GROUP BY p.id, p.anio, p.mes, p.estado,
                     p.fecha_apertura, p.fecha_cierre,
                     p.observaciones, p.created_at
            ORDER BY p.anio DESC, p.mes DESC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(PeriodoContableTableDto.class));
    }

    /** Valida que todos los asientos del período cuadren (débito == crédito) */
    public List<String> comprobantesSinCuadre(Long periodoId) {
        String sql = """
            SELECT numero_comprobante
            FROM asiento_contable
            WHERE periodo_contable_id = :periodoId
              AND estado != 'ANULADO'
              AND ABS(total_debito - total_credito) > 0.01
            ORDER BY numero_comprobante
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("periodoId", periodoId);
        return jdbc.queryForList(sql, params, String.class);
    }
}

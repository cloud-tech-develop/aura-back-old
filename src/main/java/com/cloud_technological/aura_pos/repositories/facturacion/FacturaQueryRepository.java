package com.cloud_technological.aura_pos.repositories.facturacion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.facturacion.ReciboPagoDto;

@Repository
public class FacturaQueryRepository {
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    public List<ReciboPagoDto> obtenerDetalles(Long facturaId) {
        String sql = """
            SELECT
                fp.id,
                fp.factura_id,
                fp.valor,
                fp.banco,
                fp.tipo,
                fp.descripcion,
                fp.usuario_id,
                fp.metodo_pago,
                fp.created_at,
                fp.updated_at
            FROM factura_pago fp
            WHERE fp.factura_id = :facturaId
            ORDER BY fp.id ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("facturaId", facturaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ReciboPagoDto.class));
    }
    
    // Obtener siguiente consecutivo de factura para la empresa
    public Long obtenerSiguienteConsecutivo(Integer empresaId) {
        try {
            String sql = """
                SELECT COALESCE(MAX(consecutivo), 0) + 1
                FROM factura
                WHERE empresa_id = :empresaId
                AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.queryForObject(sql, params, Long.class);
        } catch (Exception e) {
            return 1L; // Si ocurre un error, retornar 1 como el primer consecutivo
        }
    }
}

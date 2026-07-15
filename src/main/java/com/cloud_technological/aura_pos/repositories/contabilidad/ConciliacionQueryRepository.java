package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.MovimientoLibroDto;

/**
 * Consultas del libro para la conciliación bancaria (E9): movimientos
 * contabilizados sobre la cuenta contable del banco, con la marca de si ya
 * fueron tomados por alguna línea de extracto.
 */
@Repository
public class ConciliacionQueryRepository {

    private static final String BASE = """
        SELECT ad.id AS asiento_detalle_id,
               a.fecha,
               a.numero_comprobante,
               COALESCE(ad.descripcion, a.descripcion) AS descripcion,
               a.tipo_origen,
               ad.debito,
               ad.credito,
               EXISTS (SELECT 1 FROM extracto_linea el
                       WHERE el.asiento_detalle_id = ad.id) AS conciliado
        FROM asiento_detalle ad
        JOIN asiento_contable a ON a.id = ad.asiento_id
        WHERE a.empresa_id = :empresaId
          AND ad.cuenta_id = :cuentaId
          AND a.estado = 'CONTABILIZADO'
        """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Movimientos del libro de la cuenta del banco en un rango de fechas. */
    public List<MovimientoLibroDto> movimientosLibro(Integer empresaId, Long cuentaId,
            LocalDate desde, LocalDate hasta) {
        String sql = BASE + """
              AND a.fecha BETWEEN :desde AND :hasta
            ORDER BY a.fecha, ad.id
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("cuentaId", cuentaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, params, (rs, i) -> mapear(rs));
    }

    /** Un detalle puntual, validando que sea de la cuenta del banco y la empresa. */
    public Optional<MovimientoLibroDto> movimiento(Integer empresaId, Long cuentaId,
            Long asientoDetalleId) {
        String sql = BASE + "  AND ad.id = :detalleId\n";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("cuentaId", cuentaId)
                .addValue("detalleId", asientoDetalleId);
        List<MovimientoLibroDto> rows = jdbc.query(sql, params, (rs, i) -> mapear(rs));
        return rows.stream().findFirst();
    }

    private static MovimientoLibroDto mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MovimientoLibroDto(
                rs.getLong("asiento_detalle_id"),
                rs.getDate("fecha").toLocalDate(),
                rs.getString("numero_comprobante"),
                rs.getString("descripcion"),
                rs.getString("tipo_origen"),
                rs.getBigDecimal("debito"),
                rs.getBigDecimal("credito"),
                rs.getBoolean("conciliado"));
    }
}

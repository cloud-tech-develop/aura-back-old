package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaProyeccionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;

@Repository
public class AsientoContableQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<AsientoContableTableDto> paginar(Integer empresaId, String desde, String hasta,
            String tipoOrigen, int page, int rows) {
        int offset = page * rows;
        String base = """
            SELECT
                a.id,
                a.numero_comprobante,
                a.fecha,
                a.descripcion,
                a.tipo_origen,
                a.origen_id,
                a.total_debito,
                a.total_credito,
                a.estado,
                a.created_at,
                COUNT(*) OVER() AS total_rows
            FROM asiento_contable a
            WHERE a.empresa_id = :empresaId
              AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
            """;
        String filtroTipo = (tipoOrigen != null && !tipoOrigen.isBlank())
                ? "  AND a.tipo_origen = :tipoOrigen\n" : "";
        String sql = base + filtroTipo
                + "ORDER BY a.fecha DESC, a.id DESC\nLIMIT :rows OFFSET :offset\n";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta)
                .addValue("rows", rows)
                .addValue("offset", offset);
        if (tipoOrigen != null && !tipoOrigen.isBlank()) {
            params.addValue("tipoOrigen", tipoOrigen);
        }

        return jdbc.query(sql, params, (rs, i) -> {
            AsientoContableTableDto dto = new AsientoContableTableDto();
            dto.setId(rs.getLong("id"));
            dto.setNumeroComprobante(rs.getString("numero_comprobante"));
            dto.setFecha(rs.getString("fecha"));
            dto.setDescripcion(rs.getString("descripcion"));
            dto.setTipoOrigen(rs.getString("tipo_origen"));
            dto.setOrigenId(rs.getObject("origen_id") != null ? rs.getLong("origen_id") : null);
            dto.setTotalDebito(rs.getBigDecimal("total_debito"));
            dto.setTotalCredito(rs.getBigDecimal("total_credito"));
            dto.setEstado(rs.getString("estado"));
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
    }

    public List<AsientoDetalleDto> obtenerDetalles(Long asientoId) {
        String sql = """
            SELECT
                ad.id,
                ad.cuenta_id,
                pc.codigo AS cuenta_codigo,
                pc.nombre AS cuenta_nombre,
                pc.tipo   AS cuenta_tipo,
                ad.descripcion,
                ad.debito,
                ad.credito
            FROM asiento_detalle ad
            JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
            WHERE ad.asiento_id = :asientoId
            ORDER BY ad.id
            """;
        return jdbc.query(sql, Map.of("asientoId", asientoId), (rs, i) -> {
            AsientoDetalleDto dto = new AsientoDetalleDto();
            dto.setId(rs.getLong("id"));
            dto.setCuentaId(rs.getLong("cuenta_id"));
            dto.setCuentaCodigo(rs.getString("cuenta_codigo"));
            dto.setCuentaNombre(rs.getString("cuenta_nombre"));
            dto.setCuentaTipo(rs.getString("cuenta_tipo"));
            dto.setDescripcion(rs.getString("descripcion"));
            dto.setDebito(rs.getBigDecimal("debito"));
            dto.setCredito(rs.getBigDecimal("credito"));
            return dto;
        });
    }

    /**
     * Genera el siguiente número de comprobante para la empresa y prefijo dado.
     * Formato: {PREFIX}-{6 dígitos}  ej: CD-000001
     */
    public String siguienteNumeroComprobante(Integer empresaId, String prefix) {
        String sql = """
            SELECT COALESCE(MAX(
                CAST(SUBSTRING(numero_comprobante FROM LENGTH(:prefix) + 2) AS INTEGER)
            ), 0) + 1
            FROM asiento_contable
            WHERE empresa_id = :empresaId
              AND numero_comprobante LIKE :prefixLike
            """;
        Integer siguiente = jdbc.queryForObject(sql,
                Map.of("empresaId", empresaId, "prefix", prefix, "prefixLike", prefix + "-%"),
                Integer.class);
        return String.format("%s-%06d", prefix, siguiente != null ? siguiente : 1);
    }

    public Map<String, Object> balanceGeneral(Integer empresaId, String hasta) {
        String sql = """
            SELECT
                pc.tipo,
                SUM(CASE WHEN pc.naturaleza = 'DEBITO'  THEN ad.debito  - ad.credito
                         ELSE                                ad.credito - ad.debito END) AS saldo
            FROM asiento_detalle ad
            JOIN asiento_contable a  ON a.id = ad.asiento_id
            JOIN plan_cuenta pc      ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.fecha <= CAST(:hasta AS DATE)
              AND a.estado = 'CONTABILIZADO'
            GROUP BY pc.tipo
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                Map.of("empresaId", empresaId, "hasta", hasta));
        Map<String, Object> result = new java.util.HashMap<>();
        rows.forEach(r -> result.put((String) r.get("tipo"), r.get("saldo")));
        return result;
    }

    /** Estado de Resultados: saldos agrupados por cuenta (INGRESO, COSTO, GASTO) */
    public List<EstadoResultadosLineaDto> estadoResultados(Integer empresaId,
            String desde, String hasta) {
        String sql = """
            SELECT
                pc.tipo,
                pc.codigo,
                pc.nombre,
                SUM(CASE WHEN pc.naturaleza = 'CREDITO' THEN ad.credito - ad.debito
                         ELSE                                ad.debito  - ad.credito END) AS saldo
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
              AND a.estado = 'CONTABILIZADO'
              AND pc.tipo IN ('INGRESO', 'COSTO', 'GASTO')
            GROUP BY pc.tipo, pc.codigo, pc.nombre
            ORDER BY pc.tipo, pc.codigo
            """;
        return jdbc.query(sql, Map.of("empresaId", empresaId, "desde", desde, "hasta", hasta),
            (rs, i) -> {
                EstadoResultadosLineaDto dto = new EstadoResultadosLineaDto();
                dto.setTipo(rs.getString("tipo"));
                dto.setCodigo(rs.getString("codigo"));
                dto.setNombre(rs.getString("nombre"));
                dto.setSaldo(rs.getBigDecimal("saldo"));
                return dto;
            });
    }

    /** Flujo de Caja: saldo inicial acumulado antes del período */
    public BigDecimal saldoInicialTesoreria(Integer empresaId, String desde) {
        String sql = """
            SELECT COALESCE(SUM(
                CASE WHEN tipo IN ('RECAUDO','TRANSFERENCIA_ENTRADA') THEN monto ELSE -monto END
            ), 0)
            FROM tesoreria_movimiento
            WHERE empresa_id = :empresaId
              AND anulado = false
              AND fecha < CAST(:desde AS DATE)
            """;
        return jdbc.queryForObject(sql,
                Map.of("empresaId", empresaId, "desde", desde), BigDecimal.class);
    }

    /** Flujo de Caja: movimientos reales del período */
    public List<FlujoCajaLineaDto> movimientosTesoreria(Integer empresaId,
            String desde, String hasta) {
        String sql = """
            SELECT
                tm.fecha::text,
                tm.concepto,
                CASE WHEN tm.tipo IN ('RECAUDO','TRANSFERENCIA_ENTRADA') THEN 'INGRESO' ELSE 'EGRESO' END AS tipo,
                COALESCE(tm.categoria, 'Sin categoría') AS categoria,
                COALESCE(cb.nombre, '') AS cuenta_banco,
                tm.monto
            FROM tesoreria_movimiento tm
            LEFT JOIN cuenta_bancaria cb ON cb.id = tm.cuenta_bancaria_id
            WHERE tm.empresa_id = :empresaId
              AND tm.anulado = false
              AND tm.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
            ORDER BY tm.fecha, tm.id
            """;
        return jdbc.query(sql,
            Map.of("empresaId", empresaId, "desde", desde, "hasta", hasta),
            (rs, i) -> new FlujoCajaLineaDto(
                rs.getString("fecha"),
                rs.getString("concepto"),
                rs.getString("tipo"),
                rs.getString("categoria"),
                rs.getString("cuenta_banco"),
                rs.getBigDecimal("monto")
            ));
    }

    /** Flujo de Caja: cuentas por cobrar pendientes */
    public List<FlujoCajaProyeccionDto> proyeccionCxC(Integer empresaId) {
        String sql = """
            SELECT
                COALESCE(cc.fecha_vencimiento::text, '') AS fecha_vencimiento,
                COALESCE(t.razon_social, CONCAT(COALESCE(t.nombres,''), ' ', COALESCE(t.apellidos,'')), 'Sin nombre') AS tercero,
                cc.numero_cuenta AS referencia,
                cc.saldo_pendiente AS saldo
            FROM cuentas_cobrar cc
            LEFT JOIN tercero t ON t.id = cc.tercero_id
            WHERE cc.empresa_id = :empresaId
              AND cc.estado = 'activa'
              AND cc.saldo_pendiente > 0
            ORDER BY cc.fecha_vencimiento
            LIMIT 100
            """;
        return jdbc.query(sql, Map.of("empresaId", empresaId),
            (rs, i) -> new FlujoCajaProyeccionDto(
                rs.getString("fecha_vencimiento"),
                rs.getString("tercero"),
                rs.getString("referencia"),
                rs.getBigDecimal("saldo"),
                "CXC"
            ));
    }

    /** Flujo de Caja: cuentas por pagar pendientes */
    public List<FlujoCajaProyeccionDto> proyeccionCxP(Integer empresaId) {
        String sql = """
            SELECT
                COALESCE(cp.fecha_vencimiento::text, '') AS fecha_vencimiento,
                COALESCE(t.razon_social, CONCAT(COALESCE(t.nombres,''), ' ', COALESCE(t.apellidos,'')), 'Sin nombre') AS tercero,
                cp.numero_cuenta AS referencia,
                cp.saldo_pendiente AS saldo
            FROM cuentas_pagar cp
            LEFT JOIN tercero t ON t.id = cp.tercero_id
            WHERE cp.empresa_id = :empresaId
              AND cp.estado = 'activa'
              AND cp.saldo_pendiente > 0
            ORDER BY cp.fecha_vencimiento
            LIMIT 100
            """;
        return jdbc.query(sql, Map.of("empresaId", empresaId),
            (rs, i) -> new FlujoCajaProyeccionDto(
                rs.getString("fecha_vencimiento"),
                rs.getString("tercero"),
                rs.getString("referencia"),
                rs.getBigDecimal("saldo"),
                "CXP"
            ));
    }

    /** Libro Mayor: movimientos de una cuenta con saldo acumulado */
    public List<LibroMayorLineaDto> libroMayor(Integer empresaId, Long cuentaId,
            String desde, String hasta) {
        String sql = """
            SELECT
                a.fecha,
                a.numero_comprobante,
                a.descripcion        AS descripcion_asiento,
                ad.descripcion       AS descripcion_linea,
                a.tipo_origen,
                ad.debito,
                ad.credito,
                SUM(ad.debito - ad.credito) OVER (
                    ORDER BY a.fecha, a.id ROWS UNBOUNDED PRECEDING
                ) AS saldo_acumulado
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            WHERE a.empresa_id = :empresaId
              AND ad.cuenta_id  = :cuentaId
              AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
              AND a.estado = 'CONTABILIZADO'
            ORDER BY a.fecha, a.id
            """;
        return jdbc.query(sql,
            Map.of("empresaId", empresaId, "cuentaId", cuentaId, "desde", desde, "hasta", hasta),
            (rs, i) -> {
                LibroMayorLineaDto dto = new LibroMayorLineaDto();
                dto.setFecha(rs.getString("fecha"));
                dto.setNumeroComprobante(rs.getString("numero_comprobante"));
                dto.setDescripcion(rs.getString("descripcion_asiento"));
                dto.setDescripcionLinea(rs.getString("descripcion_linea"));
                dto.setTipoOrigen(rs.getString("tipo_origen"));
                dto.setDebito(rs.getBigDecimal("debito"));
                dto.setCredito(rs.getBigDecimal("credito"));
                dto.setSaldoAcumulado(rs.getBigDecimal("saldo_acumulado"));
                return dto;
            });
    }
}

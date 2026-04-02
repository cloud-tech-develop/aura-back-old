package com.cloud_technological.aura_pos.repositories.cartera;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.CarteraDashboardDto;
import com.cloud_technological.aura_pos.dto.cartera.ClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.CuentaVencidaAlertaDto;
import com.cloud_technological.aura_pos.dto.cartera.EdadCarteraDto;

@Repository
public class CarteraQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ─── Dashboard ────────────────────────────────────────────────────────────

    public CarteraDashboardDto dashboard(Integer empresaId) {
        CarteraDashboardDto dto = new CarteraDashboardDto();

        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);

        // KPIs de cartera
        String sqlKpi = """
            SELECT
                COALESCE(SUM(cc.saldo_pendiente), 0)                                                AS total_cartera,
                COALESCE(SUM(CASE WHEN cc.fecha_vencimiento < NOW() THEN cc.saldo_pendiente ELSE 0 END), 0) AS cartera_vencida,
                COALESCE(SUM(CASE WHEN cc.fecha_vencimiento BETWEEN NOW() AND NOW() + INTERVAL '30 days'
                                  THEN cc.saldo_pendiente ELSE 0 END), 0)                           AS cartera_por_vencer
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId
              AND cc.estado NOT IN ('pagada','anulada')
              AND cc.deleted_at IS NULL
            """;
        jdbc.query(sqlKpi, p, rs -> {
            dto.setTotalCartera(rs.getBigDecimal("total_cartera"));
            dto.setCarteraVencida(rs.getBigDecimal("cartera_vencida"));
            dto.setCarteraPorVencer(rs.getBigDecimal("cartera_por_vencer"));
        });

        // Recaudo del mes
        String sqlRecaudo = """
            SELECT COALESCE(SUM(a.monto), 0)
            FROM abonos_cobrar a
            JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
            WHERE cc.empresa_id = :empresaId
              AND DATE_TRUNC('month', a.fecha_pago) = DATE_TRUNC('month', NOW())
            """;
        BigDecimal recaudo = jdbc.queryForObject(sqlRecaudo, p, BigDecimal.class);
        dto.setRecaudoMes(recaudo != null ? recaudo : BigDecimal.ZERO);

        // Clientes con mora
        String sqlMora = """
            SELECT COUNT(DISTINCT cc.tercero_id)
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId
              AND cc.fecha_vencimiento < NOW()
              AND cc.estado NOT IN ('pagada','anulada')
              AND cc.deleted_at IS NULL
            """;
        Long clientesMora = jdbc.queryForObject(sqlMora, p, Long.class);
        dto.setClientesConMora(clientesMora != null ? clientesMora : 0L);

        // Clientes bloqueados
        String sqlBloq = """
            SELECT COUNT(*) FROM tercero_credito
            WHERE empresa_id = :empresaId AND estado_credito = 'BLOQUEADO'
            """;
        Long bloq = jdbc.queryForObject(sqlBloq, p, Long.class);
        dto.setClientesBloqueados(bloq != null ? bloq : 0L);

        // Solicitudes pendientes
        String sqlSol = """
            SELECT COUNT(*) FROM solicitud_autorizacion_credito
            WHERE empresa_id = :empresaId AND estado = 'PENDIENTE'
            """;
        Long sol = jdbc.queryForObject(sqlSol, p, Long.class);
        dto.setSolicitudesPendientes(sol != null ? sol : 0L);

        // Edades de cartera
        String sqlEdades = """
            SELECT
                COALESCE(SUM(CASE WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) BETWEEN 0 AND 30
                                  AND cc.fecha_vencimiento < NOW() THEN cc.saldo_pendiente ELSE 0 END), 0) AS edad0a30,
                COALESCE(SUM(CASE WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) BETWEEN 31 AND 60
                                  THEN cc.saldo_pendiente ELSE 0 END), 0) AS edad31a60,
                COALESCE(SUM(CASE WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) BETWEEN 61 AND 90
                                  THEN cc.saldo_pendiente ELSE 0 END), 0) AS edad61a90,
                COALESCE(SUM(CASE WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) > 90
                                  THEN cc.saldo_pendiente ELSE 0 END), 0) AS edadMas90
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId
              AND cc.fecha_vencimiento < NOW()
              AND cc.estado NOT IN ('pagada','anulada')
              AND cc.deleted_at IS NULL
            """;
        jdbc.query(sqlEdades, p, rs -> {
            dto.setEdad0a30(rs.getBigDecimal("edad0a30"));
            dto.setEdad31a60(rs.getBigDecimal("edad31a60"));
            dto.setEdad61a90(rs.getBigDecimal("edad61a90"));
            dto.setEdadMas90(rs.getBigDecimal("edadMas90"));
        });

        // Top 10 alertas vencidas
        dto.setAlertasVencidas(alertasVencidas(empresaId, 10));

        return dto;
    }

    // ─── Alertas de cuentas vencidas ─────────────────────────────────────────

    public List<CuentaVencidaAlertaDto> alertasVencidas(Integer empresaId, int limit) {
        String sql = """
            SELECT
                cc.id                                                     AS cuentaId,
                cc.numero_cuenta                                          AS numeroCuenta,
                t.id                                                      AS terceroId,
                COALESCE(t.razon_social, t.nombres || ' ' || COALESCE(t.apellidos,'')) AS terceroNombre,
                t.numero_documento                                        AS terceroDocumento,
                cc.saldo_pendiente                                        AS saldoPendiente,
                TO_CHAR(cc.fecha_vencimiento, 'YYYY-MM-DD')               AS fechaVencimiento,
                GREATEST(0, EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento)::INT) AS diasVencida,
                tc.estado_credito                                         AS estadoCredito,
                COALESCE(tc.score_crediticio, 500)                        AS scoreCrediticio,
                gc_last.tipo_gestion                                      AS ultimaGestion,
                TO_CHAR(gc_last.created_at, 'YYYY-MM-DD')                 AS fechaUltimaGestion
            FROM cuentas_cobrar cc
            JOIN tercero t ON t.id = cc.tercero_id
            LEFT JOIN tercero_credito tc ON tc.tercero_id = t.id AND tc.empresa_id = cc.empresa_id
            LEFT JOIN LATERAL (
                SELECT tipo_gestion, created_at
                FROM gestion_cobro gc
                WHERE gc.cuenta_cobrar_id = cc.id
                ORDER BY gc.created_at DESC LIMIT 1
            ) gc_last ON true
            WHERE cc.empresa_id = :empresaId
              AND cc.fecha_vencimiento < NOW()
              AND cc.estado NOT IN ('pagada','anulada')
              AND cc.deleted_at IS NULL
            ORDER BY cc.saldo_pendiente DESC, diasVencida DESC
            LIMIT :limit
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("empresaId", empresaId)
            .addValue("limit", limit);
        return jdbc.query(sql, p, new BeanPropertyRowMapper<>(CuentaVencidaAlertaDto.class));
    }

    // ─── Lista clientes con cartera ───────────────────────────────────────────

    public PageImpl<ClienteCarteraDto> listarClientes(Integer empresaId, int page, int rows, String search) {
        String searchVal = search != null ? "%" + search.toLowerCase().trim() + "%" : "%";

        String sql = """
            SELECT
                t.id                                                                AS terceroId,
                COALESCE(t.razon_social, t.nombres || ' ' || COALESCE(t.apellidos,'')) AS terceroNombre,
                t.tipo_documento                                                    AS tipoDocumento,
                t.numero_documento                                                  AS numeroDocumento,
                t.telefono,
                t.email,
                tc.id                                                               AS creditoId,
                tc.cupo_credito_actual                                              AS cupoCreditoActual,
                COALESCE(saldo.saldo_cartera, 0)                                    AS saldoCartera,
                GREATEST(0, COALESCE(tc.cupo_credito_actual, 0) - COALESCE(saldo.saldo_cartera, 0)) AS saldoDisponible,
                tc.estado_credito                                                   AS estadoCredito,
                tc.nivel_riesgo                                                     AS nivelRiesgo,
                COALESCE(tc.score_crediticio, 500)                                  AS scoreCrediticio,
                tc.plazo_dias                                                       AS plazoDias,
                COALESCE(mora.total_vencido, 0)                                     AS totalVencido,
                COALESCE(mora.dias_mora_maximo, 0)::INT                             AS diasMoraMaximo,
                COALESCE(mora.docs_vencidos, 0)                                     AS documentosVencidos,
                gc_last.tipo_gestion                                                AS ultimaGestion,
                TO_CHAR(gc_last.created_at, 'YYYY-MM-DD')                           AS fechaUltimaGestion,
                COUNT(*) OVER ()                                                    AS totalRows
            FROM tercero t
            JOIN cuentas_cobrar cc ON cc.tercero_id = t.id AND cc.empresa_id = :empresaId
                AND cc.estado NOT IN ('pagada','anulada') AND cc.deleted_at IS NULL
            LEFT JOIN tercero_credito tc ON tc.tercero_id = t.id AND tc.empresa_id = :empresaId
            LEFT JOIN LATERAL (
                SELECT SUM(c.saldo_pendiente) AS saldo_cartera
                FROM cuentas_cobrar c
                WHERE c.tercero_id = t.id AND c.empresa_id = :empresaId
                  AND c.estado NOT IN ('pagada','anulada') AND c.deleted_at IS NULL
            ) saldo ON true
            LEFT JOIN LATERAL (
                SELECT
                    SUM(c.saldo_pendiente)                            AS total_vencido,
                    MAX(EXTRACT(DAY FROM NOW() - c.fecha_vencimiento)) AS dias_mora_maximo,
                    COUNT(*)                                          AS docs_vencidos
                FROM cuentas_cobrar c
                WHERE c.tercero_id = t.id AND c.empresa_id = :empresaId
                  AND c.fecha_vencimiento < NOW()
                  AND c.estado NOT IN ('pagada','anulada') AND c.deleted_at IS NULL
            ) mora ON true
            LEFT JOIN LATERAL (
                SELECT tipo_gestion, created_at
                FROM gestion_cobro g
                WHERE g.tercero_id = t.id AND g.empresa_id = :empresaId
                ORDER BY g.created_at DESC LIMIT 1
            ) gc_last ON true
            WHERE t.empresa_id = :empresaId
              AND t.deleted_at IS NULL
              AND (LOWER(COALESCE(t.razon_social, t.nombres || ' ' || COALESCE(t.apellidos,''))) LIKE :search
                OR LOWER(t.numero_documento) LIKE :search)
            GROUP BY t.id, tc.id, saldo.saldo_cartera, mora.total_vencido,
                     mora.dias_mora_maximo, mora.docs_vencidos,
                     gc_last.tipo_gestion, gc_last.created_at
            ORDER BY mora.total_vencido DESC NULLS LAST, t.id
            OFFSET :offset LIMIT :limit
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("empresaId", empresaId)
            .addValue("search", searchVal)
            .addValue("offset", page * rows)
            .addValue("limit", rows);

        List<ClienteCarteraDto> list = jdbc.query(sql, p, new BeanPropertyRowMapper<>(ClienteCarteraDto.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, rows), total);
    }

    // ─── Edades de cartera por cliente ───────────────────────────────────────

    public List<EdadCarteraDto> edadesCartera(Integer empresaId) {
        String sql = """
            SELECT
                t.id AS terceroId,
                COALESCE(t.razon_social, t.nombres || ' ' || COALESCE(t.apellidos,'')) AS terceroNombre,
                t.numero_documento AS numeroDocumento,
                COALESCE(SUM(CASE
                    WHEN cc.fecha_vencimiento >= NOW() OR
                         EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) <= 30
                    THEN cc.saldo_pendiente ELSE 0 END), 0) AS corriente,
                COALESCE(SUM(CASE
                    WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) BETWEEN 31 AND 60
                    THEN cc.saldo_pendiente ELSE 0 END), 0) AS dias31a60,
                COALESCE(SUM(CASE
                    WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) BETWEEN 61 AND 90
                    THEN cc.saldo_pendiente ELSE 0 END), 0) AS dias61a90,
                COALESCE(SUM(CASE
                    WHEN EXTRACT(DAY FROM NOW() - cc.fecha_vencimiento) > 90
                    THEN cc.saldo_pendiente ELSE 0 END), 0) AS mas90dias,
                COALESCE(SUM(cc.saldo_pendiente), 0) AS total
            FROM cuentas_cobrar cc
            JOIN tercero t ON t.id = cc.tercero_id
            WHERE cc.empresa_id = :empresaId
              AND cc.estado NOT IN ('pagada','anulada')
              AND cc.deleted_at IS NULL
            GROUP BY t.id, t.razon_social, t.nombres, t.apellidos, t.numero_documento
            HAVING COALESCE(SUM(cc.saldo_pendiente), 0) > 0
            ORDER BY total DESC
            """;
        return jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId),
            new BeanPropertyRowMapper<>(EdadCarteraDto.class));
    }

    // ─── Saldo cartera de un tercero ─────────────────────────────────────────

    public BigDecimal saldoCarteraTercero(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT COALESCE(SUM(saldo_pendiente), 0)
            FROM cuentas_cobrar
            WHERE tercero_id = :terceroId
              AND empresa_id = :empresaId
              AND estado NOT IN ('pagada','anulada')
              AND deleted_at IS NULL
            """;
        BigDecimal val = jdbc.queryForObject(sql,
            new MapSqlParameterSource().addValue("terceroId", terceroId).addValue("empresaId", empresaId),
            BigDecimal.class);
        return val != null ? val : BigDecimal.ZERO;
    }

    // ─── Días de mora máxima de un tercero ───────────────────────────────────

    public int diasMoraMaxima(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT COALESCE(MAX(EXTRACT(DAY FROM NOW() - fecha_vencimiento)::INT), 0)
            FROM cuentas_cobrar
            WHERE tercero_id = :terceroId
              AND empresa_id = :empresaId
              AND fecha_vencimiento < NOW()
              AND estado NOT IN ('pagada','anulada')
              AND deleted_at IS NULL
            """;
        Integer val = jdbc.queryForObject(sql,
            new MapSqlParameterSource().addValue("terceroId", terceroId).addValue("empresaId", empresaId),
            Integer.class);
        return val != null ? val : 0;
    }

    // ─── Pagos consecutivos a tiempo (para motor de aumento) ─────────────────

    public int pagosConsecutivosATiempo(Long terceroId, Integer empresaId) {
        String sql = """
            SELECT COUNT(*)
            FROM (
                SELECT a.fecha_pago, cc.fecha_vencimiento
                FROM abonos_cobrar a
                JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
                WHERE cc.tercero_id = :terceroId AND cc.empresa_id = :empresaId
                ORDER BY a.fecha_pago DESC
                LIMIT 10
            ) pagos
            WHERE pagos.fecha_pago <= pagos.fecha_vencimiento + INTERVAL '1 day'
            """;
        Integer val = jdbc.queryForObject(sql,
            new MapSqlParameterSource().addValue("terceroId", terceroId).addValue("empresaId", empresaId),
            Integer.class);
        return val != null ? val : 0;
    }
}

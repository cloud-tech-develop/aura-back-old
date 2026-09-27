package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroDeudorDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroGrupoDto;
import com.cloud_technological.aura_pos.dto.cartera.tablero.TableroMesDto;

@Repository
public class TableroCarteraQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Cuentas vivas de la empresa: las anuladas no son cartera. */
    private static final String CUENTAS = """
        cuentas AS (
            SELECT cc.id, cc.tercero_id, cc.venta_id,
                   cc.fecha_emision::date AS emision,
                   cc.fecha_vencimiento::date AS vence,
                   COALESCE(cc.total_deuda, 0) AS total_deuda,
                   GREATEST(COALESCE(cc.saldo_pendiente, 0), 0) AS saldo
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
              AND COALESCE(cc.estado, '') <> 'anulada'
        )""";

    /**
     * Foto mensual reconstruida hacia atrás desde el saldo de hoy: al saldo
     * actual se le suman los pagos (abonos y cruces de anticipo) fechados después
     * del corte. Así el mes en curso cuadra exacto con las cuentas, aunque haya
     * cuentas con total_abonado desalineado.
     */
    public List<TableroMesDto> meses(Integer empresaId, int meses) {
        String sql = "WITH " + CUENTAS + """
            , meses AS (
                SELECT m::date AS inicio,
                       (m + INTERVAL '1 month' - INTERVAL '1 day')::date AS fin,
                       LEAST((m + INTERVAL '1 month' - INTERVAL '1 day')::date, CURRENT_DATE) AS corte
                FROM generate_series(date_trunc('month', CURRENT_DATE) - (:meses - 1) * INTERVAL '1 month',
                                     date_trunc('month', CURRENT_DATE), INTERVAL '1 month') m
            ), movs AS (
                SELECT a.cuenta_cobrar_id AS cuenta_id, a.fecha_pago::date AS fecha, a.monto
                FROM abonos_cobrar a
                JOIN cuentas c ON c.id = a.cuenta_cobrar_id
                WHERE a.deleted_at IS NULL
                UNION ALL
                SELECT x.cuenta_cobrar_id, x.fecha, x.monto
                FROM anticipo_cruce x
                JOIN cuentas c ON c.id = x.cuenta_cobrar_id
            ), fotos AS (
                SELECT m.inicio, m.fin, m.corte, c.id, c.vence, c.emision, c.total_deuda,
                       GREATEST(c.saldo + COALESCE((SELECT SUM(v.monto) FROM movs v
                                 WHERE v.cuenta_id = c.id AND v.fecha > m.corte), 0), 0) AS saldo_corte,
                       CASE WHEN c.emision < m.inicio
                            THEN GREATEST(c.saldo + COALESCE((SELECT SUM(v.monto) FROM movs v
                                 WHERE v.cuenta_id = c.id AND v.fecha >= m.inicio), 0), 0)
                            ELSE c.total_deuda END AS saldo_inicio,
                       COALESCE((SELECT SUM(v.monto) FROM movs v
                                 WHERE v.cuenta_id = c.id AND v.fecha BETWEEN m.inicio AND m.corte), 0) AS pagado_mes
                FROM meses m
                JOIN cuentas c ON c.emision <= m.corte
            )
            SELECT inicio AS mes, corte,
                   COALESCE(SUM(saldo_corte), 0) AS saldo_total,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE vence IS NULL OR vence >= corte), 0) AS al_dia,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE corte - vence BETWEEN 1 AND 30), 0) AS dias1a30,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE corte - vence BETWEEN 31 AND 60), 0) AS dias31a60,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE corte - vence BETWEEN 61 AND 90), 0) AS dias61a90,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE corte - vence > 90), 0) AS mas90,
                   COALESCE(SUM(saldo_corte) FILTER (WHERE vence < corte), 0) AS vencido,
                   COALESCE(SUM(pagado_mes), 0) AS recaudado,
                   COALESCE(SUM(total_deuda) FILTER (WHERE emision >= inicio), 0) AS ventas_credito,
                   COALESCE(SUM(total_deuda) FILTER (WHERE emision > corte - 90), 0) AS ventas90,
                   COALESCE(SUM(saldo_inicio) FILTER (WHERE vence IS NOT NULL AND vence <= fin), 0) AS cobrable,
                   COALESCE(SUM(LEAST(pagado_mes, saldo_inicio)) FILTER (WHERE vence IS NOT NULL AND vence <= fin), 0)
                       AS recaudo_cobrable
            FROM (SELECT * FROM fotos
                  UNION ALL
                  -- meses sin cuentas también salen, en cero
                  SELECT inicio, fin, corte, NULL, NULL, NULL, 0, 0, 0, 0 FROM meses) f
            GROUP BY inicio, corte
            ORDER BY inicio
            """;
        return jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId).addValue("meses", meses),
                new BeanPropertyRowMapper<>(TableroMesDto.class));
    }

    public List<TableroDeudorDto> topDeudores(Integer empresaId, int limite) {
        String sql = "WITH " + CUENTAS + """
            SELECT c.tercero_id,
                   MAX(COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos)))) AS tercero_nombre,
                   MAX(t.numero_documento) AS numero_documento,
                   COUNT(*)::INT AS facturas,
                   SUM(c.saldo) AS saldo,
                   COALESCE(SUM(c.saldo) FILTER (WHERE c.vence < CURRENT_DATE), 0) AS vencido,
                   COALESCE(MAX(CURRENT_DATE - c.vence) FILTER (WHERE c.vence < CURRENT_DATE), 0)::INT AS dias_mora_maximo,
                   MAX(tc.score_crediticio) AS score_crediticio
            FROM cuentas c
            JOIN tercero t ON t.id = c.tercero_id
            LEFT JOIN tercero_credito tc ON tc.tercero_id = c.tercero_id AND tc.empresa_id = :empresaId
            WHERE c.saldo > 0
            GROUP BY c.tercero_id
            ORDER BY SUM(c.saldo) DESC
            LIMIT :limite
            """;
        return jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId).addValue("limite", limite),
                new BeanPropertyRowMapper<>(TableroDeudorDto.class));
    }

    /** Clientes con saldo y clientes con algo vencido. */
    public int[] clientes(Integer empresaId) {
        String sql = "WITH " + CUENTAS + """
            SELECT COUNT(DISTINCT tercero_id) FILTER (WHERE saldo > 0) AS con_saldo,
                   COUNT(DISTINCT tercero_id) FILTER (WHERE saldo > 0 AND vence < CURRENT_DATE) AS en_mora
            FROM cuentas
            """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new int[] { rs.getInt("con_saldo"), rs.getInt("en_mora") });
    }

    /**
     * Saldo por quien vendió: el vendedor del pedido si la venta vino de uno, si
     * no el usuario que facturó. Las cuentas creadas a mano van aparte.
     */
    public List<TableroGrupoDto> porVendedor(Integer empresaId) {
        String sql = "WITH " + CUENTAS + """
            SELECT COALESCE(uv.username, uf.username,
                            CASE WHEN c.venta_id IS NULL THEN 'Cuentas manuales' ELSE 'Sin usuario' END) AS nombre,
                   SUM(c.saldo) AS valor,
                   COALESCE(SUM(c.saldo) FILTER (WHERE c.vence < CURRENT_DATE), 0) AS vencido,
                   COUNT(DISTINCT c.tercero_id)::INT AS cantidad
            FROM cuentas c
            LEFT JOIN venta v ON v.id = c.venta_id
            LEFT JOIN usuario uf ON uf.id = v.usuario_id
            LEFT JOIN LATERAL (
                SELECT pv.vendedor_id FROM pedido_vendedor pv WHERE pv.venta_id = c.venta_id LIMIT 1
            ) pv ON true
            LEFT JOIN usuario uv ON uv.id = pv.vendedor_id
            WHERE c.saldo > 0
            GROUP BY 1
            ORDER BY SUM(c.saldo) DESC
            """;
        return jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId),
                new BeanPropertyRowMapper<>(TableroGrupoDto.class));
    }

    /** Recaudo del mes en curso por medio de pago; los cruces de anticipo van aparte. */
    public List<TableroGrupoDto> recaudoPorMedio(Integer empresaId) {
        String sql = """
            SELECT nombre, SUM(monto) AS valor, COUNT(*)::INT AS cantidad
            FROM (
                SELECT COALESCE(NULLIF(UPPER(a.metodo_pago), ''), 'SIN MEDIO') AS nombre, a.monto
                FROM abonos_cobrar a
                JOIN cuentas_cobrar cc ON cc.id = a.cuenta_cobrar_id
                WHERE cc.empresa_id = :empresaId AND a.deleted_at IS NULL
                  AND a.fecha_pago >= date_trunc('month', CURRENT_DATE)
                UNION ALL
                SELECT 'CRUCE DE ANTICIPO', x.monto
                FROM anticipo_cruce x
                WHERE x.empresa_id = :empresaId AND x.cuenta_cobrar_id IS NOT NULL
                  AND x.fecha >= date_trunc('month', CURRENT_DATE)
            ) r
            GROUP BY nombre
            ORDER BY SUM(monto) DESC
            """;
        return jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId),
                new BeanPropertyRowMapper<>(TableroGrupoDto.class));
    }
}

package com.cloud_technological.aura_pos.repositories.cartera;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.CandidatoCastigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Castigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresDetalle;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresMora;

import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;

/**
 * Cartera C5 (intereses de mora) y C9 (deterioro y castigo), V205. Tablas sin
 * entidad JPA: todo por JDBC.
 */
@Repository
@RequiredArgsConstructor
public class MoraCastigoQueryRepository {

    public static final String ORIGEN_INTERES = "INTERES_MORA";
    public static final String ORIGEN_CASTIGO = "CASTIGO_CARTERA";

    private static final String NOMBRE_TERCERO =
            "COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos)))";

    /** Número de la factura de la cuenta, igual que Documentos.numeroVenta. */
    private static final String REFERENCIA_VENTA = """
            CASE WHEN v.id IS NULL THEN NULL
                 WHEN v.consecutivo IS NULL THEN '#' || v.id
                 WHEN COALESCE(TRIM(v.prefijo), '') = '' THEN v.consecutivo::text
                 ELSE TRIM(v.prefijo) || '-' || v.consecutivo END""";

    private final NamedParameterJdbcTemplate jdbc;

    // ── Política ─────────────────────────────────────────────────────────────

    public record PoliticaFila(boolean interesActivo, BigDecimal tasaMoraMensual, BigDecimal tasaUsuraMensual,
            LocalDate usuraVigenteHasta, int diasGracia, String deterioroTramos, int diasSugerirCastigo,
            LocalDateTime updatedAt) {
    }

    public Optional<PoliticaFila> politica(Integer empresaId) {
        return jdbc.query("""
                SELECT interes_activo, tasa_mora_mensual, tasa_usura_mensual, usura_vigente_hasta, dias_gracia,
                       deterioro_tramos, dias_sugerir_castigo, updated_at
                  FROM cartera_politica WHERE empresa_id = :empresaId
                """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> new PoliticaFila(
                rs.getBoolean("interes_activo"), rs.getBigDecimal("tasa_mora_mensual"),
                rs.getBigDecimal("tasa_usura_mensual"),
                rs.getDate("usura_vigente_hasta") != null ? rs.getDate("usura_vigente_hasta").toLocalDate() : null,
                rs.getInt("dias_gracia"), rs.getString("deterioro_tramos"), rs.getInt("dias_sugerir_castigo"),
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null))
                .stream().findFirst();
    }

    public void guardarPolitica(Integer empresaId, PoliticaFila p, Integer usuarioId) {
        jdbc.update("""
                INSERT INTO cartera_politica (empresa_id, interes_activo, tasa_mora_mensual, tasa_usura_mensual,
                       usura_vigente_hasta, dias_gracia, deterioro_tramos, dias_sugerir_castigo, usuario_id, updated_at)
                VALUES (:empresaId, :activo, :tasa, :usura, :usuraHasta, :gracia, :tramos, :diasCastigo, :usuarioId, now())
                ON CONFLICT (empresa_id) DO UPDATE SET
                       interes_activo = EXCLUDED.interes_activo, tasa_mora_mensual = EXCLUDED.tasa_mora_mensual,
                       tasa_usura_mensual = EXCLUDED.tasa_usura_mensual,
                       usura_vigente_hasta = EXCLUDED.usura_vigente_hasta, dias_gracia = EXCLUDED.dias_gracia,
                       deterioro_tramos = EXCLUDED.deterioro_tramos,
                       dias_sugerir_castigo = EXCLUDED.dias_sugerir_castigo,
                       usuario_id = EXCLUDED.usuario_id, updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("activo", p.interesActivo())
                .addValue("tasa", p.tasaMoraMensual())
                .addValue("usura", p.tasaUsuraMensual())
                .addValue("usuraHasta", p.usuraVigenteHasta() != null ? Date.valueOf(p.usuraVigenteHasta()) : null)
                .addValue("gracia", p.diasGracia())
                .addValue("tramos", p.deterioroTramos())
                .addValue("diasCastigo", p.diasSugerirCastigo())
                .addValue("usuarioId", usuarioId));
    }

    // ── Intereses de mora ────────────────────────────────────────────────────

    /** Factura vencida con saldo, candidata a interés. */
    public record CuentaMora(Long cuentaCobrarId, String numeroCuenta, String referencia, Long terceroId,
            String terceroNombre, String documento, LocalDate fechaVencimiento, BigDecimal saldo,
            LocalDate ultimoCorte) {
    }

    /**
     * Cuentas vencidas a la fecha. No entran las cuentas de intereses (no se cobra
     * interés sobre interés, art. 886 C.Co.), ni las castigadas o anuladas.
     * {@code ultimoCorte} es el último día ya facturado de esa cuenta.
     */
    public List<CuentaMora> cuentasVencidas(Integer empresaId, Long terceroId, LocalDate fecha) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("fecha", Date.valueOf(fecha));
        String filtro = "";
        if (terceroId != null) {
            filtro = " AND cc.tercero_id = :terceroId";
            p.addValue("terceroId", terceroId);
        }
        return jdbc.query("""
                SELECT cc.id, cc.numero_cuenta, %s AS referencia, cc.tercero_id, %s AS tercero_nombre,
                       t.numero_documento, cc.fecha_vencimiento::date AS vence, cc.saldo_pendiente,
                       (SELECT MAX(d.hasta) FROM interes_mora_detalle d
                          JOIN interes_mora i ON i.id = d.interes_mora_id
                         WHERE d.cuenta_cobrar_id = cc.id AND i.estado = 'FACTURADO') AS ultimo_corte
                  FROM cuentas_cobrar cc
                  JOIN tercero t ON t.id = cc.tercero_id
                  LEFT JOIN venta v ON v.id = cc.venta_id
                 WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
                   AND cc.estado IN ('activa', 'parcial') AND cc.saldo_pendiente > 0
                   AND cc.fecha_vencimiento IS NOT NULL AND cc.fecha_vencimiento::date < :fecha
                   AND NOT EXISTS (SELECT 1 FROM interes_mora im
                                    WHERE im.cuenta_cobrar_id = cc.id AND im.empresa_id = cc.empresa_id)
                """.formatted(REFERENCIA_VENTA, NOMBRE_TERCERO) + filtro + """
                 ORDER BY tercero_nombre, cc.fecha_vencimiento
                """, p, (rs, i) -> new CuentaMora(rs.getLong("id"), rs.getString("numero_cuenta"),
                rs.getString("referencia"), rs.getLong("tercero_id"), rs.getString("tercero_nombre"),
                rs.getString("numero_documento"), rs.getDate("vence").toLocalDate(),
                rs.getBigDecimal("saldo_pendiente"),
                rs.getDate("ultimo_corte") != null ? rs.getDate("ultimo_corte").toLocalDate() : null));
    }

    /** Serializa los consecutivos de la empresa en la tabla dada hasta el commit. */
    private int siguienteConsecutivo(String tabla, Integer empresaId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(:clave))",
                new MapSqlParameterSource("clave", tabla + ":" + empresaId), Object.class);
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(consecutivo), 0) + 1 FROM " + tabla + " WHERE empresa_id = :empresaId",
                new MapSqlParameterSource("empresaId", empresaId), Integer.class);
        return n != null ? n : 1;
    }

    public int siguienteConsecutivoInteres(Integer empresaId) {
        return siguienteConsecutivo("interes_mora", empresaId);
    }

    public int siguienteConsecutivoCastigo(Integer empresaId) {
        return siguienteConsecutivo("cartera_castigo", empresaId);
    }

    public Long insertarInteres(Integer empresaId, Long terceroId, String numero, int consecutivo, LocalDate fecha,
            BigDecimal tasa, BigDecimal valor, String observaciones, Integer usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO interes_mora (empresa_id, tercero_id, numero, consecutivo, fecha, tasa_mensual, valor,
                       observaciones, usuario_id)
                VALUES (:empresaId, :terceroId, :numero, :consecutivo, :fecha, :tasa, :valor, :obs, :usuarioId)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId).addValue("terceroId", terceroId)
                .addValue("numero", numero).addValue("consecutivo", consecutivo)
                .addValue("fecha", Date.valueOf(fecha)).addValue("tasa", tasa).addValue("valor", valor)
                .addValue("obs", observaciones).addValue("usuarioId", usuarioId), Long.class);
    }

    public void asignarCuentaInteres(Long interesId, Long cuentaCobrarId) {
        jdbc.update("UPDATE interes_mora SET cuenta_cobrar_id = :cc WHERE id = :id",
                new MapSqlParameterSource("cc", cuentaCobrarId).addValue("id", interesId));
    }

    public void insertarDetalle(Long interesId, Long cuentaCobrarId, LocalDate desde, LocalDate hasta, int dias,
            BigDecimal saldoBase, BigDecimal valor) {
        jdbc.update("""
                INSERT INTO interes_mora_detalle (interes_mora_id, cuenta_cobrar_id, desde, hasta, dias, saldo_base, valor)
                VALUES (:interesId, :cc, :desde, :hasta, :dias, :saldo, :valor)
                """, new MapSqlParameterSource()
                .addValue("interesId", interesId).addValue("cc", cuentaCobrarId)
                .addValue("desde", Date.valueOf(desde)).addValue("hasta", Date.valueOf(hasta))
                .addValue("dias", dias).addValue("saldo", saldoBase).addValue("valor", valor));
    }

    private static final String SELECT_INTERES = """
            SELECT i.id, i.numero, i.fecha, i.tercero_id, %s AS tercero_nombre, t.numero_documento AS documento,
                   i.tasa_mensual, i.valor, i.cuenta_cobrar_id, cc.numero_cuenta, cc.saldo_pendiente AS saldo_cuenta,
                   i.estado, i.observaciones, i.motivo_anulacion, u.username AS usuario_nombre, i.created_at,
                   COUNT(*) OVER() AS total_rows
              FROM interes_mora i
              JOIN tercero t ON t.id = i.tercero_id
              LEFT JOIN cuentas_cobrar cc ON cc.id = i.cuenta_cobrar_id
              LEFT JOIN usuario u ON u.id = i.usuario_id
            """.formatted(NOMBRE_TERCERO);

    public PageImpl<InteresMora> listarIntereses(Integer empresaId, Long terceroId, String search, int page,
            int rows) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("limit", rows).addValue("offset", page * rows);
        StringBuilder sql = new StringBuilder(SELECT_INTERES).append(" WHERE i.empresa_id = :empresaId");
        if (terceroId != null) {
            sql.append(" AND i.tercero_id = :terceroId");
            p.addValue("terceroId", terceroId);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (i.numero ILIKE :q OR t.numero_documento ILIKE :q OR t.razon_social ILIKE :q"
                    + " OR CONCAT(t.nombres, ' ', t.apellidos) ILIKE :q)");
            p.addValue("q", "%" + search.trim() + "%");
        }
        sql.append(" ORDER BY i.fecha DESC, i.id DESC LIMIT :limit OFFSET :offset");
        long[] total = {0};
        List<InteresMora> list = jdbc.query(sql.toString(), p, (rs, i) -> {
            total[0] = rs.getLong("total_rows");
            return new BeanPropertyRowMapper<>(InteresMora.class).mapRow(rs, i);
        });
        return new PageImpl<>(list, PageRequest.of(page, Math.max(rows, 1)), total[0]);
    }

    public Optional<InteresMora> interes(Long id, Integer empresaId) {
        return jdbc.query(SELECT_INTERES + " WHERE i.id = :id AND i.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(InteresMora.class)).stream().findFirst();
    }

    public List<InteresDetalle> detalleInteres(Long interesId) {
        return jdbc.query("""
                SELECT d.cuenta_cobrar_id, cc.numero_cuenta, d.desde, d.hasta, d.dias, d.saldo_base, d.valor
                  FROM interes_mora_detalle d
                  JOIN cuentas_cobrar cc ON cc.id = d.cuenta_cobrar_id
                 WHERE d.interes_mora_id = :id
                 ORDER BY d.id
                """, new MapSqlParameterSource("id", interesId), new BeanPropertyRowMapper<>(InteresDetalle.class));
    }

    public void anularInteres(Long id, Integer usuarioId, String motivo) {
        jdbc.update("""
                UPDATE interes_mora SET estado = 'ANULADO', anulado_por = :usuarioId, anulado_at = now(),
                       motivo_anulacion = :motivo
                 WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("usuarioId", usuarioId).addValue("motivo", motivo));
    }

    /** Interés de mora facturado y aún sin pagar del cliente (para la ficha). */
    public BigDecimal interesPendienteCliente(Integer empresaId, Long terceroId) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(cc.saldo_pendiente), 0)
                  FROM interes_mora i JOIN cuentas_cobrar cc ON cc.id = i.cuenta_cobrar_id
                 WHERE i.empresa_id = :empresaId AND i.tercero_id = :terceroId AND i.estado = 'FACTURADO'
                   AND cc.deleted_at IS NULL AND cc.estado NOT IN ('pagada', 'anulada', 'castigada')
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("terceroId", terceroId),
                BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Para el asiento: valor, fecha, tercero y número. */
    public record InteresContable(String numero, LocalDate fecha, Long terceroId, String terceroNombre,
            BigDecimal valor) {
    }

    public Optional<InteresContable> interesContable(Long id, Integer empresaId) {
        return jdbc.query("""
                SELECT i.numero, i.fecha, i.tercero_id, %s AS tercero_nombre, i.valor
                  FROM interes_mora i JOIN tercero t ON t.id = i.tercero_id
                 WHERE i.id = :id AND i.empresa_id = :empresaId
                """.formatted(NOMBRE_TERCERO), new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                (rs, n) -> new InteresContable(rs.getString("numero"), rs.getDate("fecha").toLocalDate(),
                        rs.getLong("tercero_id"), rs.getString("tercero_nombre"), rs.getBigDecimal("valor")))
                .stream().findFirst();
    }

    // ── Deterioro ────────────────────────────────────────────────────────────

    /** Saldo crédito (crédito − débito) de las cuentas que empiezan por el prefijo. */
    public BigDecimal saldoCreditoPrefijo(Integer empresaId, String prefijo) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ad.credito - ad.debito), 0)
                  FROM asiento_detalle ad
                  JOIN asiento_contable a ON a.id = ad.asiento_id
                  JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                 WHERE a.empresa_id = :empresaId AND a.estado = 'CONTABILIZADO' AND pc.codigo LIKE :prefijo
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("prefijo", prefijo + "%"),
                BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Propuestas de deterioro que siguen en borrador: aún no están en el mayor. */
    public long deteriorosEnBorrador(Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM asiento_contable
                 WHERE empresa_id = :empresaId AND tipo_origen = 'DETERIORO' AND estado = 'BORRADOR'
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n != null ? n : 0;
    }

    // ── Castigo ──────────────────────────────────────────────────────────────

    public List<CandidatoCastigo> candidatosCastigo(Integer empresaId, int diasMinimos, Long terceroId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId).addValue("dias", diasMinimos);
        String filtro = "";
        if (terceroId != null) {
            filtro = " AND cc.tercero_id = :terceroId";
            p.addValue("terceroId", terceroId);
        }
        return jdbc.query("""
                SELECT cc.id AS cuenta_cobrar_id, cc.numero_cuenta, %s AS referencia, cc.tercero_id,
                       %s AS tercero_nombre, t.numero_documento AS documento,
                       cc.fecha_vencimiento::date AS fecha_vencimiento,
                       (CURRENT_DATE - cc.fecha_vencimiento::date) AS dias_mora, cc.saldo_pendiente AS saldo
                  FROM cuentas_cobrar cc
                  JOIN tercero t ON t.id = cc.tercero_id
                  LEFT JOIN venta v ON v.id = cc.venta_id
                 WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
                   AND cc.estado IN ('activa', 'parcial') AND cc.saldo_pendiente > 0
                   AND cc.fecha_vencimiento IS NOT NULL
                   AND (CURRENT_DATE - cc.fecha_vencimiento::date) >= :dias
                """.formatted(REFERENCIA_VENTA, NOMBRE_TERCERO) + filtro + """
                 ORDER BY dias_mora DESC, cc.saldo_pendiente DESC
                """, p, new BeanPropertyRowMapper<>(CandidatoCastigo.class));
    }

    public Long insertarCastigo(Integer empresaId, Long cuentaCobrarId, Long terceroId, String numero,
            int consecutivo, LocalDate fecha, BigDecimal valor, BigDecimal contraProvision, BigDecimal contraGasto,
            int diasMora, String motivo, String estadoAnterior, Integer usuarioId) {
        return jdbc.queryForObject("""
                INSERT INTO cartera_castigo (empresa_id, cuenta_cobrar_id, tercero_id, numero, consecutivo, fecha,
                       valor, contra_provision, contra_gasto, dias_mora, motivo, estado_anterior, usuario_id)
                VALUES (:empresaId, :cc, :terceroId, :numero, :consecutivo, :fecha, :valor, :provision, :gasto,
                       :dias, :motivo, :estadoAnterior, :usuarioId)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId).addValue("cc", cuentaCobrarId).addValue("terceroId", terceroId)
                .addValue("numero", numero).addValue("consecutivo", consecutivo).addValue("fecha", Date.valueOf(fecha))
                .addValue("valor", valor).addValue("provision", contraProvision).addValue("gasto", contraGasto)
                .addValue("dias", diasMora).addValue("motivo", motivo).addValue("estadoAnterior", estadoAnterior)
                .addValue("usuarioId", usuarioId), Long.class);
    }

    /** Saca la cuenta de la cartera activa sin borrar su historial de abonos. */
    public void marcarCastigada(Long cuentaCobrarId, BigDecimal valor) {
        jdbc.update("""
                UPDATE cuentas_cobrar SET estado = 'castigada', valor_castigado = :valor, saldo_pendiente = 0,
                       updated_at = now()
                 WHERE id = :id
                """, new MapSqlParameterSource("id", cuentaCobrarId).addValue("valor", valor));
    }

    /** Anular el castigo: la cuenta vuelve con su saldo y su estado anterior. */
    public void restaurarCastigada(Long cuentaCobrarId, BigDecimal valor, String estadoAnterior) {
        jdbc.update("""
                UPDATE cuentas_cobrar SET estado = :estado, valor_castigado = 0, saldo_pendiente = :valor,
                       updated_at = now()
                 WHERE id = :id AND estado = 'castigada'
                """, new MapSqlParameterSource("id", cuentaCobrarId).addValue("valor", valor)
                .addValue("estado", estadoAnterior != null ? estadoAnterior : "activa"));
    }

    private static final String SELECT_CASTIGO = """
            SELECT k.id, k.numero, k.fecha, k.cuenta_cobrar_id, cc.numero_cuenta, k.tercero_id,
                   %s AS tercero_nombre, t.numero_documento AS documento, k.valor, k.contra_provision,
                   k.contra_gasto, k.dias_mora, k.motivo, k.estado, k.motivo_anulacion,
                   u.username AS usuario_nombre, k.created_at, k.estado_anterior,
                   COUNT(*) OVER() AS total_rows
              FROM cartera_castigo k
              JOIN tercero t ON t.id = k.tercero_id
              JOIN cuentas_cobrar cc ON cc.id = k.cuenta_cobrar_id
              LEFT JOIN usuario u ON u.id = k.usuario_id
            """.formatted(NOMBRE_TERCERO);

    public PageImpl<Castigo> listarCastigos(Integer empresaId, String search, int page, int rows) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("limit", rows).addValue("offset", page * rows);
        StringBuilder sql = new StringBuilder(SELECT_CASTIGO).append(" WHERE k.empresa_id = :empresaId");
        if (search != null && !search.isBlank()) {
            sql.append(" AND (k.numero ILIKE :q OR cc.numero_cuenta ILIKE :q OR t.numero_documento ILIKE :q"
                    + " OR t.razon_social ILIKE :q OR CONCAT(t.nombres, ' ', t.apellidos) ILIKE :q)");
            p.addValue("q", "%" + search.trim() + "%");
        }
        sql.append(" ORDER BY k.fecha DESC, k.id DESC LIMIT :limit OFFSET :offset");
        long[] total = {0};
        List<Castigo> list = jdbc.query(sql.toString(), p, (rs, i) -> {
            total[0] = rs.getLong("total_rows");
            return new BeanPropertyRowMapper<>(Castigo.class).mapRow(rs, i);
        });
        return new PageImpl<>(list, PageRequest.of(page, Math.max(rows, 1)), total[0]);
    }

    public record CastigoFila(Castigo castigo, String estadoAnterior) {
    }

    public Optional<CastigoFila> castigo(Long id, Integer empresaId) {
        return jdbc.query(SELECT_CASTIGO + " WHERE k.id = :id AND k.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                (rs, i) -> new CastigoFila(new BeanPropertyRowMapper<>(Castigo.class).mapRow(rs, i),
                        rs.getString("estado_anterior")))
                .stream().findFirst();
    }

    public void anularCastigo(Long id, Integer usuarioId, String motivo) {
        jdbc.update("""
                UPDATE cartera_castigo SET estado = 'ANULADO', anulado_por = :usuarioId, anulado_at = now(),
                       motivo_anulacion = :motivo
                 WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("usuarioId", usuarioId).addValue("motivo", motivo));
    }

    /** Para el asiento del castigo. */
    public record CastigoContable(String numero, LocalDate fecha, Long terceroId, String numeroCuenta,
            BigDecimal valor, BigDecimal contraProvision, BigDecimal contraGasto) {
    }

    public Optional<CastigoContable> castigoContable(Long id, Integer empresaId) {
        return jdbc.query("""
                SELECT k.numero, k.fecha, k.tercero_id, cc.numero_cuenta, k.valor, k.contra_provision, k.contra_gasto
                  FROM cartera_castigo k JOIN cuentas_cobrar cc ON cc.id = k.cuenta_cobrar_id
                 WHERE k.id = :id AND k.empresa_id = :empresaId
                """, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                (rs, n) -> new CastigoContable(rs.getString("numero"), rs.getDate("fecha").toLocalDate(),
                        rs.getLong("tercero_id"), rs.getString("numero_cuenta"), rs.getBigDecimal("valor"),
                        rs.getBigDecimal("contra_provision"), rs.getBigDecimal("contra_gasto")))
                .stream().findFirst();
    }
}

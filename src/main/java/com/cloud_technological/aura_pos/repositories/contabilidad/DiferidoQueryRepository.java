package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de los diferidos (V185) y de lo que una compra crea según la
 * clasificación de sus líneas: diferidos y fichas de activo.
 */
@Repository
public class DiferidoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Diferidos vigentes con cuotas por generar (todas las empresas: lo corre el scheduler). */
    public List<Long> vigentesConCuotasPendientes() {
        String sql = """
            SELECT d.id
              FROM diferido d
             WHERE d.estado = 'VIGENTE'
               AND (SELECT COUNT(*) FROM diferido_amortizacion a WHERE a.diferido_id = d.id) < d.meses
             ORDER BY d.id
            """;
        return jdbc.queryForList(sql, new MapSqlParameterSource(), Long.class);
    }

    public long cuotasGeneradas(Long diferidoId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM diferido_amortizacion WHERE diferido_id = :id",
                new MapSqlParameterSource("id", diferidoId), Long.class);
        return n != null ? n : 0;
    }

    public boolean cuotaDelPeriodo(Long diferidoId, String periodo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM diferido_amortizacion
                            WHERE diferido_id = :id AND periodo = :periodo)
            """, new MapSqlParameterSource().addValue("id", diferidoId).addValue("periodo", periodo),
                Boolean.class));
    }

    /** Diferidos vivos que creó un documento (la compra que se anula o se edita). */
    public List<Long> deOrigen(Integer empresaId, String origenTipo, Long origenId) {
        return jdbc.queryForList("""
            SELECT id FROM diferido
             WHERE empresa_id = :empresaId AND origen_tipo = :tipo AND origen_id = :origenId
               AND estado <> 'ANULADO'
            """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("tipo", origenTipo)
                .addValue("origenId", origenId), Long.class);
    }

    /** Fichas de activo vivas que creó la compra. */
    public List<Long> activosDeCompra(Integer empresaId, Long compraId) {
        return jdbc.queryForList("""
            SELECT id FROM activo_fijo
             WHERE empresa_id = :empresaId AND compra_id = :compraId AND deleted_at IS NULL
            """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("compraId", compraId), Long.class);
    }

    /**
     * Cuántas fichas de la compra ya tienen historia propia (depreciación,
     * baja o venta). Con alguna, la compra no se puede anular ni editar: el
     * activo ya vive por su cuenta.
     */
    public long activosDeCompraConMovimiento(Integer empresaId, Long compraId) {
        Long n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM activo_fijo a
             WHERE a.empresa_id = :empresaId AND a.compra_id = :compraId AND a.deleted_at IS NULL
               AND (a.estado <> 'ACTIVO'
                    OR a.depreciacion_acumulada <> 0
                    OR EXISTS (SELECT 1 FROM depreciacion_periodo d WHERE d.activo_id = a.id))
            """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("compraId", compraId), Long.class);
        return n != null ? n : 0;
    }

    /** Cuotas ya generadas de los diferidos vivos que creó el documento. */
    public long cuotasDeOrigen(Integer empresaId, String origenTipo, Long origenId) {
        Long n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM diferido_amortizacion a
              JOIN diferido d ON d.id = a.diferido_id
             WHERE d.empresa_id = :empresaId AND d.origen_tipo = :tipo AND d.origen_id = :origenId
               AND d.estado <> 'ANULADO'
            """, new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("tipo", origenTipo)
                .addValue("origenId", origenId), Long.class);
        return n != null ? n : 0;
    }

    /**
     * Siguiente número de ficha "AF-000123" de la empresa. Bloquea la
     * numeración hasta el fin de la transacción: dos compras al tiempo no
     * pueden tomar el mismo código.
     */
    public long siguienteConsecutivoActivo(Integer empresaId) {
        jdbc.queryForObject("SELECT COUNT(*) FROM pg_advisory_xact_lock(185185, CAST(:empresaId AS INTEGER))",
                new MapSqlParameterSource("empresaId", empresaId), Long.class);
        Long n = jdbc.queryForObject("""
            SELECT COALESCE(MAX(NULLIF(regexp_replace(codigo, '^AF-', ''), '')::bigint), 0) + 1
              FROM activo_fijo
             WHERE empresa_id = :empresaId AND codigo ~ '^AF-[0-9]+$'
            """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n != null ? n : 1;
    }
}

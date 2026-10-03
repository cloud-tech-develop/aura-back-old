package com.cloud_technological.aura_pos.repositories.documento;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.documento.DocumentoRelacionDtos.Relacionado;

/** Lectura de la cadena documental (fase D0). */
@Repository
public class DocumentoRelacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Cantidad aplicada (VIGENTE) sobre una línea origen. Nunca null. */
    public BigDecimal aplicadoCantidad(String origenTipo, Long origenLineaId) {
        String sql = """
            SELECT COALESCE(SUM(cantidad), 0) FROM documento_relacion
            WHERE origen_tipo = :tipo AND origen_linea_id = :linea AND estado = 'VIGENTE'
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tipo", origenTipo)
                .addValue("linea", origenLineaId);
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Valor aplicado (VIGENTE) sobre una línea origen. Nunca null. */
    public BigDecimal aplicadoValor(String origenTipo, Long origenLineaId) {
        String sql = """
            SELECT COALESCE(SUM(valor), 0) FROM documento_relacion
            WHERE origen_tipo = :tipo AND origen_linea_id = :linea AND estado = 'VIGENTE'
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tipo", origenTipo)
                .addValue("linea", origenLineaId);
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Cantidad aplicada (VIGENTE) por cada línea origen, en una sola consulta.
     * Evita el N+1 cuando se resuelve el pendiente de todas las líneas de un
     * documento. Las líneas sin aplicaciones no aparecen en el mapa.
     */
    public Map<Long, BigDecimal> aplicadoCantidadPorLineas(String origenTipo, List<Long> origenLineaIds) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (origenLineaIds == null || origenLineaIds.isEmpty()) return out;
        String sql = """
            SELECT origen_linea_id, COALESCE(SUM(cantidad), 0) AS aplicado
            FROM documento_relacion
            WHERE origen_tipo = :tipo AND origen_linea_id IN (:lineas) AND estado = 'VIGENTE'
            GROUP BY origen_linea_id
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tipo", origenTipo)
                .addValue("lineas", origenLineaIds);
        jdbc.query(sql, p, rs -> {
            out.put(rs.getLong("origen_linea_id"), rs.getBigDecimal("aplicado"));
        });
        return out;
    }

    /** Ids de las relaciones VIGENTE de un documento destino (para anularlas). */
    public List<Long> idsVigentesPorDestino(String destinoTipo, Long destinoId) {
        String sql = """
            SELECT id FROM documento_relacion
            WHERE destino_tipo = :tipo AND destino_id = :id AND estado = 'VIGENTE'
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tipo", destinoTipo)
                .addValue("id", destinoId);
        return jdbc.queryForList(sql, p, Long.class);
    }

    /** Documentos origen de un tipo dado con relación VIGENTE hacia este destino. */
    public List<Long> origenesVigentes(String destinoTipo, Long destinoId, String origenTipo) {
        String sql = """
            SELECT DISTINCT origen_id FROM documento_relacion
            WHERE destino_tipo = :destinoTipo AND destino_id = :destinoId
              AND origen_tipo = :origenTipo AND estado = 'VIGENTE'
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("destinoTipo", destinoTipo)
                .addValue("destinoId", destinoId)
                .addValue("origenTipo", origenTipo);
        return jdbc.queryForList(sql, p, Long.class);
    }

    /** A dónde fue este documento: relaciones donde es ORIGEN (el otro extremo es el destino). */
    public List<Relacionado> haciaAdelante(Integer empresaId, String tipo, Long id) {
        return jdbc.query(relacionados("destino", "origen"), params(empresaId, tipo, id),
                (rs, i) -> mapRelacionado(rs));
    }

    /** De dónde viene este documento: relaciones donde es DESTINO (el otro extremo es el origen). */
    public List<Relacionado> haciaAtras(Integer empresaId, String tipo, Long id) {
        return jdbc.query(relacionados("origen", "destino"), params(empresaId, tipo, id),
                (rs, i) -> mapRelacionado(rs));
    }

    /**
     * Relaciones vistas desde un documento. {@code otro} es el extremo que se
     * muestra y {@code propio} el del documento consultado. Trae también el
     * número visible del otro documento para los tipos que ya existen.
     */
    private static String relacionados(String otro, String propio) {
        return """
            SELECT r.id AS relacion_id, r.%1$s_tipo AS tipo, r.%1$s_id AS id,
                   r.%1$s_linea_id AS linea_id, r.%2$s_linea_id AS linea_propia_id,
                   r.cantidad, r.valor, r.estado, r.created_at,
                   CASE r.%1$s_tipo
                       WHEN 'COTIZACION' THEN cot.numero
                       WHEN 'VENTA' THEN CASE
                           WHEN NULLIF(TRIM(v.prefijo), '') IS NOT NULL THEN CONCAT(v.prefijo, '-', v.consecutivo)
                           ELSE CAST(v.consecutivo AS TEXT)
                       END
                       WHEN 'COMPRA' THEN co.numero_compra
                   END AS numero
            FROM documento_relacion r
            LEFT JOIN cotizacion cot ON r.%1$s_tipo = 'COTIZACION' AND cot.id = r.%1$s_id
            LEFT JOIN venta v        ON r.%1$s_tipo = 'VENTA'      AND v.id   = r.%1$s_id
            LEFT JOIN compra co      ON r.%1$s_tipo = 'COMPRA'     AND co.id  = r.%1$s_id
            WHERE r.empresa_id = :empresaId AND r.%2$s_tipo = :tipo AND r.%2$s_id = :id
            ORDER BY r.created_at, r.id
            """.formatted(otro, propio);
    }

    private MapSqlParameterSource params(Integer empresaId, String tipo, Long id) {
        return new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("tipo", tipo)
                .addValue("id", id);
    }

    private Relacionado mapRelacionado(java.sql.ResultSet rs) throws java.sql.SQLException {
        Relacionado r = new Relacionado();
        r.setRelacionId(rs.getLong("relacion_id"));
        r.setTipo(rs.getString("tipo"));
        r.setId(rs.getLong("id"));
        r.setLineaId((Long) rs.getObject("linea_id", Long.class));
        r.setLineaPropiaId((Long) rs.getObject("linea_propia_id", Long.class));
        r.setCantidad(rs.getBigDecimal("cantidad"));
        r.setValor(rs.getBigDecimal("valor"));
        r.setEstado(rs.getString("estado"));
        r.setNumero(rs.getString("numero"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        r.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return r;
    }

}

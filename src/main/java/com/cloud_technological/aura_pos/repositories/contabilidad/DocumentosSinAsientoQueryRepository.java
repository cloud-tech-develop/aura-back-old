package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.DocumentoSinAsientoDto;

/**
 * Documentos operativos cuyo reflejo en el mayor no es exactamente un asiento.
 *
 * <p>Vigentes = asientos originales (CONTABILIZADO o BORRADOR) menos sus
 * reversas no anuladas; es la misma cuenta que hace
 * {@code ContabilidadAutoServiceImpl.asientoVigente}. Un documento vivo debe
 * tener 1: con 0 falta en el mayor (el posting falló después del commit) y con
 * más de 1 está contado de más (evento duplicado).
 */
@Repository
public class DocumentosSinAsientoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<DocumentoSinAsientoDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        String sql = """
            WITH docs AS (
                SELECT 'VENTA' AS tipo, v.id, v.fecha_emision::date AS fecha,
                       COALESCE(v.prefijo, '') || COALESCE(v.consecutivo::text, v.id::text) AS numero
                FROM venta v
                WHERE v.empresa_id = :empresaId AND v.estado_venta <> 'ANULADA'
                  AND v.fecha_emision::date BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'COMPRA', c.id, c.fecha::date, 'Compra #' || c.id
                FROM compra c
                WHERE c.empresa_id = :empresaId AND c.estado <> 'ANULADA'
                  AND c.fecha::date BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'GASTO', g.id, g.fecha, 'Gasto #' || g.id
                FROM gasto g
                WHERE g.empresa_id = :empresaId AND g.estado NOT IN ('ANULADO', 'ANULADA', 'ELIMINADO')
                  AND g.fecha BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'DEVOLUCION', d.id, d.fecha_devolucion, 'Devolución #' || COALESCE(d.consecutivo::text, d.id::text)
                FROM devolucion d
                WHERE d.empresa_id = :empresaId AND d.estado <> 'ANULADA'
                  AND d.fecha_devolucion BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'MERMA', m.id, m.fecha::date, 'Merma #' || m.id
                FROM merma m
                WHERE m.empresa_id = :empresaId AND m.estado = 'APROBADA'
                  AND m.fecha::date BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'OBSEQUIO', o.id, o.fecha::date, 'Obsequio #' || o.id
                FROM obsequio o
                WHERE o.empresa_id = :empresaId AND o.estado = 'APROBADO'
                  AND o.fecha::date BETWEEN :desde AND :hasta
                UNION ALL
                SELECT 'CONSUMO_INTERNO', ci.id, ci.fecha::date, 'Consumo interno #' || ci.id
                FROM consumo_interno ci
                WHERE ci.empresa_id = :empresaId AND ci.estado = 'APROBADO'
                  AND ci.fecha::date BETWEEN :desde AND :hasta
            ),
            originales AS (
                SELECT tipo_origen, origen_id, COUNT(*) AS n
                FROM asiento_contable
                WHERE empresa_id = :empresaId AND estado IN ('CONTABILIZADO', 'BORRADOR')
                  AND tipo_origen NOT LIKE 'ANULACION\\_%'
                GROUP BY 1, 2
            ),
            reversas AS (
                SELECT SUBSTRING(tipo_origen FROM 11) AS tipo_origen, origen_id, COUNT(*) AS n
                FROM asiento_contable
                WHERE empresa_id = :empresaId AND estado <> 'ANULADO'
                  AND tipo_origen LIKE 'ANULACION\\_%'
                GROUP BY 1, 2
            )
            SELECT d.tipo, d.id, d.fecha, d.numero,
                   COALESCE(o.n, 0) - COALESCE(r.n, 0) AS vigentes
            FROM docs d
            LEFT JOIN originales o ON o.tipo_origen = d.tipo AND o.origen_id = d.id
            LEFT JOIN reversas   r ON r.tipo_origen = d.tipo AND r.origen_id = d.id
            WHERE COALESCE(o.n, 0) - COALESCE(r.n, 0) <> 1
            ORDER BY d.fecha DESC, d.tipo, d.id
            LIMIT 500
            """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        return jdbc.query(sql, p, (rs, i) -> new DocumentoSinAsientoDto(
                rs.getString("tipo"),
                rs.getLong("id"),
                rs.getObject("fecha", LocalDate.class),
                rs.getString("numero"),
                rs.getInt("vigentes")));
    }
}

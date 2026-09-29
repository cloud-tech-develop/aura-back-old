package com.cloud_technological.aura_pos.repositories.importacion;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Búsquedas en lote para validar un archivo completo sin una consulta por fila. */
@Repository
public class ImportacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public record Cuenta(Long id, String codigo, boolean auxiliar, boolean activa, boolean conMovimientos) {
    }

    /** El plan de cuentas completo de la empresa, por código. */
    public Map<String, Cuenta> cuentas(Integer empresaId) {
        Map<String, Cuenta> out = new HashMap<>();
        jdbc.query("""
            SELECT pc.id, pc.codigo, pc.auxiliar, pc.activa,
                   EXISTS (SELECT 1 FROM asiento_detalle d WHERE d.cuenta_id = pc.id) AS con_mov
            FROM plan_cuenta pc WHERE pc.empresa_id = :empresaId
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                out.put(rs.getString("codigo"), new Cuenta(rs.getLong("id"), rs.getString("codigo"),
                        rs.getBoolean("auxiliar"), rs.getBoolean("activa"), rs.getBoolean("con_mov")));
            });
        return out;
    }

    /** Terceros por número de documento (sin puntos ni DV). El más antiguo si hay repetidos. */
    public Map<String, Long> terceros(Integer empresaId, Collection<String> documentos) {
        Map<String, Long> out = new HashMap<>();
        if (documentos.isEmpty()) return out;
        jdbc.query("""
            SELECT DISTINCT ON (numero_documento) numero_documento, id
            FROM tercero
            WHERE empresa_id = :empresaId AND deleted_at IS NULL
              AND numero_documento IN (:docs)
            ORDER BY numero_documento, id
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("docs", documentos), rs -> {
                out.put(rs.getString("numero_documento"), rs.getLong("id"));
            });
        return out;
    }

    public record Municipio(Long id, String codigo, String nombre) {
    }

    /** Todos los municipios: el archivo puede traer código DANE o nombre. */
    public List<Municipio> municipios() {
        return jdbc.query("SELECT id, codigo, nombre FROM municipios",
                new MapSqlParameterSource(), (rs, i) -> new Municipio(rs.getLong("id"),
                        rs.getString("codigo"), rs.getString("nombre")));
    }

    /** Saldo contable por tercero en una cuenta (para cruzar la cartera con la apertura). */
    public Map<Long, BigDecimal> saldoPorTercero(Integer empresaId, Long cuentaId) {
        Map<Long, BigDecimal> out = new HashMap<>();
        jdbc.query("""
            SELECT ad.tercero_id, SUM(ad.debito - ad.credito) AS saldo
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            WHERE a.empresa_id = :empresaId AND a.estado = 'CONTABILIZADO'
              AND ad.cuenta_id = :cuentaId AND ad.tercero_id IS NOT NULL
            GROUP BY ad.tercero_id
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("cuentaId", cuentaId), rs -> {
                out.put(rs.getLong("tercero_id"), rs.getBigDecimal("saldo"));
            });
        return out;
    }

    /** Facturas del sistema anterior ya importadas (para no crearlas dos veces). */
    public boolean existeDocumentoImportado(String tabla, Integer empresaId, Long terceroId, String marca) {
        // La tabla la elige el servicio (cuentas_cobrar | cuentas_pagar), nunca el usuario.
        if (!"cuentas_cobrar".equals(tabla) && !"cuentas_pagar".equals(tabla)) {
            throw new IllegalArgumentException("Tabla no permitida: " + tabla);
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla
                + " WHERE empresa_id = :empresaId AND tercero_id = :terceroId"
                + " AND deleted_at IS NULL AND observaciones LIKE :marca",
                new MapSqlParameterSource("empresaId", empresaId).addValue("terceroId", terceroId)
                        .addValue("marca", marca + "%"), Integer.class);
        return n != null && n > 0;
    }
}

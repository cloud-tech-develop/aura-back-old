package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * El tercero al que se le paga cada impuesto (V204): la DIAN para el IVA, el
 * INC, la retefuente y el reteIVA; la secretaría de hacienda del municipio para
 * el reteICA.
 */
@Repository
public class AcreedorImpuestoQueryRepository {

    /** NIT de la DIAN (DV 4). */
    public static final String NIT_DIAN = "800197268";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Nombre y documento de un tercero vivo de la empresa. */
    public record Tercero(Long id, String nombre, String documento) {
    }

    public Optional<Tercero> tercero(Long terceroId, Integer empresaId) {
        return jdbc.query("""
                SELECT t.id,
                       COALESCE(NULLIF(t.razon_social, ''), TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS nombre,
                       t.numero_documento || COALESCE('-' || NULLIF(t.dv, ''), '') AS documento
                FROM tercero t
                WHERE t.id = :id AND t.empresa_id = :empresaId AND t.deleted_at IS NULL
                """,
                new MapSqlParameterSource("id", terceroId).addValue("empresaId", empresaId),
                (rs, i) -> new Tercero(rs.getLong("id"), rs.getString("nombre"), rs.getString("documento")))
                .stream().findFirst();
    }

    /** La DIAN de la empresa, si existe y no está borrada. */
    public Optional<Long> dian(Integer empresaId) {
        return jdbc.queryForList("""
                SELECT id FROM tercero
                WHERE empresa_id = :empresaId AND numero_documento = :nit AND deleted_at IS NULL
                ORDER BY id LIMIT 1
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("nit", NIT_DIAN), Long.class)
                .stream().findFirst();
    }

    /**
     * Crea la DIAN como tercero si la empresa no la tiene (viva o borrada: el
     * NIT es único por empresa) y la asigna como acreedora del IVA, el INC, la
     * retefuente y el reteIVA que aún no tengan. Idempotente.
     */
    public void asegurarDian(Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId).addValue("nit", NIT_DIAN);
        jdbc.update("""
                INSERT INTO tercero (empresa_id, tipo_documento, numero_documento, dv, razon_social,
                                     tipo_persona, es_cliente, es_proveedor, activo, municipio,
                                     created_at, updated_at)
                SELECT :empresaId, 'NIT', :nit, '4', 'DIRECCIÓN DE IMPUESTOS Y ADUANAS NACIONALES - DIAN',
                       'JURIDICA', false, true, true, 'Bogotá, D.C.', now(), now()
                WHERE NOT EXISTS (SELECT 1 FROM tercero
                                   WHERE empresa_id = :empresaId AND numero_documento = :nit)
                """, p);
        Long dian = dian(empresaId).orElse(null);
        if (dian == null) return; // borrada: el contador elige el acreedor a mano
        p.addValue("dian", dian);
        jdbc.update("""
                UPDATE impuesto SET tercero_acreedor_id = :dian
                WHERE empresa_id = :empresaId AND tipo IN ('IVA', 'INC') AND tercero_acreedor_id IS NULL
                """, p);
        jdbc.update("""
                UPDATE tarifa_retencion SET tercero_acreedor_id = :dian
                WHERE empresa_id = :empresaId AND tipo IN ('RETEFUENTE', 'RETEIVA')
                  AND tercero_acreedor_id IS NULL
                """, p);
    }

    /** Acreedores de los impuestos IVA activos (normalmente solo la DIAN). */
    public List<Long> acreedoresIva(Integer empresaId) {
        return jdbc.queryForList("""
                SELECT DISTINCT tercero_acreedor_id FROM impuesto
                WHERE empresa_id = :empresaId AND tipo = 'IVA' AND activo AND tercero_acreedor_id IS NOT NULL
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
    }

    /** Cuenta de una tarifa de retención y a quién se le paga. */
    public record CuentaAcreedor(String tipo, Long cuentaId, Long terceroAcreedorId) {
    }

    /** Tarifas de retención con cuenta propia: de ahí sale el acreedor de cada cuenta. */
    public List<CuentaAcreedor> cuentasRetencion(Integer empresaId) {
        return jdbc.query("""
                SELECT tipo, cuenta_contable_id, tercero_acreedor_id FROM tarifa_retencion
                WHERE empresa_id = :empresaId AND cuenta_contable_id IS NOT NULL
                """, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new CuentaAcreedor(rs.getString("tipo"), rs.getLong("cuenta_contable_id"),
                        rs.getObject("tercero_acreedor_id", Long.class)));
    }

    /** Acreedores distintos de las tarifas de reteICA activas. */
    public List<Long> acreedoresReteica(Integer empresaId) {
        return jdbc.queryForList("""
                SELECT DISTINCT tercero_acreedor_id FROM tarifa_retencion
                WHERE empresa_id = :empresaId AND tipo = 'RETEICA' AND activo AND tercero_acreedor_id IS NOT NULL
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
    }
}

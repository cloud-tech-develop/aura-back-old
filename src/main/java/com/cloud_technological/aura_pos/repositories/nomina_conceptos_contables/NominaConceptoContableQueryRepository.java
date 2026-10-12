package com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Consultas de la cuenta y el tercero por concepto de nómina (R4c-2). */
@Repository
@RequiredArgsConstructor
public class NominaConceptoContableQueryRepository {

    /** Concepto de nómina vigente (código, nombre y clase). */
    public record Concepto(String codigo, String nombre, String clase) {
    }

    /** Cambio de la empresa sobre un concepto (lo nulo usa el valor por defecto). */
    public record Cambio(Long id, String codigo, Long cuentaDebitoId, Long cuentaCreditoId, String terceroCredito,
            Long terceroFijoId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Conceptos de nómina activos (globales y propios de la empresa; si un código
     * tiene varias versiones se toma la más reciente), en el orden del desprendible.
     */
    public List<Concepto> conceptos(Integer empresaId) {
        return jdbc.query("""
                SELECT codigo, nombre, clase FROM (
                    SELECT DISTINCT ON (codigo) codigo, nombre, clase, orden
                      FROM concepto_nomina
                     WHERE (empresa_id IS NULL OR empresa_id = :empresaId) AND activo
                     ORDER BY codigo, empresa_id NULLS LAST, vigente_desde DESC
                ) c
                 ORDER BY CASE clase WHEN 'DEVENGADO' THEN 1 WHEN 'DEDUCCION' THEN 2
                                     WHEN 'APORTE_EMPLEADOR' THEN 3 ELSE 4 END, orden, codigo
                """, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new Concepto(rs.getString("codigo"), rs.getString("nombre"), rs.getString("clase")));
    }

    /** Cambios de la empresa, por código de concepto. */
    public Map<String, Cambio> cambios(Integer empresaId) {
        Map<String, Cambio> out = new HashMap<>();
        jdbc.query("""
                SELECT id, concepto_codigo, cuenta_debito_id, cuenta_credito_id, tercero_credito, tercero_fijo_id
                  FROM nomina_concepto_contable WHERE empresa_id = :empresaId
                """, new MapSqlParameterSource("empresaId", empresaId),
                rs -> {
                    out.put(rs.getString("concepto_codigo"), new Cambio(rs.getLong("id"),
                            rs.getString("concepto_codigo"), rs.getObject("cuenta_debito_id", Long.class),
                            rs.getObject("cuenta_credito_id", Long.class), rs.getString("tercero_credito"),
                            rs.getObject("tercero_fijo_id", Long.class)));
                });
        return out;
    }

    /** Id del cambio de la empresa sobre el concepto, si existe. */
    public Optional<Long> idCambio(Integer empresaId, String codigo) {
        return jdbc.queryForList("""
                SELECT id FROM nomina_concepto_contable WHERE empresa_id = :empresaId AND concepto_codigo = :codigo
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo), Long.class)
                .stream().findFirst();
    }

    /** ¿La cuenta es de la empresa, está activa y recibe movimiento (auxiliar)? */
    public boolean cuentaDeMovimiento(Long cuentaId, Integer empresaId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM plan_cuenta
                 WHERE id = :id AND empresa_id = :empresaId AND activa AND auxiliar
                """, new MapSqlParameterSource("id", cuentaId).addValue("empresaId", empresaId), Integer.class);
        return n != null && n > 0;
    }

    /** ¿El tercero es de la empresa y no está borrado? */
    public boolean terceroValido(Long terceroId, Integer empresaId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tercero WHERE id = :id AND empresa_id = :empresaId AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", terceroId).addValue("empresaId", empresaId), Integer.class);
        return n != null && n > 0;
    }

    /**
     * "Código — nombre" de las cuentas.
     *
     * @return id → etiqueta
     */
    public Map<Long, String> etiquetasCuentas(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("SELECT id, codigo || ' — ' || nombre AS etiqueta FROM plan_cuenta WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("etiqueta"));
                });
        return out;
    }

    /**
     * Nombre de los terceros.
     *
     * @return id → nombre
     */
    public Map<Long, String> nombresTerceros(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("""
                SELECT id, COALESCE(NULLIF(razon_social, ''), TRIM(CONCAT(nombres, ' ', apellidos))) AS nombre
                  FROM tercero WHERE id IN (:ids)
                """, new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("nombre"));
                });
        return out;
    }
}

package com.cloud_technological.aura_pos.repositories.conciliacion_fiscal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Ajuste;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cabecera;
import com.cloud_technological.aura_pos.dto.conciliacion_fiscal.ConciliacionFiscalDtos.Cuenta;

/** Consultas de la conciliación fiscal (V202). */
@Repository
public class ConciliacionFiscalQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String CABECERA = """
            SELECT f.id, f.anio, f.formato, f.tarifa_renta, f.origen, f.cargado_at, f.estado,
                   (SELECT COUNT(*) FROM conciliacion_fiscal_cuenta c WHERE c.conciliacion_id = f.id) AS cuentas,
                   (SELECT COUNT(*) FROM conciliacion_fiscal_ajuste a WHERE a.conciliacion_id = f.id) AS ajustes
            FROM conciliacion_fiscal f
            """;

    public List<Cabecera> listar(Integer empresaId) {
        return jdbc.query(CABECERA + " WHERE f.empresa_id = :empresaId ORDER BY f.anio DESC, f.formato",
                new MapSqlParameterSource("empresaId", empresaId), new BeanPropertyRowMapper<>(Cabecera.class));
    }

    public Cabecera cabecera(Long id, Integer empresaId) {
        List<Cabecera> r = jdbc.query(CABECERA + " WHERE f.id = :id AND f.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(Cabecera.class));
        return r.isEmpty() ? null : r.get(0);
    }

    public boolean existe(Integer empresaId, Integer anio, String formato) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM conciliacion_fiscal
                WHERE empresa_id = :empresaId AND anio = :anio AND formato = :formato
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("anio", anio)
                .addValue("formato", formato), Integer.class);
        return n != null && n > 0;
    }

    public List<Cuenta> cuentas(Long conciliacionId) {
        return jdbc.query("""
                SELECT id, codigo, nombre, saldo, renglon, renglon_manual
                FROM conciliacion_fiscal_cuenta
                WHERE conciliacion_id = :id
                ORDER BY codigo
                """, new MapSqlParameterSource("id", conciliacionId), new BeanPropertyRowMapper<>(Cuenta.class));
    }

    public List<Ajuste> ajustes(Long conciliacionId) {
        return jdbc.query("""
                SELECT id, renglon, tipo, diferencia, valor, concepto, cuenta_codigo, sugerencia
                FROM conciliacion_fiscal_ajuste
                WHERE conciliacion_id = :id
                ORDER BY id
                """, new MapSqlParameterSource("id", conciliacionId), new BeanPropertyRowMapper<>(Ajuste.class));
    }

    public void borrarCuentas(Long conciliacionId) {
        jdbc.update("DELETE FROM conciliacion_fiscal_cuenta WHERE conciliacion_id = :id",
                new MapSqlParameterSource("id", conciliacionId));
    }

    /**
     * Balance del año desde la contabilidad de Aura, por cuenta auxiliar y con el
     * signo de su naturaleza: ESF acumulado al 31 de diciembre, ERI solo con el
     * movimiento del año. Sin el asiento de cierre anual (ni su reversa): así el
     * ERI llega con el año completo y el resultado se suma al patrimonio aparte.
     */
    public List<Cuenta> balanceSistema(Integer empresaId, int anio) {
        return jdbc.query("""
                SELECT pc.codigo, pc.nombre,
                       SUM(CASE WHEN LEFT(pc.codigo, 1) IN ('1', '5', '6', '7')
                                THEN ad.debito - ad.credito ELSE ad.credito - ad.debito END) AS saldo
                FROM asiento_detalle ad
                JOIN asiento_contable a ON a.id = ad.asiento_id
                JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
                WHERE a.empresa_id = :empresaId
                  AND a.estado = 'CONTABILIZADO'
                  AND COALESCE(a.tipo_origen, '') NOT IN ('CIERRE_ANUAL', 'ANULACION_CIERRE_ANUAL')
                  AND a.fecha <= make_date(:anio, 12, 31)
                  AND (LEFT(pc.codigo, 1) IN ('1', '2', '3') OR a.fecha >= make_date(:anio, 1, 1))
                GROUP BY pc.codigo, pc.nombre
                HAVING SUM(ad.debito - ad.credito) <> 0
                ORDER BY pc.codigo
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("anio", anio),
                new BeanPropertyRowMapper<>(Cuenta.class));
    }
}

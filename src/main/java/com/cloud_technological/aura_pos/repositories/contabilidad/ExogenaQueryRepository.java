package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.CuentaTerceroMovimientoDto;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.Clasificador2276;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaExcelExporter;
import com.cloud_technological.aura_pos.dto.contabilidad.TerceroExogenaDto;

/**
 * Consultas jdbc de la exógena (E11): movimientos por cuenta × tercero para
 * la generación de lotes y los insumos del validador previo (borradores,
 * períodos abiertos, datos fiscales de terceros).
 */
@Repository
public class ExogenaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Movimientos del año agrupados por cuenta × tercero, solo contabilizados
     * y sin asientos de CIERRE (el cierre cancela las cuentas de resultado y
     * distorsionaría los pagos/ingresos brutos del año).
     */
    public List<CuentaTerceroMovimientoDto> movimientosDelAnio(Integer empresaId, int anio) {
        return consultarMovimientos(empresaId,
                "AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)",
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("desde", anio + "-01-01")
                        .addValue("hasta", anio + "-12-31"));
    }

    /** Saldos acumulados a diciembre 31 por cuenta × tercero (formatos 1008/1009). */
    public List<CuentaTerceroMovimientoDto> saldosAlCorte(Integer empresaId, int anio) {
        return consultarMovimientos(empresaId,
                "AND a.fecha <= CAST(:hasta AS DATE)",
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("hasta", anio + "-12-31"));
    }

    private List<CuentaTerceroMovimientoDto> consultarMovimientos(Integer empresaId,
            String filtroFecha, MapSqlParameterSource params) {
        String sql = """
            SELECT pc.codigo,
                   ad.tercero_id,
                   COALESCE(SUM(ad.debito), 0)  AS debito,
                   COALESCE(SUM(ad.credito), 0) AS credito
            FROM asiento_detalle ad
            JOIN asiento_contable a ON a.id = ad.asiento_id
            JOIN plan_cuenta pc     ON pc.id = ad.cuenta_id
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'CONTABILIZADO'
              AND a.tipo_origen <> 'CIERRE'
              %s
            GROUP BY pc.codigo, ad.tercero_id
            """.formatted(filtroFecha);
        return jdbc.query(sql, params,
                (rs, i) -> new CuentaTerceroMovimientoDto(
                        rs.getString("codigo"),
                        rs.getObject("tercero_id") != null ? rs.getLong("tercero_id") : null,
                        rs.getBigDecimal("debito"),
                        rs.getBigDecimal("credito")));
    }

    /** Comprobantes en borrador del año (bloquean la exógena). */
    public List<String> comprobantesBorrador(Integer empresaId, int anio) {
        String sql = """
            SELECT a.numero_comprobante || ' (' || a.fecha || ')'
            FROM asiento_contable a
            WHERE a.empresa_id = :empresaId
              AND a.estado = 'BORRADOR'
              AND a.fecha BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
            ORDER BY a.fecha
            LIMIT 50
            """;
        return jdbc.queryForList(sql, Map.of("empresaId", empresaId,
                "desde", anio + "-01-01", "hasta", anio + "-12-31"), String.class);
    }

    /** Meses del año con período contable aún ABIERTO. */
    public List<Integer> mesesAbiertos(Integer empresaId, int anio) {
        String sql = """
            SELECT mes FROM periodo_contable
            WHERE empresa_id = :empresaId AND anio = :anio AND estado = 'ABIERTO'
            ORDER BY mes
            """;
        return jdbc.queryForList(sql,
                Map.of("empresaId", empresaId, "anio", anio), Integer.class);
    }

    /** Datos fiscales de los terceros con movimiento (validador y export). */
    public List<TerceroExogenaDto> terceros(Integer empresaId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String sql = """
            SELECT t.id, t.tipo_documento, t.numero_documento, t.dv, t.razon_social,
                   t.nombres, t.apellidos, t.direccion, t.municipio,
                   t.nombre1, t.nombre2, t.apellido1, t.apellido2, t.tipo_persona,
                   m.codigo AS codigo_dane, t.codigo_pais
            FROM tercero t
            LEFT JOIN municipios m ON m.id = t.municipio_id
            WHERE t.empresa_id = :empresaId AND t.id IN (:ids)
            """;
        return jdbc.query(sql,
                new MapSqlParameterSource()
                        .addValue("empresaId", empresaId)
                        .addValue("ids", ids),
                (rs, i) -> new TerceroExogenaDto(
                        rs.getLong("id"),
                        rs.getString("tipo_documento"),
                        rs.getString("numero_documento"),
                        rs.getString("dv"),
                        rs.getString("razon_social"),
                        rs.getString("nombres"),
                        rs.getString("apellidos"),
                        rs.getString("direccion"),
                        rs.getString("municipio"),
                        rs.getString("nombre1"),
                        rs.getString("nombre2"),
                        rs.getString("apellido1"),
                        rs.getString("apellido2"),
                        rs.getString("tipo_persona"),
                        rs.getString("codigo_dane"),
                        rs.getString("codigo_pais")));
    }

    /** Lo pagado de un concepto a un empleado (tercero) en el año, para el 2276. */
    public record PagoNomina(Long terceroId, Long empleadoId, String empleadoNombre,
                             Clasificador2276.Pago pago) {
    }

    /**
     * Pagos de nómina del año por empleado y concepto (formato 2276): nóminas
     * confirmadas (aprobadas o pagadas, no prenómina) cuyo período termina en el año.
     */
    public List<PagoNomina> pagosNominaDelAnio(Integer empresaId, int anio) {
        String sql = """
            SELECT e.tercero_id, e.id AS empleado_id,
                   CONCAT(e.nombres, ' ', e.apellidos) AS empleado,
                   c.codigo, c.codigo_dian, c.clase, c.constituye_ibc,
                   COALESCE(t.clase, 'PERIODICO') AS clase_documento,
                   SUM(nd.valor) AS valor
            FROM nomina n
            JOIN periodo_nomina p ON p.id = n.periodo_id
            JOIN empleados e ON e.id = n.empleado_id
            JOIN nomina_detalle nd ON nd.nomina_id = n.id
            JOIN concepto_nomina c ON c.id = nd.concepto_id
            LEFT JOIN nomina_documento d ON d.id = n.documento_id
            LEFT JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
            WHERE n.empresa_id = :empresaId
              AND n.es_prenomina = FALSE
              AND n.estado IN ('APROBADO', 'PAGADO')
              AND p.fecha_fin BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
              AND c.clase IN ('DEVENGADO', 'DEDUCCION')
            GROUP BY e.tercero_id, e.id, e.nombres, e.apellidos, c.codigo, c.codigo_dian, c.clase,
                     c.constituye_ibc, COALESCE(t.clase, 'PERIODICO')
            """;
        return jdbc.query(sql, Map.of("empresaId", empresaId, "desde", anio + "-01-01", "hasta", anio + "-12-31"),
                (rs, i) -> new PagoNomina(
                        rs.getObject("tercero_id") != null ? rs.getLong("tercero_id") : null,
                        rs.getLong("empleado_id"),
                        rs.getString("empleado"),
                        new Clasificador2276.Pago(rs.getString("codigo"), rs.getString("codigo_dian"),
                                rs.getString("clase"), rs.getBoolean("constituye_ibc"),
                                rs.getString("clase_documento"), rs.getBigDecimal("valor"))));
    }

    /** Cuenta contable de cada cuenta bancaria → tercero del banco (formato 1012). */
    public Map<String, Long> bancoPorCuentaContable(Integer empresaId) {
        String sql = """
            SELECT pc.codigo, cb.tercero_id
            FROM cuenta_bancaria cb
            JOIN plan_cuenta pc ON pc.id = cb.cuenta_contable_id
            WHERE cb.empresa_id = :empresaId
              AND cb.tercero_id IS NOT NULL
            """;
        Map<String, Long> out = new HashMap<>();
        jdbc.query(sql, Map.of("empresaId", empresaId),
                rs -> { out.putIfAbsent(rs.getString("codigo"), rs.getLong("tercero_id")); });
        return out;
    }

    /**
     * Dirección y municipio DANE del informante (la empresa): la DIAN los pide en la
     * fila de cuantías menores. La dirección es la de su primera sede con dirección.
     */
    public ExogenaExcelExporter.Informante informante(Integer empresaId) {
        String sql = """
            SELECT (SELECT s.direccion FROM sucursal s
                     WHERE s.empresa_id = e.id AND s.direccion IS NOT NULL AND s.direccion <> ''
                     ORDER BY s.id LIMIT 1) AS direccion,
                   m.codigo AS codigo_dane
            FROM empresa e
            LEFT JOIN municipios m ON m.id = e.municipio_id
            WHERE e.id = :id
            """;
        List<ExogenaExcelExporter.Informante> r = jdbc.query(sql, Map.of("id", empresaId),
                (rs, i) -> new ExogenaExcelExporter.Informante(rs.getString("direccion"), rs.getString("codigo_dane")));
        return r.isEmpty() ? new ExogenaExcelExporter.Informante(null, null) : r.get(0);
    }

    /** Tipo de persona del informante (NATURAL | JURIDICA). */
    public String tipoPersonaEmpresa(Integer empresaId) {
        List<String> r = jdbc.queryForList("SELECT tipo_persona FROM empresa WHERE id = :id",
                Map.of("id", empresaId), String.class);
        return r.isEmpty() || r.get(0) == null ? "JURIDICA" : r.get(0);
    }

    /** Cambia el tipo de persona del informante. */
    public void cambiarTipoPersonaEmpresa(Integer empresaId, String tipoPersona) {
        jdbc.update("UPDATE empresa SET tipo_persona = :tp WHERE id = :id",
                Map.of("tp", tipoPersona, "id", empresaId));
    }
}

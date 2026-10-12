package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de la nómina electrónica MENSUAL: qué nóminas confirmadas tiene cada
 * contrato en el mes (la DIAN recibe un solo documento por empleado y mes) y cuál
 * es el documento electrónico de ese mes.
 */
@Repository
@RequiredArgsConstructor
public class NominaElectronicaMesQueryRepository {

    /** Nómina confirmada del mes. {@code numero} es el del documento (NOM-00002). */
    public record NominaMes(Long id, LocalDate periodoInicio, LocalDate periodoFin, String clase, String numero,
                            BigDecimal totalDevengado) {}

    /**
     * Contrato con nóminas confirmadas en el mes y su documento electrónico más
     * reciente (null si no se ha enviado). Las fechas de cobertura solo cuentan las
     * nóminas periódicas y la liquidación definitiva.
     */
    public record ContratoMes(Long contratoId, String empleadoNombre, String empleadoDocumento, int nominas,
                              Long ultimaNominaId, LocalDate coberturaInicio, LocalDate coberturaFin,
                              boolean definitiva, LocalDate contratoInicio, LocalDate contratoFin, String contratoEstado,
                              BigDecimal totalDevengado, Long neId, String neEstado, String neNumero, String neCune) {}

    /** Nóminas que cuentan para el mes: confirmadas (no prenómina) y con período que termina en él. */
    private static final String NOMINAS_MES = """
            SELECT n.id, n.contrato_id, p.fecha_inicio, p.fecha_fin, n.total_devengado,
                   COALESCE(t.clase, 'PERIODICO') AS clase,
                   CASE WHEN d.id IS NULL THEN NULL
                        ELSE t.prefijo || '-' || LPAD(d.numero::TEXT, 5, '0') END AS numero
            FROM nomina n
            JOIN periodo_nomina p ON p.id = n.periodo_id
            LEFT JOIN nomina_documento d ON d.id = n.documento_id
            LEFT JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
            WHERE n.empresa_id = :empresaId
              AND n.es_prenomina = FALSE
              AND n.estado IN ('APROBADO', 'PAGADO')
              AND p.fecha_fin BETWEEN :inicio AND :fin
            """;

    private static final String NOMINAS_DEL_CONTRATO = NOMINAS_MES + """
              AND n.contrato_id = :contratoId
            ORDER BY p.fecha_fin, n.id
            """;

    private static final String CONTRATOS_DEL_MES = "WITH nm AS (" + NOMINAS_MES + """
            ),
            agg AS (
                SELECT contrato_id,
                       COUNT(*) AS nominas,
                       (ARRAY_AGG(id ORDER BY fecha_fin DESC, id DESC))[1] AS ultima,
                       MIN(fecha_inicio) FILTER (WHERE clase IN ('PERIODICO', 'LIQUIDACION_DEFINITIVA')) AS cob_ini,
                       MAX(fecha_fin) FILTER (WHERE clase IN ('PERIODICO', 'LIQUIDACION_DEFINITIVA')) AS cob_fin,
                       BOOL_OR(clase = 'LIQUIDACION_DEFINITIVA') AS definitiva,
                       SUM(total_devengado) AS devengado
                FROM nm
                GROUP BY contrato_id
            )
            SELECT a.contrato_id, a.nominas, a.ultima, a.cob_ini, a.cob_fin, a.definitiva, a.devengado,
                   c.fecha_inicio AS c_ini, c.fecha_fin AS c_fin, c.estado AS c_estado,
                   COALESCE(NULLIF(CONCAT_WS(' ', tr.nombre1, tr.nombre2, tr.apellido1, tr.apellido2), ''),
                            CONCAT(e.nombres, ' ', e.apellidos)) AS nombre,
                   COALESCE(tr.numero_documento, e.numero_documento) AS documento,
                   ne.id AS ne_id, ne.estado AS ne_estado,
                   CASE WHEN ne.consecutivo IS NULL THEN NULL
                        ELSE COALESCE(ne.prefijo, '') || ne.consecutivo END AS ne_numero,
                   ne.cune AS ne_cune
            FROM agg a
            JOIN contrato_laboral c ON c.id = a.contrato_id
            JOIN empleados e ON e.id = c.empleado_id
            LEFT JOIN tercero tr ON tr.id = e.tercero_id
            LEFT JOIN LATERAL (
                SELECT x.id, x.estado, x.prefijo, x.consecutivo, x.cune
                FROM nomina_electronica x
                WHERE x.empresa_id = :empresaId
                  AND x.contrato_id = a.contrato_id
                  AND x.agno = :agno
                  AND x.mes = :mes
                  AND x.es_ajuste = FALSE
                ORDER BY x.id DESC
                LIMIT 1
            ) ne ON TRUE
            ORDER BY nombre
            """;

    private static final String DOCUMENTOS_DEL_MES = """
            SELECT id, estado
            FROM nomina_electronica
            WHERE empresa_id = :empresaId
              AND contrato_id = :contratoId
              AND agno = :agno
              AND mes = :mes
              AND es_ajuste = FALSE
            ORDER BY id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Nóminas confirmadas del contrato en el mes, en orden de período. */
    public List<NominaMes> nominasDelMes(Integer empresaId, Long contratoId, LocalDate inicio, LocalDate fin) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("contratoId", contratoId)
                .addValue("inicio", inicio)
                .addValue("fin", fin);
        return jdbc.query(NOMINAS_DEL_CONTRATO, p, (rs, i) -> new NominaMes(
                rs.getLong("id"),
                rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_fin", LocalDate.class),
                rs.getString("clase"),
                rs.getString("numero"),
                rs.getBigDecimal("total_devengado")));
    }

    /** Contratos con nóminas confirmadas en el mes, con su documento electrónico. */
    public List<ContratoMes> contratosDelMes(Integer empresaId, int agno, int mes, LocalDate inicio, LocalDate fin) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("agno", agno)
                .addValue("mes", mes)
                .addValue("inicio", inicio)
                .addValue("fin", fin);
        return jdbc.query(CONTRATOS_DEL_MES, p, (rs, i) -> new ContratoMes(
                rs.getLong("contrato_id"),
                rs.getString("nombre"),
                rs.getString("documento"),
                rs.getInt("nominas"),
                rs.getLong("ultima"),
                rs.getObject("cob_ini", LocalDate.class),
                rs.getObject("cob_fin", LocalDate.class),
                rs.getBoolean("definitiva"),
                rs.getObject("c_ini", LocalDate.class),
                rs.getObject("c_fin", LocalDate.class),
                rs.getString("c_estado"),
                rs.getBigDecimal("devengado"),
                rs.getObject("ne_id", Long.class),
                rs.getString("ne_estado"),
                rs.getString("ne_numero"),
                rs.getString("ne_cune")));
    }

    /** Documento electrónico original más reciente del contrato en el mes. */
    public Optional<Long> documentoDelMes(Integer empresaId, Long contratoId, int agno, int mes) {
        return documentos(empresaId, contratoId, agno, mes).stream().findFirst().map(d -> d[0])
                .map(Long.class::cast);
    }

    /** Documentos originales del mes que se eliminaron con nota de ajuste. */
    public long anuladosDelMes(Integer empresaId, Long contratoId, int agno, int mes) {
        return documentos(empresaId, contratoId, agno, mes).stream().filter(d -> "ANULADO".equals(d[1])).count();
    }

    private List<Object[]> documentos(Integer empresaId, Long contratoId, int agno, int mes) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("contratoId", contratoId)
                .addValue("agno", agno)
                .addValue("mes", mes);
        return jdbc.query(DOCUMENTOS_DEL_MES, p, (rs, i) -> new Object[] { rs.getLong("id"), rs.getString("estado") });
    }
}

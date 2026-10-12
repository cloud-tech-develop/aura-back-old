package com.cloud_technological.aura_pos.repositories.nomina;

import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.services.nomina.AcumuladoMes;

import lombok.RequiredArgsConstructor;

/**
 * Lo ya liquidado a un contrato en el mes, para las reglas mensuales de la
 * seguridad social y la retención (ver {@link AcumuladoMes}).
 */
@Repository
@RequiredArgsConstructor
public class AcumuladoMesQueryRepository {

    /**
     * Nóminas previas del mes: del mismo contrato, confirmadas (aprobadas o
     * pagadas, no prenómina), con período que termina en el mismo mes y no después
     * del de la nómina actual, de otro documento, y que no sean de prestaciones
     * (prima y cesantías tienen su propio tratamiento).
     */
    private static final String ACUMULADO = """
            WITH previas AS (
                SELECT n.id, n.ibc, n.total_devengado
                FROM nomina n
                JOIN periodo_nomina p ON p.id = n.periodo_id
                LEFT JOIN nomina_documento d ON d.id = n.documento_id
                LEFT JOIN nomina_tipo_comprobante t ON t.id = d.tipo_comprobante_id
                WHERE n.contrato_id = :contratoId
                  AND n.id <> :nominaId
                  AND n.es_prenomina = FALSE
                  AND n.estado IN ('APROBADO', 'PAGADO')
                  AND date_trunc('month', p.fecha_fin) = date_trunc('month', CAST(:fechaFin AS date))
                  AND p.fecha_fin <= CAST(:fechaFin AS date)
                  AND COALESCE(n.documento_id, -1) <> COALESCE(CAST(:documentoId AS bigint), -2)
                  AND COALESCE(t.clase, 'PERIODICO') NOT IN ('PRIMA', 'CESANTIAS', 'INTERESES_CESANTIAS')
            )
            SELECT
                (SELECT COUNT(*) FROM previas) AS nominas,
                (SELECT COALESCE(SUM(ibc), 0) FROM previas) AS ibc,
                (SELECT COALESCE(SUM(total_devengado), 0) FROM previas) AS devengado,
                COALESCE(SUM(CASE WHEN c.clase = 'DEVENGADO' AND c.constituye_ibc AND c.codigo <> 'AUX_TRANSP'
                                  THEN nd.valor END), 0) AS salarial,
                COALESCE(SUM(CASE WHEN c.clase = 'DEVENGADO' AND NOT c.constituye_ibc AND c.codigo <> 'AUX_TRANSP'
                                  THEN nd.valor END), 0) AS no_salarial,
                COALESCE(SUM(CASE WHEN c.codigo IN ('DED_SALUD', 'DED_PENSION', 'DED_FONDO_SOLIDARIDAD')
                                  THEN nd.valor END), 0) AS aportes,
                COALESCE(SUM(CASE WHEN c.codigo = 'DED_FONDO_SOLIDARIDAD' THEN nd.valor END), 0) AS fsp,
                COALESCE(SUM(CASE WHEN c.codigo IN ('RETEFUENTE_P1', 'RETEFUENTE_P2') THEN nd.valor END), 0)
                    AS retencion
            FROM nomina_detalle nd
            JOIN concepto_nomina c ON c.id = nd.concepto_id
            WHERE nd.nomina_id IN (SELECT id FROM previas)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Acumulado del mes previo a la nómina dada.
     *
     * @param nominaId    la nómina que se liquida (se excluye)
     * @param contratoId  contrato de la nómina
     * @param documentoId documento de la nómina (se excluyen sus otras filas); puede ser null
     * @param fechaFin    fin del período de la nómina
     */
    public AcumuladoMes previoDelMes(Long nominaId, Long contratoId, Long documentoId, LocalDate fechaFin) {
        MapSqlParameterSource p = new MapSqlParameterSource("nominaId", nominaId)
                .addValue("contratoId", contratoId)
                .addValue("documentoId", documentoId)
                .addValue("fechaFin", fechaFin);
        return jdbc.queryForObject(ACUMULADO, p, (rs, i) -> new AcumuladoMes(
                rs.getInt("nominas"),
                rs.getBigDecimal("ibc"),
                rs.getBigDecimal("salarial"),
                rs.getBigDecimal("no_salarial"),
                rs.getBigDecimal("devengado"),
                rs.getBigDecimal("aportes"),
                rs.getBigDecimal("fsp"),
                rs.getBigDecimal("retencion")));
    }
}

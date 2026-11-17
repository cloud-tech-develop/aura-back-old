package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Parámetros legales de nómina por año (V239): salario mínimo y auxilio de
 * transporte. La tabla es global: la ley es la misma para todas las empresas.
 */
@Repository
@RequiredArgsConstructor
public class ParametroAnualQueryRepository {

    /** Valores de ley de un año. */
    public record ParametroAnual(int agno, BigDecimal smmlv, BigDecimal auxilioTransporte, String norma) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Parámetros del año.
     *
     * @return vacío si el año no está cargado
     */
    public Optional<ParametroAnual> delAgno(int agno) {
        List<ParametroAnual> filas = jdbc.query("""
                SELECT agno, smmlv, auxilio_transporte, norma
                  FROM nomina_parametro_anual WHERE agno = :agno
                """, new MapSqlParameterSource("agno", agno), (rs, i) -> mapear(rs));
        return filas.stream().findFirst();
    }

    /** Todos los años cargados, del más reciente al más antiguo. */
    public List<ParametroAnual> todos() {
        return jdbc.query("""
                SELECT agno, smmlv, auxilio_transporte, norma
                  FROM nomina_parametro_anual ORDER BY agno DESC
                """, new MapSqlParameterSource(), (rs, i) -> mapear(rs));
    }

    private ParametroAnual mapear(ResultSet rs) throws SQLException {
        return new ParametroAnual(rs.getInt("agno"), rs.getBigDecimal("smmlv"),
                rs.getBigDecimal("auxilio_transporte"), rs.getString("norma"));
    }
}

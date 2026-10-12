package com.cloud_technological.aura_pos.repositories.nomina;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Consultas de la nómina electrónica que no son simples por id. */
@Repository
@RequiredArgsConstructor
public class NominaElectronicaQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Reserva el siguiente consecutivo del año de forma atómica.
     *
     * <p>{@code ON CONFLICT DO UPDATE ... RETURNING} en una sola sentencia: dos hilos
     * que pidan a la vez obtienen números distintos. Con {@code SELECT max()+1}
     * habría condición de carrera, y un consecutivo duplicado ante la DIAN no se
     * deshace. Va con {@code queryForObject} porque devuelve una fila: como
     * {@code @Modifying} de JPA fallaba con "A result was returned when none was expected".
     *
     * @return el consecutivo reservado
     */
    public Long reservarConsecutivo(Integer empresaId, Integer agno) {
        return jdbc.queryForObject("""
                INSERT INTO nomina_electronica_consecutivo (empresa_id, agno, ultimo)
                VALUES (:empresaId, :agno, 1)
                ON CONFLICT (empresa_id, agno)
                DO UPDATE SET ultimo = nomina_electronica_consecutivo.ultimo + 1
                RETURNING ultimo
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("agno", agno), Long.class);
    }
}

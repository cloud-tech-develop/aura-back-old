package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;

/**
 * Consultas sobre las novedades de nómina ya liquidadas (B-01).
 *
 * <p>Las novedades cuelgan de {@code nomina} por {@code @OneToMany cascade},
 * pero para promediar el salario variable de un empleado a lo largo de un
 * semestre/año hace falta consultarlas transversalmente, no dentro de una sola
 * nómina. De ahí este repositorio.
 */
public interface NominaNovedadJPARepository extends JpaRepository<NominaNovedadEntity, Long> {

    /**
     * Suma de lo <b>devengado variable salarial</b> de un empleado en el rango de
     * fechas de los períodos de nómina que lo cruzan.
     *
     * <p>Base para el promedio de prima, cesantías e intereses. Incluye comisiones,
     * horas extra, recargos y dominicales/festivos (todo lo que {@code constituye_ibc}).
     * Excluye deducciones y lo no salarial (bonos de mera liberalidad).
     *
     * <p>Para <b>vacaciones</b> se pasa {@code excluirHorasExtra = true}: el trabajo
     * suplementario no entra a la base de vacaciones (CST art. 192 vs. art. 17).
     *
     * @param excluirHorasExtra si es {@code true}, no suma las novedades cuyo
     *                          {@code tipo} empieza por {@code HORA_EXTRA}.
     */
    @Query("""
            SELECT COALESCE(SUM(nv.valorTotal), 0)
              FROM NominaNovedadEntity nv
             WHERE nv.nomina.empresa.id = :empresaId
               AND nv.nomina.empleado.id = :empleadoId
               AND nv.nomina.estado <> 'ANULADO'
               AND nv.esDeduccion = false
               AND nv.constituyeIbc = true
               AND nv.nomina.periodo.fechaInicio <= :hasta
               AND nv.nomina.periodo.fechaFin   >= :desde
               AND (:excluirHorasExtra = false OR nv.tipo NOT LIKE 'HORA_EXTRA%')
            """)
    BigDecimal sumVariableSalarial(@Param("empresaId") Integer empresaId,
                                   @Param("empleadoId") Long empleadoId,
                                   @Param("desde") LocalDate desde,
                                   @Param("hasta") LocalDate hasta,
                                   @Param("excluirHorasExtra") boolean excluirHorasExtra);
}

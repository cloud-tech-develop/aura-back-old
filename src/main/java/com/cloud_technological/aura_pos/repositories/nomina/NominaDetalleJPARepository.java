package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;

@Repository
public interface NominaDetalleJPARepository extends JpaRepository<NominaDetalleEntity, Long> {

    List<NominaDetalleEntity> findByNominaId(Long nominaId);

    void deleteByNominaId(Long nominaId);

    /**
     * Total de una clase de concepto en una nómina.
     *
     * <p>Es el invariante que los tests tienen que verificar:
     * {@code sumarPorClase(nominaId, "DEVENGADO") == nomina.totalDevengado}.
     * Si no cuadra, el detalle y el agregado divergieron y el desprendible
     * miente.
     */
    @Query("""
            SELECT COALESCE(SUM(d.valor), 0)
              FROM NominaDetalleEntity d
             WHERE d.nomina.id = :nominaId
               AND d.concepto.clase = :clase
            """)
    BigDecimal sumarPorClase(@Param("nominaId") Long nominaId, @Param("clase") String clase);

    /** Detalle para el desprendible y para armar el XML de la DIAN (Fase 5). */
    @Query("""
            SELECT d FROM NominaDetalleEntity d
              JOIN FETCH d.concepto c
             WHERE d.nomina.id = :nominaId
             ORDER BY c.orden, d.id
            """)
    List<NominaDetalleEntity> findParaDesprendible(@Param("nominaId") Long nominaId);

    /**
     * Distribución del costo por proyecto/frente (Fase 4).
     *
     * <p><b>Esta es la fuente única del reparto.</b> El asiento contable deriva
     * de aquí, no de un cálculo paralelo: si `nomina_detalle` y el asiento
     * repartieran por su cuenta, podrían divergir y el detalle de nómina diría
     * una cosa y la contabilidad otra para la misma obra.
     *
     * <p>Agrupa los DEVENGADOS: es lo que refleja dónde se trabajó.
     *
     * @return filas de [proyectoId, frenteId, valor]
     */
    @Query("""
            SELECT d.proyectoId, d.frenteId, SUM(d.valor)
              FROM NominaDetalleEntity d
             WHERE d.nomina.id = :nominaId
               AND d.concepto.clase = 'DEVENGADO'
               AND d.proyectoId IS NOT NULL
             GROUP BY d.proyectoId, d.frenteId
             ORDER BY SUM(d.valor) DESC
            """)
    List<Object[]> distribucionPorProyecto(@Param("nominaId") Long nominaId);
}

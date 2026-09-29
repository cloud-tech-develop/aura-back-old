package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.FondoSolidaridadRangoEntity;

@Repository
public interface FondoSolidaridadRangoJPARepository
        extends JpaRepository<FondoSolidaridadRangoEntity, Long> {

    /**
     * Rango que aplica a un IBC expresado en SMMLV, en un año dado.
     *
     * <p>El rango es [desde, hasta): el borde inferior incluye, el superior no.
     * Con {@code smmlvHasta IS NULL} es el último rango, sin tope.
     */
    @Query("""
            SELECT r FROM FondoSolidaridadRangoEntity r
             WHERE r.agno = :agno
               AND r.smmlvDesde <= :ibcEnSmmlv
               AND (r.smmlvHasta IS NULL OR r.smmlvHasta > :ibcEnSmmlv)
             ORDER BY r.smmlvDesde DESC
            """)
    java.util.List<FondoSolidaridadRangoEntity> findAplicables(@Param("agno") Integer agno,
                                                               @Param("ibcEnSmmlv") BigDecimal ibcEnSmmlv);

    default Optional<FondoSolidaridadRangoEntity> findRango(Integer agno, BigDecimal ibcEnSmmlv) {
        return findAplicables(agno, ibcEnSmmlv).stream().findFirst();
    }
}

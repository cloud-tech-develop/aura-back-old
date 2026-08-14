package com.cloud_technological.aura_pos.repositories.nomina;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ContratoAfiliacionEntity;
import com.cloud_technological.aura_pos.entity.EntidadSeguridadSocialEntity;

/** Repositorios de afiliaciones a seguridad social (Fase 5.5). */
public final class AfiliacionJPARepositories {

    private AfiliacionJPARepositories() {}

    @Repository
    public interface EntidadSeguridadSocialRepo
            extends JpaRepository<EntidadSeguridadSocialEntity, Long> {

        List<EntidadSeguridadSocialEntity> findByTipoAndActivoTrueOrderByNombre(String tipo);

        Optional<EntidadSeguridadSocialEntity> findByTipoAndCodigoOficial(String tipo, String codigoOficial);

        Optional<EntidadSeguridadSocialEntity> findByNit(String nit);
    }

    @Repository
    public interface ContratoAfiliacionRepo extends JpaRepository<ContratoAfiliacionEntity, Long> {

        /** La afiliación vigente de un tipo. Solo puede haber una. */
        @Query("""
                SELECT a FROM ContratoAfiliacionEntity a
                 WHERE a.contrato.id = :contratoId
                   AND a.tipo = :tipo
                   AND a.fechaHasta IS NULL
                """)
        Optional<ContratoAfiliacionEntity> findVigente(@Param("contratoId") Long contratoId,
                                                       @Param("tipo") String tipo);

        /** Afiliación que regía en una fecha. Para reconstruir PILA de un mes pasado. */
        @Query("""
                SELECT a FROM ContratoAfiliacionEntity a
                 WHERE a.contrato.id = :contratoId
                   AND a.tipo = :tipo
                   AND a.fechaDesde <= :fecha
                   AND (a.fechaHasta IS NULL OR a.fechaHasta >= :fecha)
                """)
        Optional<ContratoAfiliacionEntity> findEnFecha(@Param("contratoId") Long contratoId,
                                                       @Param("tipo") String tipo,
                                                       @Param("fecha") LocalDate fecha);

        List<ContratoAfiliacionEntity> findByContratoId(Long contratoId);

        /**
         * Afiliaciones que empezaron dentro de un período.
         *
         * <p>Es lo que alimenta las banderas de traslado de PILA: si hay una
         * afiliación nueva de tipo EPS dentro del período y había otra antes,
         * hubo traslado ({@code tae} = a esta EPS, {@code tde} = desde la anterior).
         */
        @Query("""
                SELECT a FROM ContratoAfiliacionEntity a
                 WHERE a.contrato.id = :contratoId
                   AND a.fechaDesde BETWEEN :desde AND :hasta
                 ORDER BY a.tipo, a.fechaDesde
                """)
        List<ContratoAfiliacionEntity> findCambiosEnPeriodo(@Param("contratoId") Long contratoId,
                                                            @Param("desde") LocalDate desde,
                                                            @Param("hasta") LocalDate hasta);
    }
}

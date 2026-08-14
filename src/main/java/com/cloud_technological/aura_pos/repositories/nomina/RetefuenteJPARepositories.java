package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.EmpleadoDeduccionRentaEntity;
import com.cloud_technological.aura_pos.entity.RetefuentePorcentajeFijoEntity;
import com.cloud_technological.aura_pos.entity.RetefuenteRangoEntity;
import com.cloud_technological.aura_pos.entity.UvtValorEntity;

/**
 * Repositorios de retefuente (Fase 4.5). Agrupados: son cuatro interfaces
 * chicas del mismo subdominio.
 */
public final class RetefuenteJPARepositories {

    private RetefuenteJPARepositories() {}

    @Repository
    public interface UvtValorRepo extends JpaRepository<UvtValorEntity, Integer> {
        Optional<UvtValorEntity> findByAgno(Integer agno);

        /** UVT más reciente disponible: fallback si no hay del año pedido. */
        Optional<UvtValorEntity> findTopByOrderByAgnoDesc();
    }

    @Repository
    public interface RetefuenteRangoRepo extends JpaRepository<RetefuenteRangoEntity, Long> {

        /**
         * Rango que aplica a una base en UVT.
         *
         * <p>Rango [desde, hasta): el borde inferior incluye, el superior no.
         * Es el criterio del art. 383; verificar con contador.
         */
        @Query("""
                SELECT r FROM RetefuenteRangoEntity r
                 WHERE r.agno = :agno
                   AND r.uvtDesde <= :baseUvt
                   AND (r.uvtHasta IS NULL OR r.uvtHasta > :baseUvt)
                 ORDER BY r.uvtDesde DESC
                """)
        List<RetefuenteRangoEntity> findAplicables(@Param("agno") Integer agno,
                                                   @Param("baseUvt") BigDecimal baseUvt);

        default Optional<RetefuenteRangoEntity> findRango(Integer agno, BigDecimal baseUvt) {
            return findAplicables(agno, baseUvt).stream().findFirst();
        }
    }

    @Repository
    public interface RetefuentePorcentajeFijoRepo
            extends JpaRepository<RetefuentePorcentajeFijoEntity, Long> {

        Optional<RetefuentePorcentajeFijoEntity> findByContratoIdAndSemestre(Long contratoId, String semestre);
    }

    @Repository
    public interface EmpleadoDeduccionRentaRepo
            extends JpaRepository<EmpleadoDeduccionRentaEntity, Long> {

        /** Deducciones vigentes de un contrato en una fecha. */
        @Query("""
                SELECT d FROM EmpleadoDeduccionRentaEntity d
                 WHERE d.contrato.id = :contratoId
                   AND d.vigenteDesde <= :fecha
                   AND (d.vigenteHasta IS NULL OR d.vigenteHasta >= :fecha)
                """)
        List<EmpleadoDeduccionRentaEntity> findVigentes(@Param("contratoId") Long contratoId,
                                                        @Param("fecha") LocalDate fecha);

        /** Todas las deducciones de un contrato, para la pantalla de gestión. */
        List<EmpleadoDeduccionRentaEntity> findByContratoIdOrderByVigenteDesdeDesc(Long contratoId);

        java.util.Optional<EmpleadoDeduccionRentaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    }
}

package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.NominaElectronicaLogEntity;

/** Repositorios de nómina electrónica (Fase 5). */
public final class NominaElectronicaJPARepositories {

    private NominaElectronicaJPARepositories() {}

    @Repository
    public interface NominaElectronicaRepo extends JpaRepository<NominaElectronicaEntity, Long> {

        /** El documento ORIGINAL de una nómina. Los ajustes son filas aparte. */
        @Query("""
                SELECT ne FROM NominaElectronicaEntity ne
                 WHERE ne.nomina.id = :nominaId
                   AND ne.esAjuste = FALSE
                """)
        Optional<NominaElectronicaEntity> findOriginalByNomina(@Param("nominaId") Long nominaId);

        /** Pendientes de envío o rechazados: lo que el job debe reintentar. */
        @Query("""
                SELECT ne FROM NominaElectronicaEntity ne
                 WHERE ne.empresaId = :empresaId
                   AND ne.estado IN ('PENDIENTE', 'RECHAZADO')
                 ORDER BY ne.id
                """)
        List<NominaElectronicaEntity> findPendientes(@Param("empresaId") Integer empresaId);

        List<NominaElectronicaEntity> findByEmpresaIdAndAgnoAndMes(Integer empresaId, Integer agno, Integer mes);

        /** Todas las nóminas electrónicas de la empresa, más recientes primero. */
        List<NominaElectronicaEntity> findByEmpresaIdOrderByIdDesc(Integer empresaId);

        /** Ubica el documento por la referencia con la que se registró en Factus. */
        Optional<NominaElectronicaEntity> findByEmpresaIdAndReferenceCode(Integer empresaId, String referenceCode);

        /**
         * Reserva el siguiente consecutivo de forma atómica.
         *
         * <p>{@code ON CONFLICT DO UPDATE} en una sola sentencia: dos hilos que
         * pidan a la vez obtienen números distintos. Hacerlo con
         * {@code SELECT max()+1} sería una condición de carrera, y un
         * consecutivo duplicado ante la DIAN no se deshace.
         *
         * @return el consecutivo reservado
         */
        @Modifying
        @Query(value = """
                INSERT INTO nomina_electronica_consecutivo (empresa_id, agno, ultimo)
                VALUES (:empresaId, :agno, 1)
                ON CONFLICT (empresa_id, agno)
                DO UPDATE SET ultimo = nomina_electronica_consecutivo.ultimo + 1
                RETURNING ultimo
                """, nativeQuery = true)
        Long reservarConsecutivo(@Param("empresaId") Integer empresaId, @Param("agno") Integer agno);
    }

    @Repository
    public interface NominaElectronicaLogRepo extends JpaRepository<NominaElectronicaLogEntity, Long> {
        List<NominaElectronicaLogEntity> findByNominaElectronicaIdOrderByIdDesc(Long nominaElectronicaId);
    }
}

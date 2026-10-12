package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

        /**
         * Documentos ORIGINALES de una nómina, del más reciente al más antiguo. Hay más
         * de uno cuando el anterior se eliminó con nota de ajuste y se reenvió corregido.
         * Los ajustes son filas aparte.
         */
        @Query("""
                SELECT ne FROM NominaElectronicaEntity ne
                 WHERE ne.nomina.id = :nominaId
                   AND ne.esAjuste = FALSE
                 ORDER BY ne.id DESC
                """)
        java.util.List<NominaElectronicaEntity> findOriginalesByNomina(@Param("nominaId") Long nominaId);

        /** El documento original vigente de la nómina: el último enviado. */
        default Optional<NominaElectronicaEntity> findOriginalByNomina(Long nominaId) {
            return findOriginalesByNomina(nominaId).stream().findFirst();
        }

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

        // El consecutivo se reserva en NominaElectronicaQueryRepository.reservarConsecutivo.
    }

    @Repository
    public interface NominaElectronicaLogRepo extends JpaRepository<NominaElectronicaLogEntity, Long> {
        List<NominaElectronicaLogEntity> findByNominaElectronicaIdOrderByIdDesc(Long nominaElectronicaId);
    }
}

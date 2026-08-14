package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.PilaAportanteConfigEntity;
import com.cloud_technological.aura_pos.entity.PilaCatalogoEntity;
import com.cloud_technological.aura_pos.entity.PilaCotizanteEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.entity.PilaEntidadEntity;
import com.cloud_technological.aura_pos.entity.PilaLayoutCampoEntity;
import com.cloud_technological.aura_pos.entity.PilaPlanillaEntity;
import com.cloud_technological.aura_pos.entity.PilaTipoCotizanteEntity;

/** Repositorios de PILA (Fase 6). */
public final class PilaJPARepositories {

    private PilaJPARepositories() {}

    /** Catálogo global de tipos de cotizante (P2). */
    @Repository
    public interface PilaTipoCotizanteRepo extends JpaRepository<PilaTipoCotizanteEntity, String> {
    }

    /** Catálogo global de entidades EPS/AFP/ARL/CCF (P2b). */
    @Repository
    public interface PilaEntidadRepo extends JpaRepository<PilaEntidadEntity, Long> {
        Optional<PilaEntidadEntity> findByTipoAndCodigo(String tipo, String codigo);
    }

    /** Catálogo global clave-valor (tipo planilla, tipo aportante…) (P2). */
    @Repository
    public interface PilaCatalogoRepo extends JpaRepository<PilaCatalogoEntity, Long> {
        List<PilaCatalogoEntity> findByDominio(String dominio);
    }

    /** Configuración del aportante por empresa (P4b). */
    @Repository
    public interface PilaAportanteConfigRepo extends JpaRepository<PilaAportanteConfigEntity, Integer> {
    }

    /** Layout del archivo PILA (P6). */
    @Repository
    public interface PilaLayoutCampoRepo extends JpaRepository<PilaLayoutCampoEntity, Long> {
        List<PilaLayoutCampoEntity> findByTipoRegistroAndActivoTrueOrderByOrdenAsc(String tipoRegistro);
    }

    @Repository
    public interface PilaEncabezadoRepo extends JpaRepository<PilaEncabezadoEntity, Long> {
        Optional<PilaEncabezadoEntity> findByEmpresaIdAndPeriodo(Integer empresaId, String periodo);
        List<PilaEncabezadoEntity> findByEmpresaIdOrderByPeriodoDesc(Integer empresaId);
    }

    @Repository
    public interface PilaPlanillaRepo extends JpaRepository<PilaPlanillaEntity, Long> {
        List<PilaPlanillaEntity> findByEncabezadoId(Long encabezadoId);
    }

    @Repository
    public interface PilaCotizanteRepo extends JpaRepository<PilaCotizanteEntity, Long> {
        List<PilaCotizanteEntity> findByPlanillaIdOrderBySecuencia(Long planillaId);

        /**
         * Borra los cotizantes de una planilla como un DELETE inmediato.
         *
         * <p>Un {@code deleteBy...} derivado encola los remove() y Hibernate los
         * ejecuta DESPUÉS de los inserts al hacer flush: la regeneración chocaría
         * con la única {@code (planilla_id, secuencia)}. El bulk delete corre ya.
         */
        @Modifying
        @Query("DELETE FROM PilaCotizanteEntity c WHERE c.planilla.id = :planillaId")
        void deleteByPlanillaId(@Param("planillaId") Long planillaId);
    }
}

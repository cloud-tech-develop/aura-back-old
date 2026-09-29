package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.ExtractoLineaEntity;

public interface ExtractoLineaJPARepository extends JpaRepository<ExtractoLineaEntity, Long> {

    List<ExtractoLineaEntity> findByExtractoIdOrderByFechaAscIdAsc(Long extractoId);

    Optional<ExtractoLineaEntity> findByIdAndExtractoId(Long id, Long extractoId);

    long countByExtractoIdAndEstado(Long extractoId, String estado);

    /** ¿El detalle del libro ya está tomado por otra línea (de cualquier extracto)? */
    boolean existsByAsientoDetalleId(Long asientoDetalleId);
}

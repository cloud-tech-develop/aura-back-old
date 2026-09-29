package com.cloud_technological.aura_pos.repositories.movimiento_caja;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;

public interface MovimientoCajaJPARepository extends JpaRepository<MovimientoCajaEntity, Long> {
    List<MovimientoCajaEntity> findByTurnoCajaIdOrderByCreatedAtAsc(Long turnoCajaId);

    /** Los movimientos que dejó un documento; los busca su anulación. */
    List<MovimientoCajaEntity> findByOrigenTipoAndOrigenId(String origenTipo, Long origenId);
}

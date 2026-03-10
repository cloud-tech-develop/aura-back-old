package com.cloud_technological.aura_pos.repositories.turno_caja;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;

public interface TurnoCajaJPARepository extends JpaRepository<TurnoCajaEntity, Long> {
    Optional<TurnoCajaEntity> findByIdAndCajaSucursalEmpresaId(Long id, Integer empresaId);
    // Verificar si ya hay un turno abierto en esa caja
    Optional<TurnoCajaEntity> findByCajaIdAndEstado(Long cajaId, String estado);
    // Turno abierto del usuario actual
    Optional<TurnoCajaEntity> findByUsuarioIdAndEstado(Long usuarioId, String estado);
}
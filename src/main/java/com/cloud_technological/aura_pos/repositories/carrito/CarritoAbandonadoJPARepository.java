package com.cloud_technological.aura_pos.repositories.carrito;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CarritoAbandonadoEntity;

public interface CarritoAbandonadoJPARepository extends JpaRepository<CarritoAbandonadoEntity, Long> {
}

package com.cloud_technological.aura_pos.repositories.notas_contables;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.NotaContableEntity;

@Repository
public interface NotaContableJPARepository extends JpaRepository<NotaContableEntity, Long> {
    
    List<NotaContableEntity> findByFacturaIdOrderByCreatedAtDesc(Long facturaId);
    
    Optional<NotaContableEntity> findByIdAndFacturaId(Long id, Long facturaId);
}

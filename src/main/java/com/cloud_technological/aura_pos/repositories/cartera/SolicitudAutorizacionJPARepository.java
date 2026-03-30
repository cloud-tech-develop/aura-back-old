package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SolicitudAutorizacionCreditoEntity;

public interface SolicitudAutorizacionJPARepository extends JpaRepository<SolicitudAutorizacionCreditoEntity, Long> {
    List<SolicitudAutorizacionCreditoEntity> findByEmpresaIdAndEstadoOrderByCreatedAtDesc(Integer empresaId, String estado);
}

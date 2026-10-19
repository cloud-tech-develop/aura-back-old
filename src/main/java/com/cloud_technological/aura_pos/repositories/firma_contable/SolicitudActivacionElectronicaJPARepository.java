package com.cloud_technological.aura_pos.repositories.firma_contable;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.SolicitudActivacionElectronicaEntity;

/** Regla del proyecto: por JPA solo findById/save/delete; las consultas van en ActivacionElectronicaQueryRepository. */
public interface SolicitudActivacionElectronicaJPARepository
        extends JpaRepository<SolicitudActivacionElectronicaEntity, Long> {
}

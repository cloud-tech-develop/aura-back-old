package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ContratoRenovacionEntity;

@Repository
public interface ContratoRenovacionJPARepository extends JpaRepository<ContratoRenovacionEntity, Long> {

    List<ContratoRenovacionEntity> findByContratoIdOrderByFechaInicialDesc(Long contratoId);

    /**
     * Cuántas veces se ha prorrogado un contrato.
     *
     * <p>Importa legalmente: un contrato a término fijo inferior a un año solo
     * se puede prorrogar tres veces; a partir de la cuarta el término mínimo
     * pasa a ser de un año.
     */
    long countByContratoId(Long contratoId);
}

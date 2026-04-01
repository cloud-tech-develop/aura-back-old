package com.cloud_technological.aura_pos.repositories.comprobante_caja;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cloud_technological.aura_pos.entity.ComprobanteCajaEntity;

public interface ComprobanteCajaJPARepository extends JpaRepository<ComprobanteCajaEntity, Long> {
    Optional<ComprobanteCajaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    Optional<ComprobanteCajaEntity> findByNumeroComprobante(String numeroComprobante);
}

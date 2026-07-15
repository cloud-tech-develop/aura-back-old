package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.DividendoPagoEntity;

public interface DividendoPagoJPARepository
        extends JpaRepository<DividendoPagoEntity, Long> {

    Optional<DividendoPagoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<DividendoPagoEntity> findByDistribucionIdOrderByFechaAscIdAsc(Long distribucionId);
}

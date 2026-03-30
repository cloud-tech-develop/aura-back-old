package com.cloud_technological.aura_pos.repositories.tesoreria;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;

public interface CuentaBancariaJPARepository extends JpaRepository<CuentaBancariaEntity, Long> {
    List<CuentaBancariaEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);
    Optional<CuentaBancariaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}

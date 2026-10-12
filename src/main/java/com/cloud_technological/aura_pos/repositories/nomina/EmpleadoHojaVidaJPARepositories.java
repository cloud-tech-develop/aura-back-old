package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.EmpleadoCuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoExperienciaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoFamiliarEntity;

/**
 * Repositorios JPA de la hoja de vida del empleado. Solo consultas simples; los
 * listados están en {@link EmpleadoHojaVidaQueryRepository}.
 */
public final class EmpleadoHojaVidaJPARepositories {

    private EmpleadoHojaVidaJPARepositories() {
    }

    public interface CuentaBancariaRepo extends JpaRepository<EmpleadoCuentaBancariaEntity, Long> {
        Optional<EmpleadoCuentaBancariaEntity> findByIdAndEmpleadoId(Long id, Long empleadoId);
    }

    public interface FamiliarRepo extends JpaRepository<EmpleadoFamiliarEntity, Long> {
        Optional<EmpleadoFamiliarEntity> findByIdAndEmpleadoId(Long id, Long empleadoId);
    }

    public interface ExperienciaRepo extends JpaRepository<EmpleadoExperienciaEntity, Long> {
        Optional<EmpleadoExperienciaEntity> findByIdAndEmpleadoId(Long id, Long empleadoId);
    }
}

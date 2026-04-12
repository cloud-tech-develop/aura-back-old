package com.cloud_technological.aura_pos.repositories.tipo_empleado;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.TipoEmpleadoEntity;

@Repository
public interface TipoEmpleadoJPARepository extends JpaRepository<TipoEmpleadoEntity, Long> {

    @Query("SELECT te FROM TipoEmpleadoEntity te WHERE te.empresa.id = :empresaId OR te.empresa.id = 1 AND te.activo = true")
    List<TipoEmpleadoEntity> findByEmpresaIdAndActivoTrue(Long empresaId);

    boolean existsByEmpresaIdAndNombreAndActivoTrue(Long empresaId, String nombre);

    boolean existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(Long empresaId, String nombre, Long id);
}

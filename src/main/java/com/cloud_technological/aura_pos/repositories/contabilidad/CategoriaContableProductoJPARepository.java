package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CategoriaContableProductoEntity;

public interface CategoriaContableProductoJPARepository
        extends JpaRepository<CategoriaContableProductoEntity, Long> {

    List<CategoriaContableProductoEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);

    Optional<CategoriaContableProductoEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    Optional<CategoriaContableProductoEntity> findByEmpresaIdAndNombre(Integer empresaId, String nombre);
}

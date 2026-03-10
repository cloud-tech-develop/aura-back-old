package com.cloud_technological.aura_pos.repositories.categorias;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.CategoriaEntity;

public interface CategoriaJPARepository extends JpaRepository<CategoriaEntity, Long> {
    // Para validar duplicados en la misma empresa
    boolean existsByNombreAndEmpresaIdAndDeletedAtIsNull(String nombre, Integer empresaId);
    
    // Para evitar duplicados al editar (excluyendo el propio ID)
    boolean existsByNombreAndEmpresaIdAndIdNotAndDeletedAtIsNull(String nombre, Integer empresaId, Long id);

    // Buscar por ID y Empresa (Seguridad)
    Optional<CategoriaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
}
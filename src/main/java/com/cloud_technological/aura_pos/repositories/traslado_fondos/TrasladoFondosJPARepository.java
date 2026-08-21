package com.cloud_technological.aura_pos.repositories.traslado_fondos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloud_technological.aura_pos.entity.TrasladoFondosEntity;

public interface TrasladoFondosJPARepository extends JpaRepository<TrasladoFondosEntity, Long> {

    Optional<TrasladoFondosEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<TrasladoFondosEntity> findByEmpresaIdAndFechaBetweenOrderByFechaDescIdDesc(
            Integer empresaId, LocalDate desde, LocalDate hasta);

    List<TrasladoFondosEntity> findByEmpresaIdAndConceptoAndFechaBetweenOrderByFechaDescIdDesc(
            Integer empresaId, String concepto, LocalDate desde, LocalDate hasta);
}

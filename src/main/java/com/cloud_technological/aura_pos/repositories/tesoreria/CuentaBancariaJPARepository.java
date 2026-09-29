package com.cloud_technological.aura_pos.repositories.tesoreria;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;

public interface CuentaBancariaJPARepository extends JpaRepository<CuentaBancariaEntity, Long> {
    List<CuentaBancariaEntity> findByEmpresaIdOrderByNombreAsc(Integer empresaId);
    Optional<CuentaBancariaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);
    Optional<CuentaBancariaEntity> findFirstByEmpresaIdAndTipoAndActivaIsTrue(Integer empresaId, String tipo);

    boolean existsByEmpresaIdAndCodigoIgnoreCase(Integer empresaId, String codigo);

    boolean existsByEmpresaIdAndCodigoIgnoreCaseAndIdNot(Integer empresaId, String codigo, Long id);

    /** Mayor consecutivo de la serie automática CB-###; 0 si no hay ninguno. */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM 4) AS INTEGER)), 0)
              FROM cuenta_bancaria
             WHERE empresa_id = :empresaId AND codigo ~ '^CB-[0-9]{1,9}$'
            """, nativeQuery = true)
    Integer maxConsecutivoCodigo(@Param("empresaId") Integer empresaId);
}

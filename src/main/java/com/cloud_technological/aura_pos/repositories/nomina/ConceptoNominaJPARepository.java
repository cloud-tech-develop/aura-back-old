package com.cloud_technological.aura_pos.repositories.nomina;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;

@Repository
public interface ConceptoNominaJPARepository extends JpaRepository<ConceptoNominaEntity, Long> {

    /**
     * Conceptos aplicables a una empresa en una fecha.
     *
     * <p>Devuelve los globales ({@code empresa_id IS NULL}) más los propios de
     * la empresa. Si la empresa personalizó un código, aparecen los dos — la
     * resolución del ganador va en el servicio, no aquí (una query no debería
     * decidir reglas de negocio).
     *
     * <p>Ordenado por {@code orden}: los conceptos que dependen de otros deben
     * calcularse después de sus bases.
     */
    @Query("""
            SELECT c FROM ConceptoNominaEntity c
             WHERE (c.empresaId IS NULL OR c.empresaId = :empresaId)
               AND c.activo = TRUE
               AND c.vigenteDesde <= :fecha
               AND (c.vigenteHasta IS NULL OR c.vigenteHasta >= :fecha)
             ORDER BY c.orden, c.id
            """)
    List<ConceptoNominaEntity> findVigentes(@Param("empresaId") Integer empresaId,
                                            @Param("fecha") LocalDate fecha);

    /**
     * Un concepto por código, vigente en una fecha.
     *
     * <p>Prefiere el de la empresa sobre el global: {@code ORDER BY empresaId
     * NULLS LAST} pone primero la personalización si existe.
     */
    @Query("""
            SELECT c FROM ConceptoNominaEntity c
             WHERE c.codigo = :codigo
               AND (c.empresaId IS NULL OR c.empresaId = :empresaId)
               AND c.activo = TRUE
               AND c.vigenteDesde <= :fecha
               AND (c.vigenteHasta IS NULL OR c.vigenteHasta >= :fecha)
             ORDER BY c.empresaId NULLS LAST
            """)
    List<ConceptoNominaEntity> findVigentePorCodigo(@Param("empresaId") Integer empresaId,
                                                    @Param("codigo") String codigo,
                                                    @Param("fecha") LocalDate fecha);

    Optional<ConceptoNominaEntity> findByIdAndEmpresaId(Long id, Integer empresaId);

    List<ConceptoNominaEntity> findByCodigoOrderByVigenteDesdeDesc(String codigo);
}

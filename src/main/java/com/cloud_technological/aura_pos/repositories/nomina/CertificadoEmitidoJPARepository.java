package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.entity.CertificadoEmitidoEntity;

@Repository
public interface CertificadoEmitidoJPARepository extends JpaRepository<CertificadoEmitidoEntity, Long> {

    /**
     * Certificado anual ya emitido.
     *
     * <p>Si existe, se devuelve el mismo: un certificado es un documento con
     * valor probatorio, no se regenera.
     */
    @Query("""
            SELECT c FROM CertificadoEmitidoEntity c
             WHERE c.tercero.id = :terceroId
               AND c.tipo = :tipo
               AND c.agno = :agno
             ORDER BY c.emitidoAt DESC
            """)
    List<CertificadoEmitidoEntity> findAnual(@Param("terceroId") Long terceroId,
                                             @Param("tipo") String tipo,
                                             @Param("agno") Integer agno);

    Optional<CertificadoEmitidoEntity> findFirstByNominaIdAndTipo(Long nominaId, String tipo);

    List<CertificadoEmitidoEntity> findByTerceroIdOrderByEmitidoAtDesc(Long terceroId);

    /**
     * Suma anual de un concepto para el certificado de ingresos y retenciones.
     *
     * <p>Agrega {@code nomina_detalle} del año. Excluye nóminas anuladas.
     */
    @Query("""
            SELECT COALESCE(SUM(d.valor), 0)
              FROM NominaDetalleEntity d
             WHERE d.nomina.contrato.empleado.tercero.id = :terceroId
               AND d.nomina.estado <> 'ANULADO'
               AND d.concepto.codigo IN :codigos
               AND EXTRACT(YEAR FROM d.nomina.periodo.fechaFin) = :agno
            """)
    BigDecimal sumarAnualPorConceptos(@Param("terceroId") Long terceroId,
                                      @Param("codigos") List<String> codigos,
                                      @Param("agno") Integer agno);
}

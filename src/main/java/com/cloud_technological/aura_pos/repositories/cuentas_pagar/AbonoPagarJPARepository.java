package com.cloud_technological.aura_pos.repositories.cuentas_pagar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;

public interface AbonoPagarJPARepository extends JpaRepository<AbonoPagarEntity, Long> {
    Optional<AbonoPagarEntity> findByIdAndCuentaPagarId(Long id, Long cuentaPagarId);
    List<AbonoPagarEntity> findByCuentaPagarId(Long cuentaPagarId);

    /** Los abonos que dejó un documento concreto; los busca su reversa. */
    List<AbonoPagarEntity> findByCuentaPagarIdAndReferencia(Long cuentaPagarId, String referencia);
    List<AbonoPagarEntity> findByTurnoCajaIdOrderByFechaPagoAsc(Long turnoCajaId);

    // Solo el efectivo sale del cajón; un pago por banco no baja el arqueo.
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM AbonoPagarEntity a "
            + "WHERE a.turnoCaja.id = :turnoCajaId "
            + "AND (a.metodoPago IS NULL OR UPPER(a.metodoPago) LIKE '%EFECTIVO%')")
    BigDecimal sumMontoByTurnoCajaId(@Param("turnoCajaId") Long turnoCajaId);
}

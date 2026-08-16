package com.cloud_technological.aura_pos.repositories.cuentas_cobrar;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;

public interface AbonoCobrarJPARepository extends JpaRepository<AbonoCobrarEntity, Long> {
    Optional<AbonoCobrarEntity> findByIdAndCuentaCobrarId(Long id, Long cuentaCobrarId);
    List<AbonoCobrarEntity> findByCuentaCobrarId(Long cuentaCobrarId);
    List<AbonoCobrarEntity> findByTurnoCajaIdOrderByFechaPagoAsc(Long turnoCajaId);

    // Solo el efectivo cuenta para el arqueo: un abono con datáfono o
    // transferencia queda atado al turno para trazabilidad, pero si se sumara
    // aquí el cajero cerraría con un faltante que nunca existió.
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM AbonoCobrarEntity a "
            + "WHERE a.turnoCaja.id = :turnoCajaId "
            + "AND (a.metodoPago IS NULL OR UPPER(a.metodoPago) LIKE '%EFECTIVO%')")
    BigDecimal sumMontoByTurnoCajaId(@Param("turnoCajaId") Long turnoCajaId);
}

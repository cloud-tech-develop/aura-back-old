package com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.entity.DiferidoAmortizacionEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DiferidoAmortizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Amortización mensual de gastos diferidos (E6): el día 1 de cada mes (o a
 * demanda) genera la cuota del período por cada gasto diferido con cuotas
 * pendientes. Idempotente por (gasto, período): reejecutar no duplica.
 */
@Service
@RequiredArgsConstructor
public class DiferidoService {

    private static final Logger log = LoggerFactory.getLogger(DiferidoService.class);
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GastoJPARepository gastoRepo;
    private final DiferidoAmortizacionJPARepository amortizacionRepo;
    private final ApplicationEventPublisher eventPublisher;

    /** Día 1 de cada mes a las 03:00. */
    @Scheduled(cron = "0 0 3 1 * *")
    public void amortizarMesActual() {
        int generadas = amortizar(LocalDate.now());
        log.info("Amortización de diferidos: {} cuota(s) generada(s)", generadas);
    }

    /** Corre la amortización del mes de la fecha dada. Devuelve cuántas cuotas generó. */
    @Transactional
    public int amortizar(LocalDate fecha) {
        String periodo = fecha.format(PERIODO);
        int generadas = 0;
        for (GastoEntity gasto : gastoRepo.findByEsDiferidoTrue()) {
            if (gasto.getMesesDiferido() == null || gasto.getMesesDiferido() <= 0
                    || gasto.getEmpresa() == null) {
                continue;
            }
            long cuotasGeneradas = amortizacionRepo.countByGastoId(gasto.getId());
            if (cuotasGeneradas >= gasto.getMesesDiferido()
                    || amortizacionRepo.existsByGastoIdAndPeriodo(gasto.getId(), periodo)) {
                continue;
            }

            Integer empresaId = gasto.getEmpresa().getId();
            BigDecimal total = ReglasAsiento.nz(gasto.getMonto());
            BigDecimal cuota = total.divide(
                    BigDecimal.valueOf(gasto.getMesesDiferido()), ReglasAsiento.ESCALA,
                    ReglasAsiento.REDONDEO);
            // La última cuota absorbe el residuo de redondeo.
            if (cuotasGeneradas == gasto.getMesesDiferido() - 1) {
                cuota = total.subtract(cuota.multiply(
                        BigDecimal.valueOf(gasto.getMesesDiferido() - 1)));
            }

            DiferidoAmortizacionEntity amortizacion = amortizacionRepo.save(
                    DiferidoAmortizacionEntity.builder()
                            .empresaId(empresaId)
                            .gastoId(gasto.getId())
                            .periodo(periodo)
                            .monto(cuota)
                            .build());
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    "DIFERIDO", amortizacion.getId(), empresaId, null));
            generadas++;
        }
        return generadas;
    }
}

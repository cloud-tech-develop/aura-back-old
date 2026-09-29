package com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeterioroCalculoJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Deterioro de cartera por edades (E6): calcula la propuesta con los tramos
 * parametrizados (% por días de mora sobre el saldo vencido) y genera el
 * asiento DB 5199 · CR 1399 SIEMPRE en borrador — el contador aprueba.
 */
@Service
@RequiredArgsConstructor
public class DeterioroService {

    private final CuentaCobrarJPARepository cuentaCobrarRepo;
    private final DeterioroCalculoJPARepository calculoRepo;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Calcula la propuesta sin persistir nada (vista previa).
     *
     * @param tramos días de mora mínimos → % (ej. {31: 5, 91: 20, 181: 50});
     *               a cada factura vencida se le aplica el tramo mayor que alcance
     */
    public Map<String, Object> calcular(Integer empresaId, Map<Integer, BigDecimal> tramos) {
        if (tramos == null || tramos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Defina al menos un tramo: días de mora → porcentaje.");
        }
        TreeMap<Integer, BigDecimal> porDias = new TreeMap<>(tramos);
        LocalDate hoy = LocalDate.now();

        BigDecimal total = BigDecimal.ZERO;
        int facturas = 0;
        for (CuentaCobrarEntity cxc : cuentaCobrarRepo.findByEmpresaIdAndEstado(empresaId, "activa")) {
            if (cxc.getFechaVencimiento() == null
                    || ReglasAsiento.nz(cxc.getSaldoPendiente()).signum() <= 0) {
                continue;
            }
            long diasMora = ChronoUnit.DAYS.between(cxc.getFechaVencimiento().toLocalDate(), hoy);
            var tramo = porDias.floorEntry((int) Math.min(diasMora, Integer.MAX_VALUE));
            if (diasMora <= 0 || tramo == null || tramo.getValue().signum() <= 0) {
                continue;
            }
            BigDecimal deterioro = cxc.getSaldoPendiente()
                    .multiply(tramo.getValue())
                    .divide(BigDecimal.valueOf(100), ReglasAsiento.ESCALA, ReglasAsiento.REDONDEO);
            total = total.add(deterioro);
            facturas++;
        }
        return Map.of("fecha", hoy.toString(), "facturasVencidas", facturas,
                "montoPropuesto", total, "tramos", porDias.toString());
    }

    /** Persiste la propuesta y dispara su asiento en BORRADOR. */
    @Transactional
    public DeterioroCalculoEntity proponer(Integer empresaId, Long usuarioId,
            Map<Integer, BigDecimal> tramos) {
        Map<String, Object> calculo = calcular(empresaId, tramos);
        BigDecimal monto = (BigDecimal) calculo.get("montoPropuesto");
        if (monto.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No hay cartera vencida que deteriorar con esos tramos.");
        }
        DeterioroCalculoEntity propuesta = calculoRepo.save(DeterioroCalculoEntity.builder()
                .empresaId(empresaId)
                .fecha(LocalDate.now())
                .monto(monto)
                .detalle(calculo.get("facturasVencidas") + " facturas, tramos " + calculo.get("tramos"))
                .usuarioId(usuarioId)
                .build());
        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                "DETERIORO", propuesta.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));
        return propuesta;
    }
}

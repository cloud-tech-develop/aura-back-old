package com.cloud_technological.aura_pos.contabilidad.infrastructure.cierre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.contabilidad.domain.ReglasAsiento;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosLineaDto;
import com.cloud_technological.aura_pos.entity.CierreAnualEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.DistribucionUtilidadesEntity;
import com.cloud_technological.aura_pos.entity.DividendoPagoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.CierreAnualJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DistribucionUtilidadesJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DividendoPagoJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;

import lombok.RequiredArgsConstructor;

/**
 * Cierre anual fiscal (E8): el sistema SUGIERE (utilidad × tarifa, 10% de
 * reserva) pero el contador DIGITA los valores — la renta fiscal no es la
 * contable y la distribución la decide la asamblea. Cada operación queda
 * persistida y su asiento nace por el registry (CIERRE_ANUAL /
 * DISTRIBUCION_UTILIDAD / DIVIDENDO_PAGO), siempre CONTABILIZADO.
 */
@Service
@RequiredArgsConstructor
public class CierreAnualService {

    /** Tarifa general de renta para la sugerencia; el contador digita el valor real. */
    private static final BigDecimal TARIFA_RENTA_DEFAULT = new BigDecimal("35");
    private static final BigDecimal PCT_RESERVA_LEGAL = new BigDecimal("10");
    private static final BigDecimal PCT_TOPE_RESERVA_CAPITAL = new BigDecimal("50");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final CierreAnualJPARepository cierreRepo;
    private final DistribucionUtilidadesJPARepository distribucionRepo;
    private final DividendoPagoJPARepository pagoRepo;
    private final AsientoContableQueryRepository queryRepo;
    private final ConfiguracionContableService config;
    private final ApplicationEventPublisher eventPublisher;

    // ── Paso 1 del wizard: provisión de renta ────────────────────────────

    /**
     * Sugerencia de provisión: utilidad contable del año (excluyendo el
     * propio gasto 54xx) × tarifa. Solo orienta — el valor lo digita el
     * contador porque la renta fiscal difiere de la contable.
     */
    public Map<String, Object> sugerirProvisionRenta(Integer empresaId, int anio, BigDecimal tarifa) {
        BigDecimal t = tarifa != null ? tarifa : TARIFA_RENTA_DEFAULT;
        List<EstadoResultadosLineaDto> lineas = queryRepo.estadoResultados(
                empresaId, anio + "-01-01", anio + "-12-31");

        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal costos = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        for (EstadoResultadosLineaDto l : lineas) {
            BigDecimal saldo = ReglasAsiento.nz(l.getSaldo());
            switch (l.getTipo()) {
                case "INGRESO" -> ingresos = ingresos.add(saldo);
                case "COSTO" -> costos = costos.add(saldo);
                case "GASTO" -> {
                    if (l.getCodigo() == null || !l.getCodigo().startsWith("54")) {
                        gastos = gastos.add(saldo);
                    }
                }
                default -> { }
            }
        }
        BigDecimal utilidad = ingresos.subtract(costos).subtract(gastos);
        BigDecimal sugerido = utilidad.signum() > 0
                ? ReglasAsiento.normalizar(utilidad.multiply(t).divide(CIEN, ReglasAsiento.ESCALA, ReglasAsiento.REDONDEO))
                : BigDecimal.ZERO;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("anio", anio);
        r.put("utilidadAntesDeImpuesto", utilidad);
        r.put("tarifa", t);
        r.put("provisionSugerida", sugerido);
        r.put("yaProvisionado", cierreRepo.existsByEmpresaIdAndAnioAndTipo(
                empresaId, anio, CierreAnualEntity.TIPO_PROVISION_RENTA));
        return r;
    }

    /** Registra la provisión con el valor DIGITADO → asiento DB 5405 · CR 2404. */
    @Transactional
    public CierreAnualEntity provisionarRenta(Integer empresaId, Long usuarioId,
            int anio, BigDecimal monto, String detalle, LocalDate fecha) {
        if (monto == null || monto.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La provisión de renta debe ser mayor que cero.");
        }
        if (cierreRepo.existsByEmpresaIdAndAnioAndTipo(
                empresaId, anio, CierreAnualEntity.TIPO_PROVISION_RENTA)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una provisión de renta registrada para el año " + anio + ".");
        }
        // La provisión pertenece al año que se cierra: por defecto 31/12.
        LocalDate f = fecha != null ? fecha : LocalDate.of(anio, 12, 31);
        CierreAnualEntity op = cierreRepo.save(CierreAnualEntity.builder()
                .empresaId(empresaId)
                .anio(anio)
                .tipo(CierreAnualEntity.TIPO_PROVISION_RENTA)
                .monto(monto)
                .detalle(detalle)
                .fecha(f)
                .usuarioId(usuarioId)
                .build());
        publicar("CIERRE_ANUAL", op.getId(), empresaId, usuarioId);
        return op;
    }

    // ── Apertura de año: traslado 3605 → 3705 ────────────────────────────

    /**
     * Traslada el saldo de utilidad/pérdida del ejercicio (3605) a resultados
     * acumulados (3705). Se corre al abrir el nuevo año, después del cierre
     * de diciembre (que es quien deja el resultado en 3605).
     */
    @Transactional
    public CierreAnualEntity trasladarUtilidad(Integer empresaId, Long usuarioId,
            int anio, LocalDate fecha) {
        if (cierreRepo.existsByEmpresaIdAndAnioAndTipo(
                empresaId, anio, CierreAnualEntity.TIPO_TRASLADO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El traslado a resultados acumulados del año " + anio + " ya se registró.");
        }
        Long cuenta3605 = config.resolverCuenta(empresaId, ConceptoContable.UTILIDAD_EJERCICIO).getId();
        // saldoCuenta es débito − crédito: la utilidad (saldo crédito) da negativo.
        BigDecimal monto = ReglasAsiento.nz(queryRepo.saldoCuenta(empresaId, cuenta3605)).negate();
        if (monto.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La cuenta de utilidad del ejercicio (3605) no tiene saldo que trasladar. "
                            + "Cierre primero el período de diciembre.");
        }
        CierreAnualEntity op = cierreRepo.save(CierreAnualEntity.builder()
                .empresaId(empresaId)
                .anio(anio)
                .tipo(CierreAnualEntity.TIPO_TRASLADO)
                .monto(monto)
                .detalle(monto.signum() > 0 ? "Utilidad del ejercicio " + anio
                        : "Pérdida del ejercicio " + anio)
                .fecha(fecha != null ? fecha : LocalDate.now())
                .usuarioId(usuarioId)
                .build());
        publicar("CIERRE_ANUAL", op.getId(), empresaId, usuarioId);
        return op;
    }

    public List<CierreAnualEntity> listarOperaciones(Integer empresaId) {
        return cierreRepo.findByEmpresaIdOrderByAnioDescCreatedAtDesc(empresaId);
    }

    // ── Distribución de utilidades (post-asamblea) ───────────────────────

    /** Saldos y sugerencia: reserva legal 10% con tope 50% del capital. */
    public Map<String, Object> sugerirDistribucion(Integer empresaId) {
        BigDecimal acumulados = saldoCredito(empresaId, ConceptoContable.RESULTADOS_ACUMULADOS);
        BigDecimal reservaActual = saldoCredito(empresaId, ConceptoContable.RESERVA_LEGAL);
        BigDecimal capital = saldoCredito(empresaId, ConceptoContable.CAPITAL_SOCIAL);

        BigDecimal reservaSugerida = BigDecimal.ZERO;
        if (acumulados.signum() > 0) {
            reservaSugerida = ReglasAsiento.normalizar(
                    acumulados.multiply(PCT_RESERVA_LEGAL).divide(CIEN, ReglasAsiento.ESCALA, ReglasAsiento.REDONDEO));
            if (capital.signum() > 0) {
                BigDecimal margenTope = tope(capital).subtract(reservaActual).max(BigDecimal.ZERO);
                reservaSugerida = reservaSugerida.min(margenTope);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("utilidadesAcumuladas", acumulados);
        r.put("reservaLegalActual", reservaActual);
        r.put("capitalSocial", capital);
        r.put("topeReservaLegal", capital.signum() > 0 ? tope(capital) : null);
        r.put("reservaSugerida", reservaSugerida);
        r.put("dividendosDisponibles", acumulados.subtract(reservaSugerida).max(BigDecimal.ZERO));
        return r;
    }

    /** Registra la distribución decidida por la asamblea → DB 3705 · CR 330505 / 2360. */
    @Transactional
    public DistribucionUtilidadesEntity distribuir(Integer empresaId, Long usuarioId, int anio,
            BigDecimal reservaLegal, BigDecimal dividendos, String observaciones, LocalDate fecha) {
        BigDecimal reserva = ReglasAsiento.nz(reservaLegal);
        BigDecimal div = ReglasAsiento.nz(dividendos);
        if (reserva.signum() < 0 || div.signum() < 0 || reserva.add(div).signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La distribución requiere reserva y/o dividendos positivos.");
        }
        if (distribucionRepo.existsByEmpresaIdAndAnio(empresaId, anio)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una distribución de utilidades para el año " + anio + ".");
        }
        BigDecimal acumulados = saldoCredito(empresaId, ConceptoContable.RESULTADOS_ACUMULADOS);
        if (reserva.add(div).compareTo(acumulados) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La distribución (" + reserva.add(div) + ") supera las utilidades acumuladas "
                            + "disponibles en 3705 (" + acumulados + "). ¿Ya corrió el traslado 3605→3705?");
        }
        BigDecimal capital = saldoCredito(empresaId, ConceptoContable.CAPITAL_SOCIAL);
        if (reserva.signum() > 0 && capital.signum() > 0) {
            BigDecimal reservaActual = saldoCredito(empresaId, ConceptoContable.RESERVA_LEGAL);
            if (reservaActual.add(reserva).compareTo(tope(capital)) > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "La reserva legal superaría el 50% del capital social ("
                                + tope(capital) + "). Reserva actual: " + reservaActual + ".");
            }
        }
        DistribucionUtilidadesEntity d = distribucionRepo.save(DistribucionUtilidadesEntity.builder()
                .empresaId(empresaId)
                .anio(anio)
                .utilidadBase(acumulados)
                .reservaLegal(reserva)
                .dividendos(div)
                .observaciones(observaciones)
                .fecha(fecha != null ? fecha : LocalDate.now())
                .usuarioId(usuarioId)
                .build());
        publicar("DISTRIBUCION_UTILIDAD", d.getId(), empresaId, usuarioId);
        return d;
    }

    public List<DistribucionUtilidadesEntity> listarDistribuciones(Integer empresaId) {
        return distribucionRepo.findByEmpresaIdOrderByAnioDesc(empresaId);
    }

    // ── Pago de dividendos decretados ────────────────────────────────────

    @Transactional
    public DividendoPagoEntity pagarDividendos(Integer empresaId, Long usuarioId,
            Long distribucionId, BigDecimal monto, String metodoPago,
            Long cuentaBancariaId, Long terceroId, LocalDate fecha) {
        DistribucionUtilidadesEntity d = distribucionRepo
                .findByIdAndEmpresaId(distribucionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Distribución de utilidades no encontrada"));
        if (monto == null || monto.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto del pago debe ser mayor que cero.");
        }
        BigDecimal pagado = pagoRepo.findByDistribucionIdOrderByFechaAscIdAsc(distribucionId)
                .stream().map(DividendoPagoEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendiente = d.getDividendos().subtract(pagado);
        if (monto.compareTo(pendiente) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El pago (" + monto + ") supera los dividendos pendientes ("
                            + pendiente + ") de la distribución " + d.getAnio() + ".");
        }
        DividendoPagoEntity pago = pagoRepo.save(DividendoPagoEntity.builder()
                .empresaId(empresaId)
                .distribucionId(distribucionId)
                .monto(monto)
                .metodoPago(metodoPago != null ? metodoPago : "EFECTIVO")
                .cuentaBancariaId(cuentaBancariaId)
                .terceroId(terceroId)
                .fecha(fecha != null ? fecha : LocalDate.now())
                .usuarioId(usuarioId)
                .build());
        publicar("DIVIDENDO_PAGO", pago.getId(), empresaId, usuarioId);
        return pago;
    }

    public List<DividendoPagoEntity> listarPagos(Integer empresaId, Long distribucionId) {
        distribucionRepo.findByIdAndEmpresaId(distribucionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Distribución de utilidades no encontrada"));
        return pagoRepo.findByDistribucionIdOrderByFechaAscIdAsc(distribucionId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Saldo crédito (positivo) de la cuenta del concepto; 0 si no hay movimientos. */
    private BigDecimal saldoCredito(Integer empresaId, ConceptoContable concepto) {
        Long cuentaId = config.resolverCuenta(empresaId, concepto).getId();
        return ReglasAsiento.nz(queryRepo.saldoCuenta(empresaId, cuentaId)).negate();
    }

    private static BigDecimal tope(BigDecimal capital) {
        return ReglasAsiento.normalizar(capital.multiply(PCT_TOPE_RESERVA_CAPITAL)
                .divide(CIEN, ReglasAsiento.ESCALA, ReglasAsiento.REDONDEO));
    }

    private void publicar(String tipoOrigen, Long origenId, Integer empresaId, Long usuarioId) {
        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                tipoOrigen, origenId, empresaId,
                usuarioId != null ? usuarioId.intValue() : null));
    }
}

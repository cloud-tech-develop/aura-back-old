package com.cloud_technological.aura_pos.services.cartera;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.DeterioroService;
import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.CandidatoCastigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Castigar;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Castigo;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.DeterioroFactura;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.DeterioroSugerido;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Politica;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.DeterioroCalculoEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.cartera.MoraCastigoQueryRepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;
import java.time.temporal.ChronoUnit;

/**
 * Cartera C9: deterioro sugerido y castigo (docs/PLAN_CARTERA.md).
 *
 * <p>Deterioro: aplica los tramos de la política a cada factura vencida y lo
 * compara con la provisión que ya hay en el mayor; solo propone lo que falta.
 * El asiento sale en borrador con el generador de siempre (DETERIORO).
 *
 * <p>Castigo: la factura incobrable sale de la cartera activa (estado
 * 'castigada', saldo 0, historial intacto). El asiento usa primero la provisión
 * (DB 1399) y el resto va al gasto (DB 5199) · CR 1305. Se puede anular: la
 * cuenta vuelve con su saldo y el asiento se reversa.
 */
@Service
@RequiredArgsConstructor
public class CastigoCarteraService {

    public static final String PREFIJO = "CAS";

    private final MoraCastigoQueryRepository query;
    private final InteresMoraService interesMora;
    private final DeterioroService deterioroService;
    private final ConfiguracionContableService configuracionContable;
    private final CuentaCobrarJPARepository cuentaCobrarRepo;
    private final PeriodoContableResolver periodoGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Una cuenta castigada está congelada: cobrarle o reversarle un pago dejaría
     * el saldo vivo con valor_castigado puesto y el asiento del castigo intacto.
     * Si el cliente paga después del castigo, primero se anula el castigo.
     */
    public static void exigirNoCastigada(CuentaCobrarEntity cuenta) {
        if (cuenta != null && "castigada".equals(cuenta.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La cuenta " + cuenta.getNumeroCuenta() + " está castigada. Anule el castigo"
                            + " (Cartera → Castigos) antes de registrar o reversar pagos sobre ella.");
    }

    // ── Deterioro ────────────────────────────────────────────────────────────

    public DeterioroSugerido deterioroSugerido(Integer empresaId) {
        Politica pol = interesMora.politica(empresaId);
        TreeMap<Integer, BigDecimal> tramos = new TreeMap<>(pol.getDeterioroTramos());
        LocalDate hoy = InteresMoraService.hoy();

        DeterioroSugerido out = new DeterioroSugerido();
        out.setFecha(hoy);
        out.setTramos(tramos);
        for (CandidatoCastigo c : query.candidatosCastigo(empresaId, 1, null)) {
            var tramo = tramos.floorEntry(c.getDiasMora());
            if (tramo == null || tramo.getValue().signum() <= 0) continue;
            DeterioroFactura f = new DeterioroFactura();
            f.setCuentaCobrarId(c.getCuentaCobrarId());
            f.setNumeroCuenta(c.getReferencia() != null ? c.getReferencia() : c.getNumeroCuenta());
            f.setTerceroNombre(c.getTerceroNombre());
            f.setFechaVencimiento(c.getFechaVencimiento());
            f.setDiasMora(c.getDiasMora());
            f.setSaldo(c.getSaldo());
            f.setPorcentaje(tramo.getValue());
            f.setValor(c.getSaldo().multiply(tramo.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            out.getFacturas().add(f);
        }
        out.setTotalCalculado(out.getFacturas().stream().map(DeterioroFactura::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        out.setProvisionActual(provisionActual(empresaId));
        out.setAjuste(out.getTotalCalculado().subtract(out.getProvisionActual()));
        out.setBorradoresPendientes(query.deteriorosEnBorrador(empresaId));
        return out;
    }

    /** Propone en borrador solo lo que falta provisionar. */
    @Transactional
    public DeterioroCalculoEntity proponerDeterioro(Integer empresaId, Long usuarioId) {
        DeterioroSugerido s = deterioroSugerido(empresaId);
        if (s.getBorradoresPendientes() > 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Ya hay una propuesta de deterioro en borrador. Apruébela o anúlela en Revisión de comprobantes"
                            + " antes de proponer otra.");
        if (s.getAjuste().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La provisión de cartera ya cubre el deterioro calculado: no hay nada que proponer.");
        return deterioroService.proponerMonto(empresaId, usuarioId, s.getAjuste(),
                s.getFacturas().size() + " facturas vencidas, ajuste sobre provisión de "
                        + s.getProvisionActual().setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

    private BigDecimal provisionActual(Integer empresaId) {
        try {
            String codigo = configuracionContable.resolverCuenta(empresaId, ConceptoContable.PROVISION_CARTERA)
                    .getCodigo();
            return query.saldoCreditoPrefijo(empresaId, codigo).max(BigDecimal.ZERO);
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }

    // ── Castigo ──────────────────────────────────────────────────────────────

    public List<CandidatoCastigo> candidatos(Integer empresaId, Integer dias, Long terceroId) {
        int minimo = dias != null ? Math.max(0, dias) : interesMora.politica(empresaId).getDiasSugerirCastigo();
        return query.candidatosCastigo(empresaId, minimo, terceroId);
    }

    @Transactional
    public List<Castigo> castigar(Integer empresaId, Integer usuarioId, Castigar dto) {
        if (dto.getCuentaCobrarIds() == null || dto.getCuentaCobrarIds().isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija las facturas a castigar.");
        if (dto.getMotivo() == null || dto.getMotivo().trim().length() < 10)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Escriba el motivo del castigo (mínimo 10 caracteres): es el soporte del contador ante la DIAN.");
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : InteresMoraService.hoy();
        if (fecha.isAfter(InteresMoraService.hoy()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha del castigo no puede ser futura.");
        periodoGuard.exigirAbierto(empresaId, fecha);

        // La provisión se va gastando factura por factura dentro del mismo lote.
        BigDecimal provision = provisionActual(empresaId);
        List<Castigo> out = new ArrayList<>();
        for (Long ccId : new LinkedHashSet<>(dto.getCuentaCobrarIds())) {
            CuentaCobrarEntity cc = cuentaCobrarRepo.findByIdAndEmpresaId(ccId, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));
            String estado = cc.getEstado();
            if (!"activa".equals(estado) && !"parcial".equals(estado))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta " + cc.getNumeroCuenta() + " está " + estado + ": solo se castiga cartera abierta.");
            BigDecimal saldo = InteresMoraService.nz(cc.getSaldoPendiente());
            if (saldo.signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta " + cc.getNumeroCuenta() + " no tiene saldo.");
            if (cc.getFechaVencimiento() == null || !cc.getFechaVencimiento().toLocalDate().isBefore(fecha))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta " + cc.getNumeroCuenta() + " no está vencida: no se puede castigar.");
            int diasMora = (int) ChronoUnit.DAYS.between(cc.getFechaVencimiento().toLocalDate(), fecha);

            BigDecimal contraProvision = saldo.min(provision);
            BigDecimal contraGasto = saldo.subtract(contraProvision);
            provision = provision.subtract(contraProvision);

            int consecutivo = query.siguienteConsecutivoCastigo(empresaId);
            String numero = String.format("%s-%06d", PREFIJO, consecutivo);
            Long id = query.insertarCastigo(empresaId, ccId, cc.getTercero().getId(), numero, consecutivo, fecha,
                    saldo, contraProvision, contraGasto, diasMora, dto.getMotivo().trim(), estado, usuarioId);
            query.marcarCastigada(ccId, saldo);
            eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                    MoraCastigoQueryRepository.ORIGEN_CASTIGO, id, empresaId, usuarioId));
            out.add(obtener(empresaId, id));
        }
        return out;
    }

    @Transactional
    public Castigo anular(Integer empresaId, Integer usuarioId, Long id, String motivo) {
        var fila = query.castigo(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Castigo no encontrado"));
        Castigo k = fila.castigo();
        if (!"CASTIGADO".equals(k.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este castigo ya está anulado.");
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la anulación.");
        query.restaurarCastigada(k.getCuentaCobrarId(), k.getValor(), fila.estadoAnterior());
        query.anularCastigo(id, usuarioId, motivo.trim());
        eventPublisher.publishEvent(new ContabilidadReversaEvent(
                MoraCastigoQueryRepository.ORIGEN_CASTIGO, id, empresaId, usuarioId));
        return obtener(empresaId, id);
    }

    public Castigo obtener(Integer empresaId, Long id) {
        return query.castigo(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Castigo no encontrado"))
                .castigo();
    }

    public PageImpl<Castigo> listar(Integer empresaId, String search, int page, int rows) {
        return query.listarCastigos(empresaId, search, page, rows);
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.obligaciones.CreateObligacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.CuotaAmortizacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.ObligacionDto;
import com.cloud_technological.aura_pos.dto.obligaciones.PagarCuotaDto;
import com.cloud_technological.aura_pos.entity.CuotaAmortizacionEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.ObligacionFinancieraEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.event.OperacionContabilizableEvent;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.obligaciones.CuotaAmortizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.obligaciones.ObligacionFinancieraJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ObligacionFinancieraService;

@Service
public class ObligacionFinancieraServiceImpl implements ObligacionFinancieraService {

    @Autowired private ObligacionFinancieraJPARepository obligacionRepo;
    @Autowired private CuotaAmortizacionJPARepository cuotaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private UsuarioJPARepository usuarioRepository;
    @Autowired private TurnoCajaJPARepository turnoCajaRepo;
    @Autowired private MovimientoCajaJPARepository movimientoCajaRepo;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ObligacionDto crear(CreateObligacionDto dto, Integer empresaId, Long usuarioId) {
        ObligacionFinancieraEntity o = ObligacionFinancieraEntity.builder()
                .empresaId(empresaId)
                .entidad(dto.getEntidad())
                .terceroId(dto.getTerceroId())
                .numero(dto.getNumero())
                .montoPrincipal(dto.getMontoPrincipal())
                .tasaMensual(dto.getTasaMensual())
                .plazoMeses(dto.getPlazoMeses())
                .fechaDesembolso(dto.getFechaDesembolso())
                .cuentaBancariaId(dto.getCuentaBancariaId())
                .saldoCapital(dto.getMontoPrincipal())
                .estado("ACTIVA")
                .build();

        o.setCuotas(generarTablaAmortizacion(o));
        ObligacionFinancieraEntity saved = obligacionRepo.save(o);

        // El desembolso aumenta el saldo de la cuenta bancaria elegida.
        if (saved.getCuentaBancariaId() != null) {
            cuentaBancariaRepo.findByIdAndEmpresaId(saved.getCuentaBancariaId(), empresaId)
                    .ifPresent(cb -> {
                        cb.setSaldoActual(cb.getSaldoActual().add(saved.getMontoPrincipal()));
                        cuentaBancariaRepo.save(cb);
                    });
        }

        // Asiento de desembolso (DB Bancos / CR Obligaciones financieras) tras commit.
        eventPublisher.publishEvent(new OperacionContabilizableEvent(
                "OBLIGACION", saved.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));

        return toDto(saved);
    }

    /** Genera la tabla de amortización con cuota fija (sistema francés). */
    private List<CuotaAmortizacionEntity> generarTablaAmortizacion(ObligacionFinancieraEntity o) {
        int n = o.getPlazoMeses();
        BigDecimal principal = o.getMontoPrincipal();
        BigDecimal i = o.getTasaMensual().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        // Cuota fija: P*i / (1 - (1+i)^-n).  Si i = 0 → P/n.
        BigDecimal cuotaFija;
        if (i.signum() == 0) {
            cuotaFija = principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            double id = i.doubleValue();
            double factor = id / (1 - Math.pow(1 + id, -n));
            cuotaFija = principal.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        }

        List<CuotaAmortizacionEntity> cuotas = new ArrayList<>();
        BigDecimal saldo = principal;
        for (int k = 1; k <= n; k++) {
            BigDecimal interes = saldo.multiply(i).setScale(2, RoundingMode.HALF_UP);
            BigDecimal abonoCapital;
            BigDecimal cuotaPeriodo;
            if (k == n) {
                // Última cuota: cancela el saldo restante exactamente.
                abonoCapital = saldo;
                cuotaPeriodo = abonoCapital.add(interes);
            } else {
                abonoCapital = cuotaFija.subtract(interes);
                cuotaPeriodo = cuotaFija;
            }
            saldo = saldo.subtract(abonoCapital).setScale(2, RoundingMode.HALF_UP);

            cuotas.add(CuotaAmortizacionEntity.builder()
                    .obligacion(o)
                    .numeroCuota(k)
                    .fechaVencimiento(o.getFechaDesembolso().plusMonths(k))
                    .cuota(cuotaPeriodo)
                    .abonoCapital(abonoCapital)
                    .interes(interes)
                    .saldo(saldo.max(BigDecimal.ZERO))
                    .estado("PENDIENTE")
                    .build());
        }
        return cuotas;
    }

    @Override
    public List<ObligacionDto> listar(Integer empresaId) {
        return obligacionRepo.findByEmpresaIdOrderByFechaDesembolsoDesc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public ObligacionDto obtenerPorId(Long id, Integer empresaId) {
        ObligacionFinancieraEntity o = obligacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));
        return toDto(o);
    }

    @Override
    @Transactional
    public CuotaAmortizacionDto pagarCuota(Long obligacionId, Long cuotaId, PagarCuotaDto pago,
            Integer empresaId, Long usuarioId) {
        ObligacionFinancieraEntity o = obligacionRepo.findByIdAndEmpresaId(obligacionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));

        CuotaAmortizacionEntity cuota = cuotaRepo.findByIdAndObligacionId(cuotaId, obligacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuota no encontrada"));

        if ("PAGADA".equals(cuota.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuota #" + cuota.getNumeroCuota() + " ya está pagada");
        }

        // ── Resolver el ORIGEN del dinero ────────────────────────────────────
        // El pasivo puede cancelarse desde cualquier activo monetario: efectivo
        // (caja) o cualquier cuenta bancaria, no solo la del desembolso. Si no se
        // envía origen, cae a la cuenta del desembolso (comportamiento anterior).
        String metodoPago = (pago != null && pago.getMetodoPago() != null)
                ? pago.getMetodoPago().trim().toUpperCase() : null;
        boolean esEfectivo = metodoPago != null && metodoPago.contains("EFECTIVO");
        Long cuentaOrigen = (pago != null && pago.getCuentaBancariaId() != null)
                ? pago.getCuentaBancariaId()
                : (esEfectivo ? null : o.getCuentaBancariaId());

        cuota.setEstado("PAGADA");
        cuota.setFechaPago(LocalDate.now());
        cuota.setMetodoPago(metodoPago);
        cuota.setCuentaBancariaIdPago(cuentaOrigen);
        cuotaRepo.save(cuota);

        o.setSaldoCapital(o.getSaldoCapital().subtract(cuota.getAbonoCapital()).max(BigDecimal.ZERO));
        if (o.getSaldoCapital().signum() == 0) {
            o.setEstado("PAGADA");
        }
        obligacionRepo.save(o);

        // El pago disminuye el saldo del activo del que sale el dinero.
        if (cuentaOrigen != null) {
            // Salida desde una cuenta bancaria (la elegida, o la del desembolso por defecto).
            cuentaBancariaRepo.findByIdAndEmpresaId(cuentaOrigen, empresaId)
                    .ifPresent(cb -> {
                        cb.setSaldoActual(cb.getSaldoActual().subtract(cuota.getCuota()));
                        cuentaBancariaRepo.save(cb);
                    });
        } else {
            // Pago en efectivo → egreso de caja en el turno abierto (patrón de compras CONTADO).
            UsuarioEntity usuario = usuarioId != null
                    ? usuarioRepository.findById(usuarioId.intValue()).orElse(null) : null;
            if (usuario != null) {
                turnoCajaRepo.findByUsuarioIdAndEstado(usuarioId, "ABIERTA")
                        .ifPresent(turno -> {
                            MovimientoCajaEntity egreso = MovimientoCajaEntity.builder()
                                    .turnoCaja(turno)
                                    .usuario(usuario)
                                    .tipo("EGRESO")
                                    .concepto("Pago cuota #" + cuota.getNumeroCuota()
                                            + " — obligación #" + o.getId())
                                    .monto(cuota.getCuota())
                                    .build();
                            movimientoCajaRepo.save(egreso);
                        });
            }
        }

        // Asiento del pago de la cuota (DB capital + DB interés / CR Caja/Bancos del
        // origen elegido) tras commit. El motor relee metodoPago/cuentaBancariaIdPago.
        eventPublisher.publishEvent(new OperacionContabilizableEvent(
                "CUOTA", cuota.getId(), empresaId,
                usuarioId != null ? usuarioId.intValue() : null));

        return toCuotaDto(cuota);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        ObligacionFinancieraEntity o = obligacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));
        boolean tieneCuotasPagadas = cuotaRepo.findByObligacionIdOrderByNumeroCuotaAsc(id)
                .stream().anyMatch(c -> "PAGADA".equals(c.getEstado()));
        if (tieneCuotasPagadas) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede anular: la obligación ya tiene cuotas pagadas");
        }
        o.setEstado("ANULADA");
        obligacionRepo.save(o);

        // Reversar el asiento de desembolso tras commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "OBLIGACION", o.getId(), empresaId, null));
    }

    // ── Mapeo ──────────────────────────────────────────────────────────────
    private ObligacionDto toDto(ObligacionFinancieraEntity o) {
        List<CuotaAmortizacionDto> cuotas = cuotaRepo
                .findByObligacionIdOrderByNumeroCuotaAsc(o.getId())
                .stream().map(this::toCuotaDto).collect(Collectors.toList());
        return ObligacionDto.builder()
                .id(o.getId())
                .entidad(o.getEntidad())
                .terceroId(o.getTerceroId())
                .numero(o.getNumero())
                .montoPrincipal(o.getMontoPrincipal())
                .tasaMensual(o.getTasaMensual())
                .plazoMeses(o.getPlazoMeses())
                .fechaDesembolso(o.getFechaDesembolso())
                .cuentaBancariaId(o.getCuentaBancariaId())
                .saldoCapital(o.getSaldoCapital())
                .estado(o.getEstado())
                .cuotas(cuotas)
                .build();
    }

    private CuotaAmortizacionDto toCuotaDto(CuotaAmortizacionEntity c) {
        return CuotaAmortizacionDto.builder()
                .id(c.getId())
                .numeroCuota(c.getNumeroCuota())
                .fechaVencimiento(c.getFechaVencimiento())
                .cuota(c.getCuota())
                .abonoCapital(c.getAbonoCapital())
                .interes(c.getInteres())
                .saldo(c.getSaldo())
                .estado(c.getEstado())
                .fechaPago(c.getFechaPago())
                .build();
    }
}

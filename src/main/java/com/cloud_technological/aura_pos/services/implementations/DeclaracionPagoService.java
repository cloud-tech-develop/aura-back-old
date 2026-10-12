package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.CuentaPorPagar;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.PorPagar;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.RegistrarPagoDeclaracionDto;
import com.cloud_technological.aura_pos.entity.DeclaracionPagoCuentaEntity;
import com.cloud_technological.aura_pos.entity.DeclaracionPagoEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionPagoQueryRepository.SaldoCuenta;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * A quién se le paga cada declaración y el registro del pago (V204).
 *
 * <p>IVA (300): saldo del período de la 2408 menos la 135517, a la DIAN.
 * Retención (350): 2365 y 2367, al acreedor de la tarifa dueña de cada cuenta
 * (normalmente la DIAN). ReteICA: 2368, a la secretaría de hacienda de cada
 * municipio, por la cuenta de su tarifa.
 *
 * <p>El pago cierra el saldo de esas cuentas contra la caja o el banco, a
 * nombre del acreedor. Las líneas de cada factura siguen a nombre del cliente o
 * proveedor (certificados y exógena).
 */
@Service
@RequiredArgsConstructor
public class DeclaracionPagoService {

    public static final String TIPO_ORIGEN = DeclaracionPagoQueryRepository.TIPO_ORIGEN;
    private static final Set<String> TIPOS = Set.of("IVA", "RETENCION", "RETEICA", "ICA");

    private final DeclaracionPagoQueryRepository query;
    private final DeclaracionPagoJPARepository pagoRepo;
    private final DeclaracionPagoCuentaJPARepository pagoCuentaRepo;
    private final AcreedorImpuestoQueryRepository acreedores;
    private final PlanCuentaJPARepository planRepo;
    private final CuentaBancariaJPARepository cuentaBancariaRepo;
    private final PeriodoContableResolver periodoGuard;
    private final ApplicationEventPublisher eventPublisher;

    // ── Qué se le debe a quién ───────────────────────────────────────────────

    /**
     * Lo que hay por pagar en una declaración, por acreedor.
     *
     * @param declaracion IVA (formulario 300) o RETENCION (350 + reteICA)
     */
    public List<PorPagar> porPagar(String declaracion, Integer empresaId, LocalDate desde,
            LocalDate hasta, List<String> advertencias) {
        List<PorPagar> grupos = new ArrayList<>();
        if ("IVA".equals(declaracion)) {
            List<Long> ivas = acreedores.acreedoresIva(empresaId);
            Long acreedor = !ivas.isEmpty() ? ivas.get(0) : acreedores.dian(empresaId).orElse(null);
            if (ivas.size() > 1) {
                advertencias.add("Los impuestos de IVA tienen acreedores distintos: el IVA se declara y "
                        + "se paga a la DIAN. Revise el acreedor en Contabilidad → Impuestos");
            }
            agrupar(grupos, "IVA", query.saldos(empresaId, desde, hasta, List.of("2408", "135517")),
                    c -> acreedor);
        } else {
            Map<Long, Set<Long>> nacional = acreedoresPorCuenta(empresaId, Set.of("RETEFUENTE", "RETEIVA"));
            Map<Long, Set<Long>> ica = acreedoresPorCuenta(empresaId, Set.of("RETEICA"));
            Long dian = acreedores.dian(empresaId).orElse(null);
            List<Long> municipios = acreedores.acreedoresReteica(empresaId);
            Long unicoMunicipio = municipios.size() == 1 ? municipios.get(0) : null;

            agrupar(grupos, "RETENCION", query.saldos(empresaId, desde, hasta, List.of("2365", "2367")),
                    c -> acreedorDeCuenta(c, nacional, dian, advertencias));
            agrupar(grupos, "RETEICA", query.saldos(empresaId, desde, hasta, List.of("2368")),
                    c -> acreedorDeCuenta(c, ica, unicoMunicipio, advertencias));
        }

        completarTerceros(grupos, empresaId);
        completarPagos(grupos, empresaId, desde, hasta);
        for (PorPagar g : grupos) {
            if (g.getTerceroId() == null && g.getValor().signum() > 0) {
                advertencias.add(("RETEICA".equals(g.getTipo())
                        ? "Hay reteICA sin municipio asignado: elija la secretaría de hacienda en cada tarifa de "
                                + "reteICA (Contabilidad → Tarifas de retención), con una cuenta 2368 por municipio"
                        : "Hay impuestos sin entidad a la que pagar: asigne el acreedor en Contabilidad → "
                                + "Impuestos o Tarifas de retención")
                        + ". Hasta entonces no se puede registrar su pago");
            }
        }
        return grupos;
    }

    /**
     * ICA propio del municipio: lo causado en la 2412 menos lo que le retuvieron
     * (135518), todo a nombre de la secretaría de hacienda que eligió el contador.
     */
    public List<PorPagar> porPagarIca(Integer empresaId, LocalDate desde, LocalDate hasta,
            Long municipio, List<String> advertencias) {
        List<PorPagar> grupos = new ArrayList<>();
        agrupar(grupos, "ICA", query.saldos(empresaId, desde, hasta, List.of("2412", "135518")), c -> municipio);
        completarTerceros(grupos, empresaId);
        completarPagos(grupos, empresaId, desde, hasta);
        if (municipio == null && grupos.stream().anyMatch(g -> g.getValor().signum() > 0)) {
            advertencias.add("Elija la secretaría de hacienda del municipio para poder registrar el pago del ICA");
        }
        return grupos;
    }

    /** Cuenta → acreedores de las tarifas de esos tipos que la usan. */
    private Map<Long, Set<Long>> acreedoresPorCuenta(Integer empresaId, Set<String> tipos) {
        Map<Long, Set<Long>> mapa = new HashMap<>();
        for (var c : acreedores.cuentasRetencion(empresaId)) {
            if (!tipos.contains(c.tipo()) || c.terceroAcreedorId() == null) continue;
            mapa.computeIfAbsent(c.cuentaId(), k -> new HashSet<>()).add(c.terceroAcreedorId());
        }
        return mapa;
    }

    /**
     * El acreedor de una cuenta: el único de sus tarifas; si ninguna tarifa la
     * usa, el de por defecto; si la usan tarifas de varios acreedores, ninguno
     * (no se puede partir el saldo de una cuenta entre municipios).
     */
    private Long acreedorDeCuenta(SaldoCuenta c, Map<Long, Set<Long>> mapa, Long porDefecto,
            List<String> advertencias) {
        Set<Long> set = mapa.get(c.cuentaId());
        if (set == null || set.isEmpty()) return porDefecto;
        if (set.size() == 1) return set.iterator().next();
        advertencias.add("La cuenta " + c.codigo() + " la usan tarifas de acreedores distintos: use una "
                + "subcuenta por entidad (por ejemplo, una 2368 por municipio) para saber a quién pagarle");
        return null;
    }

    private void agrupar(List<PorPagar> grupos, String tipo, List<SaldoCuenta> saldos,
            Function<SaldoCuenta, Long> acreedorDe) {
        Map<Long, PorPagar> porAcreedor = new LinkedHashMap<>();
        PorPagar sinAcreedor = null;
        for (SaldoCuenta s : saldos) {
            Long acreedor = acreedorDe.apply(s);
            PorPagar g;
            if (acreedor == null) {
                if (sinAcreedor == null) {
                    sinAcreedor = nuevo(tipo, null);
                }
                g = sinAcreedor;
            } else {
                g = porAcreedor.computeIfAbsent(acreedor, a -> nuevo(tipo, a));
            }
            g.getCuentas().add(new CuentaPorPagar(s.cuentaId(), s.codigo(), s.nombre(), s.saldo()));
            g.setValor(g.getValor().add(s.saldo()));
        }
        grupos.addAll(porAcreedor.values());
        if (sinAcreedor != null) grupos.add(sinAcreedor);
    }

    private static PorPagar nuevo(String tipo, Long acreedor) {
        PorPagar g = new PorPagar();
        g.setTipo(tipo);
        g.setTerceroId(acreedor);
        return g;
    }

    private void completarTerceros(List<PorPagar> grupos, Integer empresaId) {
        for (PorPagar g : grupos) {
            if (g.getTerceroId() == null) {
                g.setTerceroNombre("Sin entidad asignada");
                continue;
            }
            acreedores.tercero(g.getTerceroId(), empresaId).ifPresent(t -> {
                g.setTerceroNombre(t.nombre());
                g.setTerceroDocumento(t.documento());
            });
        }
    }

    private void completarPagos(List<PorPagar> grupos, Integer empresaId, LocalDate desde,
            LocalDate hasta) {
        var pagos = query.pagosVigentes(empresaId, desde, hasta);
        for (PorPagar g : grupos) {
            pagos.stream()
                    .filter(p -> p.tipo().equals(g.getTipo()) && Objects.equals(p.terceroAcreedorId(), g.getTerceroId()))
                    .findFirst()
                    .ifPresent(p -> {
                        g.setPagoId(p.id());
                        g.setPagoNumero("PD-" + p.consecutivo());
                        g.setPagoFecha(p.fecha());
                        g.setPagoValor(p.valor());
                        g.setPagoFormulario(p.numeroFormulario());
                        g.setPagoAsiento(p.numeroAsiento());
                    });
        }
    }

    // ── Registrar y anular el pago ───────────────────────────────────────────

    @Transactional
    public PorPagar registrar(Integer empresaId, Integer usuarioId, RegistrarPagoDeclaracionDto dto) {
        String tipo = dto.getTipo() != null ? dto.getTipo().trim().toUpperCase() : null;
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de declaración inválido: use IVA, RETENCION, RETEICA o ICA.");
        }
        if (dto.getDesde() == null || dto.getHasta() == null || dto.getHasta().isBefore(dto.getDesde())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período de la declaración no es válido.");
        }
        if (dto.getTerceroAcreedorId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique a quién se le paga la declaración.");
        }
        if (dto.getFecha() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique la fecha del pago.");
        }
        if (dto.getFecha().isBefore(dto.getDesde())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha del pago no puede ser anterior al período declarado.");
        }
        periodoGuard.exigirAbierto(empresaId, dto.getFecha());
        String metodo = validarOrigenPago(empresaId, dto);

        // El valor y las cuentas se recalculan aquí: no se confía en el front.
        String declaracion = "IVA".equals(tipo) ? "IVA" : "RETENCION";
        List<PorPagar> candidatos = "ICA".equals(tipo)
                ? porPagarIca(empresaId, dto.getDesde(), dto.getHasta(), dto.getTerceroAcreedorId(), new ArrayList<>())
                : porPagar(declaracion, empresaId, dto.getDesde(), dto.getHasta(), new ArrayList<>());
        PorPagar g = candidatos.stream()
                .filter(x -> x.getTipo().equals(tipo) && Objects.equals(x.getTerceroId(), dto.getTerceroAcreedorId()))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "No hay saldo por pagar a ese acreedor en el período."));
        if (g.getPagoId() != null) {
            throw new GlobalException(HttpStatus.CONFLICT, "Esta declaración ya tiene el pago " + g.getPagoNumero()
                    + ". Anúlelo si necesita registrarlo de nuevo.");
        }
        if (g.getValor().signum() <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El resultado del período es saldo a favor: no hay nada que pagar.");
        }

        // Lo que de verdad se paga: el saldo exacto aproximado a miles (art. 577
        // E.T.) más intereses y sanción. El saldo exacto es lo que se cancela en
        // las cuentas del impuesto; la diferencia de redondeo va al ajuste al peso.
        BigDecimal intereses = noNegativo(dto.getIntereses(), "Los intereses");
        BigDecimal sancion = noNegativo(dto.getSancion(), "La sanción");
        BigDecimal ajuste = BigDecimal.ZERO;
        if (!Boolean.FALSE.equals(dto.getRedondearMiles())) {
            BigDecimal mil = BigDecimal.valueOf(1000);
            BigDecimal redondeado = g.getValor().divide(mil, 0, RoundingMode.HALF_UP).multiply(mil);
            ajuste = redondeado.subtract(g.getValor());
        }

        DeclaracionPagoEntity pago;
        try {
            pago = pagoRepo.saveAndFlush(DeclaracionPagoEntity.builder()
                    .empresaId(empresaId)
                    .consecutivo(query.siguienteConsecutivo(empresaId))
                    .tipo(tipo)
                    .desde(dto.getDesde())
                    .hasta(dto.getHasta())
                    .terceroAcreedorId(dto.getTerceroAcreedorId())
                    .fecha(dto.getFecha())
                    .numeroFormulario(dto.getNumeroFormulario() != null && !dto.getNumeroFormulario().isBlank()
                            ? dto.getNumeroFormulario().trim() : null)
                    .valor(g.getValor())
                    .ajusteRedondeo(ajuste)
                    .intereses(intereses)
                    .sancion(sancion)
                    .metodoPago(metodo)
                    .cuentaBancariaId(dto.getCuentaBancariaId())
                    .cuentaContableId(dto.getCuentaContableId())
                    .usuarioId(usuarioId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new GlobalException(HttpStatus.CONFLICT, "Esta declaración ya tiene un pago registrado.");
        }
        for (CuentaPorPagar c : g.getCuentas()) {
            pagoCuentaRepo.save(DeclaracionPagoCuentaEntity.builder()
                    .pagoId(pago.getId())
                    .cuentaId(c.getCuentaId())
                    .valor(c.getValor())
                    .build());
        }

        // El asiento sale después del commit (ContabilizacionListener).
        eventPublisher.publishEvent(new DocumentoContabilizableEvent(TIPO_ORIGEN, pago.getId(), empresaId, usuarioId));

        g.setPagoId(pago.getId());
        g.setPagoNumero("PD-" + pago.getConsecutivo());
        g.setPagoFecha(pago.getFecha());
        g.setPagoValor(pago.getValor().add(ajuste).add(intereses).add(sancion));
        g.setPagoFormulario(pago.getNumeroFormulario());
        return g;
    }

    @Transactional
    public void anular(Integer empresaId, Integer usuarioId, Long pagoId, String motivo) {
        DeclaracionPagoEntity pago = pagoRepo.findById(pagoId)
                .filter(p -> p.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (!DeclaracionPagoEntity.ESTADO_PAGADA.equals(pago.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El pago ya está anulado.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la anulación.");
        }
        pago.setEstado(DeclaracionPagoEntity.ESTADO_ANULADA);
        pago.setAnuladoPor(usuarioId);
        pago.setAnuladoAt(LocalDateTime.now());
        pago.setMotivoAnulacion(motivo.trim());
        pagoRepo.save(pago);
        // El contraasiento sale después del commit, con fecha de hoy.
        eventPublisher.publishEvent(new ContabilidadReversaEvent(TIPO_ORIGEN, pago.getId(), empresaId, usuarioId));
    }

    private static BigDecimal noNegativo(BigDecimal v, String campo) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, campo + " no pueden ser negativos.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * De dónde sale la plata: una cuenta bancaria de la empresa, una cuenta
     * contable habilitada como medio de pago, o la caja (efectivo).
     */
    private String validarOrigenPago(Integer empresaId, RegistrarPagoDeclaracionDto dto) {
        String metodo = dto.getMetodoPago() != null && !dto.getMetodoPago().isBlank()
                ? dto.getMetodoPago().trim().toUpperCase()
                : (dto.getCuentaBancariaId() != null ? "TRANSFERENCIA" : "EFECTIVO");
        if (dto.getCuentaBancariaId() != null) {
            cuentaBancariaRepo.findByIdAndEmpresaId(dto.getCuentaBancariaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta bancaria no existe."));
        }
        if (dto.getCuentaContableId() != null) {
            PlanCuentaEntity c = planRepo.findByIdAndEmpresaId(dto.getCuentaContableId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta de pago no existe."));
            if (!Boolean.TRUE.equals(c.getActiva()) || !Boolean.TRUE.equals(c.getAuxiliar())
                    || !Boolean.TRUE.equals(c.getEsMedioPago())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta " + c.getCodigo()
                        + " no está habilitada como medio de pago.");
            }
        }
        if (!"EFECTIVO".equals(metodo) && dto.getCuentaBancariaId() == null && dto.getCuentaContableId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la cuenta bancaria de donde sale el pago.");
        }
        return metodo;
    }
}

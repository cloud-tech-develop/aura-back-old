package com.cloud_technological.aura_pos.services.cartera;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.CalculoInteres;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.FacturarInteres;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresCliente;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresCuenta;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.InteresMora;
import com.cloud_technological.aura_pos.dto.cartera.mora.MoraCastigoDtos.Politica;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.cartera.MoraCastigoQueryRepository;
import com.cloud_technological.aura_pos.repositories.cartera.MoraCastigoQueryRepository.CuentaMora;
import com.cloud_technological.aura_pos.repositories.cartera.MoraCastigoQueryRepository.PoliticaFila;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Cartera C5: intereses de mora (docs/PLAN_CARTERA.md). Opcional por empresa.
 *
 * <p>El interés se calcula a la fecha sobre el saldo vencido, interés simple
 * mensual (tasa × días / 30), con la tasa pactada topada en la usura que registra
 * el usuario. Nunca se causa solo: se cobra al facturarlo, que crea una cuenta
 * por cobrar aparte y el asiento DB 1305 · CR 4210. Cada factura recuerda hasta
 * qué día quedó cobrada, así el siguiente cobro empieza ahí. No se cobra interés
 * sobre interés (art. 886 C.Co.).
 */
@Service
@RequiredArgsConstructor
public class InteresMoraService {

    public static final String PREFIJO = "IM";
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Tramos por defecto: días de mora → % de deterioro. */
    static final Map<Integer, BigDecimal> TRAMOS_DEFECTO = new TreeMap<>(Map.of(
            91, new BigDecimal("5"), 181, new BigDecimal("20"), 271, new BigDecimal("50"), 361, new BigDecimal("100")));

    private final MoraCastigoQueryRepository query;
    private final CuentaCobrarService cuentaCobrarService;
    private final CuentaCobrarJPARepository cuentaCobrarRepo;
    private final PeriodoContableResolver periodoGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ── Política ─────────────────────────────────────────────────────────────

    public Politica politica(Integer empresaId) {
        Politica p = new Politica();
        query.politica(empresaId).ifPresentOrElse(f -> {
            p.setInteresActivo(f.interesActivo());
            p.setTasaMoraMensual(f.tasaMoraMensual());
            p.setTasaUsuraMensual(f.tasaUsuraMensual());
            p.setUsuraVigenteHasta(f.usuraVigenteHasta());
            p.setDiasGracia(f.diasGracia());
            p.setDeterioroTramos(leerTramos(f.deterioroTramos()));
            p.setDiasSugerirCastigo(f.diasSugerirCastigo());
            p.setUpdatedAt(f.updatedAt());
        }, () -> p.setDeterioroTramos(new TreeMap<>(TRAMOS_DEFECTO)));
        p.setTasaAplicable(tasaAplicable(p));
        p.setAvisos(avisos(p, hoy()));
        return p;
    }

    @Transactional
    public Politica guardarPolitica(Integer empresaId, Integer usuarioId, Politica dto) {
        BigDecimal tasa = nz(dto.getTasaMoraMensual());
        BigDecimal usura = nz(dto.getTasaUsuraMensual());
        if (tasa.signum() < 0 || usura.signum() < 0 || tasa.compareTo(BigDecimal.TEN) > 0
                || usura.compareTo(BigDecimal.TEN) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las tasas son mensuales y en porcentaje: entre 0 y 10 (por ejemplo 2,1 para 2,1 % mensual).");
        if (dto.isInteresActivo()) {
            if (tasa.signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba la tasa de mora mensual.");
            if (usura.signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Registre la tasa de usura vigente (mensual): el interés de mora nunca puede pasarla.");
            if (dto.getUsuraVigenteHasta() == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Indique hasta qué fecha rige la tasa de usura (la Superfinanciera la certifica cada mes).");
        }
        if (dto.getDiasGracia() < 0 || dto.getDiasGracia() > 365)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días de gracia van de 0 a 365.");
        if (dto.getDiasSugerirCastigo() < 30)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los días para sugerir castigo deben ser al menos 30.");

        Map<Integer, BigDecimal> tramos = new TreeMap<>();
        if (dto.getDeterioroTramos() != null) {
            for (var e : dto.getDeterioroTramos().entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (e.getKey() < 1)
                    throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días de cada tramo deben ser mayores a 0.");
                if (e.getValue().signum() < 0 || e.getValue().compareTo(new BigDecimal("100")) > 0)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "El porcentaje de cada tramo va de 0 a 100.");
                tramos.put(e.getKey(), e.getValue());
            }
        }
        query.guardarPolitica(empresaId, new PoliticaFila(dto.isInteresActivo(), tasa, usura,
                dto.getUsuraVigenteHasta(), dto.getDiasGracia(), escribirTramos(tramos),
                dto.getDiasSugerirCastigo(), null), usuarioId);
        return politica(empresaId);
    }

    // ── Cálculo ──────────────────────────────────────────────────────────────

    /** Interés causado a la fecha, por cliente. No graba nada. */
    public CalculoInteres calcular(Integer empresaId, Long terceroId, LocalDate fecha) {
        Politica pol = politica(empresaId);
        LocalDate corte = fecha != null ? fecha : hoy();
        CalculoInteres out = new CalculoInteres();
        out.setFecha(corte);
        out.setInteresActivo(pol.isInteresActivo());
        out.setTasaMensual(pol.getTasaAplicable());
        out.setDiasGracia(pol.getDiasGracia());
        out.setAvisos(new ArrayList<>(pol.getAvisos()));
        if (!pol.isInteresActivo() || pol.getTasaAplicable().signum() <= 0) return out;

        Map<Long, InteresCliente> porCliente = new LinkedHashMap<>();
        for (CuentaMora c : query.cuentasVencidas(empresaId, terceroId, corte)) {
            InteresCuenta ic = calcularCuenta(c, corte, pol.getDiasGracia(), pol.getTasaAplicable());
            if (ic == null) continue;
            InteresCliente cli = porCliente.computeIfAbsent(c.terceroId(), k -> {
                InteresCliente x = new InteresCliente();
                x.setTerceroId(c.terceroId());
                x.setTerceroNombre(c.terceroNombre());
                x.setDocumento(c.documento());
                return x;
            });
            cli.getCuentas().add(ic);
            cli.setSaldoVencido(cli.getSaldoVencido().add(ic.getSaldo()));
            cli.setValor(cli.getValor().add(ic.getValor()));
        }
        out.setClientes(new ArrayList<>(porCliente.values()));
        out.getClientes().sort((a, b) -> b.getValor().compareTo(a.getValor()));
        out.setTotal(out.getClientes().stream().map(InteresCliente::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
        return out;
    }

    /**
     * Interés de una factura a la fecha de corte. Mientras no pasen los días de
     * gracia no hay interés; pasados, corre desde el vencimiento (o desde el
     * último corte facturado).
     */
    static InteresCuenta calcularCuenta(CuentaMora c, LocalDate corte, int diasGracia, BigDecimal tasaMensual) {
        int diasMora = (int) ChronoUnit.DAYS.between(c.fechaVencimiento(), corte);
        if (diasMora <= diasGracia) return null;
        LocalDate desde = c.ultimoCorte() != null && c.ultimoCorte().isAfter(c.fechaVencimiento())
                ? c.ultimoCorte()
                : c.fechaVencimiento();
        int dias = (int) ChronoUnit.DAYS.between(desde, corte);
        if (dias <= 0) return null;
        BigDecimal valor = c.saldo().multiply(tasaMensual).multiply(BigDecimal.valueOf(dias))
                .divide(BigDecimal.valueOf(3000), 0, RoundingMode.HALF_UP);
        if (valor.signum() <= 0) return null;
        InteresCuenta ic = new InteresCuenta();
        ic.setCuentaCobrarId(c.cuentaCobrarId());
        ic.setNumeroCuenta(c.numeroCuenta());
        ic.setReferencia(c.referencia());
        ic.setFechaVencimiento(c.fechaVencimiento());
        ic.setSaldo(c.saldo());
        ic.setDiasMora(diasMora);
        ic.setDesde(desde);
        ic.setHasta(corte);
        ic.setDias(dias);
        ic.setValor(valor);
        return ic;
    }

    // ── Facturar ─────────────────────────────────────────────────────────────

    @Transactional
    public InteresMora facturar(Integer empresaId, Integer usuarioId, FacturarInteres dto) {
        if (dto.getTerceroId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el cliente.");
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : hoy();
        if (fecha.isAfter(hoy()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se cobran intereses de días que no han pasado.");
        Politica pol = politica(empresaId);
        if (!pol.isInteresActivo())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El cobro de intereses de mora está apagado. Actívelo en la política de cartera.");
        if (pol.getUsuraVigenteHasta() != null && pol.getUsuraVigenteHasta().isBefore(fecha))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La tasa de usura registrada rigió hasta el " + pol.getUsuraVigenteHasta().format(DMY)
                            + ". Actualícela en la política de cartera antes de cobrar intereses.");
        periodoGuard.exigirAbierto(empresaId, fecha);

        CalculoInteres calc = calcular(empresaId, dto.getTerceroId(), fecha);
        if (calc.getClientes().isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este cliente no tiene intereses de mora por cobrar.");
        InteresCliente cli = calc.getClientes().get(0);
        Set<Long> elegidas = dto.getCuentaCobrarIds() != null && !dto.getCuentaCobrarIds().isEmpty()
                ? new HashSet<>(dto.getCuentaCobrarIds())
                : null;
        List<InteresCuenta> cuentas = cli.getCuentas().stream()
                .filter(c -> elegidas == null || elegidas.contains(c.getCuentaCobrarId()))
                .toList();
        if (cuentas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ninguna de las facturas elegidas tiene interés.");
        BigDecimal total = cuentas.stream().map(InteresCuenta::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        int consecutivo = query.siguienteConsecutivoInteres(empresaId);
        String numero = String.format("%s-%06d", PREFIJO, consecutivo);
        String obs = dto.getObservaciones() != null && !dto.getObservaciones().isBlank()
                ? dto.getObservaciones().trim()
                : null;
        Long interesId = query.insertarInteres(empresaId, cli.getTerceroId(), numero, consecutivo, fecha,
                pol.getTasaAplicable(), total, obs, usuarioId);
        for (InteresCuenta c : cuentas)
            query.insertarDetalle(interesId, c.getCuentaCobrarId(), c.getDesde(), c.getHasta(), c.getDias(),
                    c.getSaldo(), c.getValor());

        // La cuenta aparte: se cobra con los mismos recibos y abonos de siempre.
        int plazo = dto.getPlazoDias() != null ? Math.max(0, dto.getPlazoDias()) : 0;
        CreateCuentaCobrarDto cxc = new CreateCuentaCobrarDto();
        cxc.setClienteId(cli.getTerceroId());
        cxc.setTotalDeuda(total);
        cxc.setFechaEmision(fecha.atStartOfDay());
        cxc.setFechaVencimiento(fecha.plusDays(plazo).atStartOfDay());
        cxc.setObservaciones("Intereses de mora " + numero + " · " + pol.getTasaAplicable().stripTrailingZeros()
                .toPlainString() + " % mensual" + (obs != null ? " - " + obs : ""));
        Long cuentaId = cuentaCobrarService.crear(cxc, empresaId, usuarioId != null ? usuarioId.longValue() : null)
                .getId();
        query.asignarCuentaInteres(interesId, cuentaId);

        eventPublisher.publishEvent(new DocumentoContabilizableEvent(
                MoraCastigoQueryRepository.ORIGEN_INTERES, interesId, empresaId, usuarioId));
        return obtener(empresaId, interesId);
    }

    @Transactional
    public InteresMora anular(Integer empresaId, Integer usuarioId, Long id, String motivo) {
        InteresMora im = obtener(empresaId, id);
        if (!"FACTURADO".equals(im.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este cobro de intereses ya está anulado.");
        if (motivo == null || motivo.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la anulación.");
        if (im.getCuentaCobrarId() != null) {
            CuentaCobrarEntity cxc = cuentaCobrarRepo.findById(im.getCuentaCobrarId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta de intereses no encontrada"));
            if (cxc.getTotalAbonado() != null && cxc.getTotalAbonado().signum() > 0)
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El cliente ya abonó a estos intereses: anule los abonos antes de anular el cobro.");
            cxc.setEstado("anulada");
            cxc.setSaldoPendiente(BigDecimal.ZERO);
            cuentaCobrarRepo.saveAndFlush(cxc);
        }
        query.anularInteres(id, usuarioId, motivo.trim());
        // Los días vuelven a quedar por cobrar: el corte se lee solo de cobros FACTURADO.
        eventPublisher.publishEvent(new ContabilidadReversaEvent(
                MoraCastigoQueryRepository.ORIGEN_INTERES, id, empresaId, usuarioId));
        return obtener(empresaId, id);
    }

    public InteresMora obtener(Integer empresaId, Long id) {
        InteresMora im = query.interes(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cobro de intereses no encontrado"));
        im.setDetalle(query.detalleInteres(id));
        return im;
    }

    public PageImpl<InteresMora> listar(Integer empresaId, Long terceroId, String search, int page, int rows) {
        return query.listarIntereses(empresaId, terceroId, search, page, rows);
    }

    /** Para la ficha del cliente: lo causado a hoy y lo facturado sin pagar. */
    public Map<String, Object> resumenCliente(Integer empresaId, Long terceroId) {
        CalculoInteres calc = calcular(empresaId, terceroId, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interesActivo", calc.isInteresActivo());
        out.put("tasaMensual", calc.getTasaMensual());
        out.put("causado", calc.getTotal());
        out.put("facturadoPendiente", query.interesPendienteCliente(empresaId, terceroId));
        out.put("cuentas", calc.getClientes().isEmpty() ? List.of() : calc.getClientes().get(0).getCuentas());
        return out;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    static BigDecimal tasaAplicable(Politica p) {
        BigDecimal tasa = nz(p.getTasaMoraMensual());
        BigDecimal usura = nz(p.getTasaUsuraMensual());
        return usura.signum() > 0 && tasa.compareTo(usura) > 0 ? usura : tasa;
    }

    private static List<String> avisos(Politica p, LocalDate hoy) {
        List<String> out = new ArrayList<>();
        if (!p.isInteresActivo()) return out;
        if (nz(p.getTasaUsuraMensual()).signum() > 0 && nz(p.getTasaMoraMensual()).compareTo(p.getTasaUsuraMensual()) > 0)
            out.add("La tasa pactada pasa la usura: se cobra la usura (" + p.getTasaUsuraMensual().stripTrailingZeros()
                    .toPlainString() + " % mensual).");
        if (p.getUsuraVigenteHasta() != null && p.getUsuraVigenteHasta().isBefore(hoy))
            out.add("La tasa de usura registrada rigió hasta el " + p.getUsuraVigenteHasta().format(DMY)
                    + ": actualícela para poder facturar intereses.");
        return out;
    }

    Map<Integer, BigDecimal> leerTramos(String json) {
        if (json == null || json.isBlank()) return new TreeMap<>(TRAMOS_DEFECTO);
        try {
            return new TreeMap<>(objectMapper.readValue(json, new TypeReference<Map<Integer, BigDecimal>>() {
            }));
        } catch (Exception e) {
            return new TreeMap<>(TRAMOS_DEFECTO);
        }
    }

    private String escribirTramos(Map<Integer, BigDecimal> tramos) {
        try {
            return objectMapper.writeValueAsString(tramos);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tramos de deterioro inválidos");
        }
    }

    static LocalDate hoy() {
        return LocalDate.now(BOGOTA);
    }

    static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

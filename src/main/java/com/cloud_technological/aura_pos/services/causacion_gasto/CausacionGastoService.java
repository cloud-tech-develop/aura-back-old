package com.cloud_technological.aura_pos.services.causacion_gasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Calculo;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Crear;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Detalle;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Fila;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Linea;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.LineaCrear;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.CausacionGastoEntity;
import com.cloud_technological.aura_pos.entity.CausacionGastoLineaEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.causacion_gasto.CausacionGastoJPARepository;
import com.cloud_technological.aura_pos.repositories.causacion_gasto.CausacionGastoLineaJPARepository;
import com.cloud_technological.aura_pos.repositories.causacion_gasto.CausacionGastoQueryRepository;
import com.cloud_technological.aura_pos.repositories.centros_costos.CentroCostoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.retenciones.TarifaRetencionJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.services.implementations.PeriodoContableResolver;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/**
 * Causación de gastos y servicios (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §4).
 *
 * <p>La factura de un servicio con varias líneas: queda UNA cuenta por pagar por el
 * neto (lo que de verdad se le paga al proveedor) y un asiento CAUSACION_GASTO que
 * se arma después del commit. No mueve caja: el pago se registra después como abono
 * a la cuenta por pagar. No se edita: se anula (reversa el asiento y anula la cuenta
 * por pagar) y se vuelve a causar.
 */
@Service
@RequiredArgsConstructor
public class CausacionGastoService {

    public static final String TIPO_ORIGEN = "CAUSACION_GASTO";
    static final Set<String> TIPOS_SOPORTE = Set.of("FACTURA", "DOCUMENTO_SOPORTE", "CUENTA_COBRO", "OTRO");

    private final CausacionGastoJPARepository causacionRepo;
    private final CausacionGastoLineaJPARepository lineaRepo;
    private final CausacionGastoQueryRepository query;
    private final TerceroJPARepository terceroRepo;
    private final SucursalJPARepository sucursalRepo;
    private final PlanCuentaJPARepository cuentaRepo;
    private final CentroCostoJPARepository centroCostoRepo;
    private final TarifaRetencionJPARepository tarifaRepo;
    private final CuentaPagarService cuentaPagarService;
    private final CuentaPagarJPARepository cuentaPagarRepo;
    private final PeriodoContableResolver periodoGuard;
    private final ApplicationEventPublisher eventPublisher;

    /** Calcula sin grabar: el formulario muestra lo que va a quedar. */
    public Calculo calcular(Integer empresaId, Crear dto) {
        TerceroEntity tercero = dto.getTerceroId() != null
                ? terceroRepo.findByIdAndEmpresaId(dto.getTerceroId(), empresaId).orElse(null)
                : null;
        return CalculadoraCausacion.calcular(dto.getLineas(),
                tercero != null ? tercero.getTipoPersona() : null, tarifas(empresaId, dto.getLineas()));
    }

    @Transactional
    public Detalle crear(Integer empresaId, Integer usuarioId, Crear dto) {
        TerceroEntity tercero = validarTercero(empresaId, dto.getTerceroId());
        if (dto.getSucursalId() != null) {
            sucursalRepo.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La sede no existe."));
        }
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();
        LocalDate vence = dto.getFechaVencimiento() != null ? dto.getFechaVencimiento() : fecha.plusDays(30);
        if (vence.isBefore(fecha)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El vencimiento no puede ser anterior a la fecha.");
        }
        periodoGuard.exigirAbierto(empresaId, fecha);
        String tipoSoporte = dto.getTipoSoporte() != null ? dto.getTipoSoporte().toUpperCase() : "FACTURA";
        if (!TIPOS_SOPORTE.contains(tipoSoporte)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de soporte no válido: " + dto.getTipoSoporte());
        }
        String numeroSoporte = dto.getNumeroSoporte() != null && !dto.getNumeroSoporte().isBlank()
                ? dto.getNumeroSoporte().trim() : null;
        if (numeroSoporte != null && query.soporteRepetido(empresaId, tercero.getId(), numeroSoporte)) {
            throw new GlobalException(HttpStatus.CONFLICT, "La factura " + numeroSoporte
                    + " de este proveedor ya está causada. Anule la anterior si quiere causarla de nuevo.");
        }
        if (dto.getCuentaPagarContableId() != null) {
            PlanCuentaEntity cxp = cuentaMovimiento(empresaId, dto.getCuentaPagarContableId(), "La cuenta por pagar");
            if (!cxp.getCodigo().startsWith("2")) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta por pagar debe ser un pasivo (clase 2), como 2205 o 2335.");
            }
        }
        validarLineas(empresaId, dto.getLineas());

        Calculo calculo = CalculadoraCausacion.calcular(dto.getLineas(), tercero.getTipoPersona(),
                tarifas(empresaId, dto.getLineas()));

        CausacionGastoEntity c = causacionRepo.save(CausacionGastoEntity.builder()
                .empresaId(empresaId)
                .sucursalId(dto.getSucursalId())
                .consecutivo(query.siguienteConsecutivo(empresaId))
                .terceroId(tercero.getId())
                .fecha(fecha)
                .fechaVencimiento(vence)
                .tipoSoporte(tipoSoporte)
                .numeroSoporte(numeroSoporte)
                .descripcion(dto.getDescripcion())
                .cuentaPagarContableId(dto.getCuentaPagarContableId())
                .subtotal(calculo.getSubtotal())
                .iva(calculo.getIva())
                .retefuente(calculo.getRetefuente())
                .reteiva(calculo.getReteiva())
                .reteica(calculo.getReteica())
                .totalPagar(calculo.getTotalPagar())
                .estado(CausacionGastoEntity.ESTADO_CAUSADA)
                .usuarioId(usuarioId)
                .build());

        for (Linea l : calculo.getLineas()) {
            lineaRepo.save(CausacionGastoLineaEntity.builder()
                    .causacionId(c.getId())
                    .cuentaId(l.getCuentaId())
                    .descripcion(l.getDescripcion())
                    .centroCostoId(l.getCentroCostoId())
                    .base(l.getBase())
                    .tarifaIva(l.getTarifaIva())
                    .valorIva(l.getValorIva())
                    .ivaDescontable(l.getIvaDescontable())
                    .tarifaRetencionId(l.getTarifaRetencionId())
                    .tarifaRetefuente(l.getTarifaRetefuente())
                    .valorRetefuente(l.getValorRetefuente())
                    .tarifaReteiva(l.getTarifaReteiva())
                    .valorReteiva(l.getValorReteiva())
                    .tarifaReteica(l.getTarifaReteica())
                    .valorReteica(l.getValorReteica())
                    .build());
        }

        // Lo que se le debe al proveedor es el neto: las retenciones se le pagan a la DIAN.
        CreateCuentaPagarDto cxp = new CreateCuentaPagarDto();
        cxp.setProveedorId(tercero.getId());
        cxp.setTotalDeuda(calculo.getTotalPagar());
        cxp.setFechaEmision(fecha.atStartOfDay());
        cxp.setFechaVencimiento(vence.atStartOfDay());
        cxp.setNumeroFacturaExterno(numeroSoporte);
        cxp.setObservaciones("Causación CG-" + c.getConsecutivo()
                + (dto.getDescripcion() != null && !dto.getDescripcion().isBlank() ? " - " + dto.getDescripcion() : ""));
        c.setCuentaPagarId(cuentaPagarService.crear(cxp, empresaId, usuarioId != null ? usuarioId.longValue() : null).getId());
        causacionRepo.save(c);

        eventPublisher.publishEvent(new DocumentoContabilizableEvent(TIPO_ORIGEN, c.getId(), empresaId, usuarioId));

        Detalle d = obtener(empresaId, c.getId());
        d.setAvisos(calculo.getAvisos());
        return d;
    }

    @Transactional
    public Detalle anular(Integer empresaId, Integer usuarioId, Long id, String motivo) {
        CausacionGastoEntity c = causacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Causación no encontrada"));
        if (!CausacionGastoEntity.ESTADO_CAUSADA.equals(c.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La causación ya está anulada.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la anulación.");
        }
        if (c.getCuentaPagarId() != null) {
            cuentaPagarRepo.findById(c.getCuentaPagarId()).ifPresent(cxp -> {
                if (cxp.getTotalAbonado() != null && cxp.getTotalAbonado().signum() > 0) {
                    throw new GlobalException(HttpStatus.CONFLICT,
                            "Esta causación ya tiene pagos: anule los abonos de la cuenta por pagar antes de anularla.");
                }
                cxp.setEstado("anulada");
                cxp.setSaldoPendiente(BigDecimal.ZERO);
                cuentaPagarRepo.save(cxp);
            });
        }
        c.setEstado(CausacionGastoEntity.ESTADO_ANULADA);
        c.setAnuladoPor(usuarioId);
        c.setAnuladoAt(LocalDateTime.now());
        c.setMotivoAnulacion(motivo.trim());
        causacionRepo.save(c);

        // El contraasiento sale después del commit, con fecha de hoy.
        eventPublisher.publishEvent(new ContabilidadReversaEvent(TIPO_ORIGEN, c.getId(), empresaId, usuarioId));
        return obtener(empresaId, id);
    }

    public Detalle obtener(Integer empresaId, Long id) {
        Detalle d = query.detalle(id, empresaId);
        if (d == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Causación no encontrada");
        d.setLineas(query.lineas(id));
        return d;
    }

    public PageImpl<Fila> listar(Integer empresaId, PageableDto<Object> pageable) {
        return query.listar(pageable, empresaId);
    }

    // ── Validaciones ─────────────────────────────────────────────────────────

    private TerceroEntity validarTercero(Integer empresaId, Long terceroId) {
        if (terceroId == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el proveedor.");
        }
        TerceroEntity t = terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El proveedor no existe."));
        if (!Boolean.TRUE.equals(t.getEsProveedor())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El tercero no está marcado como proveedor. Márquelo en Terceros para poder causarle.");
        }
        return t;
    }

    private void validarLineas(Integer empresaId, List<LineaCrear> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Agregue al menos una línea a la causación.");
        }
        int n = 0;
        for (LineaCrear l : lineas) {
            n++;
            if (l.getCuentaId() == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + n + ": falta la cuenta.");
            }
            PlanCuentaEntity cuenta = cuentaMovimiento(empresaId, l.getCuentaId(), "Línea " + n + ": la cuenta");
            char clase = cuenta.getCodigo().charAt(0);
            if (clase != '5' && clase != '6' && clase != '7' && clase != '1') {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + n + ": la cuenta " + cuenta.getCodigo()
                        + " no es de gasto o costo (5, 6, 7) ni de activo (1).");
            }
            if (l.getCentroCostoId() != null) {
                final int linea = n;
                centroCostoRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(l.getCentroCostoId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                                "Línea " + linea + ": el centro de costo no existe."));
            }
        }
    }

    private PlanCuentaEntity cuentaMovimiento(Integer empresaId, Long cuentaId, String que) {
        PlanCuentaEntity c = cuentaRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, que + " no existe."));
        if (!Boolean.TRUE.equals(c.getActiva())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, que + " " + c.getCodigo() + " está inactiva.");
        }
        if (!Boolean.TRUE.equals(c.getAuxiliar())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, que + " " + c.getCodigo()
                    + " no recibe movimiento: elija una subcuenta auxiliar.");
        }
        return c;
    }

    /** Conceptos de retención activos de la empresa que usan las líneas. */
    private Map<Long, TarifaRetencionEntity> tarifas(Integer empresaId, List<LineaCrear> lineas) {
        Map<Long, TarifaRetencionEntity> r = new HashMap<>();
        if (lineas == null) return r;
        lineas.stream().map(LineaCrear::getTarifaRetencionId).filter(Objects::nonNull).distinct()
                .forEach(id -> tarifaRepo.findByIdAndEmpresaId(id, empresaId)
                        .filter(t -> Boolean.TRUE.equals(t.getActivo()) && "RETEFUENTE".equalsIgnoreCase(t.getTipo()))
                        .ifPresent(t -> r.put(id, t)));
        return r;
    }
}

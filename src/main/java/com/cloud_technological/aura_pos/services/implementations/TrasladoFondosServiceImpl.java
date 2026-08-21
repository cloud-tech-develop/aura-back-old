package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent;
import com.cloud_technological.aura_pos.dto.traslado_fondos.CreateTrasladoFondosDto;
import com.cloud_technological.aura_pos.dto.traslado_fondos.TrasladoFondosDto;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.TrasladoFondosEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.traslado_fondos.TrasladoFondosJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.services.TesoreriaService;
import com.cloud_technological.aura_pos.services.TrasladoFondosService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Registra el traslado y todos sus efectos: el arqueo de las cajas que toca,
 * el extracto de los bancos que toca y el asiento contable.
 *
 * <p>Los dos extremos se resuelven con {@link OrigenFondosService}, el mismo
 * que usan compras y gastos. No es reutilización por comodidad: las reglas de
 * "qué cuenta contable es esta caja" y "esta cuenta está habilitada para mover
 * dinero" tienen que ser idénticas vengan de donde vengan, o el traslado
 * acreditaría una cuenta que el gasto rechaza.
 */
@Service
@RequiredArgsConstructor
public class TrasladoFondosServiceImpl implements TrasladoFondosService {

    private static final String TIPO_ORIGEN = "TRASLADO_FONDOS";
    private static final String EFECTIVO = "EFECTIVO";
    private static final String TRANSFERENCIA = "TRANSFERENCIA";

    private final TrasladoFondosJPARepository trasladoRepository;
    private final MovimientoCajaJPARepository movimientoCajaRepository;
    private final TurnoCajaJPARepository turnoRepository;
    private final CuentaBancariaJPARepository cuentaBancariaRepository;
    private final PlanCuentaJPARepository planCuentaRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final OrigenFondosService origenFondosService;
    private final TesoreriaService tesoreriaService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TrasladoFondosDto crear(Integer empresaId, Integer usuarioId,
            CreateTrasladoFondosDto dto) {
        validarExtremosDistintos(dto);

        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();

        // Resolver ambos extremos ANTES de guardar: si el destino no sirve como
        // medio de pago, el traslado no debe existir a medias con el arqueo del
        // origen ya movido.
        OrigenFondosService.OrigenFondos origen = resolverExtremo(empresaId, dto, true);
        OrigenFondosService.OrigenFondos destino = resolverExtremo(empresaId, dto, false);

        TrasladoFondosEntity traslado = TrasladoFondosEntity.builder()
                .empresaId(empresaId)
                .sucursalId(dto.getSucursalId())
                .fecha(fecha)
                .monto(dto.getMonto())
                .origenTipo(normalizarTipo(dto.getOrigenTipo()))
                .origenTurnoCajaId(origen.turnoId())
                .origenCuentaBancoId(dto.getOrigenCuentaBancoId())
                .origenCuentaId(origen.cuentaContableId())
                .destinoTipo(normalizarTipo(dto.getDestinoTipo()))
                .destinoTurnoCajaId(destino.turnoId())
                .destinoCuentaBancoId(dto.getDestinoCuentaBancoId())
                .destinoCuentaId(destino.cuentaContableId())
                .concepto(dto.getConcepto() != null && !dto.getConcepto().isBlank()
                        ? dto.getConcepto().trim().toUpperCase()
                        : TrasladoFondosEntity.CONCEPTO_TRASLADO)
                .observacion(dto.getObservacion())
                .responsableId(dto.getResponsableId())
                .usuarioId(usuarioId)
                .estado(TrasladoFondosEntity.ESTADO_CONFIRMADO)
                .build();

        traslado = trasladoRepository.save(traslado);

        moverArqueos(traslado, origen, destino, usuarioId);
        moverBancos(traslado, dto, empresaId, usuarioId);

        // DB destino / CR origen, tras el commit.
        eventPublisher.publishEvent(
                new DocumentoContabilizableEvent(TIPO_ORIGEN, traslado.getId(), empresaId, usuarioId));

        return toDto(traslado);
    }

    @Override
    public TrasladoFondosDto obtener(Long id, Integer empresaId) {
        return toDto(trasladoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "Traslado de fondos no encontrado")));
    }

    @Override
    public List<TrasladoFondosDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta,
            String concepto) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();

        List<TrasladoFondosEntity> traslados = concepto != null && !concepto.isBlank()
                ? trasladoRepository.findByEmpresaIdAndConceptoAndFechaBetweenOrderByFechaDescIdDesc(
                        empresaId, concepto.trim().toUpperCase(), d, h)
                : trasladoRepository.findByEmpresaIdAndFechaBetweenOrderByFechaDescIdDesc(
                        empresaId, d, h);

        return traslados.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Resolución de extremos ──────────────────────────────────────────────

    /**
     * Traduce un extremo del traslado a la pregunta que ya sabe responder el
     * resolutor de origen de fondos: qué cuenta contable es y, si es efectivo,
     * en qué turno cae.
     */
    private OrigenFondosService.OrigenFondos resolverExtremo(Integer empresaId,
            CreateTrasladoFondosDto dto, boolean esOrigen) {
        String tipo = normalizarTipo(esOrigen ? dto.getOrigenTipo() : dto.getDestinoTipo());
        String etiqueta = esOrigen ? "origen del traslado" : "destino del traslado";

        Long turnoId = esOrigen ? dto.getOrigenTurnoCajaId() : dto.getDestinoTurnoCajaId();
        Long bancoId = esOrigen ? dto.getOrigenCuentaBancoId() : dto.getDestinoCuentaBancoId();
        Long cuentaId = esOrigen ? dto.getOrigenCuentaId() : dto.getDestinoCuentaId();

        switch (tipo) {
            case TrasladoFondosEntity.TIPO_CAJA -> {
                exigirSolo(cuentaId == null && bancoId == null, etiqueta,
                        "una caja no lleva cuenta bancaria ni cuenta contable");
                return origenFondosService.resolver(empresaId, new OrigenFondosService.Solicitud(
                        EFECTIVO, turnoId, null, null, dto.getSucursalId(), etiqueta));
            }
            case TrasladoFondosEntity.TIPO_BANCO -> {
                if (bancoId == null) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Debe indicar la cuenta bancaria del " + etiqueta);
                }
                exigirSolo(cuentaId == null && turnoId == null, etiqueta,
                        "un banco no lleva turno de caja ni cuenta contable");
                return origenFondosService.resolver(empresaId, new OrigenFondosService.Solicitud(
                        TRANSFERENCIA, null, bancoId, null, dto.getSucursalId(), etiqueta));
            }
            case TrasladoFondosEntity.TIPO_CUENTA -> {
                if (cuentaId == null) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Debe indicar la cuenta contable del " + etiqueta);
                }
                exigirSolo(bancoId == null && turnoId == null, etiqueta,
                        "una cuenta contable no lleva turno de caja ni cuenta bancaria");
                return origenFondosService.resolver(empresaId, new OrigenFondosService.Solicitud(
                        null, null, null, cuentaId, dto.getSucursalId(), etiqueta));
            }
            default -> throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El " + etiqueta + " debe ser CAJA, BANCO o CUENTA");
        }
    }

    /**
     * Mover plata de un sitio al mismo sitio no es un traslado: es un asiento
     * que se anula solo y un arqueo que suma y resta lo mismo. Se rechaza aquí
     * y no con un CHECK en la tabla porque el mensaje tiene que explicar cuál
     * de los tres identificadores coincide.
     */
    private void validarExtremosDistintos(CreateTrasladoFondosDto dto) {
        String origen = normalizarTipo(dto.getOrigenTipo());
        String destino = normalizarTipo(dto.getDestinoTipo());
        if (!origen.equals(destino)) {
            return;
        }
        boolean mismoDestino = switch (origen) {
            case TrasladoFondosEntity.TIPO_CAJA ->
                    java.util.Objects.equals(dto.getOrigenTurnoCajaId(), dto.getDestinoTurnoCajaId());
            case TrasladoFondosEntity.TIPO_BANCO ->
                    java.util.Objects.equals(dto.getOrigenCuentaBancoId(), dto.getDestinoCuentaBancoId());
            case TrasladoFondosEntity.TIPO_CUENTA ->
                    java.util.Objects.equals(dto.getOrigenCuentaId(), dto.getDestinoCuentaId());
            default -> false;
        };
        if (mismoDestino) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El origen y el destino del traslado son el mismo. Elija destinos distintos");
        }
    }

    private void exigirSolo(boolean condicion, String etiqueta, String explicacion) {
        if (!condicion) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Datos incoherentes en el " + etiqueta + ": " + explicacion);
        }
    }

    private static String normalizarTipo(String tipo) {
        return tipo != null ? tipo.trim().toUpperCase() : "";
    }

    // ── Efectos ─────────────────────────────────────────────────────────────

    /**
     * El efectivo que sale de un cajón tiene que verse en su cierre, y el que
     * entra a otro también. Constituir la caja menor desde la caja del punto
     * baja el arqueo de esa caja: la plata efectivamente ya no está ahí.
     */
    private void moverArqueos(TrasladoFondosEntity traslado,
            OrigenFondosService.OrigenFondos origen,
            OrigenFondosService.OrigenFondos destino, Integer usuarioId) {
        UsuarioEntity usuario = usuarioId != null
                ? usuarioRepository.findById(usuarioId).orElse(null)
                : null;

        if (origen.generaMovimientoCaja()) {
            movimientoCajaRepository.save(MovimientoCajaEntity.builder()
                    .turnoCaja(origen.turno())
                    .usuario(usuario)
                    .tipo("EGRESO")
                    .concepto(descripcion(traslado) + " (salida)")
                    .monto(traslado.getMonto())
                    .fecha(traslado.getFecha())
                    .origenTipo(MovimientoCajaEntity.ORIGEN_TRASLADO_FONDOS)
                    .origenId(traslado.getId())
                    .origenInferido(origen.turnoInferido())
                    .metodoPago(EFECTIVO)
                    .build());
        }
        if (destino.generaMovimientoCaja()) {
            movimientoCajaRepository.save(MovimientoCajaEntity.builder()
                    .turnoCaja(destino.turno())
                    .usuario(usuario)
                    .tipo("INGRESO")
                    .concepto(descripcion(traslado) + " (entrada)")
                    .monto(traslado.getMonto())
                    .fecha(traslado.getFecha())
                    .origenTipo(MovimientoCajaEntity.ORIGEN_TRASLADO_FONDOS)
                    .origenId(traslado.getId())
                    .origenInferido(destino.turnoInferido())
                    .metodoPago(EFECTIVO)
                    .build());
        }
    }

    /**
     * Ajusta el saldo y deja el registro en el extracto interno de las cuentas
     * bancarias involucradas, para que la conciliación vea el movimiento.
     */
    private void moverBancos(TrasladoFondosEntity traslado, CreateTrasladoFondosDto dto,
            Integer empresaId, Integer usuarioId) {
        String referencia = "TF-" + traslado.getId();

        if (TrasladoFondosEntity.TIPO_BANCO.equals(traslado.getOrigenTipo())
                && traslado.getOrigenCuentaBancoId() != null) {
            tesoreriaService.registrarMovimientoDeDocumento(empresaId, usuarioId,
                    new TesoreriaService.MovimientoDocumento(
                            traslado.getOrigenCuentaBancoId(), true, traslado.getMonto(),
                            descripcion(traslado) + " (salida)", null, referencia, TIPO_ORIGEN));
        }
        if (TrasladoFondosEntity.TIPO_BANCO.equals(traslado.getDestinoTipo())
                && traslado.getDestinoCuentaBancoId() != null) {
            tesoreriaService.registrarMovimientoDeDocumento(empresaId, usuarioId,
                    new TesoreriaService.MovimientoDocumento(
                            traslado.getDestinoCuentaBancoId(), false, traslado.getMonto(),
                            descripcion(traslado) + " (entrada)", null, referencia, TIPO_ORIGEN));
        }
    }

    private static String descripcion(TrasladoFondosEntity t) {
        return switch (t.getConcepto()) {
            case TrasladoFondosEntity.CONCEPTO_CONSTITUCION_CAJA_MENOR -> "Constitución de caja menor";
            case TrasladoFondosEntity.CONCEPTO_REEMBOLSO_CAJA_MENOR -> "Reembolso de caja menor";
            case TrasladoFondosEntity.CONCEPTO_CONSIGNACION -> "Consignación bancaria";
            default -> "Traslado de fondos";
        };
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────

    private TrasladoFondosDto toDto(TrasladoFondosEntity e) {
        TrasladoFondosDto dto = new TrasladoFondosDto();
        dto.setId(e.getId());
        dto.setSucursalId(e.getSucursalId());
        dto.setFecha(e.getFecha());
        dto.setMonto(e.getMonto());

        dto.setOrigenTipo(e.getOrigenTipo());
        dto.setOrigenTurnoCajaId(e.getOrigenTurnoCajaId());
        dto.setOrigenCuentaBancoId(e.getOrigenCuentaBancoId());
        dto.setOrigenCuentaId(e.getOrigenCuentaId());
        dto.setOrigenNombre(nombreExtremo(e.getOrigenTipo(), e.getOrigenTurnoCajaId(),
                e.getOrigenCuentaBancoId(), e.getOrigenCuentaId(), e.getEmpresaId()));

        dto.setDestinoTipo(e.getDestinoTipo());
        dto.setDestinoTurnoCajaId(e.getDestinoTurnoCajaId());
        dto.setDestinoCuentaBancoId(e.getDestinoCuentaBancoId());
        dto.setDestinoCuentaId(e.getDestinoCuentaId());
        dto.setDestinoNombre(nombreExtremo(e.getDestinoTipo(), e.getDestinoTurnoCajaId(),
                e.getDestinoCuentaBancoId(), e.getDestinoCuentaId(), e.getEmpresaId()));

        dto.setConcepto(e.getConcepto());
        dto.setObservacion(e.getObservacion());
        dto.setResponsableId(e.getResponsableId());
        if (e.getResponsableId() != null) {
            dto.setResponsableNombre(usuarioRepository.findById(e.getResponsableId())
                    .map(UsuarioEntity::getUsername).orElse(null));
        }
        dto.setUsuarioId(e.getUsuarioId());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    /** Nombre legible del extremo, para que el listado no muestre solo ids. */
    private String nombreExtremo(String tipo, Long turnoId, Long bancoId, Long cuentaId,
            Integer empresaId) {
        return switch (tipo != null ? tipo : "") {
            case TrasladoFondosEntity.TIPO_CAJA -> turnoId == null ? "Caja"
                    : turnoRepository.findByIdAndCajaSucursalEmpresaId(turnoId, empresaId)
                            .map(t -> t.getCaja() != null ? t.getCaja().getNombre() : "Caja")
                            .orElse("Caja");
            case TrasladoFondosEntity.TIPO_BANCO -> bancoId == null ? "Banco"
                    : cuentaBancariaRepository.findByIdAndEmpresaId(bancoId, empresaId)
                            .map(c -> c.getNombre()).orElse("Banco");
            default -> cuentaId == null ? "Cuenta contable"
                    : planCuentaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                            .map(c -> c.getCodigo() + " — " + c.getNombre())
                            .orElse("Cuenta contable");
        };
    }
}

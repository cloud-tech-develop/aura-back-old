package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.compras.CreateGastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoDto;
import com.cloud_technological.aura_pos.dto.compras.GastoTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoQueryRepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.OrigenFondosService;
import com.cloud_technological.aura_pos.services.TesoreriaService;
import com.cloud_technological.aura_pos.utils.Terceros;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.services.GastoService;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.event.OperacionContabilizableEvent;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.ComprobanteCajaService;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GastoServiceImpl implements GastoService {

    /** Guard: no se registran documentos con fecha en un mes contable cerrado. */
    @Autowired
    private PeriodoContableResolver periodoGuard;

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TerceroJPARepository terceroJPARepository;
    @Autowired private PlanCuentaJPARepository planCuentaJPARepository;
    @Autowired private ControlFechaRetroactivaService controlFechaRetroactiva;
    @Autowired private GastoJPARepository gastoJPARepository;
    @Autowired private GastoQueryRepository gastoQueryRepository;
    @Autowired private EmpresaJPARepository empresaJPARepository;
    @Autowired private SucursalJPARepository sucursalJPARepository;
    @Autowired private UsuarioJPARepository usuarioJPARepository;
    @Autowired private OrigenFondosService origenFondosService;
    @Autowired private MovimientoCajaJPARepository movimientoCajaJPARepository;
    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private CuentaPagarService cuentaPagarService;
    @Autowired private ComprobanteCajaService comprobanteCajaService;
    @Autowired private ResolucionCuentaPago resolucionCuentaPago;
    @Autowired private CuentaPagarJPARepository cuentaPagarJPARepository;
    @Autowired private ContabilidadAutoService contabilidadAutoService;
    @Autowired private SecurityUtils securityUtils;

    @Override
    public GastoDto obtener(Long id, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        return toDto(gasto);
    }

    @Override
    @Transactional
    public GastoDto crear(CreateGastoDto dto, Integer empresaId, Long usuarioId) {
        var empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElse(null);

        GastoEntity gasto = new GastoEntity();
        gasto.setEmpresa(empresa);
        gasto.setSucursal(sucursal);
        gasto.setUsuario(usuario);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        gasto.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now());
        periodoGuard.exigirAbierto(empresaId, gasto.getFecha());
        gasto.setDeducible(dto.getDeducible());
        gasto.setEstado("ACTIVO");
        gasto.setCreatedAt(LocalDateTime.now());
        mapCamposTributarios(dto, gasto);
        if (valorAPagar(gasto).signum() < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las retenciones no pueden superar el valor del gasto más el IVA");

        // De dónde sale la plata. Antes no se preguntaba: el asiento acreditaba
        // CAJA a ciegas, así que un gasto pagado por transferencia dejaba la
        // caja contable en negativo con el banco intacto, y como nunca se
        // registraba movimiento_caja tampoco aparecía en el cierre del cajero.
        boolean esCredito = MediosPago.CREDITO.equalsIgnoreCase(dto.getFormaPago());
        boolean salidaOtroDia = Boolean.TRUE.equals(dto.getSalidaCajaOtroDia());
        OrigenFondosService.OrigenFondos origen = null;
        if (!esCredito) {
            origen = origenFondosService.resolver(empresaId,
                    new OrigenFondosService.Solicitud(
                            dto.getMetodoPago(),
                            null,
                            dto.getCuentaBancariaId(),
                            dto.getCuentaPagoId(),
                            dto.getSucursalId(),
                            "gasto",
                            salidaOtroDia));
            origenFondosService.exigirSaldoDisponible(empresaId, origen, valorAPagar(gasto),
                    "gasto", null, null);
            gasto.setCuentaPagoId(origen.cuentaContableId());
            gasto.setSalidaCajaOtroDia(salidaOtroDia);
        }

        // Freno de documentos viejos: solo cuando la plata sale de la caja, que
        // es la única vía que le descuadra el arqueo a otra persona.
        Integer autorizadoPor = controlFechaRetroactiva.validar(
                empresaId,
                dto.getFecha(),
                origen != null && origen.generaMovimientoCaja(),
                dto.getMotivoRetroactivo(),
                usuarioId,
                "gasto");
        if (autorizadoPor != null) {
            gasto.setMotivoRetroactivo(dto.getMotivoRetroactivo().trim());
            gasto.setAutorizadoPor(autorizadoPor);
        }

        gasto = gastoJPARepository.save(gasto);

        if (esCredito) {
            // El gasto a crédito no saca plata hoy: deja una obligación con el
            // tercero, igual que una compra a crédito. Sin egreso no hay
            // comprobante: se emitirá cuando se abone la cuenta por pagar.
            registrarCuentaPorPagar(dto, gasto, empresaId, usuarioId);
        } else {
            registrarEgresoDeCaja(origen, gasto, empresaId);
            descontarSaldoBancario(dto, gasto, empresaId, usuarioId);
            emitirComprobanteEgreso(dto, gasto, empresaId, usuarioId);
        }

        // Asiento contable del gasto tras el commit.
        eventPublisher.publishEvent(
                new OperacionContabilizableEvent(
                        "GASTO", gasto.getId(), empresaId, usuarioId != null ? usuarioId.intValue() : null));

        return toDto(gasto);
    }

    /** El efectivo del gasto sale de una caja y tiene que verse en su cierre. */
    private void registrarEgresoDeCaja(OrigenFondosService.OrigenFondos origen,
            GastoEntity gasto, Integer empresaId) {
        if (origen == null || !origen.generaMovimientoCaja()) {
            return;
        }
        MovimientoCajaEntity egreso = MovimientoCajaEntity.builder()
                .turnoCaja(origen.turno())
                .usuario(gasto.getUsuario())
                .tipo("EGRESO")
                .concepto("Gasto #" + gasto.getId()
                        + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""))
                .monto(valorAPagar(gasto))
                // La plata sale hoy aunque el gasto sea de otra fecha: el turno
                // que se afecta es el de hoy, y la fecha del documento se guarda
                // aparte para que el cierre muestre el desfase en vez de
                // esconderlo dentro del total.
                .fecha(LocalDate.now())
                .fechaDocumento(gasto.getFecha())
                .origenTipo(MovimientoCajaEntity.ORIGEN_GASTO)
                .origenId(gasto.getId())
                .origenInferido(origen.turnoInferido())
                .metodoPago(gasto.getMetodoPago())
                .build();
        movimientoCajaJPARepository.save(egreso);
    }

    private void descontarSaldoBancario(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        String beneficiario = dto.getTerceroId() != null
                ? terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                        .map(Terceros::nombreVisible).orElse(null)
                : null;
        String referencia = dto.getNumeroDocSoporte() != null && !dto.getNumeroDocSoporte().isBlank()
                ? dto.getNumeroDocSoporte().trim()
                : "GASTO-" + gasto.getId();

        tesoreriaService.registrarMovimientoDeDocumento(empresaId,
                usuarioId != null ? usuarioId.intValue() : null,
                new TesoreriaService.MovimientoDocumento(
                        dto.getCuentaBancariaId(), true, valorAPagar(gasto),
                        "Gasto #" + gasto.getId()
                                + (gasto.getDescripcion() != null ? " — " + gasto.getDescripcion() : ""),
                        beneficiario, referencia, gasto.getCategoria()));
    }

    /**
     * Todo dinero que sale necesita su soporte, salga de la caja, del banco o de
     * una cuenta contable. Se sincroniza (no se genera a secas) para que editar
     * el gasto corrija el comprobante en vez de emitir uno nuevo.
     */
    private void emitirComprobanteEgreso(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        String beneficiario = dto.getTerceroId() != null
                ? terceroJPARepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                        .map(Terceros::nombreVisible).orElse(null)
                : null;

        comprobanteCajaService.sincronizarDeDocumento(empresaId,
                usuarioId != null ? usuarioId.intValue() : null,
                "EGRESO",
                "Gasto #" + gasto.getId()
                        + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""),
                valorAPagar(gasto),
                gasto.getMetodoPago(),
                beneficiario,
                "GASTO", gasto.getId(), null);
    }

    private void registrarCuentaPorPagar(CreateGastoDto dto, GastoEntity gasto,
            Integer empresaId, Long usuarioId) {
        if (dto.getTerceroId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un gasto a crédito necesita el tercero al que se le queda debiendo");
        }
        CreateCuentaPagarDto cxp = new CreateCuentaPagarDto();
        cxp.setProveedorId(dto.getTerceroId());
        cxp.setTotalDeuda(valorAPagar(gasto));
        cxp.setFechaEmision(gasto.getFecha().atStartOfDay());
        cxp.setFechaVencimiento(dto.getFechaVencimiento() != null
                ? dto.getFechaVencimiento()
                : gasto.getFecha().plusDays(30).atStartOfDay());
        cxp.setObservaciones("Gasto #" + gasto.getId() + " - Gasto a crédito");
        cuentaPagarService.crear(cxp, empresaId, usuarioId);
    }

    @Override
    @Transactional
    public GastoDto actualizar(Long id, CreateGastoDto dto, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        if ("ELIMINADO".equalsIgnoreCase(gasto.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El gasto está anulado: no se puede editar");

        // Editar solo cambiaba las columnas: la caja, el banco y la cuenta por
        // pagar seguían con el valor viejo y el asiento también. Lo que mueve
        // dinero (valor, fecha, forma o medio de pago, cuenta, tercero de la
        // deuda) no se edita: se anula y se registra de nuevo, que deja todo
        // consistente. Lo demás (cuenta de gasto, centro de costo, IVA,
        // retenciones…) sí, y el asiento se rehace abajo.
        String formaNueva = dto.getFormaPago() != null ? dto.getFormaPago().trim().toUpperCase() : "CONTADO";
        String metodoNuevo = dto.getMetodoPago() != null ? MediosPago.normalizar(dto.getMetodoPago()) : MediosPago.EFECTIVO;
        boolean cambiaDinero =
                (dto.getMonto() != null && (gasto.getMonto() == null || dto.getMonto().compareTo(gasto.getMonto()) != 0))
                || (dto.getFecha() != null && !dto.getFecha().equals(gasto.getFecha()))
                || !formaNueva.equalsIgnoreCase(gasto.getFormaPago() != null ? gasto.getFormaPago() : "CONTADO")
                || (!MediosPago.CREDITO.equalsIgnoreCase(formaNueva)
                        && !metodoNuevo.equalsIgnoreCase(gasto.getMetodoPago() != null ? gasto.getMetodoPago() : MediosPago.EFECTIVO))
                || !Objects.equals(dto.getCuentaBancariaId(), gasto.getCuentaBancariaId())
                || (MediosPago.CREDITO.equalsIgnoreCase(formaNueva)
                        && !Objects.equals(dto.getTerceroId(), gasto.getTerceroId()));
        if (cambiaDinero)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El valor, la fecha y la forma de pago de un gasto no se editan: anúlelo y regístrelo de nuevo");

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        BigDecimal aPagarAntes = valorAPagar(gasto);
        gasto.setSucursal(sucursal);
        gasto.setCategoria(dto.getCategoria());
        gasto.setDescripcion(dto.getDescripcion());
        gasto.setMonto(dto.getMonto());
        if (dto.getFecha() != null) gasto.setFecha(dto.getFecha());
        periodoGuard.exigirAbierto(empresaId, gasto.getFecha());
        gasto.setDeducible(dto.getDeducible());
        mapCamposTributarios(dto, gasto);
        // El IVA y las retenciones cambian lo que sale de la caja o se le debe
        // al tercero: igual que el valor, eso no se edita.
        if (valorAPagar(gasto).compareTo(aPagarAntes) != 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Cambiar el IVA o las retenciones cambia el valor pagado: anule el gasto y regístrelo de nuevo");

        gasto = gastoJPARepository.save(gasto);

        // El asiento se rehace con los datos nuevos cuando la edición quede
        // confirmada, reversando y generando en una sola transacción.
        final Long gastoId = gasto.getId();
        final Long usuarioActual = securityUtils.getUsuarioId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            contabilidadAutoService.reprocesarGasto(gastoId, empresaId,
                                    usuarioActual != null ? usuarioActual.intValue() : null);
                        } catch (Exception ex) {
                            LoggerFactory.getLogger(GastoServiceImpl.class)
                                    .warn("No se pudo rehacer el asiento del gasto #{}: {}", gastoId, ex.getMessage());
                        }
                    }
                });
        return toDto(gasto);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        GastoEntity gasto = gastoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        if ("ELIMINADO".equalsIgnoreCase(gasto.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El gasto ya está anulado");

        // Antes solo cambiaba el estado: la cuenta por pagar seguía viva y el
        // dinero que salió de la caja o del banco no volvía. Ahora se deshace
        // lo mismo que el gasto movió.
        Long cxpId = gastoQueryRepository.cuentaPorPagarDeGasto(empresaId, id);
        if (cxpId != null) {
            var cxp = cuentaPagarJPARepository.findById(cxpId).orElse(null);
            if (cxp != null) {
                if (cxp.getTotalAbonado() != null && cxp.getTotalAbonado().signum() > 0)
                    throw new GlobalException(HttpStatus.CONFLICT,
                            "La cuenta por pagar de este gasto ya tiene abonos: anule los abonos antes de anular el gasto");
                cxp.setEstado("anulada");
                cxp.setSaldoPendiente(BigDecimal.ZERO);
                cuentaPagarJPARepository.save(cxp);
            }
        }
        if (!MediosPago.CREDITO.equalsIgnoreCase(gasto.getFormaPago())) {
            devolverDineroDelGasto(gasto, empresaId);
        }

        gasto.setEstado("ELIMINADO");
        gastoJPARepository.save(gasto);

        // El soporte no puede seguir vigente si el gasto ya no existe.
        comprobanteCajaService.anularDeDocumento(empresaId, "GASTO", gasto.getId(),
                "Gasto eliminado");

        // Reversar el asiento del gasto tras el commit.
        eventPublisher.publishEvent(
                new ContabilidadReversaEvent(
                        "GASTO", gasto.getId(), empresaId, null));
    }

    /**
     * Devuelve al banco o a la caja lo que salió por el gasto. Al banco
     * siempre que el gasto tuvo cuenta bancaria; a la caja solo si de verdad
     * dejó un egreso de caja (un gasto "ya salió de la caja otro día" no lo
     * dejó, y devolverlo inflaría el arqueo de hoy).
     */
    private void devolverDineroDelGasto(GastoEntity gasto, Integer empresaId) {
        Long usuarioId = securityUtils.getUsuarioId();
        // Se devuelve lo que salió, que en los gastos de antes del arreglo no
        // es el neto sino el valor sin IVA ni retenciones.
        BigDecimal salio = gastoQueryRepository.montoEgresoRegistrado(empresaId, gasto.getId())
                .orElseGet(() -> valorAPagar(gasto));
        if (gasto.getCuentaBancariaId() != null) {
            tesoreriaService.registrarMovimientoDeDocumento(empresaId,
                    usuarioId != null ? usuarioId.intValue() : null,
                    new TesoreriaService.MovimientoDocumento(
                            gasto.getCuentaBancariaId(), false, salio,
                            "Reverso gasto #" + gasto.getId() + " (anulación)",
                            null, "GASTO-" + gasto.getId(), gasto.getCategoria()));
        }
        if (gastoQueryRepository.tuvoEgresoDeCaja(MovimientoCajaEntity.ORIGEN_GASTO, gasto.getId())) {
            OrigenFondosService.OrigenFondos origen = origenFondosService.resolver(empresaId,
                    new OrigenFondosService.Solicitud(
                            gasto.getMetodoPago(), null, null, gasto.getCuentaPagoId(),
                            gasto.getSucursal() != null ? gasto.getSucursal().getId() : null,
                            "reverso del gasto",
                            Boolean.TRUE.equals(gasto.getSalidaCajaOtroDia())));
            if (origen.generaMovimientoCaja()) {
                UsuarioEntity usuario = usuarioId != null
                        ? usuarioJPARepository.findById(usuarioId.intValue()).orElse(null) : null;
                movimientoCajaJPARepository.save(MovimientoCajaEntity.builder()
                        .turnoCaja(origen.turno())
                        .usuario(usuario)
                        .tipo("INGRESO")
                        .concepto("Reverso gasto #" + gasto.getId() + " (anulación)")
                        .monto(salio)
                        // Entra hoy a la caja abierta: si el turno que pagó ya
                        // cerró, devolverle la plata no lo reabre.
                        .fecha(LocalDate.now())
                        .fechaDocumento(gasto.getFecha())
                        .origenTipo(MovimientoCajaEntity.ORIGEN_GASTO)
                        .origenId(gasto.getId())
                        .origenInferido(origen.turnoInferido())
                        .metodoPago(gasto.getMetodoPago())
                        .build());
            }
        }
    }

    @Override
    public PageImpl<GastoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return gastoQueryRepository.listar(pageable, empresaId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Lo que de verdad sale de la caja o del banco, o se le queda debiendo al
     * tercero: valor + IVA − retenciones. Es la misma cifra que el asiento
     * acredita; usar el valor solo dejaba el arqueo, el banco y la cuenta por
     * pagar distintos de la contabilidad en todo gasto con IVA o retenciones.
     */
    private BigDecimal valorAPagar(GastoEntity g) {
        return nvl(g.getMonto()).add(nvl(g.getValorIva()))
                .subtract(nvl(g.getValorRetefuente())).subtract(nvl(g.getValorReteica()));
    }

    private void mapCamposTributarios(CreateGastoDto dto, GastoEntity gasto) {
        // Origen de fondos (V142). El método se normaliza porque el cierre de
        // caja compara metodo_pago = 'EFECTIVO' exacto.
        gasto.setFormaPago(dto.getFormaPago() != null
                ? dto.getFormaPago().trim().toUpperCase() : "CONTADO");
        gasto.setMetodoPago(dto.getMetodoPago() != null
                ? MediosPago.normalizar(dto.getMetodoPago()) : MediosPago.EFECTIVO);
        gasto.setCuentaBancariaId(dto.getCuentaBancariaId());
        gasto.setTerceroId(dto.getTerceroId());
        gasto.setCuentaContableId(dto.getCuentaContableId());
        gasto.setCentroCostoId(dto.getCentroCostoId());
        gasto.setEsDiferido(Boolean.TRUE.equals(dto.getEsDiferido()));
        gasto.setMesesDiferido(dto.getMesesDiferido());
        gasto.setProyectoId(dto.getProyectoId());
        gasto.setFrenteId(dto.getFrenteId());
        gasto.setPeriodoContableId(dto.getPeriodoContableId());
        gasto.setBaseIva(nvl(dto.getBaseIva()));
        gasto.setTarifaIva(nvl(dto.getTarifaIva()));
        gasto.setValorIva(nvl(dto.getValorIva()));
        gasto.setBaseRetefuente(nvl(dto.getBaseRetefuente()));
        gasto.setTarifaRetefuente(nvl(dto.getTarifaRetefuente()));
        gasto.setValorRetefuente(nvl(dto.getValorRetefuente()));
        gasto.setBaseReteica(nvl(dto.getBaseReteica()));
        gasto.setTarifaReteica(nvl(dto.getTarifaReteica()));
        gasto.setValorReteica(nvl(dto.getValorReteica()));
        // Sin retención no hay concepto: evita que quede colgado uno viejo.
        gasto.setRetefuenteTarifaId(nvl(dto.getValorRetefuente()).signum() > 0 ? dto.getRetefuenteTarifaId() : null);
        gasto.setReteicaTarifaId(nvl(dto.getValorReteica()).signum() > 0 ? dto.getReteicaTarifaId() : null);
        gasto.setTipoDocSoporte(dto.getTipoDocSoporte());
        gasto.setNumeroDocSoporte(dto.getNumeroDocSoporte());
    }

    /**
     * Reconstruye la pregunta "¿de dónde sale la plata?" a partir de lo que
     * quedó guardado.
     *
     * <p>El orden es el mismo de {@code OrigenFondosServiceImpl.resolver}, y
     * tiene que seguir siéndolo: si los dos divergen, el formulario reabre el
     * gasto con un origen que el sistema nunca usó.
     *
     * <p>El caso que obliga a que esto viva en el backend es CAJA contra
     * CUENTA. Los dos guardan {@code cuenta_pago_id}: el primero con la cuenta
     * de efectivo que resolvió el sistema, el segundo con la que eligió quien
     * registró el gasto. Distinguirlos exige saber cuál es la cuenta de
     * efectivo de la empresa, y eso solo lo sabe el resolutor.
     *
     * <p>Queda un empate que no se puede deshacer: si alguien eligió a mano
     * justamente la cuenta de CAJA, se lee como CAJA. Es la lectura correcta
     * para el asiento — la misma cuenta se acredita en los dos casos.
     */
    private String origenFondosDe(GastoEntity g) {
        if (MediosPago.CREDITO.equalsIgnoreCase(g.getFormaPago())) {
            return "CREDITO";
        }
        // Antes que las cuentas: es una afirmación sobre un hecho pasado, y la
        // cuenta que quedó guardada es la de CAJA, que se confundiría con un
        // pago de caja normal.
        if (Boolean.TRUE.equals(g.getSalidaCajaOtroDia())) {
            return "CAJA_OTRO_DIA";
        }
        if (g.getCuentaBancariaId() != null) {
            return "BANCO";
        }
        if (g.getCuentaPagoId() == null) {
            return "CAJA";
        }
        Integer empresaId = g.getEmpresa() != null ? g.getEmpresa().getId() : null;
        Long cuentaCaja = empresaId != null
                ? resolucionCuentaPago.resolver(empresaId, MediosPago.EFECTIVO, null)
                : null;
        return g.getCuentaPagoId().equals(cuentaCaja) ? "CAJA" : "CUENTA";
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private GastoDto toDto(GastoEntity g) {
        GastoDto dto = new GastoDto();
        dto.setId(g.getId());
        dto.setEmpresaId(g.getEmpresa() != null ? g.getEmpresa().getId() : null);
        dto.setSucursalId(g.getSucursal() != null ? g.getSucursal().getId().longValue() : null);
        dto.setSucursalNombre(g.getSucursal() != null ? g.getSucursal().getNombre() : null);
        dto.setUsuarioId(g.getUsuario() != null ? g.getUsuario().getId() : null);
        dto.setUsuarioNombre(g.getUsuario() != null ? g.getUsuario().getUsername() : null);
        dto.setCategoria(g.getCategoria());
        dto.setDescripcion(g.getDescripcion());
        dto.setMonto(g.getMonto());
        dto.setFecha(g.getFecha());
        dto.setDeducible(g.getDeducible());
        dto.setEstado(g.getEstado());
        dto.setCreatedAt(g.getCreatedAt());
        // Origen de fondos
        dto.setFormaPago(g.getFormaPago());
        dto.setMetodoPago(g.getMetodoPago());
        dto.setCuentaBancariaId(g.getCuentaBancariaId());
        dto.setCuentaPagoId(g.getCuentaPagoId());
        dto.setSalidaCajaOtroDia(g.getSalidaCajaOtroDia());
        dto.setOrigenFondos(origenFondosDe(g));
        // Campos tributarios
        dto.setTerceroId(g.getTerceroId());
        dto.setCuentaContableId(g.getCuentaContableId());
        Integer empId = g.getEmpresa() != null ? g.getEmpresa().getId() : null;
        if (g.getTerceroId() != null && empId != null) {
            terceroJPARepository.findByIdAndEmpresaId(g.getTerceroId(), empId)
                    .ifPresent(t -> dto.setTerceroNombre(
                            t.getRazonSocial() != null && !t.getRazonSocial().isBlank()
                                    ? t.getRazonSocial()
                                    : ((t.getNombres() != null ? t.getNombres() : "") + " "
                                            + (t.getApellidos() != null ? t.getApellidos() : "")).trim()));
        }
        if (g.getCuentaContableId() != null && empId != null) {
            planCuentaJPARepository.findByIdAndEmpresaId(g.getCuentaContableId(), empId)
                    .ifPresent(c -> dto.setCuentaContableNombre(c.getCodigo() + " - " + c.getNombre()));
        }
        dto.setCentroCostoId(g.getCentroCostoId());
        dto.setPeriodoContableId(g.getPeriodoContableId());
        dto.setBaseIva(g.getBaseIva());
        dto.setTarifaIva(g.getTarifaIva());
        dto.setValorIva(g.getValorIva());
        dto.setBaseRetefuente(g.getBaseRetefuente());
        dto.setTarifaRetefuente(g.getTarifaRetefuente());
        dto.setValorRetefuente(g.getValorRetefuente());
        dto.setBaseReteica(g.getBaseReteica());
        dto.setTarifaReteica(g.getTarifaReteica());
        dto.setValorReteica(g.getValorReteica());
        dto.setRetefuenteTarifaId(g.getRetefuenteTarifaId());
        dto.setReteicaTarifaId(g.getReteicaTarifaId());
        dto.setTipoDocSoporte(g.getTipoDocSoporte());
        dto.setNumeroDocSoporte(g.getNumeroDocSoporte());
        return dto;
    }
}

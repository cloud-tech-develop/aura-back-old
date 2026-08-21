package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.caja.AbrirTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CerrarTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CreateMovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.MovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.ResumenTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaTableDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.entity.CajaEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.TurnoCajaMapper;
import com.cloud_technological.aura_pos.repositories.caja.CajaJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaQueryRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ComprobanteCajaService;
import com.cloud_technological.aura_pos.services.TurnoCajaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class TurnoCajaServiceImpl implements TurnoCajaService {

    private final TurnoCajaQueryRepository turnoRepository;
    private final TurnoCajaJPARepository turnoJPARepository;
    private final CajaJPARepository cajaJPARepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final TurnoCajaMapper turnoMapper;
    private final AbonoCobrarJPARepository abonoCobrarRepository;
    private final AbonoPagarJPARepository abonoPagarRepository;
    private final CuentaCobrarJPARepository cuentaCobrarRepository;
    private final CuentaPagarJPARepository cuentaPagarRepository;
    private final MovimientoCajaJPARepository movimientoCajaRepository;
    private final ComprobanteCajaService comprobanteCajaService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService controlFechaRetroactiva;
    private final com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort periodoContable;

    @Autowired
    public TurnoCajaServiceImpl(TurnoCajaQueryRepository turnoRepository,
            TurnoCajaJPARepository turnoJPARepository,
            CajaJPARepository cajaJPARepository,
            UsuarioJPARepository usuarioJPARepository,
            TurnoCajaMapper turnoMapper,
            AbonoCobrarJPARepository abonoCobrarRepository,
            AbonoPagarJPARepository abonoPagarRepository,
            CuentaCobrarJPARepository cuentaCobrarRepository,
            CuentaPagarJPARepository cuentaPagarRepository,
            MovimientoCajaJPARepository movimientoCajaRepository,
            ComprobanteCajaService comprobanteCajaService,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService controlFechaRetroactiva,
            com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort periodoContable) {
        this.turnoRepository = turnoRepository;
        this.turnoJPARepository = turnoJPARepository;
        this.cajaJPARepository = cajaJPARepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.turnoMapper = turnoMapper;
        this.abonoCobrarRepository = abonoCobrarRepository;
        this.abonoPagarRepository = abonoPagarRepository;
        this.cuentaCobrarRepository = cuentaCobrarRepository;
        this.cuentaPagarRepository = cuentaPagarRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.comprobanteCajaService = comprobanteCajaService;
        this.eventPublisher = eventPublisher;
        this.controlFechaRetroactiva = controlFechaRetroactiva;
        this.periodoContable = periodoContable;
    }

    @Override
    public PageImpl<TurnoCajaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return turnoRepository.listar(pageable, empresaId);
    }

    @Override
    public TurnoCajaDto obtenerPorId(Long id, Integer empresaId) {
        TurnoCajaEntity entity = turnoJPARepository.findByIdAndCajaSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
        return turnoMapper.toDto(entity);
    }

    @Override
    public TurnoCajaDto obtenerTurnoActivo(Long usuarioId) {
        return turnoRepository.obtenerTurnoActivo(usuarioId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "No hay turno activo"));
    }

    @Override
    public List<TurnoCajaDto> listarAbiertos(Integer empresaId, Integer sucursalId) {
        return turnoJPARepository
                .findByCajaSucursalEmpresaIdAndEstadoOrderByFechaAperturaAsc(empresaId, "ABIERTA")
                .stream()
                .filter(t -> sucursalId == null
                        || (t.getCaja() != null && t.getCaja().getSucursal() != null
                                && sucursalId.equals(t.getCaja().getSucursal().getId())))
                .map(turnoMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public TurnoCajaDto abrir(AbrirTurnoDto dto, Integer empresaId, Long usuarioId) {
        CajaEntity caja = cajaJPARepository.findByIdAndSucursalEmpresaId(dto.getCajaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Caja no encontrada"));

        if (!caja.getActiva())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La caja está inactiva");

        if (turnoJPARepository.findByCajaIdAndEstado(dto.getCajaId(), "ABIERTA").isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La caja ya tiene un turno abierto");

        if (turnoJPARepository.findByUsuarioIdAndEstado(usuarioId, "ABIERTA").isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya tienes un turno abierto en otra caja");

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        TurnoCajaEntity turno = new TurnoCajaEntity();
        turno.setCaja(caja);
        turno.setUsuario(usuario);
        turno.setBaseInicial(dto.getBaseInicial());
        turno.setFechaApertura(LocalDateTime.now());
        turno.setEstado("ABIERTA");

        return turnoMapper.toDto(turnoJPARepository.save(turno));
    }

    @Override
    @Transactional
    public ResumenTurnoDto cerrar(Long id, CerrarTurnoDto dto, Integer empresaId) {
        TurnoCajaEntity turno = turnoJPARepository.findByIdAndCajaSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        if (turno.getEstado().equals("CERRADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El turno ya está cerrado");

        BigDecimal totalSistema = turnoRepository.calcularTotalEfectivoSistema(id);
        BigDecimal sumIngresos  = abonoCobrarRepository.sumMontoByTurnoCajaId(id);
        BigDecimal sumEgresos   = abonoPagarRepository.sumMontoByTurnoCajaId(id);
        BigDecimal ingresos     = sumIngresos != null ? sumIngresos : BigDecimal.ZERO;
        BigDecimal egresos      = sumEgresos  != null ? sumEgresos  : BigDecimal.ZERO;
        BigDecimal comisiones   = turnoRepository.totalComisionesTurno(id);

        // Incluir movimientos genéricos de caja (ej: devoluciones en efectivo)
        BigDecimal movIngreso = BigDecimal.ZERO;
        BigDecimal movEgreso  = BigDecimal.ZERO;
        for (MovimientoCajaEntity m : movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(id)) {
            if ("INGRESO".equals(m.getTipo()))      movIngreso = movIngreso.add(m.getMonto());
            else if ("EGRESO".equals(m.getTipo()))  movEgreso  = movEgreso.add(m.getMonto());
        }

        BigDecimal totalEsperado = turno.getBaseInicial()
                .add(totalSistema)
                .add(ingresos).add(movIngreso)
                .subtract(egresos).subtract(movEgreso)
                .subtract(comisiones);

        turno.setFechaCierre(LocalDateTime.now());
        turno.setTotalEfectivoSistema(totalSistema);
        turno.setTotalEfectivoReal(dto.getTotalEfectivoReal());
        turno.setDiferencia(dto.getTotalEfectivoReal().subtract(totalEsperado));
        turno.setEstado("CERRADA");
        turnoJPARepository.save(turno);

        // E3: la diferencia del cierre se contabiliza tras el commit
        // (faltante → gasto · CR Caja; sobrante → DB Caja · ingreso).
        if (turno.getDiferencia() != null && turno.getDiferencia().signum() != 0) {
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent(
                            "DIFERENCIA_CAJA", turno.getId(), empresaId, null));
        }

        return construirResumen(id);
    }

    @Override
    public ResumenTurnoDto resumen(Long id, Integer empresaId) {
        turnoJPARepository.findByIdAndCajaSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
        return construirResumen(id);
    }

    @Override
    @Transactional
    public MovimientoCajaDto registrarMovimiento(Long turnoId, CreateMovimientoCajaDto dto, Integer empresaId, Long usuarioId) {
        TurnoCajaEntity turno = turnoJPARepository.findByIdAndCajaSucursalEmpresaId(turnoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        if ("CERRADA".equals(turno.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se pueden registrar movimientos en un turno cerrado");

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        if ("INGRESO".equals(dto.getTipo()) && dto.getCuentaCobrarId() != null) {
            CuentaCobrarEntity cuenta = cuentaCobrarRepository.findById(dto.getCuentaCobrarId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));

            if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta por cobrar ya está pagada");

            AbonoCobrarEntity abono = AbonoCobrarEntity.builder()
                    .cuentaCobrar(cuenta)
                    .usuario(usuario)
                    .turnoCaja(turno)
                    .monto(dto.getMonto())
                    .metodoPago("efectivo")
                    .referencia(recortar(dto.getConcepto(), 255))
                    .fechaPago(LocalDateTime.now())
                    .build();
            AbonoCobrarEntity saved = abonoCobrarRepository.save(abono);

            cuenta.setTotalAbonado(cuenta.getTotalAbonado().add(dto.getMonto()));
            cuenta.setSaldoPendiente(cuenta.getSaldoPendiente().subtract(dto.getMonto()));
            if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0) {
                cuenta.setSaldoPendiente(BigDecimal.ZERO);
                cuenta.setEstado("pagada");
            }
            cuentaCobrarRepository.save(cuenta);

            Integer empId = turno.getCaja().getSucursal().getEmpresa().getId();
            String terceroNombreCxC = resolverNombreTercero(cuenta.getTercero());
            comprobanteCajaService.generar(
                empId, usuarioId.intValue(),
                "INGRESO", dto.getConcepto(), dto.getMonto(),
                "EFECTIVO", terceroNombreCxC,
                "ABONO_CXC", saved.getId(), turnoId
            );

            // Asiento del recaudo (DB Caja · CR Clientes) tras el commit (evento único).
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent(
                            "ABONO_COBRAR", saved.getId(), empId, usuarioId.intValue()));

            return abonoCobrarToDto(saved);
        }

        if ("EGRESO".equals(dto.getTipo()) && dto.getCuentaPagarId() != null) {
            CuentaPagarEntity cuenta = cuentaPagarRepository.findById(dto.getCuentaPagarId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta por pagar no encontrada"));

            if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta por pagar ya está pagada");

            AbonoPagarEntity abono = AbonoPagarEntity.builder()
                    .cuentaPagar(cuenta)
                    .usuario(usuario)
                    .turnoCaja(turno)
                    .monto(dto.getMonto())
                    .metodoPago("efectivo")
                    .referencia(recortar(dto.getConcepto(), 255))
                    .fechaPago(LocalDateTime.now())
                    .build();
            AbonoPagarEntity saved = abonoPagarRepository.save(abono);

            cuenta.setTotalAbonado(cuenta.getTotalAbonado().add(dto.getMonto()));
            cuenta.setSaldoPendiente(cuenta.getSaldoPendiente().subtract(dto.getMonto()));
            if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0) {
                cuenta.setSaldoPendiente(BigDecimal.ZERO);
                cuenta.setEstado("pagada");
            }
            cuentaPagarRepository.save(cuenta);

            Integer empId = turno.getCaja().getSucursal().getEmpresa().getId();
            String terceroNombreCxP = resolverNombreTercero(cuenta.getTercero());
            comprobanteCajaService.generar(
                empId, usuarioId.intValue(),
                "EGRESO", dto.getConcepto(), dto.getMonto(),
                "EFECTIVO", terceroNombreCxP,
                "ABONO_CXP", saved.getId(), turnoId
            );

            // Asiento del pago a proveedor (DB Proveedores · CR Caja) tras el commit (evento único).
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent(
                            "ABONO_PAGAR", saved.getId(), empId, usuarioId.intValue()));

            return abonoPagarToDto(saved);
        }

        MovimientoCajaEntity movimiento = MovimientoCajaEntity.builder()
                .turnoCaja(turno)
                .usuario(usuario)
                .tipo(dto.getTipo())
                .concepto(dto.getConcepto())
                .monto(dto.getMonto())
                // Movimiento que el cajero registra a mano sobre su propio
                // turno abierto: la fecha del dinero y la del documento son la
                // misma, así que fechaDocumento se queda en null.
                .fecha(java.time.LocalDate.now())
                .origenTipo(MovimientoCajaEntity.ORIGEN_MANUAL)
                .conceptoCajaId(dto.getConceptoCajaId())
                .metodoPago(dto.getMetodoPago())
                .build();
        MovimientoCajaEntity saved = movimientoCajaRepository.save(movimiento);

        Integer empId = turno.getCaja().getSucursal().getEmpresa().getId();
        comprobanteCajaService.generar(
            empId, usuarioId.intValue(),
            dto.getTipo(), dto.getConcepto(), dto.getMonto(),
            dto.getMetodoPago(), dto.getEntregadoA(),
            "MANUAL", saved.getId(), turnoId
        );

        // Si trae concepto de caja, genera su asiento (DB/CR Caja + cuenta del concepto) tras el commit.
        if (dto.getConceptoCajaId() != null) {
            eventPublisher.publishEvent(new com.cloud_technological.aura_pos.event.MovimientoCajaContabilizableEvent(
                    saved.getId(), empId, usuarioId.intValue()));
        }

        return movimientoCajaToDto(saved);
    }

    // ─── Conversores ────────────────────────────────────────────────────────────

    private MovimientoCajaDto abonoCobrarToDto(AbonoCobrarEntity e) {
        MovimientoCajaDto dto = new MovimientoCajaDto();
        dto.setId(e.getId());
        dto.setTipo("INGRESO");
        dto.setConcepto(e.getReferencia());
        dto.setMonto(e.getMonto());
        // `fecha` es el día en toda la lista; el instante va en registradoEn.
        // Mezclar formatos rompía el orden del detalle, que compara texto.
        dto.setFecha(e.getFechaPago() != null ? e.getFechaPago().toLocalDate().toString() : null);
        dto.setRegistradoEn(e.getFechaPago() != null ? e.getFechaPago().toString() : null);
        dto.setEsDeOtraFecha(Boolean.FALSE);
        dto.setUsuarioNombre(e.getUsuario() != null ? e.getUsuario().getUsername() : null);
        if (e.getCuentaCobrar() != null) {
            dto.setCuentaNumero(e.getCuentaCobrar().getNumeroCuenta());
            dto.setTerceroNombre(resolverNombreTercero(e.getCuentaCobrar().getTercero()));
        }
        return dto;
    }

    private MovimientoCajaDto abonoPagarToDto(AbonoPagarEntity e) {
        MovimientoCajaDto dto = new MovimientoCajaDto();
        dto.setId(e.getId());
        dto.setTipo("EGRESO");
        dto.setConcepto(e.getReferencia());
        dto.setMonto(e.getMonto());
        dto.setFecha(e.getFechaPago() != null ? e.getFechaPago().toLocalDate().toString() : null);
        dto.setRegistradoEn(e.getFechaPago() != null ? e.getFechaPago().toString() : null);
        dto.setEsDeOtraFecha(Boolean.FALSE);
        dto.setUsuarioNombre(e.getUsuario() != null ? e.getUsuario().getUsername() : null);
        if (e.getCuentaPagar() != null) {
            dto.setCuentaNumero(e.getCuentaPagar().getNumeroCuenta());
            dto.setTerceroNombre(resolverNombreTercero(e.getCuentaPagar().getTercero()));
        }
        return dto;
    }

    @Override
    @Transactional
    public MovimientoCajaDto registrarAjusteRetroactivo(Long turnoId,
            com.cloud_technological.aura_pos.dto.caja.CreateAjusteRetroactivoDto dto,
            Integer empresaId, Long usuarioId) {

        TurnoCajaEntity turno = turnoJPARepository.findByIdAndCajaSucursalEmpresaId(turnoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        // Sobre un turno abierto no hay nada que corregir: se registra el
        // movimiento normal y el cierre lo recoge solo.
        if (!"CERRADA".equals(turno.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El turno todavía está abierto. Registre el movimiento normalmente; "
                            + "el ajuste retroactivo es solo para arqueos ya cerrados");
        }
        if (!"INGRESO".equals(dto.getTipo()) && !"EGRESO".equals(dto.getTipo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El ajuste debe ser INGRESO (sobró plata) o EGRESO (salió y no se registró)");
        }
        controlFechaRetroactiva.exigirRolAutorizador(empresaId, "corregir un arqueo cerrado");

        // La fecha manda sobre el período contable. Si el período de esa fecha
        // ya cerró, el asiento no puede nacer ahí: se rechaza aquí, con nombres
        // que el usuario entiende, en vez de fallar después en el posting.
        LocalDate fechaAjuste = dto.getFechaDocumento() != null
                ? dto.getFechaDocumento()
                : (turno.getFechaCierre() != null
                        ? turno.getFechaCierre().toLocalDate() : LocalDate.now());
        try {
            periodoContable.abiertoPara(empresaId, fechaAjuste);
        } catch (RuntimeException ex) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El período contable de " + fechaAjuste + " está cerrado, así que el ajuste "
                            + "no puede registrarse en esa fecha. Use una fecha dentro de un "
                            + "período abierto");
        }

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Usuario no encontrado"));

        // El cierre original se conserva: al primer ajuste se copia, y de ahí en
        // adelante `diferencia` ya no se vuelve a tocar.
        if (turno.getDiferenciaOriginal() == null) {
            turno.setDiferenciaOriginal(turno.getDiferencia());
        }

        MovimientoCajaEntity ajuste = MovimientoCajaEntity.builder()
                .turnoCaja(turno)
                .usuario(usuario)
                .tipo(dto.getTipo())
                .concepto(dto.getConcepto() != null && !dto.getConcepto().isBlank()
                        ? dto.getConcepto().trim()
                        : "Ajuste retroactivo del cierre")
                .monto(dto.getMonto())
                .fecha(fechaAjuste)
                .fechaDocumento(fechaAjuste)
                .origenTipo(MovimientoCajaEntity.ORIGEN_MANUAL)
                .esAjusteRetroactivo(Boolean.TRUE)
                .motivoAjuste(dto.getMotivo().trim())
                .autorizadoPor(usuarioId.intValue())
                .conceptoCajaId(dto.getConceptoCajaId())
                .metodoPago("EFECTIVO")
                .build();
        MovimientoCajaEntity saved = movimientoCajaRepository.save(ajuste);

        turno.setDiferenciaAjustada(calcularDiferenciaAjustada(turno));
        turnoJPARepository.save(turno);

        // Asiento del ajuste con SU fecha, no la de hoy.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.MovimientoCajaContabilizableEvent(
                        saved.getId(), empresaId, usuarioId.intValue()));

        return movimientoCajaToDto(saved);
    }

    /**
     * El cierre original más todo lo que los ajustes movieron después.
     *
     * <p>Un EGRESO que no se había registrado hace el faltante mayor (la plata
     * ya no estaba); un INGRESO lo reduce.
     */
    private BigDecimal calcularDiferenciaAjustada(TurnoCajaEntity turno) {
        BigDecimal base = turno.getDiferenciaOriginal() != null
                ? turno.getDiferenciaOriginal()
                : (turno.getDiferencia() != null ? turno.getDiferencia() : BigDecimal.ZERO);

        for (MovimientoCajaEntity m : movimientoCajaRepository
                .findByTurnoCajaIdOrderByCreatedAtAsc(turno.getId())) {
            if (!Boolean.TRUE.equals(m.getEsAjusteRetroactivo()) || m.getMonto() == null) {
                continue;
            }
            base = "EGRESO".equals(m.getTipo())
                    ? base.add(m.getMonto())
                    : base.subtract(m.getMonto());
        }
        return base;
    }

    private static BigDecimal sumarPorTipo(List<MovimientoCajaDto> movimientos, String tipo) {
        return movimientos.stream()
                .filter(m -> tipo.equals(m.getTipo()))
                .map(MovimientoCajaDto::getMonto)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MovimientoCajaDto movimientoCajaToDto(MovimientoCajaEntity e) {
        MovimientoCajaDto dto = new MovimientoCajaDto();
        dto.setId(e.getId());
        dto.setTipo(e.getTipo());
        dto.setConcepto(e.getConcepto());
        dto.setMonto(e.getMonto());
        // `fecha` es el día del dinero, no el instante de digitación: antes se
        // devolvía created_at y el desfase con el documento quedaba invisible.
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setRegistradoEn(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setFechaDocumento(e.getFechaDocumento() != null
                ? e.getFechaDocumento().toString() : null);
        dto.setOrigenTipo(e.getOrigenTipo());
        dto.setOrigenId(e.getOrigenId());
        dto.setEsDeOtraFecha(e.esDeOtraFecha());
        dto.setEsAjusteRetroactivo(e.getEsAjusteRetroactivo());
        dto.setMotivoAjuste(e.getMotivoAjuste());
        dto.setAutorizadoPor(e.getAutorizadoPor());
        dto.setUsuarioNombre(e.getUsuario() != null ? e.getUsuario().getUsername() : null);
        return dto;
    }

    /** Devuelve razonSocial cuando está disponible; si no, nombres + apellidos. */
    /**
     * El concepto del movimiento es texto libre del cajero y se guarda como
     * `referencia` del abono, que es más corta. Se recorta en vez de dejar que
     * Postgres tumbe el INSERT ("value too long for type character varying").
     */
    private static String recortar(String valor, int max) {
        if (valor == null || valor.length() <= max) return valor;
        return valor.substring(0, max);
    }

    private String resolverNombreTercero(com.cloud_technological.aura_pos.entity.TerceroEntity t) {
        if (t == null) return null;
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        String nombre = (t.getNombres() != null ? t.getNombres() : "")
                + (t.getApellidos() != null ? " " + t.getApellidos() : "");
        return nombre.isBlank() ? null : nombre.trim();
    }

    // ─── Resumen ─────────────────────────────────────────────────────────────────

    private ResumenTurnoDto construirResumen(Long turnoId) {
        TurnoCajaEntity entity = turnoJPARepository.findById(turnoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        TurnoCajaDto turnoDto = turnoMapper.toDto(entity);

        var porCategoria  = turnoRepository.ventasPorCategoria(turnoId);
        var porMetodoPago = turnoRepository.ventasPorMetodoPago(turnoId);
        var totales       = turnoRepository.totalesGenerales(turnoId);

        ResumenTurnoDto resumen = new ResumenTurnoDto();

        resumen.setTurnoId(turnoDto.getId());
        resumen.setCajaNombre(turnoDto.getCajaNombre());
        resumen.setUsuarioNombre(turnoDto.getUsuarioNombre());
        resumen.setFechaApertura(turnoDto.getFechaApertura() != null
                ? turnoDto.getFechaApertura().toString() : null);
        resumen.setBaseInicial(entity.getBaseInicial());
        resumen.setEstado(entity.getEstado());

        resumen.setVentasPorCategoria(porCategoria);
        resumen.setVentasPorMetodoPago(porMetodoPago);

        resumen.setTotalVentasBruto(toBD(totales.get("total_ventas_bruto")));
        resumen.setTotalDescuentos(toBD(totales.get("total_descuentos")));
        resumen.setTotalImpuestos(toBD(totales.get("total_impuestos")));
        resumen.setTotalNeto(toBD(totales.get("total_neto")));
        resumen.setTotalTransacciones(toInt(totales.get("total_transacciones")));

        // Movimientos (se calculan primero para incluirlos en totalEsperado)
        List<MovimientoCajaDto> movimientos = new ArrayList<>();
        abonoCobrarRepository.findByTurnoCajaIdOrderByFechaPagoAsc(turnoId)
                .stream().map(this::abonoCobrarToDto).forEach(movimientos::add);
        abonoPagarRepository.findByTurnoCajaIdOrderByFechaPagoAsc(turnoId)
                .stream().map(this::abonoPagarToDto).forEach(movimientos::add);
        movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(turnoId)
                .stream().map(this::movimientoCajaToDto).forEach(movimientos::add);
        movimientos.sort(Comparator.comparing(
                m -> m.getRegistradoEn() != null ? m.getRegistradoEn()
                        : (m.getFecha() != null ? m.getFecha() : ""),
                Comparator.naturalOrder()));

        BigDecimal totalIngresosAbonos = abonoCobrarRepository.sumMontoByTurnoCajaId(turnoId);
        BigDecimal totalEgresosAbonos  = abonoPagarRepository.sumMontoByTurnoCajaId(turnoId);
        
        List<MovimientoCajaEntity> movimientosCaja = movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(turnoId);
        BigDecimal totalIngresosCaja = BigDecimal.ZERO;
        BigDecimal totalEgresosCaja = BigDecimal.ZERO;
        for (MovimientoCajaEntity m : movimientosCaja) {
            // Un movimiento por transferencia se sigue listando en el detalle,
            // pero no altera el efectivo esperado: no pasó por el cajón. Los
            // movimientos sin método (los históricos) sí eran efectivo.
            boolean afectaElArqueo = m.getMetodoPago() == null
                    || com.cloud_technological.aura_pos.utils.MediosPago.esEfectivo(m.getMetodoPago());
            if (!afectaElArqueo) {
                continue;
            }
            if ("INGRESO".equals(m.getTipo())) {
                totalIngresosCaja = totalIngresosCaja.add(m.getMonto());
            } else if ("EGRESO".equals(m.getTipo())) {
                totalEgresosCaja = totalEgresosCaja.add(m.getMonto());
            }
        }
        
        BigDecimal ingresosBD    = (totalIngresosAbonos != null ? totalIngresosAbonos : BigDecimal.ZERO).add(totalIngresosCaja);
        BigDecimal egresosBD     = (totalEgresosAbonos  != null ? totalEgresosAbonos  : BigDecimal.ZERO).add(totalEgresosCaja);

        var comisionesList = turnoRepository.comisionesPorTurno(turnoId);
        BigDecimal totalComisiones = comisionesList.stream()
                .map(c -> c.getTotalComision() != null ? c.getTotalComision() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal efectivoSistema;
        if ("ABIERTA".equals(entity.getEstado())) {
            efectivoSistema = turnoRepository.calcularTotalEfectivoSistema(turnoId);
            resumen.setTotalEfectivoSistema(efectivoSistema);
            resumen.setTotalEfectivoReal(null);
            resumen.setDiferencia(null);
        } else {
            efectivoSistema = entity.getTotalEfectivoSistema() != null
                    ? entity.getTotalEfectivoSistema() : BigDecimal.ZERO;
            resumen.setTotalEfectivoSistema(entity.getTotalEfectivoSistema());
            resumen.setTotalEfectivoReal(entity.getTotalEfectivoReal());
            resumen.setDiferencia(entity.getDiferencia());
        }

        BigDecimal totalEsperado = entity.getBaseInicial()
                .add(efectivoSistema)
                .add(ingresosBD)
                .subtract(egresosBD)
                .subtract(totalComisiones);
        resumen.setTotalEsperado(totalEsperado);

        resumen.setMovimientos(movimientos);
        resumen.setTotalIngresos(ingresosBD);
        resumen.setTotalEgresos(egresosBD);

        // Lo que el cajero no reconoce, separado: documentos de otras fechas
        // pagados desde esta caja. Siguen contando en el efectivo esperado —
        // la plata sí salió del cajón — pero mostrados aparte deja de ser un
        // faltante inexplicable y pasa a ser una cifra con nombre.
        List<MovimientoCajaDto> otrasFechas = movimientos.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEsDeOtraFecha()))
                .toList();
        resumen.setMovimientosDeOtrasFechas(otrasFechas);
        resumen.setTotalIngresosOtrasFechas(sumarPorTipo(otrasFechas, "INGRESO"));
        resumen.setTotalEgresosOtrasFechas(sumarPorTipo(otrasFechas, "EGRESO"));

        // Correcciones hechas después del cierre. El original no se toca: se
        // muestran las tres cifras para que se vea qué firmó el cajero ese día
        // y qué se corrigió encima.
        resumen.setAjustesRetroactivos(movimientos.stream()
                .filter(m -> Boolean.TRUE.equals(m.getEsAjusteRetroactivo()))
                .toList());
        resumen.setDiferenciaOriginal(entity.getDiferenciaOriginal() != null
                ? entity.getDiferenciaOriginal() : entity.getDiferencia());
        resumen.setDiferenciaAjustada(entity.getDiferenciaAjustada());
        resumen.setComisiones(comisionesList);
        resumen.setTotalComisiones(totalComisiones);

        resumen.setDetalleEfectivo(turnoRepository.detalleEfectivoTurno(turnoId));

        var credito = turnoRepository.ventasCreditoTurno(turnoId);
        resumen.setCantidadVentasCredito(toInt(credito.get("cantidad_ventas_credito")));
        resumen.setTotalVentasCredito(toBD(credito.get("total_ventas_credito")));
        resumen.setDetalleVentasCredito(turnoRepository.detalleVentasCreditoTurno(turnoId));

        return resumen;
    }

    private BigDecimal toBD(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer i) return i;
        return Integer.parseInt(val.toString());
    }
}

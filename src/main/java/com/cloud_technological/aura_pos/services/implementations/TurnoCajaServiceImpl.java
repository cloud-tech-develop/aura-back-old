package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
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
            MovimientoCajaJPARepository movimientoCajaRepository) {
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

        BigDecimal totalSistema  = turnoRepository.calcularTotalEfectivoSistema(id);
        BigDecimal sumIngresos   = abonoCobrarRepository.sumMontoByTurnoCajaId(id);
        BigDecimal sumEgresos    = abonoPagarRepository.sumMontoByTurnoCajaId(id);
        BigDecimal ingresos      = sumIngresos != null ? sumIngresos : BigDecimal.ZERO;
        BigDecimal egresos       = sumEgresos  != null ? sumEgresos  : BigDecimal.ZERO;
        BigDecimal comisiones    = turnoRepository.totalComisionesTurno(id);
        BigDecimal totalEsperado = turno.getBaseInicial().add(totalSistema).add(ingresos).subtract(egresos).subtract(comisiones);

        turno.setFechaCierre(LocalDateTime.now());
        turno.setTotalEfectivoSistema(totalSistema);
        turno.setTotalEfectivoReal(dto.getTotalEfectivoReal());
        turno.setDiferencia(dto.getTotalEfectivoReal().subtract(totalEsperado));
        turno.setEstado("CERRADA");
        turnoJPARepository.save(turno);

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
                    .referencia(dto.getConcepto())
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
                    .referencia(dto.getConcepto())
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

            return abonoPagarToDto(saved);
        }

        MovimientoCajaEntity movimiento = MovimientoCajaEntity.builder()
                .turnoCaja(turno)
                .usuario(usuario)
                .tipo(dto.getTipo())
                .concepto(dto.getConcepto())
                .monto(dto.getMonto())
                .build();
        MovimientoCajaEntity saved = movimientoCajaRepository.save(movimiento);
        return movimientoCajaToDto(saved);
    }

    // ─── Conversores ────────────────────────────────────────────────────────────

    private MovimientoCajaDto abonoCobrarToDto(AbonoCobrarEntity e) {
        MovimientoCajaDto dto = new MovimientoCajaDto();
        dto.setId(e.getId());
        dto.setTipo("INGRESO");
        dto.setConcepto(e.getReferencia());
        dto.setMonto(e.getMonto());
        dto.setFecha(e.getFechaPago() != null ? e.getFechaPago().toString() : null);
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
        dto.setFecha(e.getFechaPago() != null ? e.getFechaPago().toString() : null);
        dto.setUsuarioNombre(e.getUsuario() != null ? e.getUsuario().getUsername() : null);
        if (e.getCuentaPagar() != null) {
            dto.setCuentaNumero(e.getCuentaPagar().getNumeroCuenta());
            dto.setTerceroNombre(resolverNombreTercero(e.getCuentaPagar().getTercero()));
        }
        return dto;
    }

    private MovimientoCajaDto movimientoCajaToDto(MovimientoCajaEntity e) {
        MovimientoCajaDto dto = new MovimientoCajaDto();
        dto.setId(e.getId());
        dto.setTipo(e.getTipo());
        dto.setConcepto(e.getConcepto());
        dto.setMonto(e.getMonto());
        dto.setFecha(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        dto.setUsuarioNombre(e.getUsuario() != null ? e.getUsuario().getUsername() : null);
        return dto;
    }

    /** Devuelve razonSocial cuando está disponible; si no, nombres + apellidos. */
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
                m -> m.getFecha() != null ? m.getFecha() : "",
                Comparator.naturalOrder()));

        BigDecimal totalIngresosAbonos = abonoCobrarRepository.sumMontoByTurnoCajaId(turnoId);
        BigDecimal totalEgresosAbonos  = abonoPagarRepository.sumMontoByTurnoCajaId(turnoId);
        
        List<MovimientoCajaEntity> movimientosCaja = movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(turnoId);
        BigDecimal totalIngresosCaja = BigDecimal.ZERO;
        BigDecimal totalEgresosCaja = BigDecimal.ZERO;
        for (MovimientoCajaEntity m : movimientosCaja) {
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
        resumen.setComisiones(comisionesList);
        resumen.setTotalComisiones(totalComisiones);

        resumen.setDetalleEfectivo(turnoRepository.detalleEfectivoTurno(turnoId));

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

package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoTableDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.AdicionActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.CreateActivoFijoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciacionPeriodoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.MantenimientoActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ProyeccionDepreciacionDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ReporteActivosDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.RetiroActivoDto;
import com.cloud_technological.aura_pos.entity.ActivoFijoAdicionEntity;
import com.cloud_technological.aura_pos.entity.ActivoFijoEntity;
import com.cloud_technological.aura_pos.entity.ActivoFijoMantenimientoEntity;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.DepreciacionPeriodoEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoAdicionJPARepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoJPARepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoMantenimientoJPARepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.ActivoFijoQueryRepository;
import com.cloud_technological.aura_pos.repositories.activos_fijos.DepreciacionPeriodoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.utils.DepreciacionCalculo;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Activos fijos (Fase 3): ficha, depreciación, adiciones, mantenimientos,
 * baja y venta.
 *
 * <p>La ficha no genera asiento al crearse: si viene de una compra, la compra
 * ya debitó la cuenta del activo; si se registra a mano, su costo entra por
 * saldos iniciales o por un comprobante. Lo que sí contabiliza: depreciación
 * (un asiento por corrida del período), adiciones, baja y venta.
 */
@Service
public class ActivoFijoServiceImpl {

    @Autowired private ActivoFijoJPARepository activoJPARepository;
    @Autowired private ActivoFijoQueryRepository activoQuery;
    @Autowired private DepreciacionPeriodoJPARepository depreciacionJPARepository;
    @Autowired private ActivoFijoMantenimientoJPARepository mantenimientoRepo;
    @Autowired private ActivoFijoAdicionJPARepository adicionRepo;
    @Autowired private AsientoContableJPARepository asientoJPARepository;
    @Autowired private PeriodoContableJPARepository periodoJPARepository;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository planCuentaRepository;
    @Autowired private com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository asientoQueryRepository;
    @Autowired private com.cloud_technological.aura_pos.services.ConfiguracionContableService configContable;
    @Autowired private com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository terceroRepo;
    @Autowired private PeriodoContableResolver periodoResolver;

    /**
     * Qué clase del PUC acepta cada cuenta del activo. Sin esto se podía poner
     * la caja como depreciación acumulada: cada mes el asiento sacaba plata de
     * la caja sin que nadie la hubiera tocado.
     */
    private static final String[] PREFIJOS_ACTIVO = { "15", "16" };
    private static final String[] PREFIJOS_DEPRECIACION = { "1592", "1597", "1598", "1698" };
    private static final String[] PREFIJOS_GASTO = { "51", "52", "72", "73" };
    private static final List<String> METODOS = List.of(DepreciacionCalculo.LINEA_RECTA,
            DepreciacionCalculo.SALDO_DECRECIENTE, DepreciacionCalculo.UNIDADES_PRODUCCION);
    private static final String PREFIJO_DEPRECIACION = "DP";
    private static final String PREFIJO_ACTIVOS = "AF";

    // ── CRUD ────────────────────────────────────────────────────────────────────

    @Transactional
    public ActivoFijoDto crear(CreateActivoFijoDto dto, Integer empresaId) {
        if (activoJPARepository.existsByCodigoAndEmpresaIdAndDeletedAtIsNull(dto.getCodigo(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un activo con el código " + dto.getCodigo());

        validarCuentas(dto, empresaId);
        ActivoFijoEntity entity = new ActivoFijoEntity();
        entity.setEmpresaId(empresaId);
        mapFromDto(dto, entity, empresaId);
        entity.setDepreciacionAcumulada(BigDecimal.ZERO);
        entity.setValorAdiciones(BigDecimal.ZERO);
        entity.setEstado("ACTIVO");
        return toDto(activoJPARepository.save(entity));
    }

    @Transactional
    public ActivoFijoDto actualizar(Long id, CreateActivoFijoDto dto, Integer empresaId) {
        ActivoFijoEntity entity = buscar(id, empresaId);
        exigirVigente(entity, "editar");

        if (activoJPARepository.existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(dto.getCodigo(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código ya está en uso por otro activo");

        // El costo de una ficha que viene de una compra es el de la compra: se
        // corrige editando la compra, o el activo dejaría de cuadrar con su 15.
        // Con depreciaciones encima tampoco: el mayor ya está movido con él.
        boolean cambiaCosto = dto.getValorCompra() != null
                && dto.getValorCompra().compareTo(entity.getValorCompra()) != 0;
        if (cambiaCosto && entity.getCompraId() != null)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El costo de este activo viene de la compra #" + entity.getCompraId()
                            + ": corríjalo editando la compra");
        if (cambiaCosto && activoQuery.mesesDepreciados(id) > 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El activo ya tiene depreciaciones: su costo no se cambia. Registre una adición");

        validarCuentas(dto, empresaId);
        mapFromDto(dto, entity, empresaId);
        return toDto(activoJPARepository.save(entity));
    }

    public ActivoFijoDto getById(Long id, Integer empresaId) {
        return toDto(buscar(id, empresaId));
    }

    public PageImpl<ActivoFijoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return activoQuery.listar(pageable, empresaId);
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ActivoFijoEntity entity = buscar(id, empresaId);
        if (entity.getCompraId() != null)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Este activo lo creó la compra #" + entity.getCompraId() + ": anule la compra o dé de baja el activo");
        if (activoQuery.mesesDepreciados(id) > 0 || entity.getAsientoRetiroId() != null)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El activo ya tiene movimientos contables: no se elimina, se da de baja");
        entity.setDeletedAt(LocalDateTime.now());
        activoJPARepository.save(entity);
    }

    // ── Depreciación ────────────────────────────────────────────────────────────

    /**
     * Deprecia el período para todos los activos vigentes que aún no lo
     * tienen. Un solo asiento por corrida, fechado el último día del mes.
     *
     * @param unidades uso del mes por activo, solo para UNIDADES_PRODUCCION
     */
    @Transactional
    public List<DepreciacionPeriodoDto> calcularDepreciacionPeriodo(Long periodoId, Map<Long, BigDecimal> unidades,
            Integer empresaId, Integer usuarioId) {
        periodoJPARepository.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));
        LocalDate fecha = periodoResolver.ultimoDia(periodoId);
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, fecha);
        YearMonth mes = YearMonth.from(fecha);

        List<DepreciacionPeriodoEntity> cuotas = new ArrayList<>();
        List<ActivoFijoEntity> depreciados = new ArrayList<>();
        for (ActivoFijoEntity activo : activoJPARepository.findByEmpresaIdAndEstadoAndDeletedAtIsNull(empresaId, "ACTIVO")) {
            LocalDate inicio = activo.getFechaInicioDepreciacion() != null
                    ? activo.getFechaInicioDepreciacion() : activo.getFechaAdquisicion();
            // No se deprecian meses anteriores a su inicio ni el mismo activo dos veces.
            if (inicio == null || YearMonth.from(inicio).isAfter(mes)
                    || activoQuery.depreciadoEnPeriodo(activo.getId(), periodoId)) {
                continue;
            }
            BigDecimal usoMes = unidades != null ? unidades.get(activo.getId()) : null;
            int hechas = activoQuery.mesesDepreciados(activo.getId());
            BigDecimal cuota = DepreciacionCalculo.cuota(activo.getMetodoDepreciacion(), activo.costoTotal(),
                    activo.getValorResidual(), activo.getDepreciacionAcumulada(), activo.getVidaUtilMeses(),
                    hechas, usoMes, activo.getUnidadesEstimadas());
            if (cuota.signum() <= 0) {
                // Ya llegó al residual: queda DEPRECIADO para no volver a mirarlo.
                if (pendiente(activo).signum() <= 0) {
                    activo.setEstado("DEPRECIADO");
                    activoJPARepository.save(activo);
                }
                continue;
            }

            DepreciacionPeriodoEntity dep = new DepreciacionPeriodoEntity();
            dep.setActivoId(activo.getId());
            dep.setEmpresaId(empresaId);
            dep.setPeriodoId(periodoId);
            dep.setValor(cuota);
            dep.setMetodo(activo.getMetodoDepreciacion());
            dep.setUnidades(usoMes);
            dep.setCalculadoEn(LocalDateTime.now());
            cuotas.add(dep);

            activo.setDepreciacionAcumulada(activo.getDepreciacionAcumulada().add(cuota));
            if (pendiente(activo).signum() <= 0) {
                activo.setEstado("DEPRECIADO");
            }
            depreciados.add(activo);
        }
        if (cuotas.isEmpty()) {
            return List.of();
        }

        // Un asiento con una línea de gasto y una de acumulada por activo: el
        // gasto lleva el centro de costo del activo.
        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        for (int i = 0; i < cuotas.size(); i++) {
            ActivoFijoEntity activo = depreciados.get(i);
            BigDecimal valor = cuotas.get(i).getValor();
            AsientoDetalleEntity debito = linea(cuentaGastoDepreciacion(activo, empresaId),
                    "Depreciación " + activo.getCodigo() + " — " + activo.getDescripcion(), valor, BigDecimal.ZERO);
            debito.setCentroCostoId(activo.getCentroCostoId());
            lineas.add(debito);
            lineas.add(linea(cuentaDepreciacion(activo, empresaId),
                    "Depreciación acumulada " + activo.getCodigo(), BigDecimal.ZERO, valor));
        }
        AsientoContableEntity asiento = guardarAsiento(empresaId, usuarioId, fecha, periodo.getId(),
                PREFIJO_DEPRECIACION, "Depreciación de activos fijos " + mes, "DEPRECIACION", periodoId, lineas);

        List<DepreciacionPeriodoDto> resultado = new ArrayList<>();
        for (int i = 0; i < cuotas.size(); i++) {
            DepreciacionPeriodoEntity dep = cuotas.get(i);
            dep.setAsientoId(asiento.getId());
            DepreciacionPeriodoDto dto = toDepDto(depreciacionJPARepository.save(dep));
            dto.setActivoCodigo(depreciados.get(i).getCodigo());
            dto.setActivoDescripcion(depreciados.get(i).getDescripcion());
            resultado.add(dto);
            activoJPARepository.save(depreciados.get(i));
        }
        return resultado;
    }

    /**
     * Deshace la depreciación de un período abierto (se calculó mal, faltaba
     * un activo, cambió la vida útil). El contraasiento va con la misma fecha,
     * para que el mes quede como si no se hubiera depreciado.
     */
    @Transactional
    public int reversarDepreciacionPeriodo(Long periodoId, Integer empresaId, Integer usuarioId) {
        periodoJPARepository.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período contable no encontrado"));
        LocalDate fecha = periodoResolver.ultimoDia(periodoId);
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, fecha);
        if (activoQuery.hayDepreciacionPosterior(empresaId, periodoId))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Hay meses posteriores depreciados: reverse primero el más reciente");

        List<Long> ids = activoQuery.depreciacionesDelPeriodo(empresaId, periodoId);
        if (ids.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período no tiene depreciaciones que reversar");
        }
        Map<Long, List<AsientoDetalleEntity>> contra = new LinkedHashMap<>();
        for (Long id : ids) {
            DepreciacionPeriodoEntity dep = depreciacionJPARepository.findById(id).orElseThrow();
            ActivoFijoEntity activo = activoJPARepository.findById(dep.getActivoId()).orElseThrow();
            if (!"ACTIVO".equals(activo.getEstado()) && !"DEPRECIADO".equals(activo.getEstado()))
                throw new GlobalException(HttpStatus.CONFLICT,
                        "El activo " + activo.getCodigo() + " ya se retiró: no se reversa su depreciación");
            activo.setDepreciacionAcumulada(activo.getDepreciacionAcumulada().subtract(dep.getValor()));
            activo.setEstado("ACTIVO");
            activoJPARepository.save(activo);
            if (dep.getAsientoId() != null) {
                contra.computeIfAbsent(dep.getAsientoId(), k -> new ArrayList<>());
            }
            depreciacionJPARepository.delete(dep);
        }
        // Un contraasiento por cada corrida, con el débito y el crédito cruzados.
        for (Long asientoId : contra.keySet()) {
            AsientoContableEntity original = asientoJPARepository.findById(asientoId).orElse(null);
            if (original == null) continue;
            List<AsientoDetalleEntity> lineas = new ArrayList<>();
            for (AsientoDetalleEntity d : original.getDetalles()) {
                AsientoDetalleEntity l = linea(d.getCuentaId(), "Reversa: " + d.getDescripcion(),
                        d.getCredito(), d.getDebito());
                l.setCentroCostoId(d.getCentroCostoId());
                l.setTerceroId(d.getTerceroId());
                lineas.add(l);
            }
            AsientoContableEntity reversa = guardarAsiento(empresaId, usuarioId, original.getFecha(), periodo.getId(),
                    PREFIJO_DEPRECIACION, "Reversa de " + original.getNumeroComprobante() + " — " + original.getDescripcion(),
                    "ANULACION_DEPRECIACION", periodoId, lineas);
            reversa.setReversaDeId(original.getId());
            original.setRevertidoPorId(reversa.getId());
            asientoJPARepository.save(reversa);
            asientoJPARepository.save(original);
        }
        return ids.size();
    }

    public List<DepreciacionPeriodoDto> historialDepreciacion(Long activoId, Integer empresaId) {
        buscar(activoId, empresaId);
        return activoQuery.historial(activoId);
    }

    /** Cómo seguiría la depreciación si nada cambia: una fila por mes hasta el residual. */
    public List<ProyeccionDepreciacionDto> proyeccion(Long id, Integer empresaId) {
        ActivoFijoEntity a = buscar(id, empresaId);
        List<ProyeccionDepreciacionDto> filas = new ArrayList<>();
        if (DepreciacionCalculo.UNIDADES_PRODUCCION.equals(a.getMetodoDepreciacion())) {
            return filas; // depende del uso de cada mes: no hay cómo proyectarla
        }
        BigDecimal acumulada = a.getDepreciacionAcumulada();
        int hechas = activoQuery.mesesDepreciados(id);
        LocalDate inicio = a.getFechaInicioDepreciacion() != null ? a.getFechaInicioDepreciacion() : a.getFechaAdquisicion();
        YearMonth mes = YearMonth.from(inicio).plusMonths(hechas);
        for (int i = 0; i < 600; i++) {
            BigDecimal cuota = DepreciacionCalculo.cuota(a.getMetodoDepreciacion(), a.costoTotal(), a.getValorResidual(),
                    acumulada, a.getVidaUtilMeses(), hechas + i, null, null);
            if (cuota.signum() <= 0) break;
            acumulada = acumulada.add(cuota);
            filas.add(new ProyeccionDepreciacionDto(mes.plusMonths(i).toString(), cuota, acumulada,
                    a.costoTotal().subtract(acumulada)));
        }
        return filas;
    }

    // ── Adiciones y mantenimientos ──────────────────────────────────────────────

    /**
     * Mejora que se capitaliza: DB cuenta del activo · CR contrapartida (caja,
     * banco o proveedor). Sube el costo y, si lo dice, la vida útil; las cuotas
     * siguientes se recalculan solas (DepreciacionCalculo es prospectivo).
     */
    @Transactional
    public AdicionActivoDto registrarAdicion(Long id, AdicionActivoDto dto, Integer empresaId, Integer usuarioId) {
        ActivoFijoEntity activo = buscar(id, empresaId);
        exigirVigente(activo, "registrar una adición a");
        if (dto.getValor() == null || dto.getValor().signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El valor de la adición debe ser mayor que cero");
        if (dto.getDescripcion() == null || dto.getDescripcion().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Describa la adición o mejora");
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, fecha);
        PlanCuentaEntity contrapartida = cuentaAuxiliar(dto.getCuentaContrapartidaId(), empresaId, "contrapartida");
        Long terceroId = validarTercero(dto.getTerceroId(), empresaId);
        int meses = dto.getMesesAdicionales() != null ? Math.max(0, dto.getMesesAdicionales()) : 0;

        AsientoDetalleEntity debito = linea(cuentaActivo(activo, empresaId),
                "Adición " + activo.getCodigo() + ": " + dto.getDescripcion().trim(), dto.getValor(), BigDecimal.ZERO);
        debito.setCentroCostoId(activo.getCentroCostoId());
        AsientoDetalleEntity credito = linea(contrapartida.getId(), "Adición " + activo.getCodigo(),
                BigDecimal.ZERO, dto.getValor());
        credito.setTerceroId(terceroId);
        AsientoContableEntity asiento = guardarAsiento(empresaId, usuarioId, fecha, periodo.getId(), PREFIJO_ACTIVOS,
                "Adición al activo " + activo.getCodigo() + " — " + dto.getDescripcion().trim(),
                "ADICION_ACTIVO", activo.getId(), List.of(debito, credito));

        ActivoFijoAdicionEntity adicion = new ActivoFijoAdicionEntity();
        adicion.setEmpresaId(empresaId);
        adicion.setActivoId(id);
        adicion.setFecha(fecha);
        adicion.setDescripcion(dto.getDescripcion().trim());
        adicion.setValor(dto.getValor());
        adicion.setMesesAdicionales(meses);
        adicion.setCuentaContrapartidaId(contrapartida.getId());
        adicion.setTerceroId(terceroId);
        adicion.setAsientoId(asiento.getId());
        adicionRepo.save(adicion);

        activo.setValorAdiciones(activo.getValorAdiciones().add(dto.getValor()));
        activo.setVidaUtilMeses(activo.getVidaUtilMeses() + meses);
        activo.setEstado("ACTIVO"); // un activo ya depreciado vuelve a tener qué depreciar
        activoJPARepository.save(activo);
        dto.setId(adicion.getId());
        dto.setAsientoId(asiento.getId());
        return dto;
    }

    public List<AdicionActivoDto> adiciones(Long id, Integer empresaId) {
        buscar(id, empresaId);
        return activoQuery.adiciones(id);
    }

    @Transactional
    public MantenimientoActivoDto registrarMantenimiento(Long id, MantenimientoActivoDto dto, Integer empresaId) {
        buscar(id, empresaId);
        if (dto.getDescripcion() == null || dto.getDescripcion().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Describa el mantenimiento");
        ActivoFijoMantenimientoEntity m = new ActivoFijoMantenimientoEntity();
        m.setEmpresaId(empresaId);
        m.setActivoId(id);
        m.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDate.now());
        m.setTipo("CORRECTIVO".equalsIgnoreCase(dto.getTipo()) ? "CORRECTIVO" : "PREVENTIVO");
        m.setDescripcion(dto.getDescripcion().trim());
        m.setCosto(dto.getCosto() != null ? dto.getCosto() : BigDecimal.ZERO);
        m.setTerceroId(validarTercero(dto.getTerceroId(), empresaId));
        m.setProximo(dto.getProximo());
        m = mantenimientoRepo.save(m);
        dto.setId(m.getId());
        dto.setActivoId(id);
        return dto;
    }

    @Transactional
    public void eliminarMantenimiento(Long id, Long mantenimientoId, Integer empresaId) {
        buscar(id, empresaId);
        ActivoFijoMantenimientoEntity m = mantenimientoRepo.findById(mantenimientoId)
                .filter(x -> x.getActivoId().equals(id) && empresaId.equals(x.getEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Mantenimiento no encontrado"));
        mantenimientoRepo.delete(m);
    }

    public List<MantenimientoActivoDto> mantenimientos(Long id, Integer empresaId) {
        buscar(id, empresaId);
        return activoQuery.mantenimientos(id);
    }

    // ── Baja y venta ────────────────────────────────────────────────────────────

    /**
     * Baja por daño, pérdida u obsolescencia: DB depreciación acumulada · DB
     * pérdida (5310) por el valor en libros · CR cuenta del activo por su costo.
     */
    @Transactional
    public ActivoFijoDto darDeBaja(Long id, RetiroActivoDto dto, Integer empresaId, Integer usuarioId) {
        ActivoFijoEntity activo = buscar(id, empresaId);
        LocalDate fecha = validarRetiro(activo, dto);
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, fecha);

        BigDecimal enLibros = activo.costoTotal().subtract(activo.getDepreciacionAcumulada());
        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        agregarRetiroDelActivo(activo, empresaId, lineas);
        if (enLibros.signum() > 0) {
            AsientoDetalleEntity perdida = linea(configContable.resolverCuenta(empresaId,
                    ConceptoContable.PERDIDA_BAJA_ACTIVOS).getId(), "Pérdida por baja " + activo.getCodigo(),
                    enLibros, BigDecimal.ZERO);
            perdida.setCentroCostoId(activo.getCentroCostoId());
            lineas.add(perdida);
        }
        AsientoContableEntity asiento = guardarAsiento(empresaId, usuarioId, fecha, periodo.getId(), PREFIJO_ACTIVOS,
                "Baja del activo " + activo.getCodigo() + " — " + dto.getMotivo().trim(), "BAJA_ACTIVO",
                activo.getId(), lineas);

        activo.setEstado("DADO_DE_BAJA");
        activo.setFechaRetiro(fecha);
        activo.setMotivoRetiro(dto.getMotivo().trim());
        activo.setAsientoRetiroId(asiento.getId());
        return toDto(activoJPARepository.save(activo));
    }

    /**
     * Venta: DB cobro (caja/banco/clientes) por precio + IVA · DB depreciación
     * acumulada · CR costo del activo · CR IVA generado · CR 4245 utilidad o
     * DB 5310 pérdida por la diferencia contra el valor en libros.
     */
    @Transactional
    public ActivoFijoDto vender(Long id, RetiroActivoDto dto, Integer empresaId, Integer usuarioId) {
        ActivoFijoEntity activo = buscar(id, empresaId);
        LocalDate fecha = validarRetiro(activo, dto);
        if (dto.getValorVenta() == null || dto.getValorVenta().signum() < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el precio de venta (sin IVA)");
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, fecha);
        PlanCuentaEntity cobro = cuentaAuxiliar(dto.getCuentaCobroId(), empresaId, "cobro");
        Long comprador = validarTercero(dto.getCompradorTerceroId(), empresaId);

        BigDecimal precio = dto.getValorVenta().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaPct = dto.getIvaPorcentaje() != null ? dto.getIvaPorcentaje() : BigDecimal.ZERO;
        BigDecimal iva = precio.multiply(ivaPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal enLibros = activo.costoTotal().subtract(activo.getDepreciacionAcumulada());
        BigDecimal resultado = precio.subtract(enLibros);

        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        AsientoDetalleEntity recibe = linea(cobro.getId(), "Venta del activo " + activo.getCodigo(),
                precio.add(iva), BigDecimal.ZERO);
        recibe.setTerceroId(comprador);
        lineas.add(recibe);
        agregarRetiroDelActivo(activo, empresaId, lineas);
        if (iva.signum() > 0) {
            AsientoDetalleEntity l = linea(configContable.resolverCuenta(empresaId, ConceptoContable.IVA_GENERADO).getId(),
                    "IVA venta activo " + activo.getCodigo(), BigDecimal.ZERO, iva);
            l.setTerceroId(comprador);
            lineas.add(l);
        }
        if (resultado.signum() > 0) {
            lineas.add(linea(configContable.resolverCuenta(empresaId, ConceptoContable.UTILIDAD_VENTA_ACTIVOS).getId(),
                    "Utilidad venta " + activo.getCodigo(), BigDecimal.ZERO, resultado));
        } else if (resultado.signum() < 0) {
            lineas.add(linea(configContable.resolverCuenta(empresaId, ConceptoContable.PERDIDA_BAJA_ACTIVOS).getId(),
                    "Pérdida venta " + activo.getCodigo(), resultado.negate(), BigDecimal.ZERO));
        }
        AsientoContableEntity asiento = guardarAsiento(empresaId, usuarioId, fecha, periodo.getId(), PREFIJO_ACTIVOS,
                "Venta del activo " + activo.getCodigo() + " — " + dto.getMotivo().trim(), "VENTA_ACTIVO",
                activo.getId(), lineas);

        activo.setEstado("VENDIDO");
        activo.setFechaRetiro(fecha);
        activo.setMotivoRetiro(dto.getMotivo().trim());
        activo.setValorVenta(precio);
        activo.setCompradorTerceroId(comprador);
        activo.setAsientoRetiroId(asiento.getId());
        return toDto(activoJPARepository.save(activo));
    }

    /** Deshace una baja o una venta hecha por error: contraasiento con la misma fecha. */
    @Transactional
    public ActivoFijoDto anularRetiro(Long id, Integer empresaId, Integer usuarioId) {
        ActivoFijoEntity activo = buscar(id, empresaId);
        if (activo.getAsientoRetiroId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El activo no está dado de baja ni vendido");
        AsientoContableEntity original = asientoJPARepository.findById(activo.getAsientoRetiroId()).orElseThrow();
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, original.getFecha());
        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        for (AsientoDetalleEntity d : original.getDetalles()) {
            AsientoDetalleEntity l = linea(d.getCuentaId(), "Reversa: " + d.getDescripcion(), d.getCredito(), d.getDebito());
            l.setCentroCostoId(d.getCentroCostoId());
            l.setTerceroId(d.getTerceroId());
            lineas.add(l);
        }
        AsientoContableEntity reversa = guardarAsiento(empresaId, usuarioId, original.getFecha(), periodo.getId(),
                PREFIJO_ACTIVOS, "Reversa de " + original.getNumeroComprobante() + " — " + original.getDescripcion(),
                "ANULACION_" + original.getTipoOrigen(), activo.getId(), lineas);
        reversa.setReversaDeId(original.getId());
        original.setRevertidoPorId(reversa.getId());
        asientoJPARepository.save(reversa);
        asientoJPARepository.save(original);

        activo.setEstado(pendiente(activo).signum() > 0 ? "ACTIVO" : "DEPRECIADO");
        activo.setFechaRetiro(null);
        activo.setMotivoRetiro(null);
        activo.setValorVenta(null);
        activo.setCompradorTerceroId(null);
        activo.setAsientoRetiroId(null);
        return toDto(activoJPARepository.save(activo));
    }

    // ── Informe ─────────────────────────────────────────────────────────────────

    /**
     * @param agrupar CATEGORIA | CENTRO_COSTO | RESPONSABLE
     * @param periodoId mes cuya depreciación se muestra (opcional)
     */
    public ReporteActivosDto reporte(Integer empresaId, String estado, String categoria, Long centroCostoId,
            Long periodoId, String agrupar) {
        List<ReporteActivosDto.Fila> filas = activoQuery.reporte(empresaId, estado, categoria, centroCostoId, periodoId);
        Map<String, ReporteActivosDto.Grupo> grupos = new LinkedHashMap<>();
        BigDecimal costo = BigDecimal.ZERO, dep = BigDecimal.ZERO, libros = BigDecimal.ZERO;
        for (ReporteActivosDto.Fila f : filas) {
            String clave = switch (agrupar != null ? agrupar : "CATEGORIA") {
                case "CENTRO_COSTO" -> f.getCentroCosto();
                case "RESPONSABLE" -> f.getResponsable();
                default -> f.getCategoria();
            };
            ReporteActivosDto.Grupo g = grupos.computeIfAbsent(clave, k -> {
                ReporteActivosDto.Grupo n = new ReporteActivosDto.Grupo();
                n.setNombre(k);
                n.setCantidad(0);
                n.setCosto(BigDecimal.ZERO);
                n.setDepreciacion(BigDecimal.ZERO);
                n.setEnLibros(BigDecimal.ZERO);
                return n;
            });
            g.setCantidad(g.getCantidad() + 1);
            g.setCosto(g.getCosto().add(f.getCosto()));
            g.setDepreciacion(g.getDepreciacion().add(f.getDepreciacionAcumulada()));
            g.setEnLibros(g.getEnLibros().add(f.getValorEnLibros()));
            costo = costo.add(f.getCosto());
            dep = dep.add(f.getDepreciacionAcumulada());
            libros = libros.add(f.getValorEnLibros());
        }
        ReporteActivosDto r = new ReporteActivosDto();
        r.setFilas(filas);
        r.setGrupos(new ArrayList<>(grupos.values()));
        r.setTotalCosto(costo);
        r.setTotalDepreciacion(dep);
        r.setTotalEnLibros(libros);
        return r;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private ActivoFijoEntity buscar(Long id, Integer empresaId) {
        return activoJPARepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Activo fijo no encontrado"));
    }

    private static void exigirVigente(ActivoFijoEntity activo, String accion) {
        if (!"ACTIVO".equals(activo.getEstado()) && !"DEPRECIADO".equals(activo.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede " + accion + " un activo " + activo.getEstado().replace('_', ' ').toLowerCase());
    }

    private static BigDecimal pendiente(ActivoFijoEntity a) {
        return a.costoTotal().subtract(a.getValorResidual()).subtract(a.getDepreciacionAcumulada());
    }

    private LocalDate validarRetiro(ActivoFijoEntity activo, RetiroActivoDto dto) {
        exigirVigente(activo, "retirar");
        if (dto.getMotivo() == null || dto.getMotivo().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo del retiro");
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();
        if (activo.getFechaAdquisicion() != null && fecha.isBefore(activo.getFechaAdquisicion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha del retiro es anterior a la compra del activo");
        if (activoQuery.hijosActivos(activo.getId()) > 0)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El activo tiene componentes vigentes: retírelos o desvincúlelos primero");
        return fecha;
    }

    /** Líneas que sacan el activo de los libros: DB acumulada · CR costo. */
    private void agregarRetiroDelActivo(ActivoFijoEntity activo, Integer empresaId, List<AsientoDetalleEntity> lineas) {
        if (activo.getDepreciacionAcumulada().signum() > 0) {
            lineas.add(linea(cuentaDepreciacion(activo, empresaId), "Depreciación acumulada " + activo.getCodigo(),
                    activo.getDepreciacionAcumulada(), BigDecimal.ZERO));
        }
        lineas.add(linea(cuentaActivo(activo, empresaId), "Retiro del activo " + activo.getCodigo(),
                BigDecimal.ZERO, activo.costoTotal()));
    }

    private boolean esIntangible(ActivoFijoEntity a) {
        return "INTANGIBLE".equalsIgnoreCase(a.getCategoria());
    }

    private Long cuentaActivo(ActivoFijoEntity a, Integer empresaId) {
        if (a.getCuentaActivoId() != null) return a.getCuentaActivoId();
        return configContable.resolverCuenta(empresaId,
                esIntangible(a) ? ConceptoContable.INTANGIBLES : ConceptoContable.ACTIVO_FIJO_COMPRA).getId();
    }

    private Long cuentaDepreciacion(ActivoFijoEntity a, Integer empresaId) {
        if (a.getCuentaDepreciacionId() != null) {
            exigirClase(a.getCuentaDepreciacionId(), PREFIJOS_DEPRECIACION, "depreciación acumulada", empresaId);
            return a.getCuentaDepreciacionId();
        }
        return configContable.resolverCuenta(empresaId,
                esIntangible(a) ? ConceptoContable.AMORTIZACION_ACUMULADA : ConceptoContable.DEPRECIACION_ACUMULADA).getId();
    }

    private Long cuentaGastoDepreciacion(ActivoFijoEntity a, Integer empresaId) {
        if (a.getCuentaGastoDepId() != null) {
            exigirClase(a.getCuentaGastoDepId(), PREFIJOS_GASTO, "gasto de depreciación", empresaId);
            return a.getCuentaGastoDepId();
        }
        return configContable.resolverCuenta(empresaId, ConceptoContable.DEPRECIACION_GASTO).getId();
    }

    private PlanCuentaEntity cuentaAuxiliar(Long cuentaId, Integer empresaId, String rol) {
        if (cuentaId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la cuenta de " + rol);
        PlanCuentaEntity c = planCuentaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta de " + rol + " no existe"));
        if (!Boolean.TRUE.equals(c.getActiva()) || !Boolean.TRUE.equals(c.getAuxiliar()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + c.getCodigo() + " está inactiva o es de agrupación. Elija una subcuenta auxiliar");
        return c;
    }

    private Long validarTercero(Long terceroId, Integer empresaId) {
        if (terceroId == null) return null;
        return terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Tercero no encontrado"))
                .getId();
    }

    private static AsientoDetalleEntity linea(Long cuentaId, String descripcion, BigDecimal debito, BigDecimal credito) {
        AsientoDetalleEntity d = new AsientoDetalleEntity();
        d.setCuentaId(cuentaId);
        d.setDescripcion(descripcion != null && descripcion.length() > 250 ? descripcion.substring(0, 250) : descripcion);
        d.setDebito(debito != null ? debito : BigDecimal.ZERO);
        d.setCredito(credito != null ? credito : BigDecimal.ZERO);
        return d;
    }

    private AsientoContableEntity guardarAsiento(Integer empresaId, Integer usuarioId, LocalDate fecha, Long periodoId,
            String prefijo, String descripcion, String tipoOrigen, Long origenId, List<AsientoDetalleEntity> lineas) {
        BigDecimal debito = lineas.stream().map(AsientoDetalleEntity::getDebito).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credito = lineas.stream().map(AsientoDetalleEntity::getCredito).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debito.compareTo(credito) != 0)
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "El asiento de " + descripcion + " no cuadra (" + debito + " vs " + credito + ")");
        AsientoContableEntity asiento = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(fecha)
                .numeroComprobante(asientoQueryRepository.siguienteNumeroComprobante(empresaId, prefijo))
                .descripcion(descripcion.length() > 250 ? descripcion.substring(0, 250) : descripcion)
                .tipoOrigen(tipoOrigen)
                .origenId(origenId)
                .totalDebito(debito)
                .totalCredito(credito)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .periodoContableId(periodoId)
                .build();
        for (AsientoDetalleEntity l : lineas) {
            l.setAsiento(asiento);
            asiento.getDetalles().add(l);
        }
        return asientoJPARepository.save(asiento);
    }

    private void validarCuentas(CreateActivoFijoDto dto, Integer empresaId) {
        exigirClase(dto.getCuentaActivoId(), PREFIJOS_ACTIVO, "cuenta del activo", empresaId);
        exigirClase(dto.getCuentaDepreciacionId(), PREFIJOS_DEPRECIACION, "depreciación acumulada", empresaId);
        exigirClase(dto.getCuentaGastoDepId(), PREFIJOS_GASTO, "gasto de depreciación", empresaId);
        String metodo = dto.getMetodoDepreciacion() != null ? dto.getMetodoDepreciacion() : DepreciacionCalculo.LINEA_RECTA;
        if (!METODOS.contains(metodo))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Método de depreciación inválido: use LINEA_RECTA, SALDO_DECRECIENTE o UNIDADES_PRODUCCION");
        if (DepreciacionCalculo.UNIDADES_PRODUCCION.equals(metodo)
                && (dto.getUnidadesEstimadas() == null || dto.getUnidadesEstimadas().signum() <= 0))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Por unidades de producción hay que decir cuántas unidades (horas, km…) dará el activo en su vida");
        if (dto.getVidaUtilMeses() == null || dto.getVidaUtilMeses() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La vida útil debe ser mayor que cero");
        if (dto.getValorResidual() != null && dto.getValorCompra() != null
                && dto.getValorResidual().compareTo(dto.getValorCompra()) >= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El valor residual debe ser menor que el costo");
    }

    /** La cuenta es opcional, pero si viene tiene que ser auxiliar, activa y de su clase. */
    private void exigirClase(Long cuentaId, String[] prefijos, String rol, Integer empresaId) {
        if (cuentaId == null) {
            return;
        }
        PlanCuentaEntity cuenta = planCuentaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta de " + rol + " no existe"));
        String codigo = cuenta.getCodigo() != null ? cuenta.getCodigo() : "";
        boolean claseValida = java.util.Arrays.stream(prefijos).anyMatch(codigo::startsWith);
        if (!claseValida) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + codigo + " — " + cuenta.getNombre() + " no sirve como " + rol
                            + ". Use una cuenta que empiece por " + String.join(", ", prefijos));
        }
        if (!Boolean.TRUE.equals(cuenta.getActiva()) || !Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + codigo + " — " + cuenta.getNombre()
                            + " está inactiva o es de agrupación. Elija una subcuenta auxiliar");
        }
    }

    private void mapFromDto(CreateActivoFijoDto dto, ActivoFijoEntity entity, Integer empresaId) {
        entity.setCodigo(dto.getCodigo().trim().toUpperCase());
        entity.setDescripcion(dto.getDescripcion().trim());
        entity.setCategoria(dto.getCategoria());
        entity.setFechaAdquisicion(dto.getFechaAdquisicion());
        entity.setValorCompra(dto.getValorCompra());
        entity.setVidaUtilMeses(dto.getVidaUtilMeses());
        entity.setMetodoDepreciacion(dto.getMetodoDepreciacion() != null ? dto.getMetodoDepreciacion() : "LINEA_RECTA");
        entity.setValorResidual(dto.getValorResidual() != null ? dto.getValorResidual() : BigDecimal.ZERO);
        entity.setUbicacion(dto.getUbicacion());
        entity.setResponsable(dto.getResponsable());
        entity.setCuentaActivoId(dto.getCuentaActivoId());
        entity.setCuentaDepreciacionId(dto.getCuentaDepreciacionId());
        entity.setCuentaGastoDepId(dto.getCuentaGastoDepId());
        entity.setCentroCostoId(dto.getCentroCostoId());
        entity.setPeriodoContableId(dto.getPeriodoContableId());
        entity.setTerceroId(dto.getTerceroId());
        entity.setObservaciones(dto.getObservaciones());
        entity.setPlaca(vacioANull(dto.getPlaca()));
        entity.setSerial(vacioANull(dto.getSerial()));
        entity.setMarca(vacioANull(dto.getMarca()));
        entity.setModelo(vacioANull(dto.getModelo()));
        entity.setResponsableTerceroId(validarTercero(dto.getResponsableTerceroId(), empresaId));
        entity.setAseguradora(vacioANull(dto.getAseguradora()));
        entity.setPolizaNumero(vacioANull(dto.getPolizaNumero()));
        entity.setPolizaVence(dto.getPolizaVence());
        entity.setFechaInicioDepreciacion(dto.getFechaInicioDepreciacion());
        entity.setUnidadesEstimadas(dto.getUnidadesEstimadas());
        if (dto.getActivoPadreId() != null) {
            if (dto.getActivoPadreId().equals(entity.getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Un activo no puede ser componente de sí mismo");
            ActivoFijoEntity padre = buscar(dto.getActivoPadreId(), empresaId);
            if (entity.getId() != null && entity.getId().equals(padre.getActivoPadreId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El activo padre es componente de este activo");
        }
        entity.setActivoPadreId(dto.getActivoPadreId());
    }

    private static String vacioANull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private ActivoFijoDto toDto(ActivoFijoEntity e) {
        ActivoFijoDto dto = new ActivoFijoDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresaId());
        dto.setCodigo(e.getCodigo());
        dto.setDescripcion(e.getDescripcion());
        dto.setCategoria(e.getCategoria());
        dto.setFechaAdquisicion(e.getFechaAdquisicion());
        dto.setValorCompra(e.getValorCompra());
        dto.setVidaUtilMeses(e.getVidaUtilMeses());
        dto.setMetodoDepreciacion(e.getMetodoDepreciacion());
        dto.setDepreciacionAcumulada(e.getDepreciacionAcumulada());
        dto.setValorResidual(e.getValorResidual());
        dto.setValorAdiciones(e.getValorAdiciones());
        dto.setCostoTotal(e.costoTotal());
        dto.setValorEnLibros(e.costoTotal().subtract(e.getDepreciacionAcumulada()));
        dto.setUbicacion(e.getUbicacion());
        dto.setResponsable(e.getResponsable());
        dto.setEstado(e.getEstado());
        dto.setCuentaActivoId(e.getCuentaActivoId());
        dto.setCuentaDepreciacionId(e.getCuentaDepreciacionId());
        dto.setCuentaGastoDepId(e.getCuentaGastoDepId());
        dto.setCentroCostoId(e.getCentroCostoId());
        dto.setPeriodoContableId(e.getPeriodoContableId());
        dto.setTerceroId(e.getTerceroId());
        dto.setObservaciones(e.getObservaciones());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setPlaca(e.getPlaca());
        dto.setSerial(e.getSerial());
        dto.setMarca(e.getMarca());
        dto.setModelo(e.getModelo());
        dto.setResponsableTerceroId(e.getResponsableTerceroId());
        dto.setResponsableTerceroNombre(activoQuery.nombreTercero(e.getResponsableTerceroId()));
        dto.setActivoPadreId(e.getActivoPadreId());
        if (e.getActivoPadreId() != null) {
            activoJPARepository.findById(e.getActivoPadreId()).ifPresent(p -> dto.setActivoPadreCodigo(p.getCodigo()));
        }
        dto.setAseguradora(e.getAseguradora());
        dto.setPolizaNumero(e.getPolizaNumero());
        dto.setPolizaVence(e.getPolizaVence());
        dto.setFechaInicioDepreciacion(e.getFechaInicioDepreciacion());
        dto.setUnidadesEstimadas(e.getUnidadesEstimadas());
        dto.setMesesDepreciados(e.getId() != null ? activoQuery.mesesDepreciados(e.getId()) : 0);
        dto.setFechaRetiro(e.getFechaRetiro());
        dto.setMotivoRetiro(e.getMotivoRetiro());
        dto.setValorVenta(e.getValorVenta());
        dto.setCompradorTerceroId(e.getCompradorTerceroId());
        dto.setAsientoRetiroId(e.getAsientoRetiroId());
        dto.setCompraId(e.getCompraId());
        dto.setProductoId(e.getProductoId());
        return dto;
    }

    private DepreciacionPeriodoDto toDepDto(DepreciacionPeriodoEntity e) {
        DepreciacionPeriodoDto dto = new DepreciacionPeriodoDto();
        dto.setId(e.getId());
        dto.setActivoId(e.getActivoId());
        dto.setPeriodoId(e.getPeriodoId());
        dto.setValor(e.getValor());
        dto.setAsientoId(e.getAsientoId());
        dto.setCalculadoEn(e.getCalculadoEn());
        dto.setMetodo(e.getMetodo());
        dto.setUnidades(e.getUnidades());
        return dto;
    }
}

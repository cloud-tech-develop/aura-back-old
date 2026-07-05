package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.nomina.AddNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaTableDto;
import com.cloud_technological.aura_pos.entity.EmpleadoArlEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class NominaServiceImpl implements NominaService {

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private NominaJPARepository nominaRepo;

    @Autowired
    private NominaQueryRepository nominaQueryRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private PeriodoNominaJPARepository periodoRepo;

    @Autowired
    private NominaConfigJPARepository configRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.asistencia.PeriodoAsistenciaJPARepository periodoAsistenciaRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaNovedadNominaJPARepository asistenciaNovedadRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.asistencia.AutorizacionLiquidacionJPARepository autorizacionRepo;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.asistencia_frente.AsistenciaFrenteQueryRepository frenteQueryRepo;

    @Autowired
    private com.cloud_technological.aura_pos.services.implementations.LaboralConfigService laboralConfigService;

    @Autowired
    private com.cloud_technological.aura_pos.services.AuditoriaNominaService auditoria;

    @Autowired
    private com.cloud_technological.aura_pos.services.TesoreriaService tesoreriaService;

    @Autowired
    private com.cloud_technological.aura_pos.services.ConfiguracionContableService configuracionContable;

    @Autowired
    private com.cloud_technological.aura_pos.utils.SecurityUtils securityUtils;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal TREINTA = new BigDecimal("30");
    // Porcentajes de provisiones (fijos por ley colombiana)
    private static final BigDecimal PCT_PRIMA = new BigDecimal("8.33");
    private static final BigDecimal PCT_CESANTIAS = new BigDecimal("8.33");
    private static final BigDecimal PCT_INT_CESANTIAS = new BigDecimal("12.00");
    private static final BigDecimal PCT_VACACIONES = new BigDecimal("4.17");

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Override
    public PageImpl<NominaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return nominaQueryRepo.listar(pageable, empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public com.cloud_technological.aura_pos.dto.nomina.nomina.PeriodoResumenDto obtenerResumenPeriodo(
            Long periodoId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        List<NominaEntity> nominas = nominaRepo.findByPeriodoIdAndEmpresaId(periodoId, empresaId).stream()
                .filter(n -> !"ANULADO".equals(n.getEstado()))
                .collect(Collectors.toList());

        var dto = new com.cloud_technological.aura_pos.dto.nomina.nomina.PeriodoResumenDto();
        dto.setPeriodoId(periodo.getId());
        dto.setDocumento("NOM-" + periodo.getId());
        dto.setFechaInicio(periodo.getFechaInicio());
        dto.setFechaFin(periodo.getFechaFin());
        dto.setEstado(periodo.getEstado());
        dto.setCantidadEmpleados(nominas.size());

        BigDecimal totalDev = BigDecimal.ZERO, totalDed = BigDecimal.ZERO, totalNeto = BigDecimal.ZERO;
        for (NominaEntity n : nominas) {
            totalDev = totalDev.add(nz(n.getTotalDevengado()));
            totalDed = totalDed.add(nz(n.getTotalDeducciones()));
            totalNeto = totalNeto.add(nz(n.getNetoPagar()));

            NominaTableDto e = new NominaTableDto();
            e.setId(n.getId());
            e.setPeriodoId(periodo.getId());
            e.setPeriodoFechaInicio(periodo.getFechaInicio());
            e.setPeriodoFechaFin(periodo.getFechaFin());
            e.setEmpleadoId(n.getEmpleado().getId());
            e.setEmpleadoNombre(n.getEmpleado().getNombres() + " " + n.getEmpleado().getApellidos());
            e.setEmpleadoDocumento(n.getEmpleado().getNumeroDocumento());
            e.setCargo(n.getEmpleado().getCargo());
            e.setBanco(n.getEmpleado().getBanco());
            e.setNumeroCuenta(n.getEmpleado().getNumeroCuenta());
            e.setDiasTrabajados(n.getDiasTrabajados());
            e.setTotalDevengado(n.getTotalDevengado());
            e.setTotalDeducciones(n.getTotalDeducciones());
            e.setNetoPagar(n.getNetoPagar());
            e.setEstado(n.getEstado());
            dto.getEmpleados().add(e);

            for (NominaNovedadEntity nov : n.getNovedades()) {
                var r = new com.cloud_technological.aura_pos.dto.nomina.nomina.NovedadResumenDto();
                r.setNominaId(n.getId());
                r.setEmpleadoId(n.getEmpleado().getId());
                r.setEmpleadoNombre(e.getEmpleadoNombre());
                r.setTipo(nov.getTipo());
                r.setDescripcion(nov.getDescripcion());
                r.setCantidad(nov.getCantidad());
                r.setValorUnitario(nov.getValorUnitario());
                r.setValorTotal(nov.getValorTotal());
                r.setEsDeduccion(nov.getEsDeduccion());
                r.setOrigen(nov.getOrigen());
                dto.getNovedades().add(r);
            }
        }
        dto.setTotalDevengado(totalDev);
        dto.setTotalDeducciones(totalDed);
        dto.setTotalNeto(totalNeto);
        return dto;
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    @Override
    public NominaDto obtenerPorId(Long id, Integer empresaId) {
        NominaEntity entity = nominaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));
        return toDto(entity);
    }

    // ─── Liquidación ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NominaDto liquidar(Long periodoId, Long empleadoId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        if ("ANULADO".equals(periodo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período está anulado");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(empleadoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        if (!Boolean.TRUE.equals(empleado.getActivo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El empleado está retirado");

        NominaEntity nomina;
        if (nominaRepo.existsByEmpleadoIdAndPeriodoId(empleadoId, periodoId)) {
            nomina = nominaRepo.findByPeriodoIdAndEmpresaId(periodoId, empresaId)
                    .stream()
                    .filter(n -> n.getEmpleado().getId().equals(empleadoId))
                    .findFirst()
                    .orElseThrow();
            if ("APROBADO".equals(nomina.getEstado()) || "PAGADO".equals(nomina.getEstado()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La nómina ya está aprobada o pagada");
        } else {
            nomina = new NominaEntity();
            EmpresaEntity empresa = new EmpresaEntity();
            empresa.setId(empresaId);
            nomina.setEmpresa(empresa);
            nomina.setPeriodo(periodo);
            nomina.setEmpleado(empleado);
            nomina.setCreatedAt(LocalDateTime.now());
        }

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracionPorDefecto(empresaId));

        // Gating de asistencia (Fase 5): si el empleado requiere asistencia y no está
        // aprobada ni autorizada excepcionalmente, se bloquea la liquidación.
        if (requiereAsistencia(config, empleado)) {
            if (!asistenciaAprobadaOAutorizada(empresaId, periodo, empleado))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede liquidar: la asistencia del período no está aprobada. " +
                        "Apruebe la asistencia o registre una autorización excepcional.");
            consumirNovedadesAsistencia(nomina, periodo, empleado, empresaId);
        }

        nomina.setDiasTrabajados(diasLiquidacion(periodo, empleado, empresaId));
        calcular(nomina, empleado, config);
        nomina.setUpdatedAt(LocalDateTime.now());

        // Actualizar estado del período a LIQUIDADO si estaba ABIERTO
        if ("ABIERTO".equals(periodo.getEstado())) {
            periodo.setEstado("LIQUIDADO");
            periodoRepo.save(periodo);
        }

        return toDto(nominaRepo.save(nomina));
    }

    @Override
    @Transactional
    public void liquidarPeriodoCompleto(Long periodoId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        if ("ANULADO".equals(periodo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período está anulado");

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracionPorDefecto(empresaId));

        List<EmpleadoEntity> empleados = empleadoRepo.findByEmpresaIdAndActivoTrue(empresaId);

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        for (EmpleadoEntity empleado : empleados) {
            NominaEntity nomina;
            if (nominaRepo.existsByEmpleadoIdAndPeriodoId(empleado.getId(), periodoId)) {
                nomina = nominaRepo.findByPeriodoIdAndEmpresaId(periodoId, empresaId)
                        .stream()
                        .filter(n -> n.getEmpleado().getId().equals(empleado.getId()))
                        .filter(n -> !"APROBADO".equals(n.getEstado()) && !"PAGADO".equals(n.getEstado()))
                        .findFirst()
                        .orElse(null);
                if (nomina == null) continue; // ya aprobada, se omite
            } else {
                nomina = new NominaEntity();
                nomina.setEmpresa(empresa);
                nomina.setPeriodo(periodo);
                nomina.setEmpleado(empleado);
                nomina.setCreatedAt(LocalDateTime.now());
            }

            // Gating de asistencia por empleado; si está bloqueado, se omite del lote.
            if (requiereAsistencia(config, empleado)) {
                if (!asistenciaAprobadaOAutorizada(empresaId, periodo, empleado)) continue;
                consumirNovedadesAsistencia(nomina, periodo, empleado, empresaId);
            }

            nomina.setDiasTrabajados(diasLiquidacion(periodo, empleado, empresaId));
            calcular(nomina, empleado, config);
            nomina.setUpdatedAt(LocalDateTime.now());
            nominaRepo.save(nomina);
        }

        periodo.setEstado("LIQUIDADO");
        periodoRepo.save(periodo);
    }

    // ─── Novedades ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NominaDto agregarNovedad(Long nominaId, AddNovedadDto dto, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if ("APROBADO".equals(nomina.getEstado()) || "PAGADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se pueden agregar novedades a una nómina aprobada o pagada");

        NominaNovedadEntity novedad = new NominaNovedadEntity();
        novedad.setNomina(nomina);
        novedad.setTipo(dto.getTipo());
        novedad.setDescripcion(dto.getDescripcion());
        novedad.setCantidad(dto.getCantidad() != null ? dto.getCantidad() : BigDecimal.ONE);
        novedad.setValorUnitario(dto.getValorUnitario());
        novedad.setValorTotal(novedad.getCantidad().multiply(dto.getValorUnitario()).setScale(2, RoundingMode.HALF_UP));
        novedad.setEsDeduccion(esDeduccion(dto.getTipo()));
        nomina.getNovedades().add(novedad);

        // Recalcular con la nueva novedad
        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracionPorDefecto(empresaId));
        calcular(nomina, nomina.getEmpleado(), config);
        nomina.setUpdatedAt(LocalDateTime.now());

        return toDto(nominaRepo.save(nomina));
    }

    @Override
    @Transactional
    public NominaDto eliminarNovedad(Long nominaId, Long novedadId, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if ("APROBADO".equals(nomina.getEstado()) || "PAGADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se pueden modificar novedades de una nómina aprobada o pagada");

        nomina.getNovedades().removeIf(n -> n.getId().equals(novedadId));

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracionPorDefecto(empresaId));
        calcular(nomina, nomina.getEmpleado(), config);
        nomina.setUpdatedAt(LocalDateTime.now());

        return toDto(nominaRepo.save(nomina));
    }

    // ─── Estado ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NominaDto aprobar(Long id, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if ("ANULADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nómina está anulada");

        String estadoAnterior = nomina.getEstado();
        nomina.setEstado("APROBADO");
        nomina.setUpdatedAt(LocalDateTime.now());
        NominaDto dto = toDto(nominaRepo.save(nomina));
        auditar("NOMINA", nomina.getId(), "APROBAR", empresaId, estadoAnterior, "APROBADO", null);

        // Asiento contable de la nómina al aprobarla, tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.OperacionContabilizableEvent(
                        "NOMINA", nomina.getId(), empresaId, null));

        return dto;
    }

    @Override
    @Transactional
    public NominaDto pagar(Long id, com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto dto, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if (!"APROBADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se puede pagar una nómina en estado APROBADO");

        aplicarPago(nomina, dto, empresaId);
        cerrarPeriodoSiCompleto(nomina.getPeriodo(), empresaId);
        return toDto(nominaRepo.save(nomina));
    }

    @Override
    @Transactional
    public void pagarPeriodo(Long periodoId, com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto dto, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        List<NominaEntity> aprobadas = nominaRepo.findByPeriodoIdAndEmpresaId(periodoId, empresaId).stream()
                .filter(n -> "APROBADO".equals(n.getEstado()))
                .collect(Collectors.toList());
        if (aprobadas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay nóminas APROBADAS por pagar en este período");

        for (NominaEntity nomina : aprobadas) {
            aplicarPago(nomina, dto, empresaId);
            nominaRepo.save(nomina);
        }
        cerrarPeriodoSiCompleto(periodo, empresaId);
    }

    /** Marca la nómina como PAGADA con su origen y dispara el asiento de pago. */
    private void aplicarPago(NominaEntity nomina, com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto dto, Integer empresaId) {
        String medio = dto != null && dto.getMedioPago() != null ? dto.getMedioPago() : "EFECTIVO";
        if ("TRANSFERENCIA".equals(medio) && (dto == null || dto.getCuentaBancariaId() == null))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Para pago por transferencia debe indicar la cuenta bancaria");

        Long cuentaBancariaId = dto != null ? dto.getCuentaBancariaId() : null;
        nomina.setEstado("PAGADO");
        nomina.setMedioPago(medio);
        nomina.setCuentaBancariaId(cuentaBancariaId);
        nomina.setFechaPago(LocalDateTime.now());
        nomina.setUpdatedAt(LocalDateTime.now());
        nominaRepo.save(nomina);

        auditar("NOMINA", nomina.getId(), "PAGAR", empresaId, "APROBADO", "PAGADO", medio);

        BigDecimal neto = nomina.getNetoPagar() != null ? nomina.getNetoPagar() : BigDecimal.ZERO;
        String beneficiario = nomina.getEmpleado() != null
                ? nomina.getEmpleado().getNombres() + " " + nomina.getEmpleado().getApellidos() : "Empleado";

        if ("TRANSFERENCIA".equals(medio) && cuentaBancariaId != null && neto.signum() > 0) {
            // Egreso de tesorería: descuenta el saldo del banco, deja movimiento para
            // conciliación y genera el asiento DB Salarios por pagar / CR Banco
            // (vía la contrapartida). No se dispara NOMINA_PAGO para no duplicar.
            Long usuarioId = null;
            try { usuarioId = securityUtils.getUsuarioId(); } catch (Exception ignored) {}
            Long cuentaSalarios = configuracionContable.resolverCuenta(
                    empresaId, com.cloud_technological.aura_pos.entity.ConceptoContable.SALARIOS_POR_PAGAR).getId();

            var mov = new com.cloud_technological.aura_pos.dto.tesoreria.CreateMovimientoDto();
            mov.setCuentaBancariaId(cuentaBancariaId);
            mov.setMonto(neto);
            mov.setConcepto("Pago nómina — " + beneficiario);
            mov.setBeneficiario(beneficiario);
            mov.setFecha(java.time.LocalDate.now());
            mov.setCategoria("NOMINA");
            mov.setContrapartidaCuentaId(cuentaSalarios);
            tesoreriaService.crearEgreso(empresaId, usuarioId != null ? usuarioId.intValue() : null, mov);
        } else {
            // Efectivo (caja): solo asiento contable DB Salarios por pagar / CR Caja.
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.event.OperacionContabilizableEvent(
                            "NOMINA_PAGO", nomina.getId(), empresaId, null));
        }
    }

    private void cerrarPeriodoSiCompleto(PeriodoNominaEntity periodo, Integer empresaId) {
        boolean quedanPendientes = nominaRepo.findByPeriodoIdAndEmpresaId(periodo.getId(), empresaId)
                .stream()
                .anyMatch(n -> !"PAGADO".equals(n.getEstado()) && !"ANULADO".equals(n.getEstado()));
        if (!quedanPendientes && !"ANULADO".equals(periodo.getEstado())) {
            periodo.setEstado("PAGADO");
            periodoRepo.save(periodo);
        }
    }

    @Override
    @Transactional
    public NominaDto anular(Long id, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if ("PAGADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede anular una nómina ya pagada");

        String estadoAnterior = nomina.getEstado();
        nomina.setEstado("ANULADO");
        nomina.setUpdatedAt(LocalDateTime.now());
        NominaDto dto = toDto(nominaRepo.save(nomina));
        auditar("NOMINA", nomina.getId(), "ANULAR", empresaId, estadoAnterior, "ANULADO", null);

        // Reversar el asiento de la nómina tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "NOMINA", nomina.getId(), empresaId, null));

        return dto;
    }

    // ─── Motor de cálculo ─────────────────────────────────────────────────────

    private void calcular(NominaEntity nomina, EmpleadoEntity empleado, NominaConfigEntity config) {
        int dias = nomina.getDiasTrabajados() != null ? nomina.getDiasTrabajados() : 30;
        nomina.setDiasTrabajados(dias);
        nomina.setSalarioBase(empleado.getSalarioBase());

        BigDecimal salario = empleado.getSalarioBase();

        // Salario proporcional por días trabajados
        BigDecimal salarioProporcional = salario
                .multiply(BigDecimal.valueOf(dias))
                .divide(TREINTA, 2, RoundingMode.HALF_UP);
        nomina.setSalarioProporcional(salarioProporcional);

        // Auxilio de transporte (si salario ≤ 2 SMMLV)
        BigDecimal auxilio = BigDecimal.ZERO;
        if ("COMPLETO".equals(config.getModoNomina())) {
            BigDecimal dosSmmlv = config.getSmmlv().multiply(BigDecimal.valueOf(2));
            if (salario.compareTo(dosSmmlv) <= 0) {
                auxilio = config.getAuxilioTransporte()
                        .multiply(BigDecimal.valueOf(dias))
                        .divide(TREINTA, 2, RoundingMode.HALF_UP);
            }
        }
        nomina.setAuxilioTransporte(auxilio);

        // Novedades: separar devengos y deducciones
        BigDecimal novedadesDevengadas = BigDecimal.ZERO;
        BigDecimal novedadesDeducciones = BigDecimal.ZERO;

        for (NominaNovedadEntity nov : nomina.getNovedades()) {
            if (Boolean.TRUE.equals(nov.getEsDeduccion())) {
                novedadesDeducciones = novedadesDeducciones.add(nov.getValorTotal());
            } else {
                novedadesDevengadas = novedadesDevengadas.add(nov.getValorTotal());
            }
        }
        nomina.setTotalNovedadesDev(novedadesDevengadas);

        // Total devengado
        BigDecimal totalDevengado = salarioProporcional.add(auxilio).add(novedadesDevengadas);
        nomina.setTotalDevengado(totalDevengado);

        if ("COMPLETO".equals(config.getModoNomina())) {
            calcularCompleto(nomina, salarioProporcional, totalDevengado, novedadesDeducciones, config, empleado);
        } else {
            calcularSimplificado(nomina, novedadesDeducciones);
        }
    }

    private void calcularCompleto(NominaEntity nomina, BigDecimal salarioProporcional,
                                   BigDecimal totalDevengado, BigDecimal novedadesDeducciones,
                                   NominaConfigEntity config, EmpleadoEntity empleado) {
        // Deducciones empleado
        BigDecimal deduccionSalud = porcentaje(totalDevengado, config.getPctSaludEmpleado());
        BigDecimal deduccionPension = porcentaje(totalDevengado, config.getPctPensionEmpleado());

        nomina.setDeduccionSalud(deduccionSalud);
        nomina.setDeduccionPension(deduccionPension);
        nomina.setDeduccionOtros(novedadesDeducciones);

        BigDecimal totalDeducciones = deduccionSalud.add(deduccionPension).add(novedadesDeducciones);
        nomina.setTotalDeducciones(totalDeducciones);
        nomina.setNetoPagar(totalDevengado.subtract(totalDeducciones).max(BigDecimal.ZERO));

        // Aportes empleador
        nomina.setAporteSalud(porcentaje(totalDevengado, config.getPctSaludEmpleador()));
        nomina.setAportePension(porcentaje(totalDevengado, config.getPctPensionEmpleador()));
        nomina.setAporteCaja(porcentaje(totalDevengado, config.getPctCajaCompensacion()));
        nomina.setAporteIcbf(porcentaje(totalDevengado, config.getPctIcbf()));
        nomina.setAporteSena(porcentaje(totalDevengado, config.getPctSena()));

        // ARL según nivel de riesgo del empleado
        BigDecimal pctArl = BigDecimal.ZERO;
        EmpleadoArlEntity arl = empleado.getArl();
        if (arl != null) {
            pctArl = arl.getPorcentaje();
        }
        nomina.setAporteArl(porcentaje(totalDevengado, pctArl));

        // Prima y cesantías: base = salario proporcional + auxilio de transporte
        BigDecimal baseConAuxilio = salarioProporcional.add(nz(nomina.getAuxilioTransporte()));
        nomina.setProvisionPrima(porcentaje(baseConAuxilio, PCT_PRIMA));
        nomina.setProvisionCesantias(porcentaje(baseConAuxilio, PCT_CESANTIAS));
        // Intereses de cesantías: sobre las cesantías provisionadas
        nomina.setProvisionIntCesantias(
                nomina.getProvisionCesantias()
                        .multiply(PCT_INT_CESANTIAS)
                        .divide(CIEN, 2, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
        );
        // Vacaciones: solo salario (sin auxilio)
        nomina.setProvisionVacaciones(porcentaje(salarioProporcional, PCT_VACACIONES));
    }

    private void calcularSimplificado(NominaEntity nomina, BigDecimal novedadesDeducciones) {
        // Sin EPS, pensión ni aportes. Solo deducciones manuales (préstamos, embargos)
        nomina.setDeduccionSalud(BigDecimal.ZERO);
        nomina.setDeduccionPension(BigDecimal.ZERO);
        nomina.setDeduccionOtros(novedadesDeducciones);
        nomina.setTotalDeducciones(novedadesDeducciones);
        nomina.setNetoPagar(nomina.getTotalDevengado().subtract(novedadesDeducciones).max(BigDecimal.ZERO));

        nomina.setAporteSalud(BigDecimal.ZERO);
        nomina.setAportePension(BigDecimal.ZERO);
        nomina.setAporteArl(BigDecimal.ZERO);
        nomina.setAporteCaja(BigDecimal.ZERO);
        nomina.setAporteIcbf(BigDecimal.ZERO);
        nomina.setAporteSena(BigDecimal.ZERO);
        nomina.setProvisionPrima(BigDecimal.ZERO);
        nomina.setProvisionCesantias(BigDecimal.ZERO);
        nomina.setProvisionIntCesantias(BigDecimal.ZERO);
        nomina.setProvisionVacaciones(BigDecimal.ZERO);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal porcentaje(BigDecimal base, BigDecimal pct) {
        return base.multiply(pct).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private void auditar(String entidad, Long entidadId, String accion, Integer empresaId,
                         String valorAnterior, String valorNuevo, String motivo) {
        Long usuarioId = null;
        try { usuarioId = securityUtils.getUsuarioId(); } catch (Exception ignored) {}
        auditoria.registrar(empresaId, entidad, entidadId, accion,
                usuarioId != null ? usuarioId.intValue() : null, valorAnterior, valorNuevo, motivo);
    }

    // ─── Gating de asistencia (Fase 5) ────────────────────────────────────────

    // Valores por DEFECTO (fallback) si no existe configuración legal vigente en la empresa.
    // La fuente real de verdad es jornada_laboral_config (parametrizable por vigencia, G1/G3).
    private static final BigDecimal HORAS_MES_DEFAULT = new BigDecimal("240"); // 30 días × 8h
    private static final BigDecimal PCT_EXTRA_DIURNA_DEFAULT = new BigDecimal("25");
    private static final BigDecimal PCT_EXTRA_NOCTURNA_DEFAULT = new BigDecimal("75");
    private static final BigDecimal PCT_RECARGO_NOCTURNO_DEFAULT = new BigDecimal("35");
    private static final BigDecimal PCT_RECARGO_DOMINICAL_DEFAULT = new BigDecimal("75");

    /** ¿Este empleado debe pasar por control de asistencia antes de liquidar? */
    private boolean requiereAsistencia(NominaConfigEntity config, EmpleadoEntity empleado) {
        String modo = config.getModoLiquidacion();
        if ("CON_ASISTENCIA_OBLIGATORIA".equals(modo)) return true;
        if ("MIXTA".equals(modo)) return Boolean.TRUE.equals(empleado.getRequiereControlAsistencia());
        return false; // SIN_ASISTENCIA
    }

    /** La asistencia del período está aprobada, o existe autorización excepcional activa. */
    private boolean asistenciaAprobadaOAutorizada(Integer empresaId, PeriodoNominaEntity periodo, EmpleadoEntity empleado) {
        // 1) Autorización excepcional activa: permite liquidar aunque falte asistencia.
        if (autorizacionRepo.existsByEmpresaIdAndEmpleadoIdAndPeriodoNominaIdAndEstado(
                empresaId, empleado.getId(), periodo.getId(), "ACTIVA"))
            return true;

        // 2) Asistencia por PROYECTO/FRENTE: si hay pendientes en el período → bloquea;
        //    si está aprobada/enviada a nómina → OK. (Aditivo: no afecta empresas por turnos.)
        if (frenteQueryRepo.tieneFrentePendiente(empresaId, empleado.getId(),
                periodo.getFechaInicio(), periodo.getFechaFin()))
            return false;
        if (frenteQueryRepo.tieneFrenteConsolidada(empresaId, empleado.getId(),
                periodo.getFechaInicio(), periodo.getFechaFin()))
            return true;

        // 3) Asistencia por TURNOS: período de asistencia aprobado que cubre el período.
        return periodoAsistenciaRepo.findByEmpresaIdOrderByFechaInicioDesc(empresaId).stream()
                .anyMatch(pa ->
                        ("APROBADO".equals(pa.getEstado()) || "ENVIADO_A_NOMINA".equals(pa.getEstado()))
                        && !pa.getFechaInicio().isAfter(periodo.getFechaInicio())
                        && !pa.getFechaFin().isBefore(periodo.getFechaFin()));
    }

    /**
     * Convierte las novedades de asistencia aprobadas (staging) en novedades de nómina
     * valorizadas y las adjunta a la nómina. Idempotente: reemplaza las de origen ASISTENCIA.
     */
    private void consumirNovedadesAsistencia(NominaEntity nomina, PeriodoNominaEntity periodo,
                                             EmpleadoEntity empleado, Integer empresaId) {
        nomina.getNovedades().removeIf(n -> "ASISTENCIA".equals(n.getOrigen())
                || "PROYECTO_FRENTE".equals(n.getOrigen()));

        // Recargos/horas extra valorizados según la configuración legal VIGENTE en el
        // período (Ley 2101/2466), no con factores quemados. Se resuelve una vez por período.
        com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto cfg =
                laboralConfigService.vigente(empresaId, periodo.getFechaFin());

        // Se emparejan por FECHA trabajada dentro del rango del período (no por el FK de período,
        // que pudo quedar anclado a otro período que cubre la misma fecha). Al consumirlas se
        // "re-anclan" a este período para que el re-liquidar sea idempotente y no haya doble pago.
        var staged = asistenciaNovedadRepo.findConsumiblesParaPeriodo(
                empresaId, empleado.getId(), periodo.getId(),
                periodo.getFechaInicio(), periodo.getFechaFin());
        for (var s : staged) {
            NominaNovedadEntity nv = construirNovedadDesdeStaged(s, empleado.getSalarioBase(), cfg);
            if (nv == null) continue;
            nv.setNomina(nomina);
            nomina.getNovedades().add(nv);
            s.setPeriodoNomina(periodo);
            s.setEstado("ENVIADA_A_NOMINA");
            asistenciaNovedadRepo.save(s);
        }
    }

    private NominaNovedadEntity construirNovedadDesdeStaged(
            com.cloud_technological.aura_pos.entity.AsistenciaNovedadNominaEntity s, BigDecimal salarioBase,
            com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto cfg) {

        BigDecimal cantidad = s.getCantidad() != null ? s.getCantidad() : BigDecimal.ZERO;
        if (cantidad.compareTo(BigDecimal.ZERO) <= 0) return null;

        // Base horaria según horas_mensuales_base vigente (Ley 2101: 210 con 42h/sem), fallback 240.
        BigDecimal horasMes = cfg != null && cfg.getHorasMensualesBase() != null
                && cfg.getHorasMensualesBase().signum() > 0
                ? cfg.getHorasMensualesBase() : HORAS_MES_DEFAULT;
        BigDecimal valorHora = salarioBase.divide(horasMes, 6, RoundingMode.HALF_UP);
        BigDecimal valorDia = salarioBase.divide(TREINTA, 6, RoundingMode.HALF_UP);
        BigDecimal valorMinuto = valorHora.divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP);

        // Factores derivados de los % configurados. Horas EXTRA se pagan completas
        // (base + recargo → 1 + pct/100); los RECARGOS solo el sobrecargo (pct/100),
        // porque la jornada base ya se paga por DÍA.
        BigDecimal fExtraDiurna = factorExtra(cfg != null ? cfg.getRecargoExtraDiurna() : null, PCT_EXTRA_DIURNA_DEFAULT);
        BigDecimal fExtraNocturna = factorExtra(cfg != null ? cfg.getRecargoExtraNocturna() : null, PCT_EXTRA_NOCTURNA_DEFAULT);
        BigDecimal fRecargoNoct = factorRecargo(cfg != null ? cfg.getRecargoNocturno() : null, PCT_RECARGO_NOCTURNO_DEFAULT);
        BigDecimal fRecargoDom = factorRecargo(cfg != null ? cfg.getRecargoDominicalFestivo() : null, PCT_RECARGO_DOMINICAL_DEFAULT);

        BigDecimal valorUnitario;
        boolean deduccion;
        switch (s.getTipoNovedad()) {
            case "HORA_EXTRA_DIURNA" -> { valorUnitario = valorHora.multiply(fExtraDiurna); deduccion = false; }
            case "HORA_EXTRA_NOCTURNA" -> { valorUnitario = valorHora.multiply(fExtraNocturna); deduccion = false; }
            case "RECARGO_NOCTURNO" -> { valorUnitario = valorHora.multiply(fRecargoNoct); deduccion = false; }
            case "RECARGO_DOMINICAL_FESTIVO" -> { valorUnitario = valorHora.multiply(fRecargoDom); deduccion = false; }
            case "HORA_EXTRA_DOMINICAL_FESTIVA" -> {
                // Hora extra en domingo/festivo: se paga completa (extra) + recargo dom/fest.
                valorUnitario = valorHora.multiply(fExtraDiurna.add(fRecargoDom)); deduccion = false;
            }
            case "AUSENCIA_NO_JUSTIFICADA" -> { valorUnitario = valorDia; deduccion = true; }
            case "LLEGADA_TARDE_DESCONTADA" -> { valorUnitario = valorMinuto; deduccion = true; }
            default -> { return null; }
        }

        BigDecimal valorTotal = valorUnitario.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        NominaNovedadEntity nv = new NominaNovedadEntity();
        nv.setTipo(s.getTipoNovedad());
        nv.setDescripcion("Generada desde asistencia");
        nv.setCantidad(cantidad);
        nv.setValorUnitario(valorUnitario.setScale(2, RoundingMode.HALF_UP));
        nv.setValorTotal(valorTotal);
        nv.setEsDeduccion(deduccion);
        nv.setNaturaleza(deduccion ? "DEDUCCION" : "DEVENGADO");
        nv.setOrigen(s.getOrigen() != null ? s.getOrigen() : "ASISTENCIA");
        nv.setEstado("APLICADA");
        nv.setRequiereAprobacion(false);
        return nv;
    }

    /** Factor para hora EXTRA (se paga completa): 1 + pct/100. Usa el default si no hay config. */
    private BigDecimal factorExtra(BigDecimal pctConfig, BigDecimal pctDefault) {
        BigDecimal pct = (pctConfig != null) ? pctConfig : pctDefault;
        return BigDecimal.ONE.add(pct.divide(CIEN, 6, RoundingMode.HALF_UP));
    }

    /** Factor para RECARGO (solo el sobrecargo, la base ya se paga por día): pct/100. */
    private BigDecimal factorRecargo(BigDecimal pctConfig, BigDecimal pctDefault) {
        BigDecimal pct = (pctConfig != null) ? pctConfig : pctDefault;
        return pct.divide(CIEN, 6, RoundingMode.HALF_UP);
    }

    /**
     * Días a liquidar tomados del período (no siempre 30). Aplica la convención
     * colombiana base-30 (mes = 30 días, quincena = 15) y recorta por la fecha de
     * ingreso/retiro del empleado cuando caen dentro del período.
     */
    private int diasLiquidacion(PeriodoNominaEntity periodo, EmpleadoEntity empleado, Integer empresaId) {
        LocalDate inicio = periodo.getFechaInicio();
        LocalDate fin = periodo.getFechaFin();

        if (empleado.getFechaIngreso() != null && empleado.getFechaIngreso().isAfter(inicio))
            inicio = empleado.getFechaIngreso();
        if (empleado.getFechaRetiro() != null && empleado.getFechaRetiro().isBefore(fin))
            fin = empleado.getFechaRetiro();

        if (fin.isBefore(inicio)) return 0;

        // Pago por asistencia de frente: los jornaleros (requiere_control_asistencia = true)
        // se pagan por los DÍAS efectivamente trabajados en el frente (las horas solo son
        // control). Fallback seguro: si el empleado marcado no tiene asistencia de frente
        // consolidada en el período, se paga el período completo como siempre.
        if (Boolean.TRUE.equals(empleado.getRequiereControlAsistencia())
                && frenteQueryRepo.tieneFrenteConsolidada(empresaId, empleado.getId(), inicio, fin)) {
            return frenteQueryRepo.contarDiasTrabajadosFrente(empresaId, empleado.getId(), inicio, fin);
        }
        return diasBase30(inicio, fin);
    }

    /**
     * Cuenta días entre dos fechas (ambas inclusive) usando el criterio base-30:
     * el día 31 y el último día del mes se tratan como día 30, de modo que un mes
     * completo son 30 días y cada quincena son 15.
     */
    private int diasBase30(LocalDate inicio, LocalDate fin) {
        int d1 = inicio.getDayOfMonth();
        int d2 = fin.getDayOfMonth();

        if (d1 == 31) d1 = 30;
        boolean finDeMes = fin.getDayOfMonth() == fin.lengthOfMonth();
        if (d2 == 31 || finDeMes) d2 = 30;

        int meses = (fin.getYear() - inicio.getYear()) * 12
                + (fin.getMonthValue() - inicio.getMonthValue());
        return meses * 30 + (d2 - d1) + 1;
    }

    private boolean esDeduccion(String tipo) {
        return switch (tipo) {
            case "PRESTAMO", "EMBARGO", "OTRO_DESCUENTO" -> true;
            default -> false;
        };
    }

    private NominaConfigEntity configuracionPorDefecto(Integer empresaId) {
        NominaConfigEntity config = new NominaConfigEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        config.setEmpresa(empresa);
        return config;
    }

    private NominaDto toDto(NominaEntity entity) {
        NominaDto dto = new NominaDto();
        dto.setId(entity.getId());
        dto.setPeriodoId(entity.getPeriodo().getId());
        dto.setPeriodoFechaInicio(entity.getPeriodo().getFechaInicio());
        dto.setPeriodoFechaFin(entity.getPeriodo().getFechaFin());
        dto.setEmpleadoId(entity.getEmpleado().getId());
        dto.setEmpleadoNombre(entity.getEmpleado().getNombres() + " " + entity.getEmpleado().getApellidos());
        dto.setEmpleadoDocumento(entity.getEmpleado().getNumeroDocumento());
        dto.setCargo(entity.getEmpleado().getCargo());
        dto.setTipoContrato(entity.getEmpleado().getTipoContrato());
        dto.setBanco(entity.getEmpleado().getBanco());
        dto.setNumeroCuenta(entity.getEmpleado().getNumeroCuenta());
        dto.setTipoCuenta(entity.getEmpleado().getTipoCuenta());
        dto.setSalarioBase(entity.getSalarioBase());
        dto.setDiasTrabajados(entity.getDiasTrabajados());
        dto.setSalarioProporcional(entity.getSalarioProporcional());
        dto.setAuxilioTransporte(entity.getAuxilioTransporte());
        dto.setTotalNovedadesDev(entity.getTotalNovedadesDev());
        dto.setTotalDevengado(entity.getTotalDevengado());
        dto.setDeduccionSalud(entity.getDeduccionSalud());
        dto.setDeduccionPension(entity.getDeduccionPension());
        dto.setDeduccionOtros(entity.getDeduccionOtros());
        dto.setTotalDeducciones(entity.getTotalDeducciones());
        dto.setNetoPagar(entity.getNetoPagar());
        dto.setAporteSalud(entity.getAporteSalud());
        dto.setAportePension(entity.getAportePension());
        dto.setAporteArl(entity.getAporteArl());
        dto.setAporteCaja(entity.getAporteCaja());
        dto.setAporteIcbf(entity.getAporteIcbf());
        dto.setAporteSena(entity.getAporteSena());
        dto.setProvisionPrima(entity.getProvisionPrima());
        dto.setProvisionCesantias(entity.getProvisionCesantias());
        dto.setProvisionIntCesantias(entity.getProvisionIntCesantias());
        dto.setProvisionVacaciones(entity.getProvisionVacaciones());
        dto.setEstado(entity.getEstado());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setNovedades(entity.getNovedades().stream().map(n -> {
            NominaNovedadDto ndto = new NominaNovedadDto();
            ndto.setId(n.getId());
            ndto.setTipo(n.getTipo());
            ndto.setDescripcion(n.getDescripcion());
            ndto.setCantidad(n.getCantidad());
            ndto.setValorUnitario(n.getValorUnitario());
            ndto.setValorTotal(n.getValorTotal());
            ndto.setEsDeduccion(n.getEsDeduccion());
            return ndto;
        }).collect(Collectors.toList()));
        return dto;
    }
}

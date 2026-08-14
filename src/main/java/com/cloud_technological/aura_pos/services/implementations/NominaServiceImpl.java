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
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
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
import com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.services.nomina.BasesLiquidacion;
import com.cloud_technological.aura_pos.services.nomina.MotorLiquidacion;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@lombok.extern.slf4j.Slf4j
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
    private com.cloud_technological.aura_pos.services.nomina.VacacionesService vacacionesService;

    @Autowired
    private com.cloud_technological.aura_pos.services.nomina.CalculadoraIncapacidad calculadoraIncapacidad;

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

    @Autowired
    private ContratoLaboralService contratoLaboralService;

    @Autowired
    private MotorLiquidacion motorLiquidacion;

    @Autowired
    private NominaElectronicaService nominaElectronicaService;

    @Autowired
    private NominaDetalleJPARepository nominaDetalleRepo;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal TREINTA = new BigDecimal("30");

    // Las tarifas ya NO viven aquí: están en concepto_nomina (V104), con
    // vigencia por fechas. Un cambio de ley es una fila, no un despliegue.
    // Se eliminaron: PCT_PRIMA, PCT_CESANTIAS, PCT_INT_CESANTIAS, PCT_VACACIONES.

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Override
    public PageImpl<NominaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return nominaQueryRepo.listar(pageable, empresaId);
    }

    @Override
    public java.util.List<com.cloud_technological.aura_pos.dto.nomina.nomina.HistorialPagoDto> historialPagos(
            Long empleadoId, Integer empresaId) {
        return nominaQueryRepo.historialPagos(empleadoId, empresaId);
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
            // *Resuelto(): prefieren `tercero`, caen a las columnas legacy de
            // `empleados` mientras la reconciliación de V99 no esté completa.
            e.setEmpleadoNombre(n.getEmpleado().getNombreCompletoResuelto());
            e.setEmpleadoDocumento(n.getEmpleado().getNumeroDocumentoResuelto());
            e.setCargo(n.getEmpleado().getCargo());
            e.setBanco(n.getEmpleado().getBancoResuelto());
            e.setNumeroCuenta(n.getEmpleado().getNumeroCuentaResuelto());
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

    /**
     * @deprecated Puente hacia {@link #liquidarContrato}. Resuelve el contrato
     *             principal del empleado. Con multi-vínculo esto es ambiguo:
     *             el llamador debería indicar QUÉ contrato liquida.
     */
    @Override
    @Deprecated
    @Transactional
    public NominaDto liquidar(Long periodoId, Long empleadoId, Integer empresaId) {
        ContratoLaboralEntity contrato = contratoLaboralService.principalDe(empleadoId);
        return liquidarContrato(periodoId, contrato.getId(), empresaId);
    }

    /**
     * Liquida un contrato en un período (V103).
     *
     * <p>El contrato es el eje, no el empleado: una persona con dos contratos
     * activos genera dos nóminas por período.
     */
    @Override
    @Transactional
    public NominaDto liquidarContrato(Long periodoId, Long contratoId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        if ("ANULADO".equals(periodo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período está anulado");

        ContratoLaboralEntity contrato = contratoLaboralService.obtener(contratoId, empresaId);
        EmpleadoEntity empleado = contrato.getEmpleado();

        if (!"ACTIVO".equals(contrato.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El contrato está en estado " + contrato.getEstado());

        // B-09 — la prestación de servicios NO se liquida como nómina laboral: sin
        // relación laboral no hay deducciones de SS, aportes, prestaciones ni
        // auxilio. Se paga por cuenta de cobro/compra con retefuente por servicios.
        if ("PRESTACION_SERVICIOS".equals(contrato.getTipoContrato()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un contrato de prestación de servicios no se liquida por nómina. "
                    + "Regístralo como cuenta de cobro / compra (retención por servicios).");

        // El contrato debe solaparse con el período. Un contrato que empieza
        // después del corte, o que terminó antes, no se liquida aquí.
        if (contrato.getFechaInicio().isAfter(periodo.getFechaFin())
            || (contrato.getFechaFin() != null && contrato.getFechaFin().isBefore(periodo.getFechaInicio()))) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El contrato no está vigente en el período");
        }

        NominaEntity nomina = nominaRepo.findByContratoIdAndPeriodoId(contratoId, periodoId)
                .orElse(null);

        if (nomina != null) {
            if ("APROBADO".equals(nomina.getEstado()) || "PAGADO".equals(nomina.getEstado()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La nómina ya está aprobada o pagada");
        } else {
            nomina = new NominaEntity();
            EmpresaEntity empresa = new EmpresaEntity();
            empresa.setId(empresaId);
            nomina.setEmpresa(empresa);
            nomina.setPeriodo(periodo);
            nomina.setEmpleado(empleado);
            nomina.setContrato(contrato);
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

        // V103: se iteran CONTRATOS vigentes en el período, no empleados activos.
        //
        // Dos diferencias con lo anterior:
        //  · Un empleado con dos contratos genera dos nóminas.
        //  · `findVigentesEnPeriodo` incluye a quien ingresó o se retiró a mitad
        //    de mes (fechas que se solapan con el período). Antes se iteraban
        //    empleados con activo=true, que dejaba fuera a los retirados a mitad
        //    de mes — a los que sí hay que liquidarles sus días.
        List<ContratoLaboralEntity> contratos = contratoLaboralService.vigentesEnPeriodo(
                empresaId, periodo.getFechaInicio(), periodo.getFechaFin());

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        for (ContratoLaboralEntity contrato : contratos) {
            EmpleadoEntity empleado = contrato.getEmpleado();

            NominaEntity nomina = nominaRepo.findByContratoIdAndPeriodoId(contrato.getId(), periodoId)
                    .orElse(null);

            if (nomina != null) {
                // Ya aprobada o pagada: se omite del lote, no se recalcula.
                if ("APROBADO".equals(nomina.getEstado()) || "PAGADO".equals(nomina.getEstado())) continue;
            } else {
                nomina = new NominaEntity();
                nomina.setEmpresa(empresa);
                nomina.setPeriodo(periodo);
                nomina.setEmpleado(empleado);
                nomina.setContrato(contrato);
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

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracionPorDefecto(empresaId));

        NominaNovedadEntity novedad = new NominaNovedadEntity();
        novedad.setNomina(nomina);
        novedad.setTipo(dto.getTipo());
        novedad.setDescripcion(dto.getDescripcion());
        novedad.setCantidad(dto.getCantidad() != null ? dto.getCantidad() : BigDecimal.ONE);
        novedad.setValorUnitario(nz(dto.getValorUnitario()));
        novedad.setValorTotal(novedad.getCantidad().multiply(nz(dto.getValorUnitario())).setScale(2, RoundingMode.HALF_UP));
        novedad.setEsDeduccion(esDeduccion(dto.getTipo()));

        // F0 — conciliación de días. Fechas del hecho + cuántos días de salario
        // descuenta (si es una ausencia).
        novedad.setFechaInicio(dto.getFechaInicio());
        novedad.setFechaFin(dto.getFechaFin());
        novedad.setSubtipo(dto.getSubtipo());
        novedad.setNumeroAutorizacion(dto.getNumeroAutorizacion());
        boolean ausencia = afectaDiasSalario(dto.getTipo());
        novedad.setAfectaDiasSalario(ausencia);
        if (ausencia) {
            Integer dias = diasNovedad(dto.getFechaInicio(), dto.getFechaFin(), dto.getCantidad());
            novedad.setDias(dias);
            validarDiasAusencia(nomina, dias);
            if ("VACACIONES".equals(dto.getTipo()) && nomina.getEmpleado() != null) {
                validarSaldoVacaciones(nomina.getEmpleado().getId(), empresaId, dias);
            }
            // F1 — incapacidades y maternidad: el valor se calcula solo si no vino manual.
            if (esIncapacidadOMaternidad(dto.getTipo()) && nz(dto.getValorUnitario()).signum() == 0) {
                autocalcularIncapacidad(novedad, nomina, config, dias);
            }
        }

        nomina.getNovedades().add(novedad);

        // Recalcular con la nueva novedad
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

        // Nómina electrónica (Fase 5): se PREPARA el documento (reserva el
        // consecutivo, queda en PENDIENTE). El envío lo hace el job — nunca
        // dentro de este request: si Factus está lento, la aprobación no puede
        // quedarse esperando.
        //
        // prepararDocumento() es idempotente: si ya existe, lo devuelve.
        try {
            nominaElectronicaService.prepararDocumento(nomina.getId(), empresaId);
        } catch (RuntimeException e) {
            // No romper la aprobación por esto: el documento se puede preparar
            // después. Pero dejar rastro — una nómina aprobada sin documento
            // electrónico es un incumplimiento con la DIAN.
            log.error("No se pudo preparar la nómina electrónica de la nómina {}: {}",
                    nomina.getId(), e.getMessage());
        }

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

    /**
     * Calcula una nómina delegando en {@link MotorLiquidacion} (Fases 0 y 3).
     *
     * <p>Reemplaza al cálculo cableado que vivía aquí. Diferencias:
     * <ul>
     *   <li>Las tarifas salen de {@code concepto_nomina}, no de constantes.</li>
     *   <li>El IBC ya NO incluye el auxilio de transporte (bug B1).</li>
     *   <li>Se aplican tope de 25 SMMLV, fondo de solidaridad y exoneración 1607.</li>
     *   <li>Se emite {@code nomina_detalle} con traza.</li>
     * </ul>
     *
     * <p>Los campos agregados de {@code nomina} se mantienen como
     * denormalización de lectura, calculados desde el detalle. Deben cuadrar
     * exactamente — es el invariante que los tests tienen que verificar.
     */
    private void calcular(NominaEntity nomina, EmpleadoEntity empleado, NominaConfigEntity config) {
        ContratoLaboralEntity contrato = nomina.getContrato();
        if (contrato == null) {
            // Puente: nóminas creadas antes de V103. Tras el cierre (V118) esto
            // no puede pasar — contrato_id es NOT NULL.
            contrato = contratoLaboralService.principalDe(empleado.getId());
            nomina.setContrato(contrato);
        }

        int dias = nomina.getDiasTrabajados() != null ? nomina.getDiasTrabajados() : 30;
        nomina.setDiasTrabajados(dias);
        nomina.setSalarioBase(contrato.getSalarioBase());

        LocalDate fecha = nomina.getPeriodo() != null && nomina.getPeriodo().getFechaFin() != null
                ? nomina.getPeriodo().getFechaFin()
                : LocalDate.now();

        MotorLiquidacion.ResultadoLiquidacion r =
                motorLiquidacion.liquidar(nomina, contrato, config, fecha);

        aplicarResultado(nomina, r);
        persistirDetalle(nomina, r);
    }

    /** Vuelca los totales del motor en los campos agregados de la nómina. */
    private void aplicarResultado(NominaEntity nomina, MotorLiquidacion.ResultadoLiquidacion r) {
        BasesLiquidacion b = r.bases();

        nomina.setSalarioProporcional(b.getSalarioProporcional());
        nomina.setAuxilioTransporte(b.getAuxilioTransporte());
        nomina.setTotalNovedadesDev(b.getNovedadesIbc().add(b.getNovedadesNoIbc()));
        nomina.setTotalDevengado(r.totalDevengado());
        // IBC real (sin auxilio ni no salariales) para PILA — no es total_devengado.
        nomina.setIbc(b.getBaseIbc());

        // Deducciones: por concepto, no por campo cableado.
        nomina.setDeduccionSalud(r.valorDeConcepto("DED_SALUD"));
        nomina.setDeduccionPension(r.valorDeConcepto("DED_PENSION"));
        // `deduccionOtros` absorbe todo lo que no es salud ni pensión: fondo de
        // solidaridad, retefuente, préstamos, embargos. El desglose real está
        // en nomina_detalle; este campo es solo el agregado de lectura.
        nomina.setDeduccionOtros(
                r.totalDeducciones()
                 .subtract(r.valorDeConcepto("DED_SALUD"))
                 .subtract(r.valorDeConcepto("DED_PENSION")));
        nomina.setTotalDeducciones(r.totalDeducciones());
        nomina.setNetoPagar(r.netoPagar());

        nomina.setAporteSalud(r.valorDeConcepto("APO_SALUD"));
        nomina.setAportePension(r.valorDeConcepto("APO_PENSION"));
        nomina.setAporteArl(r.valorDeConcepto("APO_ARL"));
        nomina.setAporteCaja(r.valorDeConcepto("APO_CCF"));
        nomina.setAporteIcbf(r.valorDeConcepto("APO_ICBF"));
        nomina.setAporteSena(r.valorDeConcepto("APO_SENA"));

        nomina.setProvisionPrima(r.valorDeConcepto("PRO_PRIMA"));
        nomina.setProvisionCesantias(r.valorDeConcepto("PRO_CESANTIAS"));
        nomina.setProvisionIntCesantias(r.valorDeConcepto("PRO_INT_CES"));
        nomina.setProvisionVacaciones(r.valorDeConcepto("PRO_VACAC"));
    }

    /**
     * Reescribe el detalle de la nómina.
     *
     * <p>Borra y reinserta: una reliquidación debe producir el detalle desde
     * cero, no acumularlo sobre el anterior.
     */
    private void persistirDetalle(NominaEntity nomina, MotorLiquidacion.ResultadoLiquidacion r) {
        if (nomina.getId() != null) {
            nominaDetalleRepo.deleteByNominaId(nomina.getId());
        }
        // La nómina debe existir antes de colgarle detalle.
        NominaEntity guardada = nomina.getId() != null ? nomina : nominaRepo.save(nomina);
        r.lineas().forEach(l -> nominaDetalleRepo.save(l.aEntidad(guardada)));
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
    /**
     * B-06 — salario operativo de la nómina: SIEMPRE del contrato, nunca del campo
     * caché {@code empleado.salarioBase}, que el alta nueva deja en 0 ("el real va
     * en el contrato"). Leerlo del empleado hacía que incapacidades y novedades de
     * asistencia se valorizaran sobre 0. Fallback: nomina → empleado, por si el
     * contrato aún no trae el valor.
     */
    private BigDecimal salarioDelContrato(NominaEntity nomina, EmpleadoEntity empleado) {
        ContratoLaboralEntity c = nomina.getContrato();
        if (c != null && c.getSalarioBase() != null && c.getSalarioBase().signum() > 0)
            return c.getSalarioBase();
        if (nomina.getSalarioBase() != null && nomina.getSalarioBase().signum() > 0)
            return nomina.getSalarioBase();
        return empleado != null && empleado.getSalarioBase() != null
                ? empleado.getSalarioBase() : BigDecimal.ZERO;
    }

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
            NominaNovedadEntity nv = construirNovedadDesdeStaged(s, salarioDelContrato(nomina, empleado), cfg);
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

    /**
     * F0 — ¿esta novedad es una ausencia que descuenta días de salario?
     *
     * <p>Incapacidad, licencia no remunerada, vacaciones y licencia de maternidad
     * no se pagan como salario ordinario en esos días (tienen su pago aparte o
     * ninguno). La licencia remunerada NO: se paga como salario.
     */
    private boolean afectaDiasSalario(String tipo) {
        return switch (tipo) {
            case "INCAPACIDAD", "LICENCIA_NO_REMUNERADA", "VACACIONES", "LICENCIA_MATERNIDAD" -> true;
            default -> false;
        };
    }

    /**
     * Días de una novedad de ausencia. Preferimos el rango de fechas (inclusive);
     * si no vino, caemos a {@code cantidad} (a veces los días se digitan ahí).
     */
    private Integer diasNovedad(java.time.LocalDate inicio, java.time.LocalDate fin, BigDecimal cantidad) {
        if (inicio != null && fin != null && !fin.isBefore(inicio)) {
            return (int) (java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1);
        }
        if (cantidad != null && cantidad.signum() > 0) {
            return cantidad.setScale(0, RoundingMode.HALF_UP).intValue();
        }
        return 0;
    }

    /**
     * Los días de ausencia acumulados no pueden superar los del período. Un mes de
     * 30 días no admite 25 de incapacidad + 10 de vacaciones.
     */
    private void validarDiasAusencia(NominaEntity nomina, Integer diasNuevos) {
        int diasPeriodo = diasDelPeriodo(nomina);
        int yaAusentes = nomina.getNovedades().stream()
                .filter(n -> Boolean.TRUE.equals(n.getAfectaDiasSalario()))
                .mapToInt(n -> n.getDias() != null ? n.getDias() : 0)
                .sum();
        int total = yaAusentes + (diasNuevos != null ? diasNuevos : 0);
        if (total > diasPeriodo) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los días de ausencia (" + total + ") superan los días del período ("
                            + diasPeriodo + "). Revisa las fechas de la novedad.");
        }
    }

    private boolean esIncapacidadOMaternidad(String tipo) {
        return "INCAPACIDAD".equals(tipo) || "LICENCIA_MATERNIDAD".equals(tipo);
    }

    /**
     * F1 — calcula el valor de una incapacidad/maternidad y lo vuelca en la
     * novedad (cantidad = días, valor unitario = valor/día, total = lo que recibe
     * el trabajador). La descripción deja la traza de quién paga.
     */
    private void autocalcularIncapacidad(NominaNovedadEntity novedad, NominaEntity nomina,
                                         NominaConfigEntity config, Integer dias) {
        if (nomina.getEmpleado() == null || dias == null || dias <= 0) return;
        // B-06 — salario del contrato, no del campo caché del empleado (puede ser 0).
        BigDecimal salario = salarioDelContrato(nomina, nomina.getEmpleado());
        String subtipo = "LICENCIA_MATERNIDAD".equals(novedad.getTipo())
                ? "MATERNIDAD"
                : novedad.getSubtipo();

        var r = calculadoraIncapacidad.calcular(subtipo, dias, salario, config.getSmmlv());
        novedad.setCantidad(BigDecimal.valueOf(dias));
        novedad.setValorUnitario(r.valorDia());
        novedad.setValorTotal(r.total());
        novedad.setConstituyeIbc(false); // reemplaza salario; no infla el IBC aquí
        if (novedad.getDescripcion() == null || novedad.getDescripcion().isBlank()) {
            novedad.setDescripcion(r.descripcion());
        }
    }

    /**
     * F3 — no se pueden tomar más vacaciones de las disponibles, salvo que la
     * empresa permita anticipadas.
     */
    private void validarSaldoVacaciones(Long empleadoId, Integer empresaId, Integer diasSolicitados) {
        var saldo = vacacionesService.saldo(empleadoId, empresaId);
        if (Boolean.TRUE.equals(saldo.getPermiteAnticipadas())) return;
        BigDecimal solicitados = BigDecimal.valueOf(diasSolicitados != null ? diasSolicitados : 0);
        if (solicitados.compareTo(saldo.getDiasDisponibles()) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El empleado solo tiene " + saldo.getDiasDisponibles()
                            + " días de vacaciones disponibles y se están tomando " + solicitados
                            + ". Habilita vacaciones anticipadas en la configuración si lo permites.");
        }
    }

    /** Días del período en convención comercial (tope 30 mensual, ~15 quincenal). */
    private int diasDelPeriodo(NominaEntity nomina) {
        if (nomina.getPeriodo() != null
                && nomina.getPeriodo().getFechaInicio() != null
                && nomina.getPeriodo().getFechaFin() != null) {
            long cal = java.time.temporal.ChronoUnit.DAYS.between(
                    nomina.getPeriodo().getFechaInicio(), nomina.getPeriodo().getFechaFin()) + 1;
            return (int) Math.min(cal, 30);
        }
        return nomina.getDiasTrabajados() != null ? nomina.getDiasTrabajados() : 30;
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
        // *Resuelto(): prefieren `tercero`, caen a legacy mientras V99/V100
        // no cierren. Ver EmpleadoEntity.
        dto.setEmpleadoNombre(entity.getEmpleado().getNombreCompletoResuelto());
        dto.setEmpleadoDocumento(entity.getEmpleado().getNumeroDocumentoResuelto());
        dto.setCargo(entity.getEmpleado().getCargo());
        dto.setTipoContrato(entity.getEmpleado().getTipoContrato());
        dto.setBanco(entity.getEmpleado().getBancoResuelto());
        dto.setNumeroCuenta(entity.getEmpleado().getNumeroCuentaResuelto());
        dto.setTipoCuenta(entity.getEmpleado().getTipoCuentaResuelto());
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
            ndto.setDias(n.getDias());
            ndto.setAfectaDiasSalario(n.getAfectaDiasSalario());
            ndto.setSubtipo(n.getSubtipo());
            ndto.setFechaInicio(n.getFechaInicio());
            ndto.setFechaFin(n.getFechaFin());
            return ndto;
        }).collect(Collectors.toList()));
        return dto;
    }
}

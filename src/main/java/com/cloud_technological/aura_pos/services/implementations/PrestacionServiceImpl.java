package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.CrearPrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.PrestacionDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.LotePrestacionDto;
import com.cloud_technological.aura_pos.entity.LiquidacionPrestacionEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.event.OperacionContabilizableEvent;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.LiquidacionPrestacionJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.services.PrestacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class PrestacionServiceImpl implements PrestacionService {

    @Autowired private LiquidacionPrestacionJPARepository prestacionRepo;
    @Autowired private EmpleadoJPARepository empleadoRepo;
    @Autowired private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private NominaConfigJPARepository configRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.nomina.ContratoLaboralJPARepository contratoRepo;
    @Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Autowired private com.cloud_technological.aura_pos.services.nomina.VacacionesService vacacionesService;
    @Autowired private com.cloud_technological.aura_pos.services.nomina.PromedioSalarialService promedioService;
    @Autowired private com.cloud_technological.aura_pos.repositories.nomina.ContratoSalarioHistorialJPARepository salarioHistorialRepo;

    private static final BigDecimal D360 = new BigDecimal("360");
    private static final BigDecimal D720 = new BigDecimal("720");
    private static final BigDecimal DOS = new BigDecimal("2");
    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final BigDecimal DIEZ = new BigDecimal("10");
    private static final BigDecimal PCT_INT_CESANTIAS = new BigDecimal("0.12");
    private static final List<String> TIPOS_VALIDOS =
            List.of("PRIMA", "VACACIONES", "CESANTIAS", "INTERESES_CESANTIAS");

    /**
     * B-09/B-10 — tipos de contrato SIN prestaciones sociales laborales: prestación
     * de servicios (no hay relación laboral) y aprendizaje SENA (recibe apoyo de
     * sostenimiento, no salario; no causa cesantías, prima ni vacaciones).
     */
    private static final java.util.Set<String> SIN_PRESTACIONES_LABORALES =
            java.util.Set.of("PRESTACION_SERVICIOS", "APRENDIZAJE");

    private boolean sinPrestacionesLaborales(ContratoLaboralEntity c) {
        return c != null && SIN_PRESTACIONES_LABORALES.contains(c.getTipoContrato());
    }

    private ContratoLaboralEntity contratoActivo(Long empleadoId) {
        return contratoRepo.findActivosByEmpleado(empleadoId).stream().findFirst().orElse(null);
    }

    @Override
    public List<PrestacionDto> listar(Integer empresaId) {
        return prestacionRepo.findByEmpresaIdOrderByCreatedAtDesc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Lotes (V122): el listado agrupa; el detalle abre el desglose ─────────

    @Override
    public List<LotePrestacionDto> listarLotes(Integer empresaId) {
        java.util.Map<String, List<LiquidacionPrestacionEntity>> porLote =
                prestacionRepo.findByEmpresaIdOrderByCreatedAtDesc(empresaId).stream()
                        .filter(e -> e.getLote() != null)
                        .collect(Collectors.groupingBy(LiquidacionPrestacionEntity::getLote,
                                java.util.LinkedHashMap::new, Collectors.toList()));
        List<LotePrestacionDto> out = new java.util.ArrayList<>();
        porLote.forEach((lote, filas) -> out.add(resumenLote(lote, filas)));
        return out;
    }

    @Override
    public List<PrestacionDto> detalleLote(String lote, Integer empresaId) {
        return prestacionRepo.findByEmpresaIdAndLoteOrderByIdAsc(empresaId, lote)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    private LotePrestacionDto resumenLote(String lote, List<LiquidacionPrestacionEntity> filas) {
        LotePrestacionDto d = new LotePrestacionDto();
        d.setLote(lote);
        d.setCantidad(filas.size());
        LiquidacionPrestacionEntity p = filas.get(0);

        // ¿Es un lote MASIVO (varios empleados) o de un solo empleado (individual /
        // definitiva)? En el masivo no tiene sentido mostrar el nombre del primero
        // como si el lote fuera suyo: se muestra "N empleados".
        long empleadosDistintos = filas.stream()
                .map(f -> f.getEmpleado() != null ? f.getEmpleado().getId() : null)
                .filter(java.util.Objects::nonNull)
                .distinct().count();
        boolean masivo = empleadosDistintos > 1;

        if (masivo) {
            d.setEmpleadoNombre(empleadosDistintos + " empleados");
        } else if (p.getEmpleado() != null) {
            d.setEmpleadoId(p.getEmpleado().getId());
            d.setEmpleadoNombre(p.getEmpleado().getNombres() + " " + p.getEmpleado().getApellidos());
            d.setEmpleadoDocumento(p.getEmpleado().getNumeroDocumento());
        }
        boolean definitiva = lote.startsWith("DEF-");
        d.setDefinitiva(definitiva);
        // Masivo: es un solo tipo repetido por empleado (p.ej. PRIMA), no "Varias".
        d.setTipoResumen(definitiva ? "Liquidación definitiva"
                : masivo ? p.getTipo()
                : (filas.size() == 1 ? p.getTipo() : "Varias"));
        d.setFecha(p.getFechaHasta());
        d.setTotal(filas.stream()
                .filter(f -> !"ANULADA".equals(f.getEstado()))
                .map(f -> f.getValor() != null ? f.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        d.setEstado(estadoAgregado(filas));
        return d;
    }

    private String estadoAgregado(List<LiquidacionPrestacionEntity> filas) {
        List<String> vivos = filas.stream().map(LiquidacionPrestacionEntity::getEstado)
                .filter(s -> !"ANULADA".equals(s)).toList();
        if (vivos.isEmpty()) return "ANULADA";
        if (vivos.stream().allMatch("PAGADA"::equals)) return "PAGADA";
        if (vivos.stream().allMatch("PROGRAMADA"::equals)) return "PROGRAMADA";
        if (vivos.stream().allMatch("APROBADA"::equals)) return "APROBADA";
        return "BORRADOR";
    }

    @Override
    @Transactional
    public List<PrestacionDto> aprobarLote(String lote, Integer empresaId) {
        for (LiquidacionPrestacionEntity e : prestacionRepo.findByEmpresaIdAndLoteOrderByIdAsc(empresaId, lote)) {
            if ("BORRADOR".equals(e.getEstado())) {
                e.setEstado("APROBADA");
                e.setUpdatedAt(LocalDateTime.now());
                prestacionRepo.save(e);
            }
        }
        return detalleLote(lote, empresaId);
    }

    @Override
    @Transactional
    public List<PrestacionDto> pagarLote(String lote, PagoNominaDto dto, Integer empresaId) {
        for (LiquidacionPrestacionEntity e : prestacionRepo.findByEmpresaIdAndLoteOrderByIdAsc(empresaId, lote)) {
            if ("APROBADA".equals(e.getEstado())) pagar(e.getId(), dto, empresaId);
        }
        return detalleLote(lote, empresaId);
    }

    @Override
    @Transactional
    public List<PrestacionDto> confirmarPagoLote(String lote, Integer empresaId) {
        for (LiquidacionPrestacionEntity e : prestacionRepo.findByEmpresaIdAndLoteOrderByIdAsc(empresaId, lote)) {
            if ("PROGRAMADA".equals(e.getEstado())) confirmarPago(e.getId(), empresaId);
        }
        return detalleLote(lote, empresaId);
    }

    @Override
    @Transactional
    public List<PrestacionDto> anularLote(String lote, Integer empresaId) {
        List<LiquidacionPrestacionEntity> filas = prestacionRepo.findByEmpresaIdAndLoteOrderByIdAsc(empresaId, lote);
        for (LiquidacionPrestacionEntity e : filas) {
            if (!"PAGADA".equals(e.getEstado())) {
                e.setEstado("ANULADA");
                e.setUpdatedAt(LocalDateTime.now());
                prestacionRepo.save(e);
            }
        }
        // Si era definitiva, se deshace el retiro: reactivar empleado y contrato.
        if (lote.startsWith("DEF-") && !filas.isEmpty()) {
            LiquidacionPrestacionEntity ref = filas.get(0);
            if (ref.getEmpleado() != null) {
                EmpleadoEntity emp = ref.getEmpleado();
                emp.setActivo(Boolean.TRUE);
                emp.setFechaRetiro(null);
                empleadoRepo.save(emp);
            }
            if (ref.getContrato() != null) {
                ContratoLaboralEntity c = ref.getContrato();
                c.setEstado("ACTIVO");
                c.setFechaFin(null);
                c.setCausaRetiro(null);
                contratoRepo.save(c);
            }
        }
        return detalleLote(lote, empresaId);
    }

    @Override
    @Transactional
    public PrestacionDto crear(CrearPrestacionDto dto, Integer empresaId) {
        if (dto.getEmpleadoId() == null || dto.getTipo() == null
                || dto.getFechaDesde() == null || dto.getFechaHasta() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado, tipo y fechas son obligatorios");
        if (!TIPOS_VALIDOS.contains(dto.getTipo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo inválido. Use: " + TIPOS_VALIDOS);
        if (dto.getFechaHasta().isBefore(dto.getFechaDesde()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha hasta no puede ser anterior a la desde");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        // B-09/B-10 — servicios y aprendiz no causan prestaciones sociales.
        if (sinPrestacionesLaborales(contratoActivo(empleado.getId())))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El contrato (prestación de servicios / aprendizaje) no causa prestaciones sociales laborales");

        // F3 — vacaciones: no se pueden tomar más días hábiles de los disponibles,
        // salvo que la empresa permita anticipadas.
        if ("VACACIONES".equals(dto.getTipo())) {
            int diasHabiles = diasHabilesVacaciones(dto.getFechaDesde(), dto.getFechaHasta());
            var saldo = vacacionesService.saldo(empleado.getId(), empresaId);
            if (!Boolean.TRUE.equals(saldo.getPermiteAnticipadas())
                    && BigDecimal.valueOf(diasHabiles).compareTo(saldo.getDiasDisponibles()) > 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El empleado solo tiene " + saldo.getDiasDisponibles()
                                + " días de vacaciones disponibles y se están tomando " + diasHabiles
                                + " hábiles. Habilita vacaciones anticipadas en la configuración si lo permites.");
            }
        }

        // Individual: su propio lote de una sola fila.
        String lote = "P-" + System.currentTimeMillis();
        return toDto(crearInterno(empleado, dto.getTipo(), dto.getFechaDesde(), dto.getFechaHasta(),
                dto.getObservacion(), empresaId, lote, null, true));
    }

    @Override
    @Transactional
    public List<PrestacionDto> generarLote(
            com.cloud_technological.aura_pos.dto.nomina.prestacion.GenerarLotePrestacionDto dto, Integer empresaId) {
        if (dto.getTipo() == null || dto.getFechaDesde() == null || dto.getFechaHasta() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo y fechas son obligatorios");
        if (!TIPOS_VALIDOS.contains(dto.getTipo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo inválido. Use: " + TIPOS_VALIDOS);
        if (dto.getFechaHasta().isBefore(dto.getFechaDesde()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha hasta no puede ser anterior a la desde");

        List<EmpleadoEntity> activos = contratoRepo.findEmpleadosConContratoActivo(empresaId);
        if (activos.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay empleados con contrato activo");

        String lote = "L-" + dto.getTipo() + "-" + System.currentTimeMillis();
        List<PrestacionDto> creadas = new java.util.ArrayList<>();
        for (EmpleadoEntity emp : activos) {
            // B-09/B-10 — saltar servicios y aprendiz: no causan prestaciones.
            if (sinPrestacionesLaborales(contratoActivo(emp.getId()))) continue;

            // El "desde" se recorta al ingreso: quien entró después causa menos.
            LocalDate desde = dto.getFechaDesde();
            if (emp.getFechaIngreso() != null && emp.getFechaIngreso().isAfter(desde))
                desde = emp.getFechaIngreso();
            if (desde.isAfter(dto.getFechaHasta())) continue; // ingresó después del período

            creadas.add(toDto(crearInterno(emp, dto.getTipo(), desde, dto.getFechaHasta(),
                    dto.getObservacion(), empresaId, lote, null, false)));
        }
        if (creadas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ningún empleado activo causa esta prestación en el período");
        return creadas;
    }

    @Override
    @Transactional
    public List<PrestacionDto> liquidacionDefinitiva(
            com.cloud_technological.aura_pos.dto.nomina.prestacion.LiquidacionDefinitivaDto dto, Integer empresaId) {
        if (dto.getEmpleadoId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado es obligatorio");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        LocalDate fechaRetiro = dto.getFechaRetiro() != null ? dto.getFechaRetiro()
                : empleado.getFechaRetiro() != null ? empleado.getFechaRetiro()
                : empleado.getFechaFinContrato();
        if (fechaRetiro == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Indica la fecha de retiro (o configúrala en el empleado como retiro / fin de contrato)");

        LocalDate ingreso = empleado.getFechaIngreso();
        List<PrestacionDto> creadas = new java.util.ArrayList<>();

        // Un solo lote agrupa todas las filas de esta liquidación definitiva.
        String lote = "DEF-" + empleado.getId() + "-" + System.currentTimeMillis();
        // Contrato activo del empleado (se termina al final).
        ContratoLaboralEntity contrato = contratoRepo.findActivosByEmpleado(empleado.getId())
                .stream().findFirst().orElse(null);

        // B-09/B-10 — servicios y aprendiz no tienen liquidación definitiva laboral
        // (sin cesantías, prima, vacaciones ni indemnización). Terminar el contrato
        // no requiere este proceso.
        if (sinPrestacionesLaborales(contrato))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El contrato (prestación de servicios / aprendizaje) no tiene liquidación "
                    + "definitiva de prestaciones. Solo debe darse por terminado.");

        for (String tipo : List.of("CESANTIAS", "INTERESES_CESANTIAS", "PRIMA", "VACACIONES")) {
            // B-04 — Desde: día siguiente a la última prestación NO anulada de ese
            // tipo (no solo PAGADA); si no hay, el ingreso. Así una consignación o
            // lote ya generado en el sistema no se vuelve a liquidar.
            LocalDate desde = prestacionRepo
                    .findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoNotOrderByFechaHastaDesc(
                            empresaId, empleado.getId(), tipo, "ANULADA")
                    .map(u -> u.getFechaHasta().plusDays(1))
                    .orElse(ingreso);

            // B-04 — corte de cesantías consignadas fuera del sistema: no re-liquidar
            // lo ya consignado al fondo.
            if (("CESANTIAS".equals(tipo) || "INTERESES_CESANTIAS".equals(tipo))
                    && dto.getCesantiasCorteHasta() != null) {
                LocalDate corteSiguiente = dto.getCesantiasCorteHasta().plusDays(1);
                if (desde == null || corteSiguiente.isAfter(desde)) desde = corteSiguiente;
            }

            if (desde == null || desde.isAfter(fechaRetiro)) continue; // nada que liquidar

            creadas.add(toDto(crearInterno(empleado, tipo, desde, fechaRetiro,
                    "Liquidación definitiva", empresaId, lote, contrato, false)));
        }

        // Indemnización: solo si el motivo es despido sin justa causa.
        if ("DESPIDO_SIN_JUSTA_CAUSA".equals(dto.getMotivo())) {
            LiquidacionPrestacionEntity indem = crearIndemnizacion(empleado, fechaRetiro, empresaId, lote, contrato);
            if (indem != null) creadas.add(toDto(indem));
        }

        if (creadas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay períodos pendientes por liquidar para este empleado");

        // Efectos de la definitiva: se termina el contrato y se retira al
        // empleado, para que no entre a nuevos períodos de nómina.
        if (contrato != null) {
            contrato.setEstado("TERMINADO");
            contrato.setFechaFin(fechaRetiro);
            contrato.setCausaRetiro(dto.getMotivo());
            contratoRepo.save(contrato);
        }
        empleado.setActivo(Boolean.FALSE);
        empleado.setFechaRetiro(fechaRetiro);
        empleadoRepo.save(empleado);

        return creadas;
    }

    /**
     * Indemnización por despido sin justa causa (Art. 64 CST). El cálculo depende del
     * tipo de contrato. VALIDAR contra la normatividad vigente antes de producción.
     */
    private LiquidacionPrestacionEntity crearIndemnizacion(EmpleadoEntity empleado, LocalDate fechaRetiro,
            Integer empresaId, String lote, ContratoLaboralEntity contratoLaboral) {
        // B-02 — salario vigente a la fecha de retiro desde el historial.
        BigDecimal salario = salarioBaseVigente(empleado, contratoLaboral, fechaRetiro);
        if (salario.signum() <= 0) return null;

        BigDecimal salarioDiario = salario.divide(TREINTA, 6, RoundingMode.HALF_UP);
        // B-03 — tipo de contrato, ingreso y fin se toman del CONTRATO (fuente de
        // verdad), no del empleado; se cae al empleado solo si el contrato no los
        // trae. Un empleado con varios contratos tenía indemnización sobre datos
        // equivocados.
        String contrato = contratoLaboral != null && contratoLaboral.getTipoContrato() != null
                ? contratoLaboral.getTipoContrato() : empleado.getTipoContrato();
        LocalDate ingreso = contratoLaboral != null && contratoLaboral.getFechaInicio() != null
                ? contratoLaboral.getFechaInicio() : empleado.getFechaIngreso();
        int antiguedadDias = ingreso != null ? diasBase360(ingreso, fechaRetiro) : 0;

        BigDecimal diasIndem = BigDecimal.ZERO;
        if ("INDEFINIDO".equals(contrato)) {
            NominaConfigEntity config = configRepo.findByEmpresaId(empresaId).orElse(null);
            BigDecimal smmlv = config != null && config.getSmmlv() != null ? config.getSmmlv() : BigDecimal.ZERO;
            boolean menorA10 = smmlv.signum() > 0 && salario.compareTo(smmlv.multiply(DIEZ)) < 0;
            BigDecimal diasPrimerAnio = menorA10 ? new BigDecimal("30") : new BigDecimal("20");
            BigDecimal diasAdicional  = menorA10 ? new BigDecimal("20") : new BigDecimal("15");
            BigDecimal anios = new BigDecimal(antiguedadDias).divide(D360, 6, RoundingMode.HALF_UP);
            if (anios.compareTo(BigDecimal.ONE) <= 0) {
                diasIndem = diasPrimerAnio.multiply(anios); // proporcional el primer año
            } else {
                BigDecimal aniosAdicionales = anios.subtract(BigDecimal.ONE);
                diasIndem = diasPrimerAnio.add(diasAdicional.multiply(aniosAdicionales));
            }
        } else if ("FIJO".equals(contrato)) {
            // B-03 — fin pactado del contrato (aún sin sobreescribir por el retiro),
            // con respaldo en el dato del empleado.
            LocalDate fin = contratoLaboral != null && contratoLaboral.getFechaFin() != null
                    ? contratoLaboral.getFechaFin() : empleado.getFechaFinContrato();
            if (fin != null && fin.isAfter(fechaRetiro))
                diasIndem = new BigDecimal(diasBase360(fechaRetiro, fin)); // salarios del tiempo faltante
        } else if ("OBRA_LABOR".equals(contrato)) {
            diasIndem = new BigDecimal("15"); // mínimo legal (sin fecha de obra registrada)
        } else {
            return null; // PRESTACION_SERVICIOS u otros: no aplica indemnización laboral
        }

        BigDecimal valor = salarioDiario.multiply(diasIndem).setScale(2, RoundingMode.HALF_UP);
        if (valor.signum() <= 0) return null;

        LiquidacionPrestacionEntity e = new LiquidacionPrestacionEntity();
        e.setEmpresaId(empresaId);
        e.setEmpleado(empleado);
        e.setContrato(contratoLaboral);
        e.setLote(lote);
        e.setTipo("INDEMNIZACION");
        e.setFechaDesde(ingreso != null ? ingreso : fechaRetiro);
        e.setFechaHasta(fechaRetiro);
        e.setDias(diasIndem.setScale(0, RoundingMode.HALF_UP).intValue());
        e.setBaseSalarial(salario);
        e.setValor(valor);
        e.setEstado("BORRADOR");
        e.setObservacion("Indemnización despido sin justa causa (" + contrato + ")");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return prestacionRepo.save(e);
    }

    /** Calcula y persiste (BORRADOR) una prestación. Reutilizado por crear y liquidación definitiva. */
    private LiquidacionPrestacionEntity crearInterno(EmpleadoEntity empleado, String tipo,
            LocalDate fechaDesde, LocalDate fechaHasta, String observacion, Integer empresaId,
            String lote, ContratoLaboralEntity contrato, boolean vacacionesDisfrute) {
        // Disfrute de vacaciones: días hábiles (sin domingos ni festivos). El resto
        // —incluida la provisión de vacaciones en la liquidación definitiva— usa la
        // convención comercial base-360.
        boolean esVacaciones = "VACACIONES".equals(tipo);
        int dias = (esVacaciones && vacacionesDisfrute)
                ? diasHabilesVacaciones(fechaDesde, fechaHasta)
                : diasBase360(fechaDesde, fechaHasta);
        // B-02 — el salario base sale del historial salarial vigente a la fecha de
        // corte, no del campo caché `empleado.salarioBase` (que puede estar
        // desactualizado tras un aumento y liquidar sobre un valor equivocado).
        BigDecimal salario = salarioBaseVigente(empleado, contrato, fechaHasta);
        BigDecimal auxilio = auxilioTransporte(empresaId, salario);

        // B-01 — la base prestacional incluye el promedio del salario variable
        // (comisiones, horas extra, recargos). Sin esto, un empleado con pago
        // variable queda subpagado en prima/cesantías/vacaciones.
        //
        // Ventana de referencia: el período de causación [fechaDesde, fechaHasta].
        // Para el DISFRUTE de vacaciones ese rango son los días de descanso, no la
        // causación; se promedia entonces el último año terminado en fechaHasta.
        LocalDate promDesde = (esVacaciones && vacacionesDisfrute)
                ? fechaHasta.minusDays(359) : fechaDesde;
        int diasRef = diasBase360(promDesde, fechaHasta);
        // Vacaciones: el trabajo suplementario (horas extra) no entra a su base.
        BigDecimal promedioVariable = promedioService.promedioMensualVariable(
                empresaId, empleado.getId(), promDesde, fechaHasta, diasRef, esVacaciones);

        boolean incluyeAuxilio = !esVacaciones;
        BigDecimal base = (incluyeAuxilio ? salario.add(auxilio) : salario).add(promedioVariable);
        BigDecimal valor = calcular(tipo, salario, auxilio, promedioVariable, dias,
                esVacaciones && vacacionesDisfrute);

        // F7 — cesantías que trae de otro sistema: se suman una sola vez a la
        // primera liquidación de cesantías y se consumen (se ponen en cero).
        String obsFinal = observacion;
        if ("CESANTIAS".equals(tipo) && empleado.getCesantiasSaldoInicial() != null
                && empleado.getCesantiasSaldoInicial().signum() > 0) {
            BigDecimal saldoInicial = empleado.getCesantiasSaldoInicial();
            valor = valor.add(saldoInicial);
            empleado.setCesantiasSaldoInicial(BigDecimal.ZERO);
            empleadoRepo.save(empleado);
            obsFinal = (observacion != null ? observacion + " · " : "")
                    + "Incluye saldo inicial migrado " + saldoInicial;
        }

        LiquidacionPrestacionEntity e = new LiquidacionPrestacionEntity();
        e.setEmpresaId(empresaId);
        e.setEmpleado(empleado);
        e.setContrato(contrato);
        e.setLote(lote);
        e.setTipo(tipo);
        e.setFechaDesde(fechaDesde);
        e.setFechaHasta(fechaHasta);
        e.setDias(dias);
        e.setBaseSalarial(base);
        e.setValor(valor);
        e.setEstado("BORRADOR");
        e.setObservacion(obsFinal);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return prestacionRepo.save(e);
    }

    @Override
    @Transactional
    public PrestacionDto aprobar(Long id, Integer empresaId) {
        LiquidacionPrestacionEntity e = buscar(id, empresaId);
        if (!"BORRADOR".equals(e.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo una prestación en BORRADOR puede aprobarse");
        e.setEstado("APROBADA");
        e.setUpdatedAt(LocalDateTime.now());
        return toDto(prestacionRepo.save(e));
    }

    @Override
    @Transactional
    public PrestacionDto pagar(Long id, PagoNominaDto dto, Integer empresaId) {
        LiquidacionPrestacionEntity e = buscar(id, empresaId);
        if (!"APROBADA".equals(e.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo una prestación APROBADA puede pagarse");

        String medio = dto != null && dto.getMedioPago() != null ? dto.getMedioPago() : "EFECTIVO";
        Long cuentaBancariaId = dto != null ? dto.getCuentaBancariaId() : null;
        if ("TRANSFERENCIA".equals(medio) && cuentaBancariaId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Para transferencia debe indicar la cuenta bancaria");

        e.setMedioPago(medio);
        e.setCuentaBancariaId(cuentaBancariaId);

        // B-07 — una TRANSFERENCIA no se marca pagada hasta que el banco confirme.
        // Queda PROGRAMADA (orden de pago / dispersión generada); el descuento del
        // banco y el asiento contable ocurren en confirmarPago. El efectivo y el
        // cheque sí se confirman al entregar, así que se pagan de una.
        if ("TRANSFERENCIA".equals(medio)) {
            e.setEstado("PROGRAMADA");
            e.setUpdatedAt(LocalDateTime.now());
            return toDto(prestacionRepo.save(e));
        }
        return toDto(ejecutarPago(e, empresaId));
    }

    @Override
    @Transactional
    public PrestacionDto confirmarPago(Long id, Integer empresaId) {
        LiquidacionPrestacionEntity e = buscar(id, empresaId);
        if (!"PROGRAMADA".equals(e.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo una prestación PROGRAMADA puede confirmarse como pagada");
        return toDto(ejecutarPago(e, empresaId));
    }

    /**
     * B-07 — ejecuta el pago: marca PAGADA, descuenta el banco (solo transferencia)
     * y dispara el asiento contable. Es el único punto que mueve dinero y
     * contabilidad; representa la confirmación real del pago.
     */
    private LiquidacionPrestacionEntity ejecutarPago(LiquidacionPrestacionEntity e, Integer empresaId) {
        e.setEstado("PAGADA");
        e.setFechaPago(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        prestacionRepo.save(e);

        BigDecimal valor = e.getValor() != null ? e.getValor() : BigDecimal.ZERO;

        // Transferencia: baja el saldo de la cuenta bancaria de origen.
        if ("TRANSFERENCIA".equals(e.getMedioPago()) && e.getCuentaBancariaId() != null && valor.signum() > 0) {
            CuentaBancariaEntity cb = cuentaBancariaRepo.findByIdAndEmpresaId(e.getCuentaBancariaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta bancaria no encontrada"));
            cb.setSaldoActual(cb.getSaldoActual().subtract(valor));
            cuentaBancariaRepo.save(cb);
        }

        // Asiento del pago (consume provisión y lleva el faltante a gasto) tras el commit.
        if (valor.signum() > 0) {
            eventPublisher.publishEvent(
                    new OperacionContabilizableEvent("PRESTACION_PAGO", e.getId(), empresaId, null));
        }
        return e;
    }

    @Override
    @Transactional
    public PrestacionDto anular(Long id, Integer empresaId) {
        LiquidacionPrestacionEntity e = buscar(id, empresaId);
        if ("PAGADA".equals(e.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede anular una prestación ya pagada");
        e.setEstado("ANULADA");
        e.setUpdatedAt(LocalDateTime.now());
        return toDto(prestacionRepo.save(e));
    }

    // ─── Cálculo ──────────────────────────────────────────────────────────────

    /**
     * @param promedioVariable promedio mensual del salario variable (B-01). Para
     *        prima/cesantías/intereses se suma a {@code salario + auxilio}; para
     *        vacaciones a {@code salario} (sin auxilio) y sin horas extra —quien
     *        llama ya lo calculó excluyéndolas.
     */
    private BigDecimal calcular(String tipo, BigDecimal salario, BigDecimal auxilio,
                                BigDecimal promedioVariable, int dias, boolean vacacionesDisfrute) {
        BigDecimal d = new BigDecimal(dias);
        BigDecimal prom = promedioVariable != null ? promedioVariable : BigDecimal.ZERO;
        BigDecimal baseAux = salario.add(auxilio).add(prom);
        BigDecimal baseVac = salario.add(prom);
        switch (tipo) {
            case "PRIMA", "CESANTIAS":
                return baseAux.multiply(d).divide(D360, 2, RoundingMode.HALF_UP);
            case "VACACIONES":
                // B-08 — DISFRUTE: pago del descanso = base diaria × días hábiles
                // tomados (base/30 × días). CAUSACIÓN (definitiva): valor acumulado
                // = base × días trabajados / 720 (15 hábiles por 360 → medio mes/año).
                return vacacionesDisfrute
                        ? baseVac.multiply(d).divide(TREINTA, 2, RoundingMode.HALF_UP)
                        : baseVac.multiply(d).divide(D720, 2, RoundingMode.HALF_UP);
            case "INTERESES_CESANTIAS": {
                // Intereses = cesantías del período × 12% × días/360.
                BigDecimal cesantias = baseAux.multiply(d).divide(D360, 6, RoundingMode.HALF_UP);
                return cesantias.multiply(PCT_INT_CESANTIAS).multiply(d)
                        .divide(D360, 2, RoundingMode.HALF_UP);
            }
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Salario base vigente a una fecha de corte (B-02).
     *
     * <p>Fuente de verdad, en orden: (1) historial salarial del contrato vigente a
     * la fecha —lo correcto para liquidar sobre el salario que realmente regía—,
     * (2) el salario actual del contrato, (3) como último recurso el campo caché
     * {@code empleado.salarioBase}. Antes se usaba directamente (3), que se
     * desincroniza tras un aumento y liquidaba prestaciones sobre un valor viejo.
     *
     * @param contrato el contrato de la liquidación; si es {@code null} (prestación
     *                 individual) se toma el contrato activo del empleado.
     */
    private BigDecimal salarioBaseVigente(EmpleadoEntity empleado,
                                          ContratoLaboralEntity contrato,
                                          LocalDate fechaCorte) {
        ContratoLaboralEntity c = contrato != null ? contrato
                : contratoRepo.findActivosByEmpleado(empleado.getId()).stream().findFirst().orElse(null);

        if (c != null && c.getId() != null && fechaCorte != null) {
            BigDecimal hist = salarioHistorialRepo.findEnFecha(c.getId(), fechaCorte)
                    .map(h -> h.getSalario())
                    .orElse(null);
            if (hist != null && hist.signum() > 0) return hist;
        }
        if (c != null && c.getSalarioBase() != null && c.getSalarioBase().signum() > 0)
            return c.getSalarioBase();
        return empleado.getSalarioBase() != null ? empleado.getSalarioBase() : BigDecimal.ZERO;
    }

    /** Auxilio de transporte mensual si el modo es COMPLETO y el salario ≤ 2 SMMLV. */
    private BigDecimal auxilioTransporte(Integer empresaId, BigDecimal salario) {
        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId).orElse(null);
        if (config == null || !"COMPLETO".equals(config.getModoNomina())) return BigDecimal.ZERO;
        BigDecimal smmlv = config.getSmmlv() != null ? config.getSmmlv() : BigDecimal.ZERO;
        BigDecimal dosSmmlv = smmlv.multiply(DOS);
        if (dosSmmlv.signum() > 0 && salario.compareTo(dosSmmlv) <= 0)
            return config.getAuxilioTransporte() != null ? config.getAuxilioTransporte() : BigDecimal.ZERO;
        return BigDecimal.ZERO;
    }

    /**
     * Días hábiles de vacaciones entre dos fechas (inclusive): excluye domingos y
     * festivos colombianos. Los sábados sí cuentan (CST art. 187).
     */
    private int diasHabilesVacaciones(LocalDate desde, LocalDate hasta) {
        java.util.Set<LocalDate> festivos = new java.util.HashSet<>();
        for (int y = desde.getYear(); y <= hasta.getYear(); y++) {
            for (var f : com.cloud_technological.aura_pos.utils.FestivosColombiaUtil.calcular(y)) {
                festivos.add(f.fecha);
            }
        }
        int dias = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) continue;
            if (festivos.contains(d)) continue;
            dias++;
        }
        return dias;
    }

    /** Días entre dos fechas (inclusive) con convención base-360 (mes = 30). */
    private int diasBase360(LocalDate inicio, LocalDate fin) {
        int d1 = inicio.getDayOfMonth();
        int d2 = fin.getDayOfMonth();
        if (d1 == 31) d1 = 30;
        boolean finDeMes = fin.getDayOfMonth() == fin.lengthOfMonth();
        if (d2 == 31 || finDeMes) d2 = 30;
        int meses = (fin.getYear() - inicio.getYear()) * 12 + (fin.getMonthValue() - inicio.getMonthValue());
        return Math.max(0, meses * 30 + (d2 - d1) + 1);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LiquidacionPrestacionEntity buscar(Long id, Integer empresaId) {
        return prestacionRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Prestación no encontrada"));
    }

    private PrestacionDto toDto(LiquidacionPrestacionEntity e) {
        PrestacionDto d = new PrestacionDto();
        d.setId(e.getId());
        d.setLote(e.getLote());
        if (e.getEmpleado() != null) {
            d.setEmpleadoId(e.getEmpleado().getId());
            d.setEmpleadoNombre(e.getEmpleado().getNombres() + " " + e.getEmpleado().getApellidos());
            d.setEmpleadoDocumento(e.getEmpleado().getNumeroDocumento());
        }
        d.setTipo(e.getTipo());
        d.setFechaDesde(e.getFechaDesde());
        d.setFechaHasta(e.getFechaHasta());
        d.setDias(e.getDias());
        d.setBaseSalarial(e.getBaseSalarial());
        d.setValor(e.getValor());
        d.setEstado(e.getEstado());
        d.setMedioPago(e.getMedioPago());
        d.setCuentaBancariaId(e.getCuentaBancariaId());
        d.setFechaPago(e.getFechaPago());
        d.setObservacion(e.getObservacion());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }
}

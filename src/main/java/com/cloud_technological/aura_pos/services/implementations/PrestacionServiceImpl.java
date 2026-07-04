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
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
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
    @Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final BigDecimal D360 = new BigDecimal("360");
    private static final BigDecimal D720 = new BigDecimal("720");
    private static final BigDecimal DOS = new BigDecimal("2");
    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final BigDecimal DIEZ = new BigDecimal("10");
    private static final BigDecimal PCT_INT_CESANTIAS = new BigDecimal("0.12");
    private static final List<String> TIPOS_VALIDOS =
            List.of("PRIMA", "VACACIONES", "CESANTIAS", "INTERESES_CESANTIAS");

    @Override
    public List<PrestacionDto> listar(Integer empresaId) {
        return prestacionRepo.findByEmpresaIdOrderByCreatedAtDesc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
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

        return toDto(crearInterno(empleado, dto.getTipo(), dto.getFechaDesde(), dto.getFechaHasta(),
                dto.getObservacion(), empresaId));
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

        for (String tipo : List.of("CESANTIAS", "INTERESES_CESANTIAS", "PRIMA", "VACACIONES")) {
            // Desde: día siguiente a la última prestación pagada de ese tipo; si no hay, el ingreso.
            LocalDate desde = prestacionRepo
                    .findTopByEmpresaIdAndEmpleadoIdAndTipoAndEstadoOrderByFechaHastaDesc(
                            empresaId, empleado.getId(), tipo, "PAGADA")
                    .map(u -> u.getFechaHasta().plusDays(1))
                    .orElse(ingreso);
            if (desde == null || desde.isAfter(fechaRetiro)) continue; // nada que liquidar

            creadas.add(toDto(crearInterno(empleado, tipo, desde, fechaRetiro,
                    "Liquidación definitiva", empresaId)));
        }

        // Indemnización: solo si el motivo es despido sin justa causa.
        if ("DESPIDO_SIN_JUSTA_CAUSA".equals(dto.getMotivo())) {
            LiquidacionPrestacionEntity indem = crearIndemnizacion(empleado, fechaRetiro, empresaId);
            if (indem != null) creadas.add(toDto(indem));
        }

        if (creadas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay períodos pendientes por liquidar para este empleado");
        return creadas;
    }

    /**
     * Indemnización por despido sin justa causa (Art. 64 CST). El cálculo depende del
     * tipo de contrato. VALIDAR contra la normatividad vigente antes de producción.
     */
    private LiquidacionPrestacionEntity crearIndemnizacion(EmpleadoEntity empleado, LocalDate fechaRetiro, Integer empresaId) {
        BigDecimal salario = empleado.getSalarioBase() != null ? empleado.getSalarioBase() : BigDecimal.ZERO;
        if (salario.signum() <= 0) return null;

        BigDecimal salarioDiario = salario.divide(TREINTA, 6, RoundingMode.HALF_UP);
        String contrato = empleado.getTipoContrato();
        LocalDate ingreso = empleado.getFechaIngreso();
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
            LocalDate fin = empleado.getFechaFinContrato();
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
            LocalDate fechaDesde, LocalDate fechaHasta, String observacion, Integer empresaId) {
        int dias = diasBase360(fechaDesde, fechaHasta);
        BigDecimal salario = empleado.getSalarioBase() != null ? empleado.getSalarioBase() : BigDecimal.ZERO;
        BigDecimal auxilio = auxilioTransporte(empresaId, salario);
        boolean incluyeAuxilio = !"VACACIONES".equals(tipo);
        BigDecimal base = incluyeAuxilio ? salario.add(auxilio) : salario;
        BigDecimal valor = calcular(tipo, salario, auxilio, dias);

        LiquidacionPrestacionEntity e = new LiquidacionPrestacionEntity();
        e.setEmpresaId(empresaId);
        e.setEmpleado(empleado);
        e.setTipo(tipo);
        e.setFechaDesde(fechaDesde);
        e.setFechaHasta(fechaHasta);
        e.setDias(dias);
        e.setBaseSalarial(base);
        e.setValor(valor);
        e.setEstado("BORRADOR");
        e.setObservacion(observacion);
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

        e.setEstado("PAGADA");
        e.setMedioPago(medio);
        e.setCuentaBancariaId(cuentaBancariaId);
        e.setFechaPago(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        prestacionRepo.save(e);

        BigDecimal valor = e.getValor() != null ? e.getValor() : BigDecimal.ZERO;

        // Transferencia: baja el saldo de la cuenta bancaria de origen.
        if ("TRANSFERENCIA".equals(medio) && cuentaBancariaId != null && valor.signum() > 0) {
            CuentaBancariaEntity cb = cuentaBancariaRepo.findByIdAndEmpresaId(cuentaBancariaId, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta bancaria no encontrada"));
            cb.setSaldoActual(cb.getSaldoActual().subtract(valor));
            cuentaBancariaRepo.save(cb);
        }

        // Asiento del pago (consume provisión y lleva el faltante a gasto) tras el commit.
        if (valor.signum() > 0) {
            eventPublisher.publishEvent(
                    new OperacionContabilizableEvent("PRESTACION_PAGO", e.getId(), empresaId, null));
        }
        return toDto(e);
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

    private BigDecimal calcular(String tipo, BigDecimal salario, BigDecimal auxilio, int dias) {
        BigDecimal d = new BigDecimal(dias);
        BigDecimal baseAux = salario.add(auxilio);
        switch (tipo) {
            case "PRIMA", "CESANTIAS":
                return baseAux.multiply(d).divide(D360, 2, RoundingMode.HALF_UP);
            case "VACACIONES":
                return salario.multiply(d).divide(D720, 2, RoundingMode.HALF_UP);
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

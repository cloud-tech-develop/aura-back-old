package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private NominaJPARepository nominaRepo;

    @Autowired
    private NominaQueryRepository nominaQueryRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private PeriodoNominaJPARepository periodoRepo;

    @Autowired
    private NominaConfigJPARepository configRepo;

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

        nomina.setEstado("APROBADO");
        nomina.setUpdatedAt(LocalDateTime.now());
        return toDto(nominaRepo.save(nomina));
    }

    @Override
    @Transactional
    public NominaDto anular(Long id, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        if ("PAGADO".equals(nomina.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede anular una nómina ya pagada");

        nomina.setEstado("ANULADO");
        nomina.setUpdatedAt(LocalDateTime.now());
        return toDto(nominaRepo.save(nomina));
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

        // Provisiones sobre salario proporcional (base sin auxilio de transporte)
        nomina.setProvisionPrima(porcentaje(salarioProporcional, PCT_PRIMA));
        nomina.setProvisionCesantias(porcentaje(salarioProporcional, PCT_CESANTIAS));
        nomina.setProvisionIntCesantias(
                nomina.getProvisionCesantias()
                        .multiply(PCT_INT_CESANTIAS)
                        .divide(CIEN, 2, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
        );
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

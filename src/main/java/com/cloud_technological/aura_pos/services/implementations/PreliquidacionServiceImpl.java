package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PreliquidacionItemDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaIncidenciaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaNovedadNominaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AutorizacionLiquidacionJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.EmpleadoTurnoJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.PeriodoAsistenciaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.PreliquidacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class PreliquidacionServiceImpl implements PreliquidacionService {

    @Autowired private PeriodoNominaJPARepository periodoRepo;
    @Autowired private NominaJPARepository nominaRepo;
    @Autowired private NominaConfigJPARepository configRepo;
    @Autowired private EmpleadoJPARepository empleadoRepo;
    @Autowired private AsistenciaIncidenciaJPARepository incidenciaRepo;
    @Autowired private AsistenciaNovedadNominaJPARepository asistenciaNovedadRepo;
    @Autowired private AutorizacionLiquidacionJPARepository autorizacionRepo;
    @Autowired private PeriodoAsistenciaJPARepository periodoAsistenciaRepo;
    @Autowired private EmpleadoTurnoJPARepository empleadoTurnoRepo;

    @Override
    public List<PreliquidacionItemDto> previsualizar(Long periodoId, Integer empresaId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        NominaConfigEntity config = configRepo.findByEmpresaId(empresaId).orElse(null);
        String modo = config != null ? config.getModoLiquidacion() : "SIN_ASISTENCIA";

        // Nóminas ya calculadas del período, indexadas por empleado.
        Map<Long, NominaEntity> nominasPorEmpleado = nominaRepo
                .findByPeriodoIdAndEmpresaId(periodoId, empresaId).stream()
                .collect(Collectors.toMap(n -> n.getEmpleado().getId(), Function.identity(), (a, b) -> a));

        boolean asistenciaPeriodoAprobada = periodoAsistenciaRepo
                .findByEmpresaIdOrderByFechaInicioDesc(empresaId).stream()
                .anyMatch(pa -> ("APROBADO".equals(pa.getEstado()) || "ENVIADO_A_NOMINA".equals(pa.getEstado()))
                        && !pa.getFechaInicio().isAfter(periodo.getFechaInicio())
                        && !pa.getFechaFin().isBefore(periodo.getFechaFin()));

        return empleadoRepo.findByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(emp -> construir(emp, periodo, modo, nominasPorEmpleado.get(emp.getId()),
                        asistenciaPeriodoAprobada, empresaId))
                .collect(Collectors.toList());
    }

    private PreliquidacionItemDto construir(EmpleadoEntity emp, PeriodoNominaEntity periodo, String modo,
            NominaEntity nomina, boolean asistenciaPeriodoAprobada, Integer empresaId) {

        PreliquidacionItemDto item = new PreliquidacionItemDto();
        item.setEmpleadoId(emp.getId());
        item.setEmpleadoNombre(emp.getNombres() + " " + emp.getApellidos());
        item.setSalarioBase(emp.getSalarioBase());

        if (nomina != null) {
            item.setDiasTrabajados(nomina.getDiasTrabajados());
            item.setTotalDevengado(nomina.getTotalDevengado());
            item.setTotalDeducciones(nomina.getTotalDeducciones());
            item.setNetoPagar(nomina.getNetoPagar());
            item.setEstado(nomina.getEstado());
        } else {
            item.setEstado("SIN_LIQUIDAR");
            item.getAlertas().add("SIN_LIQUIDAR");
        }

        boolean requiereAsistencia = "CON_ASISTENCIA_OBLIGATORIA".equals(modo)
                || ("MIXTA".equals(modo) && Boolean.TRUE.equals(emp.getRequiereControlAsistencia()));

        boolean autorizado = autorizacionRepo
                .existsByEmpresaIdAndEmpleadoIdAndPeriodoNominaIdAndEstado(
                        empresaId, emp.getId(), periodo.getId(), "ACTIVA");
        if (autorizado) item.getAlertas().add("LIQUIDACION_EXCEPCIONAL");

        if (requiereAsistencia && !asistenciaPeriodoAprobada && !autorizado)
            item.getAlertas().add("ASISTENCIA_PENDIENTE");

        long incidenciasPend = incidenciaRepo.countByEmpresaIdAndEmpleadoIdAndFechaBetweenAndEstado(
                empresaId, emp.getId(), periodo.getFechaInicio(), periodo.getFechaFin(), "PENDIENTE_REVISION");
        if (incidenciasPend > 0) item.getAlertas().add("INCIDENCIAS_PENDIENTES");

        long novedadesPend = asistenciaNovedadRepo
                .findByEmpresaIdAndPeriodoNominaIdAndEmpleadoIdAndEstado(
                        empresaId, periodo.getId(), emp.getId(), "PENDIENTE").size();
        if (novedadesPend > 0) item.getAlertas().add("NOVEDADES_ASISTENCIA_PENDIENTES");

        if (emp.getArl() == null) item.getAlertas().add("SIN_CONFIGURACION_ARL");

        if (requiereAsistencia
                && empleadoTurnoRepo.findVigentes(empresaId, emp.getId(), periodo.getFechaInicio()).isEmpty())
            item.getAlertas().add("SIN_TURNO_ASIGNADO");

        return item;
    }
}

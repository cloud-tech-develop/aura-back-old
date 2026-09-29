package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaNovedadDto;
import com.cloud_technological.aura_pos.entity.AsistenciaDiaEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaIncidenciaEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaNovedadNominaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaDiaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaIncidenciaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaNovedadNominaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.AsistenciaNovedadService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AsistenciaNovedadServiceImpl implements AsistenciaNovedadService {

    @Autowired
    private AsistenciaNovedadNominaJPARepository novedadRepo;

    @Autowired
    private AsistenciaDiaJPARepository diaRepo;

    @Autowired
    private AsistenciaIncidenciaJPARepository incidenciaRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private PeriodoNominaJPARepository periodoRepo;

    private static final BigDecimal SESENTA = new BigDecimal("60");

    @Override
    @Transactional
    public List<AsistenciaNovedadDto> generarDesdePeriodo(Long periodoNominaId, Integer empresaId, Long usuarioId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoNominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período de nómina no encontrado"));

        List<EmpleadoEntity> empleados = empleadoRepo.findByEmpresaIdAndActivoTrue(empresaId);
        List<AsistenciaNovedadNominaEntity> generadas = new ArrayList<>();

        for (EmpleadoEntity empleado : empleados) {
            // Dedup: elimina las staged aún PENDIENTE de este período+empleado.
            novedadRepo.findByEmpresaIdAndPeriodoNominaIdAndEmpleadoIdAndEstado(
                            empresaId, periodoNominaId, empleado.getId(), "PENDIENTE")
                    .forEach(novedadRepo::delete);

            // Días aprobados del período → horas extra y recargos
            List<AsistenciaDiaEntity> dias = diaRepo
                    .findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(
                            empresaId, empleado.getId(), periodo.getFechaInicio(), periodo.getFechaFin());
            for (AsistenciaDiaEntity d : dias) {
                if (!"APROBADO".equals(d.getEstadoAprobacion())) continue;
                if (nz(d.getMinutosExtraDiurna()) > 0)
                    generadas.add(desdeDia(periodo, empleado, d, "HORA_EXTRA_DIURNA", "HORAS",
                            horas(d.getMinutosExtraDiurna()), usuarioId));
                if (nz(d.getMinutosExtraNocturna()) > 0)
                    generadas.add(desdeDia(periodo, empleado, d, "HORA_EXTRA_NOCTURNA", "HORAS",
                            horas(d.getMinutosExtraNocturna()), usuarioId));
                if (nz(d.getMinutosDominicalFestiva()) > 0)
                    generadas.add(desdeDia(periodo, empleado, d, "RECARGO_DOMINICAL_FESTIVO", "HORAS",
                            horas(d.getMinutosDominicalFestiva()), usuarioId));
                if (nz(d.getMinutosNocturnos()) > 0)
                    generadas.add(desdeDia(periodo, empleado, d, "RECARGO_NOCTURNO", "HORAS",
                            horas(d.getMinutosNocturnos()), usuarioId));
            }

            // Incidencias aprobadas del período → ausencias / tardanzas descontables
            List<AsistenciaIncidenciaEntity> incidencias = incidenciaRepo
                    .findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(
                            empresaId, empleado.getId(), periodo.getFechaInicio(), periodo.getFechaFin());
            for (AsistenciaIncidenciaEntity inc : incidencias) {
                if ("AUSENCIA_DIA_COMPLETO".equals(inc.getTipoIncidencia())
                        && "NO_JUSTIFICADA".equals(inc.getEstado())) {
                    generadas.add(desdeIncidencia(periodo, empleado, inc, "AUSENCIA_NO_JUSTIFICADA", "DIAS",
                            BigDecimal.ONE, usuarioId));
                } else if ("LLEGADA_TARDE".equals(inc.getTipoIncidencia())
                        && "APROBADA_COMO_NOVEDAD".equals(inc.getEstado())) {
                    int minutos = inc.getAsistenciaDia() != null ? nz(inc.getAsistenciaDia().getMinutosTarde()) : 0;
                    if (minutos > 0)
                        generadas.add(desdeIncidencia(periodo, empleado, inc, "LLEGADA_TARDE_DESCONTADA", "MINUTOS",
                                new BigDecimal(minutos), usuarioId));
                }
            }
        }

        novedadRepo.saveAll(generadas);
        return generadas.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<AsistenciaNovedadDto> listar(Long periodoNominaId, Integer empresaId) {
        return novedadRepo.findByEmpresaIdAndPeriodoNominaIdOrderByEmpleadoIdAsc(empresaId, periodoNominaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AsistenciaNovedadDto aprobar(Long id, Integer empresaId) {
        AsistenciaNovedadNominaEntity n = buscar(id, empresaId);
        if (!"PENDIENTE".equals(n.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo una novedad PENDIENTE puede aprobarse");
        n.setEstado("APROBADA");
        return toDto(novedadRepo.save(n));
    }

    @Override
    @Transactional
    public AsistenciaNovedadDto rechazar(Long id, Integer empresaId) {
        AsistenciaNovedadNominaEntity n = buscar(id, empresaId);
        n.setEstado("RECHAZADA");
        return toDto(novedadRepo.save(n));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AsistenciaNovedadNominaEntity buscar(Long id, Integer empresaId) {
        return novedadRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Novedad de asistencia no encontrada"));
    }

    private BigDecimal horas(int minutos) {
        return new BigDecimal(minutos).divide(SESENTA, 2, RoundingMode.HALF_UP);
    }

    private AsistenciaNovedadNominaEntity desdeDia(PeriodoNominaEntity periodo, EmpleadoEntity empleado,
            AsistenciaDiaEntity dia, String tipo, String unidad, BigDecimal cantidad, Long usuarioId) {
        AsistenciaNovedadNominaEntity n = base(periodo, empleado, tipo, unidad, cantidad, usuarioId);
        n.setAsistenciaDia(dia);
        return n;
    }

    private AsistenciaNovedadNominaEntity desdeIncidencia(PeriodoNominaEntity periodo, EmpleadoEntity empleado,
            AsistenciaIncidenciaEntity inc, String tipo, String unidad, BigDecimal cantidad, Long usuarioId) {
        AsistenciaNovedadNominaEntity n = base(periodo, empleado, tipo, unidad, cantidad, usuarioId);
        n.setAsistenciaIncidencia(inc);
        if (inc.getAsistenciaDia() != null) n.setAsistenciaDia(inc.getAsistenciaDia());
        return n;
    }

    private AsistenciaNovedadNominaEntity base(PeriodoNominaEntity periodo, EmpleadoEntity empleado,
            String tipo, String unidad, BigDecimal cantidad, Long usuarioId) {
        AsistenciaNovedadNominaEntity n = new AsistenciaNovedadNominaEntity();
        n.setEmpresa(periodo.getEmpresa());
        n.setPeriodoNomina(periodo);
        n.setEmpleado(empleado);
        n.setTipoNovedad(tipo);
        n.setUnidad(unidad);
        n.setCantidad(cantidad);
        n.setOrigen("ASISTENCIA");
        n.setEstado("PENDIENTE");
        n.setFechaGeneracion(LocalDateTime.now());
        n.setGeneradoPor(usuarioId != null ? usuarioId.intValue() : null);
        return n;
    }

    private int nz(Integer v) { return v != null ? v : 0; }

    private AsistenciaNovedadDto toDto(AsistenciaNovedadNominaEntity n) {
        AsistenciaNovedadDto dto = new AsistenciaNovedadDto();
        dto.setId(n.getId());
        if (n.getPeriodoNomina() != null) dto.setPeriodoNominaId(n.getPeriodoNomina().getId());
        dto.setEmpleadoId(n.getEmpleado().getId());
        dto.setEmpleadoNombre(n.getEmpleado().getNombres() + " " + n.getEmpleado().getApellidos());
        dto.setTipoNovedad(n.getTipoNovedad());
        dto.setUnidad(n.getUnidad());
        dto.setCantidad(n.getCantidad());
        dto.setValorManual(n.getValorManual());
        dto.setOrigen(n.getOrigen());
        dto.setEstado(n.getEstado());
        return dto;
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.CrearIncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.IncidenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.RevisarIncidenciaDto;
import com.cloud_technological.aura_pos.entity.AsistenciaDiaEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaIncidenciaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaDiaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaIncidenciaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.services.AsistenciaIncidenciaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AsistenciaIncidenciaServiceImpl implements AsistenciaIncidenciaService {

    @Autowired
    private AsistenciaIncidenciaJPARepository incidenciaRepo;

    @Autowired
    private AsistenciaDiaJPARepository diaRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    private static final List<String> ESTADOS_REVISION = List.of(
            "JUSTIFICADA", "NO_JUSTIFICADA", "APROBADA_COMO_NOVEDAD", "RECHAZADA", "CORREGIDA", "ANULADA");

    /**
     * Genera incidencias a partir del día ya consolidado. Regla clave del sistema:
     * cualquier falla de marcaje se convierte en una incidencia PENDIENTE_REVISION,
     * nunca en un descuento automático.
     */
    @Override
    @Transactional
    public List<IncidenciaDto> generarDesdeDia(Long empleadoId, LocalDate fecha, Integer empresaId) {
        AsistenciaDiaEntity dia = diaRepo
                .findByEmpresaIdAndEmpleadoIdAndFecha(empresaId, empleadoId, fecha)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "No hay asistencia consolidada para ese empleado y fecha; consolide primero"));

        // Reemplaza solo las incidencias auto-generadas aún pendientes (evita duplicados).
        List<AsistenciaIncidenciaEntity> previas = incidenciaRepo.findByAsistenciaDiaId(dia.getId());
        previas.stream()
                .filter(i -> "PENDIENTE_REVISION".equals(i.getEstado()))
                .forEach(incidenciaRepo::delete);

        List<AsistenciaIncidenciaEntity> nuevas = new ArrayList<>();

        if ("AUSENTE".equals(dia.getEstadoAsistencia())) {
            nuevas.add(build(dia, "AUSENCIA_DIA_COMPLETO", "Sin marcajes en un día con turno asignado", true));
        } else if ("SIN_MARCAJE_COMPLETO".equals(dia.getEstadoAsistencia())) {
            if (dia.getHoraEntradaReal() == null)
                nuevas.add(build(dia, "NO_MARCO_ENTRADA", "Falta marcaje de entrada", true));
            if (dia.getHoraSalidaReal() == null)
                nuevas.add(build(dia, "NO_MARCO_SALIDA", "Falta marcaje de salida", true));
        }

        if (nz(dia.getMinutosTarde()) > 0)
            nuevas.add(build(dia, "LLEGADA_TARDE", "Llegada tarde de " + dia.getMinutosTarde() + " min", false));

        if (dia.getTurno() == null)
            nuevas.add(build(dia, "TURNO_NO_ASIGNADO", "El empleado no tenía turno asignado ese día", false));

        if (nz(dia.getMinutosExtraDiurna()) + nz(dia.getMinutosExtraNocturna()) > 0)
            nuevas.add(build(dia, "HORAS_EXTRA_PENDIENTES_APROBACION",
                    "Horas extra detectadas pendientes de aprobación", false));

        incidenciaRepo.saveAll(nuevas);
        return nuevas.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<IncidenciaDto> listar(Long empleadoId, LocalDate desde, LocalDate hasta, Integer empresaId) {
        return incidenciaRepo
                .findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(empresaId, empleadoId, desde, hasta)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public IncidenciaDto crearManual(CrearIncidenciaDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getEmpleadoId() == null || dto.getFecha() == null || dto.getTipoIncidencia() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado, fecha y tipo son obligatorios");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        AsistenciaIncidenciaEntity inc = new AsistenciaIncidenciaEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        inc.setEmpresa(empresa);
        inc.setEmpleado(empleado);
        inc.setFecha(dto.getFecha());
        inc.setTipoIncidencia(dto.getTipoIncidencia());
        inc.setDescripcion(dto.getDescripcion());
        inc.setRequiereSoporte(Boolean.TRUE.equals(dto.getRequiereSoporte()));
        inc.setSoporteUrl(dto.getSoporteUrl());
        inc.setRegistradoPor(usuarioId != null ? usuarioId.intValue() : null);
        inc.setFechaRegistro(LocalDateTime.now());

        diaRepo.findByEmpresaIdAndEmpleadoIdAndFecha(empresaId, dto.getEmpleadoId(), dto.getFecha())
                .ifPresent(inc::setAsistenciaDia);

        return toDto(incidenciaRepo.save(inc));
    }

    @Override
    @Transactional
    public IncidenciaDto revisar(Long id, RevisarIncidenciaDto dto, Integer empresaId, Long usuarioId) {
        AsistenciaIncidenciaEntity inc = incidenciaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Incidencia no encontrada"));

        if (dto.getEstado() == null || !ESTADOS_REVISION.contains(dto.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Estado de revisión inválido. Use: " + ESTADOS_REVISION);

        if (Boolean.TRUE.equals(inc.getRequiereSoporte())
                && "JUSTIFICADA".equals(dto.getEstado())
                && (dto.getSoporteUrl() == null || dto.getSoporteUrl().isBlank())
                && (inc.getSoporteUrl() == null || inc.getSoporteUrl().isBlank()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Esta incidencia requiere soporte para justificarse");

        inc.setEstado(dto.getEstado());
        if (dto.getSoporteUrl() != null) inc.setSoporteUrl(dto.getSoporteUrl());
        inc.setObservacionRevision(dto.getObservacionRevision());
        inc.setRevisadoPor(usuarioId != null ? usuarioId.intValue() : null);
        inc.setFechaRevision(LocalDateTime.now());

        return toDto(incidenciaRepo.save(inc));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AsistenciaIncidenciaEntity build(AsistenciaDiaEntity dia, String tipo, String desc, boolean requiereSoporte) {
        AsistenciaIncidenciaEntity inc = new AsistenciaIncidenciaEntity();
        inc.setEmpresa(dia.getEmpresa());
        inc.setEmpleado(dia.getEmpleado());
        inc.setAsistenciaDia(dia);
        inc.setFecha(dia.getFecha());
        inc.setTipoIncidencia(tipo);
        inc.setDescripcion(desc);
        inc.setRequiereSoporte(requiereSoporte);
        inc.setEstado("PENDIENTE_REVISION");
        inc.setFechaRegistro(LocalDateTime.now());
        return inc;
    }

    private int nz(Integer v) { return v != null ? v : 0; }

    private IncidenciaDto toDto(AsistenciaIncidenciaEntity i) {
        IncidenciaDto dto = new IncidenciaDto();
        dto.setId(i.getId());
        if (i.getAsistenciaDia() != null) dto.setAsistenciaDiaId(i.getAsistenciaDia().getId());
        dto.setEmpleadoId(i.getEmpleado().getId());
        dto.setEmpleadoNombre(i.getEmpleado().getNombres() + " " + i.getEmpleado().getApellidos());
        dto.setFecha(i.getFecha());
        dto.setTipoIncidencia(i.getTipoIncidencia());
        dto.setDescripcion(i.getDescripcion());
        dto.setEstado(i.getEstado());
        dto.setRequiereSoporte(i.getRequiereSoporte());
        dto.setSoporteUrl(i.getSoporteUrl());
        dto.setRevisadoPor(i.getRevisadoPor());
        dto.setFechaRevision(i.getFechaRevision());
        dto.setObservacionRevision(i.getObservacionRevision());
        return dto;
    }
}

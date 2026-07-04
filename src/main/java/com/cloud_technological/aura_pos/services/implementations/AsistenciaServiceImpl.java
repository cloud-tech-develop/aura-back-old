package com.cloud_technological.aura_pos.services.implementations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.AsistenciaDiaDto;
import com.cloud_technological.aura_pos.dto.asistencia.CreateMarcajeDto;
import com.cloud_technological.aura_pos.dto.asistencia.MarcajeDto;
import com.cloud_technological.aura_pos.entity.AsistenciaDiaEntity;
import com.cloud_technological.aura_pos.entity.AsistenciaMarcajeEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoTurnoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TurnoTrabajoEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaDiaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaMarcajeJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.EmpleadoTurnoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.services.AsistenciaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class AsistenciaServiceImpl implements AsistenciaService {

    @Autowired
    private AsistenciaMarcajeJPARepository marcajeRepo;

    @Autowired
    private AsistenciaDiaJPARepository diaRepo;

    @Autowired
    private EmpleadoTurnoJPARepository empleadoTurnoRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    // Ventana de recargo nocturno en Colombia: 21:00 → 06:00
    private static final LocalTime NOCHE_INICIO = LocalTime.of(21, 0);
    private static final LocalTime NOCHE_FIN = LocalTime.of(6, 0);

    // ─── Marcajes ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MarcajeDto registrarMarcaje(CreateMarcajeDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getEmpleadoId() == null || dto.getTipoMarcaje() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado y tipo de marcaje son obligatorios");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        if (!Boolean.TRUE.equals(empleado.getActivo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El empleado está inactivo");

        LocalDateTime fh = dto.getFechaHoraMarcaje() != null ? dto.getFechaHoraMarcaje() : LocalDateTime.now();

        AsistenciaMarcajeEntity m = new AsistenciaMarcajeEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        m.setEmpresa(empresa);
        m.setEmpleado(empleado);
        m.setFecha(fh.toLocalDate());
        m.setFechaHoraMarcaje(fh);
        m.setTipoMarcaje(dto.getTipoMarcaje());
        m.setOrigenMarcaje(dto.getOrigenMarcaje() != null ? dto.getOrigenMarcaje() : "ASISTENTE");
        m.setObservacion(dto.getObservacion());
        m.setEvidenciaUrl(dto.getEvidenciaUrl());
        m.setRegistradoPor(usuarioId != null ? usuarioId.intValue() : null);
        m.setCreatedAt(LocalDateTime.now());

        return toMarcajeDto(marcajeRepo.save(m));
    }

    @Override
    public List<MarcajeDto> listarMarcajes(Long empleadoId, LocalDate fecha, Integer empresaId) {
        return marcajeRepo
                .findByEmpresaIdAndEmpleadoIdAndFechaOrderByFechaHoraMarcajeAsc(empresaId, empleadoId, fecha)
                .stream().map(this::toMarcajeDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void anularMarcaje(Long id, Integer empresaId) {
        AsistenciaMarcajeEntity m = marcajeRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Marcaje no encontrado"));
        m.setEstado("ANULADO");
        marcajeRepo.save(m);
    }

    // ─── Consolidación ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AsistenciaDiaDto consolidarDia(Long empleadoId, LocalDate fecha, Integer empresaId) {
        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(empleadoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        AsistenciaDiaEntity dia = diaRepo
                .findByEmpresaIdAndEmpleadoIdAndFecha(empresaId, empleadoId, fecha)
                .orElseGet(() -> {
                    AsistenciaDiaEntity d = new AsistenciaDiaEntity();
                    EmpresaEntity empresa = new EmpresaEntity();
                    empresa.setId(empresaId);
                    d.setEmpresa(empresa);
                    d.setEmpleado(empleado);
                    d.setFecha(fecha);
                    d.setCreatedAt(LocalDateTime.now());
                    return d;
                });

        if ("ENVIADO_A_NOMINA".equals(dia.getEstadoAprobacion()) || "BLOQUEADO".equals(dia.getEstadoAprobacion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El día ya fue enviado a nómina o está bloqueado; no se puede reconsolidar");

        calcularDia(dia, empleado, fecha, empresaId);
        dia.setUpdatedAt(LocalDateTime.now());
        return toDiaDto(diaRepo.save(dia));
    }

    @Override
    @Transactional
    public List<AsistenciaDiaDto> consolidarRango(LocalDate desde, LocalDate hasta, Integer empresaId) {
        List<EmpleadoEntity> empleados = empleadoRepo.findByEmpresaIdAndActivoTrue(empresaId);
        java.util.List<AsistenciaDiaDto> out = new java.util.ArrayList<>();
        for (EmpleadoEntity e : empleados) {
            for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
                out.add(consolidarDia(e.getId(), f, empresaId));
            }
        }
        return out;
    }

    @Override
    public List<AsistenciaDiaDto> listarDias(Long empleadoId, LocalDate desde, LocalDate hasta, Integer empresaId) {
        return diaRepo
                .findByEmpresaIdAndEmpleadoIdAndFechaBetweenOrderByFechaAsc(empresaId, empleadoId, desde, hasta)
                .stream().map(this::toDiaDto).collect(Collectors.toList());
    }

    // ─── Aprobación del día ───────────────────────────────────────────────────

    @Override
    @Transactional
    public AsistenciaDiaDto aprobarDia(Long diaId, Integer empresaId, Long usuarioId) {
        AsistenciaDiaEntity dia = diaRepo.findByIdAndEmpresaId(diaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Día de asistencia no encontrado"));
        if ("ENVIADO_A_NOMINA".equals(dia.getEstadoAprobacion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El día ya fue enviado a nómina");
        dia.setEstadoAprobacion("APROBADO");
        dia.setAprobadoPor(usuarioId != null ? usuarioId.intValue() : null);
        dia.setFechaAprobacion(LocalDateTime.now());
        dia.setUpdatedAt(LocalDateTime.now());
        return toDiaDto(diaRepo.save(dia));
    }

    @Override
    @Transactional
    public AsistenciaDiaDto rechazarDia(Long diaId, String observacion, Integer empresaId, Long usuarioId) {
        AsistenciaDiaEntity dia = diaRepo.findByIdAndEmpresaId(diaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Día de asistencia no encontrado"));
        if ("ENVIADO_A_NOMINA".equals(dia.getEstadoAprobacion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El día ya fue enviado a nómina");
        dia.setEstadoAprobacion("RECHAZADO");
        dia.setObservacion(observacion);
        dia.setAprobadoPor(usuarioId != null ? usuarioId.intValue() : null);
        dia.setFechaAprobacion(LocalDateTime.now());
        dia.setUpdatedAt(LocalDateTime.now());
        return toDiaDto(diaRepo.save(dia));
    }

    // ─── Motor de cálculo del día ─────────────────────────────────────────────

    private void calcularDia(AsistenciaDiaEntity dia, EmpleadoEntity empleado, LocalDate fecha, Integer empresaId) {
        List<AsistenciaMarcajeEntity> marcajes = marcajeRepo
                .findByEmpresaIdAndEmpleadoIdAndFechaAndEstadoOrderByFechaHoraMarcajeAsc(
                        empresaId, empleadoId(empleado), fecha, "VALIDO");

        LocalDateTime entradaReal = marcajes.stream()
                .filter(m -> "ENTRADA".equals(m.getTipoMarcaje()))
                .map(AsistenciaMarcajeEntity::getFechaHoraMarcaje)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime salidaReal = marcajes.stream()
                .filter(m -> "SALIDA".equals(m.getTipoMarcaje()))
                .map(AsistenciaMarcajeEntity::getFechaHoraMarcaje)
                .max(LocalDateTime::compareTo).orElse(null);

        // Turno vigente para la fecha (si hay asignación)
        TurnoTrabajoEntity turno = empleadoTurnoRepo.findVigentes(empresaId, empleadoId(empleado), fecha)
                .stream().findFirst().map(EmpleadoTurnoEntity::getTurno).orElse(null);

        dia.setTurno(turno);
        dia.setHoraEntradaReal(entradaReal != null ? entradaReal.toLocalTime() : null);
        dia.setHoraSalidaReal(salidaReal != null ? salidaReal.toLocalTime() : null);

        // Reset de acumulados
        dia.setMinutosProgramados(0);
        dia.setMinutosTrabajados(0);
        dia.setMinutosTarde(0);
        dia.setMinutosSalidaTemprana(0);
        dia.setMinutosExtraDiurna(0);
        dia.setMinutosExtraNocturna(0);
        dia.setMinutosDominicalFestiva(0);
        dia.setMinutosNocturnos(0);

        int minProgramados = 0;
        LocalDateTime entProg = null, salProg = null;
        if (turno != null) {
            dia.setHoraEntradaProgramada(turno.getHoraInicio());
            dia.setHoraSalidaProgramada(turno.getHoraFin());
            entProg = LocalDateTime.of(fecha, turno.getHoraInicio());
            salProg = LocalDateTime.of(fecha, turno.getHoraFin());
            if (Boolean.TRUE.equals(turno.getCruzaMedianoche()) || !turno.getHoraFin().isAfter(turno.getHoraInicio()))
                salProg = salProg.plusDays(1);
            minProgramados = (int) Duration.between(entProg, salProg).toMinutes()
                    - nz(turno.getMinutosDescanso());
            minProgramados = Math.max(0, minProgramados);
            dia.setMinutosProgramados(minProgramados);
        }

        // Sin marcaje completo
        if (entradaReal == null || salidaReal == null) {
            dia.setEstadoAsistencia(entradaReal == null && salidaReal == null && turno != null
                    ? "AUSENTE" : "SIN_MARCAJE_COMPLETO");
            return;
        }

        // Ajuste de salida que cruza medianoche
        LocalDateTime entradaDT = entradaReal;
        LocalDateTime salidaDT = salidaReal;
        if (!salidaDT.isAfter(entradaDT)) salidaDT = salidaDT.plusDays(1);

        int descanso = turno != null ? nz(turno.getMinutosDescanso()) : 0;
        int minTrabajados = Math.max(0, (int) Duration.between(entradaDT, salidaDT).toMinutes() - descanso);
        dia.setMinutosTrabajados(minTrabajados);

        // Recargo nocturno (informativo): solape con ventana 21:00–06:00
        int nocturnos = minutosNocturnos(entradaDT, salidaDT);
        dia.setMinutosNocturnos(nocturnos);

        // Dominical / festivo (por ahora solo domingo; festivos requieren calendario)
        if (fecha.getDayOfWeek() == java.time.DayOfWeek.SUNDAY)
            dia.setMinutosDominicalFestiva(minTrabajados);

        if (turno != null) {
            int tolerancia = nz(turno.getToleraLlegadaTardeMin());
            LocalDateTime limiteEntrada = entProg.plusMinutes(tolerancia);
            int tarde = (int) Math.max(0, Duration.between(limiteEntrada, entradaDT).toMinutes());
            dia.setMinutosTarde(tarde);

            int salidaTemprana = (int) Math.max(0, Duration.between(salidaDT, salProg).toMinutes());
            dia.setMinutosSalidaTemprana(salidaTemprana);

            // Horas extra = trabajado por encima de lo programado
            int extra = Math.max(0, minTrabajados - minProgramados);
            int extraNoct = Math.min(extra, nocturnos);
            dia.setMinutosExtraNocturna(extraNoct);
            dia.setMinutosExtraDiurna(extra - extraNoct);

            if (tarde > 0) dia.setEstadoAsistencia("TARDE");
            else if (salidaTemprana > 0) dia.setEstadoAsistencia("SALIDA_TEMPRANA");
            else dia.setEstadoAsistencia("ASISTIO");
        } else {
            dia.setEstadoAsistencia("ASISTIO");
        }
    }

    /** Minutos de la jornada [ini, fin] que caen dentro de la franja nocturna 21:00–06:00. */
    private int minutosNocturnos(LocalDateTime ini, LocalDateTime fin) {
        long total = 0;
        LocalDate d = ini.toLocalDate().minusDays(1);
        while (!d.isAfter(fin.toLocalDate())) {
            LocalDateTime nIni = LocalDateTime.of(d, NOCHE_INICIO);
            LocalDateTime nFin = LocalDateTime.of(d.plusDays(1), NOCHE_FIN);
            total += solape(ini, fin, nIni, nFin);
            d = d.plusDays(1);
        }
        return (int) total;
    }

    private long solape(LocalDateTime aIni, LocalDateTime aFin, LocalDateTime bIni, LocalDateTime bFin) {
        LocalDateTime ini = aIni.isAfter(bIni) ? aIni : bIni;
        LocalDateTime fin = aFin.isBefore(bFin) ? aFin : bFin;
        return fin.isAfter(ini) ? Duration.between(ini, fin).toMinutes() : 0;
    }

    private int nz(Integer v) { return v != null ? v : 0; }
    private Long empleadoId(EmpleadoEntity e) { return e.getId(); }

    // ─── Mapeos ───────────────────────────────────────────────────────────────

    private MarcajeDto toMarcajeDto(AsistenciaMarcajeEntity m) {
        MarcajeDto dto = new MarcajeDto();
        dto.setId(m.getId());
        dto.setEmpleadoId(m.getEmpleado().getId());
        dto.setEmpleadoNombre(m.getEmpleado().getNombres() + " " + m.getEmpleado().getApellidos());
        dto.setFecha(m.getFecha());
        dto.setFechaHoraMarcaje(m.getFechaHoraMarcaje());
        dto.setTipoMarcaje(m.getTipoMarcaje());
        dto.setOrigenMarcaje(m.getOrigenMarcaje());
        dto.setRegistradoPor(m.getRegistradoPor());
        dto.setObservacion(m.getObservacion());
        dto.setEvidenciaUrl(m.getEvidenciaUrl());
        dto.setEstado(m.getEstado());
        return dto;
    }

    private AsistenciaDiaDto toDiaDto(AsistenciaDiaEntity d) {
        AsistenciaDiaDto dto = new AsistenciaDiaDto();
        dto.setId(d.getId());
        dto.setEmpleadoId(d.getEmpleado().getId());
        dto.setEmpleadoNombre(d.getEmpleado().getNombres() + " " + d.getEmpleado().getApellidos());
        dto.setFecha(d.getFecha());
        if (d.getTurno() != null) {
            dto.setTurnoId(d.getTurno().getId());
            dto.setTurnoNombre(d.getTurno().getNombre());
        }
        dto.setHoraEntradaProgramada(d.getHoraEntradaProgramada());
        dto.setHoraSalidaProgramada(d.getHoraSalidaProgramada());
        dto.setHoraEntradaReal(d.getHoraEntradaReal());
        dto.setHoraSalidaReal(d.getHoraSalidaReal());
        dto.setMinutosProgramados(d.getMinutosProgramados());
        dto.setMinutosTrabajados(d.getMinutosTrabajados());
        dto.setMinutosTarde(d.getMinutosTarde());
        dto.setMinutosSalidaTemprana(d.getMinutosSalidaTemprana());
        dto.setMinutosExtraDiurna(d.getMinutosExtraDiurna());
        dto.setMinutosExtraNocturna(d.getMinutosExtraNocturna());
        dto.setMinutosDominicalFestiva(d.getMinutosDominicalFestiva());
        dto.setMinutosNocturnos(d.getMinutosNocturnos());
        dto.setEstadoAsistencia(d.getEstadoAsistencia());
        dto.setEstadoAprobacion(d.getEstadoAprobacion());
        dto.setObservacion(d.getObservacion());
        return dto;
    }
}

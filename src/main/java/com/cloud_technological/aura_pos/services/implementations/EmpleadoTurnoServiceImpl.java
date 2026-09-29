package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.CreateEmpleadoTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.EmpleadoTurnoDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoTurnoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TurnoTrabajoEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.EmpleadoTurnoJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.TurnoTrabajoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.services.EmpleadoTurnoService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class EmpleadoTurnoServiceImpl implements EmpleadoTurnoService {

    @Autowired
    private EmpleadoTurnoJPARepository empleadoTurnoRepo;

    @Autowired
    private TurnoTrabajoJPARepository turnoRepo;

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Override
    public List<EmpleadoTurnoDto> listarPorEmpleado(Long empleadoId, Integer empresaId) {
        return empleadoTurnoRepo
                .findByEmpresaIdAndEmpleadoIdOrderByFechaInicioDesc(empresaId, empleadoId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EmpleadoTurnoDto asignar(CreateEmpleadoTurnoDto dto, Integer empresaId) {
        if (dto.getEmpleadoId() == null || dto.getTurnoId() == null || dto.getFechaInicio() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Empleado, turno y fecha de inicio son obligatorios");
        if (dto.getFechaFin() != null && dto.getFechaFin().isBefore(dto.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha fin no puede ser anterior a la de inicio");

        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(dto.getEmpleadoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        TurnoTrabajoEntity turno = turnoRepo.findByIdAndEmpresaId(dto.getTurnoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));

        EmpleadoTurnoEntity entity = new EmpleadoTurnoEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        entity.setEmpleado(empleado);
        entity.setTurno(turno);
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        if (dto.getDiasSemana() != null && !dto.getDiasSemana().isBlank())
            entity.setDiasSemana(dto.getDiasSemana());
        entity.setCreatedAt(LocalDateTime.now());

        return toDto(empleadoTurnoRepo.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        EmpleadoTurnoEntity entity = empleadoTurnoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Asignación no encontrada"));
        entity.setActivo(false);
        empleadoTurnoRepo.save(entity);
    }

    private EmpleadoTurnoDto toDto(EmpleadoTurnoEntity e) {
        EmpleadoTurnoDto dto = new EmpleadoTurnoDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleado().getId());
        dto.setEmpleadoNombre(e.getEmpleado().getNombres() + " " + e.getEmpleado().getApellidos());
        dto.setTurnoId(e.getTurno().getId());
        dto.setTurnoNombre(e.getTurno().getNombre());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setDiasSemana(e.getDiasSemana());
        dto.setActivo(e.getActivo());
        return dto;
    }
}

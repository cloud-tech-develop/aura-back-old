package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.proyecto.AsignarFrenteTurnoDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTurnoDto;
import com.cloud_technological.aura_pos.entity.FrenteTurnoEntity;
import com.cloud_technological.aura_pos.entity.ProyectoFrenteEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.TurnoTrabajoJPARepository;
import com.cloud_technological.aura_pos.repositories.laboral.FrenteTurnoJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Gestión del turno vigente por frente (historial por vigencia). */
@Service
public class FrenteTurnoService {

    @Autowired private FrenteTurnoJPARepository repo;
    @Autowired private TurnoTrabajoJPARepository turnoRepo;
    @Autowired private ProyectoFrenteJPARepository frenteRepo;

    public List<FrenteTurnoDto> listar(Long frenteId, Integer empresaId) {
        return repo.findByFrenteIdAndDeletedAtIsNull(frenteId).stream()
                .filter(ft -> empresaId.equals(ft.getEmpresaId()))
                .sorted(Comparator.comparing(FrenteTurnoEntity::getFechaInicio,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FrenteTurnoDto asignar(Long frenteId, AsignarFrenteTurnoDto dto, Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity frente = frenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(frenteId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Frente no encontrado"));
        turnoRepo.findByIdAndEmpresaId(dto.getTurnoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Turno no encontrado"));

        LocalDate inicio = dto.getFechaInicio() != null ? dto.getFechaInicio() : LocalDate.now();

        // Cerrar la vigencia abierta anterior (fecha_fin null) el día antes de la nueva.
        repo.findByFrenteIdAndDeletedAtIsNull(frenteId).stream()
                .filter(ft -> ft.getFechaFin() == null)
                .filter(ft -> ft.getFechaInicio() == null || ft.getFechaInicio().isBefore(inicio))
                .forEach(ft -> {
                    ft.setFechaFin(inicio.minusDays(1));
                    ft.setUpdatedBy(usuarioId);
                    repo.save(ft);
                });

        FrenteTurnoEntity e = new FrenteTurnoEntity();
        e.setEmpresaId(empresaId);
        e.setProyectoId(frente.getProyectoId());
        e.setFrenteId(frenteId);
        e.setTurnoId(dto.getTurnoId());
        e.setFechaInicio(inicio);
        e.setFechaFin(dto.getFechaFin());
        e.setCreatedBy(usuarioId);
        return toDto(repo.save(e));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId, Long usuarioId) {
        FrenteTurnoEntity e = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Asignación de turno no encontrada"));
        e.setDeletedAt(LocalDateTime.now());
        e.setDeletedBy(usuarioId);
        repo.save(e);
    }

    private FrenteTurnoDto toDto(FrenteTurnoEntity e) {
        FrenteTurnoDto d = new FrenteTurnoDto();
        d.setId(e.getId());
        d.setTurnoId(e.getTurnoId());
        d.setFechaInicio(e.getFechaInicio());
        d.setFechaFin(e.getFechaFin());
        turnoRepo.findById(e.getTurnoId()).ifPresent(t -> d.setTurnoNombre(t.getNombre()));
        return d;
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.CreateTurnoDto;
import com.cloud_technological.aura_pos.dto.asistencia.TurnoDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TurnoTrabajoEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.TurnoTrabajoJPARepository;
import com.cloud_technological.aura_pos.services.TurnoService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class TurnoServiceImpl implements TurnoService {

    @Autowired
    private TurnoTrabajoJPARepository turnoRepo;

    @Override
    public List<TurnoDto> listar(Integer empresaId, boolean soloActivos) {
        List<TurnoTrabajoEntity> turnos = soloActivos
                ? turnoRepo.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresaId)
                : turnoRepo.findByEmpresaIdOrderByNombreAsc(empresaId);
        return turnos.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public TurnoDto obtener(Long id, Integer empresaId) {
        return toDto(buscar(id, empresaId));
    }

    @Override
    @Transactional
    public TurnoDto crear(CreateTurnoDto dto, Integer empresaId) {
        validar(dto);
        TurnoTrabajoEntity entity = new TurnoTrabajoEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        mapFromDto(dto, entity);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(turnoRepo.save(entity));
    }

    @Override
    @Transactional
    public TurnoDto actualizar(Long id, CreateTurnoDto dto, Integer empresaId) {
        validar(dto);
        TurnoTrabajoEntity entity = buscar(id, empresaId);
        mapFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(turnoRepo.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        TurnoTrabajoEntity entity = buscar(id, empresaId);
        entity.setActivo(false);
        entity.setUpdatedAt(LocalDateTime.now());
        turnoRepo.save(entity);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private TurnoTrabajoEntity buscar(Long id, Integer empresaId) {
        return turnoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Turno no encontrado"));
    }

    private void validar(CreateTurnoDto dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre del turno es obligatorio");
        if (dto.getHoraInicio() == null || dto.getHoraFin() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Hora de inicio y fin son obligatorias");
    }

    private void mapFromDto(CreateTurnoDto dto, TurnoTrabajoEntity entity) {
        entity.setNombre(dto.getNombre());
        entity.setHoraInicio(dto.getHoraInicio());
        entity.setHoraFin(dto.getHoraFin());
        entity.setMinutosDescanso(dto.getMinutosDescanso() != null ? dto.getMinutosDescanso() : 0);
        entity.setToleraLlegadaTardeMin(dto.getToleraLlegadaTardeMin() != null ? dto.getToleraLlegadaTardeMin() : 0);
        // Detecta automáticamente el cruce de medianoche si no viene explícito.
        boolean cruza = dto.getCruzaMedianoche() != null
                ? dto.getCruzaMedianoche()
                : dto.getHoraFin().isBefore(dto.getHoraInicio());
        entity.setCruzaMedianoche(cruza);
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());
    }

    private TurnoDto toDto(TurnoTrabajoEntity e) {
        TurnoDto dto = new TurnoDto();
        dto.setId(e.getId());
        dto.setNombre(e.getNombre());
        dto.setHoraInicio(e.getHoraInicio());
        dto.setHoraFin(e.getHoraFin());
        dto.setMinutosDescanso(e.getMinutosDescanso());
        dto.setToleraLlegadaTardeMin(e.getToleraLlegadaTardeMin());
        dto.setCruzaMedianoche(e.getCruzaMedianoche());
        dto.setActivo(e.getActivo());
        return dto;
    }
}

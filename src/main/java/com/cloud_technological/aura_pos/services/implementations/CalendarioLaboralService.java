package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.laboral.CalendarioDiaDto;
import com.cloud_technological.aura_pos.entity.CalendarioLaboralEntity;
import com.cloud_technological.aura_pos.repositories.laboral.CalendarioLaboralJPARepository;
import com.cloud_technological.aura_pos.utils.FestivosColombiaUtil;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class CalendarioLaboralService {

    @Autowired private CalendarioLaboralJPARepository repo;

    public List<CalendarioDiaDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return repo.findByEmpresaIdAndFechaBetweenAndDeletedAtIsNullOrderByFechaAsc(empresaId, desde, hasta)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public CalendarioDiaDto guardar(CalendarioDiaDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getFecha() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha es obligatoria");
        }
        CalendarioLaboralEntity e;
        if (dto.getId() != null) {
            e = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Día no encontrado"));
        } else {
            // Si ya existe un día para esa fecha, se actualiza (única por empresa+fecha).
            e = repo.findByEmpresaIdAndFechaAndDeletedAtIsNull(empresaId, dto.getFecha())
                    .orElseGet(CalendarioLaboralEntity::new);
        }

        e.setEmpresaId(empresaId);
        e.setFecha(dto.getFecha());
        e.setTipoDia(dto.getTipoDia() != null ? dto.getTipoDia() : "FESTIVO_REGIONAL");
        e.setNombre(dto.getNombre());
        e.setAplicaRecargo(dto.getAplicaRecargo() != null ? dto.getAplicaRecargo() : Boolean.TRUE);
        e.setEsFestivoNacional(Boolean.TRUE.equals(dto.getEsFestivoNacional()));
        e.setEsFestivoRegional(Boolean.TRUE.equals(dto.getEsFestivoRegional()));
        e.setEsDescansoEmpresa(Boolean.TRUE.equals(dto.getEsDescansoEmpresa()));
        e.setOrigen(dto.getOrigen() != null ? dto.getOrigen() : "MANUAL");
        if (e.getId() == null) e.setCreatedBy(usuarioId); else e.setUpdatedBy(usuarioId);

        return toDto(repo.save(e));
    }

    @Transactional
    public void anular(Long id, Integer empresaId, Long usuarioId) {
        CalendarioLaboralEntity e = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Día no encontrado"));
        e.setDeletedAt(LocalDateTime.now());
        e.setDeletedBy(usuarioId);
        repo.save(e);
    }

    /** Carga los festivos nacionales de Colombia del año (idempotente: omite los ya existentes). */
    @Transactional
    public int cargarFestivos(int anio, Integer empresaId, Long usuarioId) {
        int creados = 0;
        for (FestivosColombiaUtil.Festivo f : FestivosColombiaUtil.calcular(anio)) {
            if (repo.findByEmpresaIdAndFechaAndDeletedAtIsNull(empresaId, f.fecha).isPresent()) continue;
            CalendarioLaboralEntity e = new CalendarioLaboralEntity();
            e.setEmpresaId(empresaId);
            e.setFecha(f.fecha);
            e.setTipoDia("FESTIVO_NACIONAL");
            e.setNombre(f.nombre);
            e.setAplicaRecargo(Boolean.TRUE);
            e.setEsFestivoNacional(Boolean.TRUE);
            e.setOrigen("SISTEMA");
            e.setCreatedBy(usuarioId);
            repo.save(e);
            creados++;
        }
        return creados;
    }

    private CalendarioDiaDto toDto(CalendarioLaboralEntity e) {
        CalendarioDiaDto d = new CalendarioDiaDto();
        d.setId(e.getId());
        d.setFecha(e.getFecha());
        d.setTipoDia(e.getTipoDia());
        d.setNombre(e.getNombre());
        d.setAplicaRecargo(e.getAplicaRecargo());
        d.setEsFestivoNacional(e.getEsFestivoNacional());
        d.setEsFestivoRegional(e.getEsFestivoRegional());
        d.setEsDescansoEmpresa(e.getEsDescansoEmpresa());
        d.setOrigen(e.getOrigen());
        return d;
    }
}

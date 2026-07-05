package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.laboral.JornadaConfigDto;
import com.cloud_technological.aura_pos.entity.JornadaLaboralConfigEntity;
import com.cloud_technological.aura_pos.repositories.laboral.JornadaLaboralConfigJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** CRUD de la configuración de jornada/recargos por vigencia. */
@Service
public class LaboralConfigService {

    @Autowired private JornadaLaboralConfigJPARepository repo;

    public List<JornadaConfigDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdAndDeletedAtIsNullOrderByFechaInicioVigenciaDesc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Config vigente para una fecha (la de mayor fecha_inicio_vigencia <= fecha y sin fin o fin >= fecha). */
    public JornadaConfigDto vigente(Integer empresaId, LocalDate fecha) {
        return repo.findByEmpresaIdAndDeletedAtIsNullOrderByFechaInicioVigenciaDesc(empresaId).stream()
                .filter(c -> !c.getFechaInicioVigencia().isAfter(fecha))
                .filter(c -> c.getFechaFinVigencia() == null || !c.getFechaFinVigencia().isBefore(fecha))
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public JornadaConfigDto guardar(JornadaConfigDto dto, Integer empresaId, Long usuarioId) {
        JornadaLaboralConfigEntity e = dto.getId() != null
                ? repo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"))
                : new JornadaLaboralConfigEntity();

        if (dto.getFechaInicioVigencia() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de inicio de vigencia es obligatoria");
        }

        e.setEmpresaId(empresaId);
        e.setFechaInicioVigencia(dto.getFechaInicioVigencia());
        e.setFechaFinVigencia(dto.getFechaFinVigencia());
        if (dto.getHorasSemanalesLegales() != null) e.setHorasSemanalesLegales(dto.getHorasSemanalesLegales());
        if (dto.getHorasMensualesBase() != null) e.setHorasMensualesBase(dto.getHorasMensualesBase());
        if (dto.getHoraDiurnaInicio() != null) e.setHoraDiurnaInicio(dto.getHoraDiurnaInicio());
        if (dto.getHoraDiurnaFin() != null) e.setHoraDiurnaFin(dto.getHoraDiurnaFin());
        if (dto.getHoraNocturnaInicio() != null) e.setHoraNocturnaInicio(dto.getHoraNocturnaInicio());
        if (dto.getHoraNocturnaFin() != null) e.setHoraNocturnaFin(dto.getHoraNocturnaFin());
        if (dto.getRecargoNocturno() != null) e.setRecargoNocturno(dto.getRecargoNocturno());
        if (dto.getRecargoExtraDiurna() != null) e.setRecargoExtraDiurna(dto.getRecargoExtraDiurna());
        if (dto.getRecargoExtraNocturna() != null) e.setRecargoExtraNocturna(dto.getRecargoExtraNocturna());
        if (dto.getRecargoDominicalFestivo() != null) e.setRecargoDominicalFestivo(dto.getRecargoDominicalFestivo());
        if (dto.getMaxHorasExtraDia() != null) e.setMaxHorasExtraDia(dto.getMaxHorasExtraDia());
        if (dto.getMaxHorasExtraSemana() != null) e.setMaxHorasExtraSemana(dto.getMaxHorasExtraSemana());
        e.setAplicaExcepcionSectorial(Boolean.TRUE.equals(dto.getAplicaExcepcionSectorial()));
        e.setSectorExcepcion(dto.getSectorExcepcion());
        if (e.getId() == null) e.setCreatedBy(usuarioId); else e.setUpdatedBy(usuarioId);

        return toDto(repo.save(e));
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId, Long usuarioId) {
        JornadaLaboralConfigEntity e = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Configuración no encontrada"));
        e.setDeletedAt(LocalDateTime.now());
        e.setDeletedBy(usuarioId);
        repo.save(e);
    }

    private JornadaConfigDto toDto(JornadaLaboralConfigEntity e) {
        JornadaConfigDto d = new JornadaConfigDto();
        d.setId(e.getId());
        d.setFechaInicioVigencia(e.getFechaInicioVigencia());
        d.setFechaFinVigencia(e.getFechaFinVigencia());
        d.setHorasSemanalesLegales(e.getHorasSemanalesLegales());
        d.setHorasMensualesBase(e.getHorasMensualesBase());
        d.setHoraDiurnaInicio(e.getHoraDiurnaInicio());
        d.setHoraDiurnaFin(e.getHoraDiurnaFin());
        d.setHoraNocturnaInicio(e.getHoraNocturnaInicio());
        d.setHoraNocturnaFin(e.getHoraNocturnaFin());
        d.setRecargoNocturno(e.getRecargoNocturno());
        d.setRecargoExtraDiurna(e.getRecargoExtraDiurna());
        d.setRecargoExtraNocturna(e.getRecargoExtraNocturna());
        d.setRecargoDominicalFestivo(e.getRecargoDominicalFestivo());
        d.setMaxHorasExtraDia(e.getMaxHorasExtraDia());
        d.setMaxHorasExtraSemana(e.getMaxHorasExtraSemana());
        d.setAplicaExcepcionSectorial(e.getAplicaExcepcionSectorial());
        d.setSectorExcepcion(e.getSectorExcepcion());
        return d;
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.periodo.CreatePeriodoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.periodo.PeriodoNominaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.PeriodoNominaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class PeriodoNominaServiceImpl implements PeriodoNominaService {

    @Autowired
    private PeriodoNominaJPARepository periodoRepo;

    @Override
    public List<PeriodoNominaDto> listar(Integer empresaId) {
        return periodoRepo.findByEmpresaIdOrderByIdDesc(empresaId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PeriodoNominaDto crear(CreatePeriodoNominaDto dto, Integer empresaId) {
        if (dto.getFechaInicio() == null || dto.getFechaFin() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Las fechas de inicio y fin son requeridas");

        if (dto.getFechaFin().isBefore(dto.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha fin no puede ser menor a la fecha inicio");

        PeriodoNominaEntity entity = new PeriodoNominaEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setEstado("ABIERTO");
        entity.setCreatedAt(LocalDateTime.now());

        return toDto(periodoRepo.save(entity));
    }

    @Override
    @Transactional
    public PeriodoNominaDto anular(Long id, Integer empresaId) {
        PeriodoNominaEntity entity = periodoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        if ("PAGADO".equals(entity.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede anular un período ya pagado");

        entity.setEstado("ANULADO");
        return toDto(periodoRepo.save(entity));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PeriodoNominaDto toDto(PeriodoNominaEntity entity) {
        PeriodoNominaDto dto = new PeriodoNominaDto();
        dto.setId(entity.getId());
        dto.setFechaInicio(entity.getFechaInicio());
        dto.setFechaFin(entity.getFechaFin());
        dto.setEstado(entity.getEstado());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}

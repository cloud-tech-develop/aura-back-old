package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.asistencia.CrearPeriodoAsistenciaDto;
import com.cloud_technological.aura_pos.dto.asistencia.PeriodoAsistenciaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoAsistenciaEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaIncidenciaJPARepository;
import com.cloud_technological.aura_pos.repositories.asistencia.PeriodoAsistenciaJPARepository;
import com.cloud_technological.aura_pos.services.PeriodoAsistenciaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

@Service
public class PeriodoAsistenciaServiceImpl implements PeriodoAsistenciaService {

    @Autowired
    private PeriodoAsistenciaJPARepository periodoRepo;

    @Autowired
    private AsistenciaIncidenciaJPARepository incidenciaRepo;

    @Override
    public List<PeriodoAsistenciaDto> listar(Integer empresaId) {
        return periodoRepo.findByEmpresaIdOrderByFechaInicioDesc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PeriodoAsistenciaDto crear(CrearPeriodoAsistenciaDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getFechaInicio() == null || dto.getFechaFin() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Fecha inicio y fin son obligatorias");
        if (dto.getFechaFin().isBefore(dto.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha fin no puede ser anterior a la de inicio");

        PeriodoAsistenciaEntity p = new PeriodoAsistenciaEntity();
        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        p.setEmpresa(empresa);
        if (dto.getPeriodoNominaId() != null) {
            PeriodoNominaEntity pn = new PeriodoNominaEntity();
            pn.setId(dto.getPeriodoNominaId());
            p.setPeriodoNomina(pn);
        }
        p.setFechaInicio(dto.getFechaInicio());
        p.setFechaFin(dto.getFechaFin());
        p.setEstado("ABIERTO");
        p.setCreadoPor(usuarioId != null ? usuarioId.intValue() : null);
        p.setFechaCreacion(LocalDateTime.now());
        return toDto(periodoRepo.save(p));
    }

    @Override
    @Transactional
    public PeriodoAsistenciaDto cerrar(Long id, Integer empresaId, Long usuarioId) {
        PeriodoAsistenciaEntity p = buscar(id, empresaId);
        if (!"ABIERTO".equals(p.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo un período ABIERTO puede pasar a revisión");
        p.setEstado("EN_REVISION");
        p.setCerradoPor(usuarioId != null ? usuarioId.intValue() : null);
        p.setFechaCierre(LocalDateTime.now());
        return toDto(periodoRepo.save(p));
    }

    @Override
    @Transactional
    public PeriodoAsistenciaDto aprobar(Long id, Integer empresaId, Long usuarioId) {
        PeriodoAsistenciaEntity p = buscar(id, empresaId);
        if (!"EN_REVISION".equals(p.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo un período EN_REVISION puede aprobarse");

        long pendientes = incidenciaRepo.countByEmpresaIdAndFechaBetweenAndEstado(
                empresaId, p.getFechaInicio(), p.getFechaFin(), "PENDIENTE_REVISION");
        if (pendientes > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede aprobar: hay " + pendientes + " incidencia(s) pendiente(s) de revisión");

        p.setEstado("APROBADO");
        p.setAprobadoPor(usuarioId != null ? usuarioId.intValue() : null);
        p.setFechaAprobacion(LocalDateTime.now());
        return toDto(periodoRepo.save(p));
    }

    @Override
    @Transactional
    public PeriodoAsistenciaDto enviarANomina(Long id, Integer empresaId) {
        PeriodoAsistenciaEntity p = buscar(id, empresaId);
        if (!"APROBADO".equals(p.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo un período APROBADO puede enviarse a nómina");
        p.setEstado("ENVIADO_A_NOMINA");
        return toDto(periodoRepo.save(p));
    }

    private PeriodoAsistenciaEntity buscar(Long id, Integer empresaId) {
        return periodoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período de asistencia no encontrado"));
    }

    private PeriodoAsistenciaDto toDto(PeriodoAsistenciaEntity p) {
        PeriodoAsistenciaDto dto = new PeriodoAsistenciaDto();
        dto.setId(p.getId());
        if (p.getPeriodoNomina() != null) dto.setPeriodoNominaId(p.getPeriodoNomina().getId());
        dto.setFechaInicio(p.getFechaInicio());
        dto.setFechaFin(p.getFechaFin());
        dto.setEstado(p.getEstado());
        return dto;
    }
}

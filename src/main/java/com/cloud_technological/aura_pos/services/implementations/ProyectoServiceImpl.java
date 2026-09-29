package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.proyecto.CreateProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateProyectoDto;
import com.cloud_technological.aura_pos.entity.ProyectoEntity;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoQueryRepository;
import com.cloud_technological.aura_pos.services.ProyectoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProyectoServiceImpl implements ProyectoService {

    @Autowired private ProyectoJPARepository jpaRepo;
    @Autowired private ProyectoQueryRepository queryRepo;

    @Override
    public PageImpl<ProyectoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.listar(pageable, empresaId);
    }

    @Override
    public ProyectoTableDto obtenerPorId(Long id, Integer empresaId) {
        ProyectoEntity e = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        return toTableDto(e);
    }

    @Override
    @Transactional
    public ProyectoTableDto crear(CreateProyectoDto dto, Integer empresaId, Long usuarioId) {
        String codigo = dto.getCodigo().trim().toUpperCase();
        if (jpaRepo.existsByCodigoAndEmpresaIdAndDeletedAtIsNull(codigo, empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un proyecto con el código: " + codigo);
        }

        ProyectoEntity e = new ProyectoEntity();
        e.setEmpresaId(empresaId);
        e.setCodigo(codigo);
        e.setNombre(dto.getNombre().trim());
        e.setClienteId(dto.getClienteId());
        e.setDescripcion(dto.getDescripcion());
        e.setFechaInicio(dto.getFechaInicio());
        e.setFechaFin(dto.getFechaFin());
        e.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        e.setCentroCostoId(dto.getCentroCostoId());
        e.setResponsableAdministrativoId(dto.getResponsableAdministrativoId());
        e.setRequiereControlAsistencia(dto.getRequiereControlAsistencia() != null
                ? dto.getRequiereControlAsistencia() : Boolean.TRUE);
        e.setCiudad(dto.getCiudad());
        e.setUbicacion(dto.getUbicacion());
        e.setObservacion(dto.getObservacion());
        e.setCreatedBy(usuarioId);

        return toTableDto(jpaRepo.save(e));
    }

    @Override
    @Transactional
    public ProyectoTableDto actualizar(Long id, UpdateProyectoDto dto, Integer empresaId, Long usuarioId) {
        ProyectoEntity e = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));

        String codigo = dto.getCodigo().trim().toUpperCase();
        if (!e.getCodigo().equalsIgnoreCase(codigo)
                && jpaRepo.existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(codigo, empresaId, id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código ya está en uso por otro proyecto");
        }

        e.setCodigo(codigo);
        e.setNombre(dto.getNombre().trim());
        e.setClienteId(dto.getClienteId());
        e.setDescripcion(dto.getDescripcion());
        e.setFechaInicio(dto.getFechaInicio());
        e.setFechaFin(dto.getFechaFin());
        if (dto.getEstado() != null) e.setEstado(dto.getEstado());
        e.setCentroCostoId(dto.getCentroCostoId());
        e.setResponsableAdministrativoId(dto.getResponsableAdministrativoId());
        if (dto.getRequiereControlAsistencia() != null)
            e.setRequiereControlAsistencia(dto.getRequiereControlAsistencia());
        e.setCiudad(dto.getCiudad());
        e.setUbicacion(dto.getUbicacion());
        e.setObservacion(dto.getObservacion());
        e.setUpdatedBy(usuarioId);

        return toTableDto(jpaRepo.save(e));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId, Long usuarioId) {
        ProyectoEntity e = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        e.setDeletedAt(LocalDateTime.now());
        e.setDeletedBy(usuarioId);
        e.setEstado("ANULADO");
        jpaRepo.save(e);
    }

    @Override
    public List<ProyectoDto> list(Integer empresaId) {
        return queryRepo.list(empresaId);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private ProyectoTableDto toTableDto(ProyectoEntity e) {
        ProyectoTableDto d = new ProyectoTableDto();
        d.setId(e.getId());
        d.setCodigo(e.getCodigo());
        d.setNombre(e.getNombre());
        d.setClienteId(e.getClienteId());
        d.setDescripcion(e.getDescripcion());
        d.setFechaInicio(e.getFechaInicio());
        d.setFechaFin(e.getFechaFin());
        d.setEstado(e.getEstado());
        d.setCentroCostoId(e.getCentroCostoId());
        d.setResponsableAdministrativoId(e.getResponsableAdministrativoId());
        d.setRequiereControlAsistencia(e.getRequiereControlAsistencia());
        d.setCiudad(e.getCiudad());
        d.setUbicacion(e.getUbicacion());
        d.setObservacion(e.getObservacion());
        return d;
    }
}

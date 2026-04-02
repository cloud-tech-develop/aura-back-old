package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.CreateSubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateSubmoduloDto;
import com.cloud_technological.aura_pos.entity.ModuloEntity;
import com.cloud_technological.aura_pos.entity.SubmoduloEntity;
import com.cloud_technological.aura_pos.repositories.platform.ModuloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.SubmoduloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.services.SubmoduloService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmoduloServiceImpl implements SubmoduloService {

    private final SubmoduloJPARepository submoduloJPARepository;
    private final ModuloJPARepository moduloJPARepository;
    private final ModuloQueryRepository moduloQueryRepository;

    @Override
    @Transactional
    public SubmoduloTableDto crear(CreateSubmoduloDto dto) {
        if (submoduloJPARepository.existsByCodigo(dto.getCodigo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un submódulo con este código");
        }

        ModuloEntity modulo = moduloJPARepository.findById(dto.getModuloId())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));

        SubmoduloEntity entity = new SubmoduloEntity();
        entity.setModulo(modulo);
        entity.setNombre(dto.getNombre());
        entity.setCodigo(dto.getCodigo());
        entity.setDescripcion(dto.getDescripcion());
        entity.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        entity.setActivo(true);

        SubmoduloEntity saved = submoduloJPARepository.save(entity);
        return toTableDto(saved);
    }

    @Override
    @Transactional
    public SubmoduloTableDto actualizar(Integer id, UpdateSubmoduloDto dto) {
        SubmoduloEntity entity = submoduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado"));

        if (dto.getModuloId() != null && !dto.getModuloId().equals(entity.getModulo().getId())) {
            ModuloEntity modulo = moduloJPARepository.findById(dto.getModuloId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));
            entity.setModulo(modulo);
        }

        if (dto.getCodigo() != null && !dto.getCodigo().equals(entity.getCodigo())) {
            if (submoduloJPARepository.existsByCodigo(dto.getCodigo())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un submódulo con este código");
            }
            entity.setCodigo(dto.getCodigo());
        }

        if (dto.getNombre() != null) entity.setNombre(dto.getNombre());
        if (dto.getDescripcion() != null) entity.setDescripcion(dto.getDescripcion());
        if (dto.getOrden() != null) entity.setOrden(dto.getOrden());
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());

        SubmoduloEntity saved = submoduloJPARepository.save(entity);
        return toTableDto(saved);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        if (!submoduloJPARepository.existsById(id)) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado");
        }
        submoduloJPARepository.deleteById(id);
    }

    @Override
    public SubmoduloDto obtenerPorId(Integer id) {
        SubmoduloEntity entity = submoduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado"));
        return toDto(entity);
    }

    @Override
    public List<SubmoduloTableDto> listarPorModulo(Integer moduloId) {
        return moduloQueryRepository.listarSubmodulosPorModulo(moduloId);
    }

    private SubmoduloTableDto toTableDto(SubmoduloEntity entity) {
        SubmoduloTableDto dto = new SubmoduloTableDto();
        dto.setId(entity.getId());
        dto.setModuloId(entity.getModulo().getId());
        dto.setModuloNombre(entity.getModulo().getNombre());
        dto.setNombre(entity.getNombre());
        dto.setCodigo(entity.getCodigo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        dto.setOrden(entity.getOrden());
        return dto;
    }

    private SubmoduloDto toDto(SubmoduloEntity entity) {
        SubmoduloDto dto = new SubmoduloDto();
        dto.setId(entity.getId());
        dto.setModuloId(entity.getModulo().getId());
        dto.setModuloNombre(entity.getModulo().getNombre());
        dto.setNombre(entity.getNombre());
        dto.setCodigo(entity.getCodigo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        dto.setOrden(entity.getOrden());
        return dto;
    }
}

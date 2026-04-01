package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.CreateModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloDto;
import com.cloud_technological.aura_pos.dto.permisos.ModuloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateModuloDto;
import com.cloud_technological.aura_pos.entity.ModuloEntity;
import com.cloud_technological.aura_pos.mappers.ModuloMapper;
import com.cloud_technological.aura_pos.repositories.platform.ModuloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.services.ModuloService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModuloServiceImpl implements ModuloService {

    private final ModuloJPARepository moduloJPARepository;
    private final ModuloQueryRepository moduloQueryRepository;
    private final ModuloMapper moduloMapper;

    @Override
    @Transactional
    public ModuloTableDto crear(CreateModuloDto dto) {
        if (moduloJPARepository.existsByCodigo(dto.getCodigo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un módulo con este código");
        }

        ModuloEntity entity = moduloMapper.toEntity(dto);
        if (entity.getOrden() == null) {
            entity.setOrden(0);
        }
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }

        ModuloEntity saved = moduloJPARepository.save(entity);
        return moduloMapper.toTableDto(saved);
    }

    @Override
    @Transactional
    public ModuloTableDto actualizar(Integer id, UpdateModuloDto dto) {
        ModuloEntity entity = moduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));

        // Validar código único si se cambia
        if (dto.getCodigo() != null && !dto.getCodigo().equals(entity.getCodigo())) {
            if (moduloJPARepository.existsByCodigo(dto.getCodigo())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un módulo con este código");
            }
        }

        moduloMapper.updateEntityFromDto(dto, entity);
        ModuloEntity saved = moduloJPARepository.save(entity);
        return moduloMapper.toTableDto(saved);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        if (!moduloJPARepository.existsById(id)) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado");
        }
        moduloJPARepository.deleteById(id);
    }

    @Override
    public ModuloDto obtenerPorId(Integer id) {
        ModuloEntity entity = moduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));
        return moduloMapper.toDto(entity);
    }

    @Override
    public PageImpl<ModuloTableDto> page(PageableDto<Object> pageable) {
        return moduloQueryRepository.listar(pageable);
    }

    @Override
    public List<ModuloTableDto> listar() {
        return moduloQueryRepository.listarAll();
    }
}

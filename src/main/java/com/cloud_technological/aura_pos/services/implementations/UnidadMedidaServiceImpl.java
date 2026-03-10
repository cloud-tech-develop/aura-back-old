package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

import com.cloud_technological.aura_pos.dto.unidad_medida.CreateUnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedida;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UnidadMedidaTableDto;
import com.cloud_technological.aura_pos.dto.unidad_medida.UpdateUnidadMedidaDto;
import com.cloud_technological.aura_pos.entity.UnidadMedidaEntity;
import com.cloud_technological.aura_pos.mappers.UnidadMedidaMapper;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaJPARepository;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaQueryRepository;
import com.cloud_technological.aura_pos.services.UnidadMedidaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class UnidadMedidaServiceImpl implements UnidadMedidaService {

    private final UnidadMedidaQueryRepository unidadMedidaRepository;
    private final UnidadMedidaJPARepository unidadMedidaJPARepository;
    private final UnidadMedidaMapper unidadMedidaMapper;

    @Autowired
    public UnidadMedidaServiceImpl(UnidadMedidaQueryRepository unidadMedidaRepository,
            UnidadMedidaJPARepository unidadMedidaJPARepository,
            UnidadMedidaMapper unidadMedidaMapper) {
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.unidadMedidaJPARepository = unidadMedidaJPARepository;
        this.unidadMedidaMapper = unidadMedidaMapper;
    }

    @Override
    public PageImpl<UnidadMedidaTableDto> listar(PageableDto<Object> pageable) {
        return unidadMedidaRepository.listar(pageable);
    }

    @Override
    public UnidadMedidaDto obtenerPorId(Long id) {
        UnidadMedidaEntity entity = unidadMedidaJPARepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));
        return unidadMedidaMapper.toDto(entity);
    }

    @Override
    @Transactional
    public UnidadMedidaDto crear(CreateUnidadMedidaDto dto) {
        if (unidadMedidaRepository.existeNombre(dto.getNombre()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una unidad de medida con este nombre");

        UnidadMedidaEntity entity = unidadMedidaMapper.toEntity(dto);
        return unidadMedidaMapper.toDto(unidadMedidaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public UnidadMedidaDto actualizar(Long id, UpdateUnidadMedidaDto dto) {
        UnidadMedidaEntity entity = unidadMedidaJPARepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                unidadMedidaRepository.existeNombreExcluyendo(dto.getNombre(), id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso");

        unidadMedidaMapper.updateEntityFromDto(dto, entity);
        return unidadMedidaMapper.toDto(unidadMedidaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        UnidadMedidaEntity entity = unidadMedidaJPARepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));

        entity.setActivo(false);
        unidadMedidaJPARepository.save(entity);
    }
    @Override
    public List<UnidadMedida> list() {
        return unidadMedidaRepository.list();
    }
}
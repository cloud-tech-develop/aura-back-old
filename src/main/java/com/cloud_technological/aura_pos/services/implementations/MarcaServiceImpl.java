package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import com.cloud_technological.aura_pos.dto.marcas.CreateMarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaDto;
import com.cloud_technological.aura_pos.dto.marcas.MarcaTableDto;
import com.cloud_technological.aura_pos.dto.marcas.UpdateMarcaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.MarcaEntity;
import com.cloud_technological.aura_pos.mappers.MarcaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.marcas.MarcaJPARepository;
import com.cloud_technological.aura_pos.repositories.marcas.MarcaQueryRepository;
import com.cloud_technological.aura_pos.services.MarcaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class MarcaServiceImpl implements MarcaService {

    private final MarcaQueryRepository marcaRepository;
    private final MarcaJPARepository marcaJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final MarcaMapper marcaMapper;

    @Autowired
    public MarcaServiceImpl(MarcaQueryRepository marcaRepository,
            MarcaJPARepository marcaJPARepository,
            EmpresaJPARepository empresaRepository,
            MarcaMapper marcaMapper) {
        this.marcaRepository = marcaRepository;
        this.marcaJPARepository = marcaJPARepository;
        this.empresaRepository = empresaRepository;
        this.marcaMapper = marcaMapper;
    }

    @Override
    public PageImpl<MarcaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return marcaRepository.listar(pageable, empresaId);
    }

    @Override
    public MarcaTableDto obtenerPorId(Long id, Integer empresaId) {
        MarcaEntity entity = marcaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Marca no encontrada"));
        return marcaMapper.toTableDto(entity);
    }

    @Override
    @Transactional
    public MarcaTableDto crear(CreateMarcaDto dto, Integer empresaId) {
        if (marcaRepository.existeNombre(dto.getNombre(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una marca con este nombre");

        MarcaEntity entity = marcaMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);

        return marcaMapper.toTableDto(marcaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public MarcaTableDto actualizar(Long id, UpdateMarcaDto dto, Integer empresaId) {
        MarcaEntity entity = marcaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Marca no encontrada"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                marcaRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso por otra marca");

        marcaMapper.updateEntityFromDto(dto, entity);
        return marcaMapper.toTableDto(marcaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        MarcaEntity entity = marcaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Marca no encontrada"));

        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        marcaJPARepository.save(entity);
    }
    @Override
    public List<MarcaDto> list(Integer empresaId) {
        return marcaRepository.list(empresaId);
    }
}
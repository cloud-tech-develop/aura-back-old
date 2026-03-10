package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.sucursal.CreateSucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalDto;
import com.cloud_technological.aura_pos.dto.sucursal.SucursalTableDto;
import com.cloud_technological.aura_pos.dto.sucursal.UpdateSucursalDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.SucursalMapper;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalQueryRepository;
import com.cloud_technological.aura_pos.services.SucursalService;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;


@Service
public class SucursalServiceImpl implements SucursalService{

    private final SucursalJPARepository jpaRepository;
    private final SucursalQueryRepository queryRepository;
    private final SucursalMapper mapper;

    @Autowired
    public SucursalServiceImpl(SucursalJPARepository jpaRepository, SucursalQueryRepository queryRepository, SucursalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.mapper = mapper;
    }
    @Override
    public PageImpl<SucursalTableDto> paginar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.paginar(pageable, empresaId);
    }

    @Override
    public SucursalDto obtenerPorId(Integer id, Integer empresaId) {
        SucursalEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada"));
        return mapper.toDto(entity);
    }

    @Override
    public List<SucursalDto> listarActivas(Integer empresaId) {
        return queryRepository.listarActivas(empresaId);
    }

    @Override
    @Transactional
    public SucursalDto crear(CreateSucursalDto dto, Integer empresaId) {
        if (queryRepository.existeNombre(dto.getNombre(), empresaId)) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre");
        }

        SucursalEntity entity = mapper.toEntity(dto);

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        entity.setActiva(true);
        entity.setConsecutivoActual(1L);

        return mapper.toDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public SucursalDto actualizar(Integer id, UpdateSucursalDto dto, Integer empresaId) {
        SucursalEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada"));

        if (queryRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id)) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre");
        }

        mapper.updateEntity(dto, entity);
        return mapper.toDto(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Integer id, Integer empresaId) {
        SucursalEntity entity = jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada"));
        entity.setActiva(false);
        jpaRepository.save(entity);
    }
}

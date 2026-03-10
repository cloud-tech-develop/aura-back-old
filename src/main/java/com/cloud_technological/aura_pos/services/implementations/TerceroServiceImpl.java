package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTableDto;
import com.cloud_technological.aura_pos.dto.terceros.UpdateTerceroDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.mappers.TerceroMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroQueryRepository;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;


@Service
public class TerceroServiceImpl implements ITerceroService {

    private final TerceroQueryRepository terceroRepository;
    private final TerceroJPARepository terceroJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final TerceroMapper terceroMapper;

    @Autowired
    public TerceroServiceImpl(TerceroQueryRepository terceroRepository,
            TerceroJPARepository terceroJPARepository,
            EmpresaJPARepository empresaRepository,
            TerceroMapper terceroMapper) {
        this.terceroRepository = terceroRepository;
        this.terceroJPARepository = terceroJPARepository;
        this.empresaRepository = empresaRepository;
        this.terceroMapper = terceroMapper;
    }

    @Override
    public PageImpl<TerceroTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return terceroRepository.listar(pageable, empresaId);
    }

    @Override
    public TerceroDto obtenerPorId(Long id, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));
        return terceroMapper.toDto(entity);
    }

    @Override
    public List<TerceroTableDto> listarClientes(String search, Integer empresaId) {
        return terceroRepository.listarClientes(search, empresaId);
    }

    @Override
    public List<TerceroTableDto> listarProveedores(String search, Integer empresaId) {
        return terceroRepository.listarProveedores(search, empresaId);
    }

    @Override
    @Transactional
    public TerceroDto crear(CreateTerceroDto dto, Integer empresaId) {
        if (terceroRepository.existeDocumento(dto.getNumeroDocumento(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un tercero con este número de documento");

        // Validar que persona natural tenga nombres o jurídica tenga razón social
        if (dto.getRazonSocial() == null && dto.getNombres() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe ingresar razón social o nombres");

        TerceroEntity entity = terceroMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);
        entity.setCreated_at(LocalDateTime.now());
        entity.setUpdated_at(LocalDateTime.now());

        return terceroMapper.toDto(terceroJPARepository.save(entity));
    }

    @Override
    @Transactional
    public TerceroDto actualizar(Long id, UpdateTerceroDto dto, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        if (!entity.getNumeroDocumento().equals(dto.getNumeroDocumento()) &&
                terceroRepository.existeDocumentoExcluyendo(dto.getNumeroDocumento(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El número de documento ya está en uso");

        terceroMapper.updateEntityFromDto(dto, entity);
        entity.setUpdated_at(LocalDateTime.now());
        return terceroMapper.toDto(terceroJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        TerceroEntity entity = terceroJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        entity.setDeleted_at(LocalDateTime.now());
        entity.setActivo(false);
        terceroJPARepository.save(entity);
    }
}

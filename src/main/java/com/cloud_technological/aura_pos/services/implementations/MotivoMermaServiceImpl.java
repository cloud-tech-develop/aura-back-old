package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.cloud_technological.aura_pos.dto.merma.CreateMotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MotivoMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MotivoMermaTableDto;
import com.cloud_technological.aura_pos.dto.merma.UpdateMotivoMermaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.MotivoMermaEntity;
import com.cloud_technological.aura_pos.mappers.MotivoMermaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.motivo_merma.MotivoMermaJPARepository;
import com.cloud_technological.aura_pos.repositories.motivo_merma.MotivoMermaQueryRepository;
import com.cloud_technological.aura_pos.services.MotivoMermaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;


@Service
public class MotivoMermaServiceImpl implements MotivoMermaService {
    private final MotivoMermaQueryRepository motivoRepository;
    private final MotivoMermaJPARepository motivoJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final MotivoMermaMapper motivoMapper;

    @Autowired
    public MotivoMermaServiceImpl(MotivoMermaQueryRepository motivoRepository,
            MotivoMermaJPARepository motivoJPARepository,
            EmpresaJPARepository empresaRepository,
            MotivoMermaMapper motivoMapper) {
        this.motivoRepository = motivoRepository;
        this.motivoJPARepository = motivoJPARepository;
        this.empresaRepository = empresaRepository;
        this.motivoMapper = motivoMapper;
    }

    @Override
    public PageImpl<MotivoMermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return motivoRepository.listar(pageable, empresaId);
    }

    @Override
    public List<MotivoMermaDto> listarTodos(Integer empresaId) {
        return motivoJPARepository.findByEmpresaIdOrderByNombreAsc(empresaId)
                .stream()
                .map(motivoMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public MotivoMermaDto obtenerPorId(Long id, Integer empresaId) {
        MotivoMermaEntity entity = motivoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Motivo no encontrado"));
        return motivoMapper.toDto(entity);
    }

    @Override
    @Transactional
    public MotivoMermaDto crear(CreateMotivoMermaDto dto, Integer empresaId) {
        if (motivoRepository.existeNombre(dto.getNombre(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un motivo con este nombre");

        MotivoMermaEntity entity = motivoMapper.toEntity(dto);
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);
        return motivoMapper.toDto(motivoJPARepository.save(entity));
    }

    @Override
    @Transactional
    public MotivoMermaDto actualizar(Long id, UpdateMotivoMermaDto dto, Integer empresaId) {
        MotivoMermaEntity entity = motivoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Motivo no encontrado"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                motivoRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso");

        motivoMapper.updateEntityFromDto(dto, entity);
        return motivoMapper.toDto(motivoJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        MotivoMermaEntity entity = motivoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Motivo no encontrado"));
        motivoJPARepository.deleteById(entity.getId());
    }
}

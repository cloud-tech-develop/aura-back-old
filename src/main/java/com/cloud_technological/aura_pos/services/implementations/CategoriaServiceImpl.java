package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.CategoriaTableDto;
import com.cloud_technological.aura_pos.dto.categorias.CreateCategoriaDto;
import com.cloud_technological.aura_pos.dto.categorias.UpdateCategoriaDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.mappers.CategoriaMapper;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaQueryRepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.CategoriaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaQueryRepository categoriaRepository;
    private final CategoriaJPARepository categoriaJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final CategoriaMapper categoriaMapper;

    @Autowired
    public CategoriaServiceImpl(CategoriaQueryRepository categoriaRepository,
            EmpresaJPARepository empresaRepository,
            CategoriaMapper categoriaMapper,
            CategoriaJPARepository categoriaJPARepository) {
        this.categoriaRepository = categoriaRepository;
        this.empresaRepository = empresaRepository;
        this.categoriaJPARepository = categoriaJPARepository;
        this.categoriaMapper = categoriaMapper;
    }

    @Override
    public PageImpl<CategoriaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return categoriaRepository.listar(pageable, empresaId);
    }

    @Override
    public CategoriaTableDto obtenerPorId(Long id, Integer empresaId) {
        CategoriaEntity entity = categoriaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        return categoriaMapper.toTableDto(entity);
    }

    @Override
    @Transactional
    public CategoriaTableDto crear(CreateCategoriaDto dto, Integer empresaId) {
        if (categoriaRepository.existeNombre(dto.getNombre(), empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una categoría con este nombre");
        }

        CategoriaEntity entity = categoriaMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);

        if (dto.getPadreId() != null) {
            CategoriaEntity padre = categoriaJPARepository.findByIdAndEmpresaId(dto.getPadreId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La categoría padre no existe"));
            entity.setPadre(padre);
        }

        return categoriaMapper.toTableDto(categoriaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public CategoriaTableDto actualizar(Long id, UpdateCategoriaDto dto, Integer empresaId) {
        CategoriaEntity entity = categoriaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                categoriaRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso por otra categoría");
        }

        if (dto.getPadreId() != null && dto.getPadreId().equals(id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Una categoría no puede ser su propio padre");
        }

        categoriaMapper.updateEntityFromDto(dto, entity);

        if (dto.getPadreId() != null) {
            CategoriaEntity padre = categoriaJPARepository.findByIdAndEmpresaId(dto.getPadreId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La categoría padre no existe"));
            entity.setPadre(padre);
        } else {
            entity.setPadre(null);
        }

        return categoriaMapper.toTableDto(categoriaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        CategoriaEntity entity = categoriaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        categoriaJPARepository.save(entity);
    }
    @Override
    public List<CategoriaDto> list(Integer empresaId) {
        return categoriaRepository.list(empresaId);
    }
}
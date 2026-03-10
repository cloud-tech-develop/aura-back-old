package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ListaPreciosTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateListaPreciosDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.ListaPreciosEntity;
import com.cloud_technological.aura_pos.mappers.ListaPreciosMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ListaPreciosJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ListaPreciosQueryRepository;
import com.cloud_technological.aura_pos.services.ListaPreciosService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ListaPreciosServiceImpl implements ListaPreciosService {

    private final ListaPreciosQueryRepository listaPreciosRepository;
    private final ListaPreciosJPARepository listaPreciosJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final ListaPreciosMapper listaPreciosMapper;

    @Autowired
    public ListaPreciosServiceImpl(ListaPreciosQueryRepository listaPreciosRepository,
            ListaPreciosJPARepository listaPreciosJPARepository,
            EmpresaJPARepository empresaRepository,
            ListaPreciosMapper listaPreciosMapper) {
        this.listaPreciosRepository = listaPreciosRepository;
        this.listaPreciosJPARepository = listaPreciosJPARepository;
        this.empresaRepository = empresaRepository;
        this.listaPreciosMapper = listaPreciosMapper;
    }

    @Override
    public PageImpl<ListaPreciosTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return listaPreciosRepository.listar(pageable, empresaId);
    }

    @Override
    public ListaPreciosDto obtenerPorId(Long id, Integer empresaId) {
        ListaPreciosEntity entity = listaPreciosJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lista de precios no encontrada"));
        return listaPreciosMapper.toDto(entity);
    }

    @Override
    @Transactional
    public ListaPreciosDto crear(CreateListaPreciosDto dto, Integer empresaId) {
        if (listaPreciosRepository.existeNombre(dto.getNombre(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una lista de precios con este nombre");

        ListaPreciosEntity entity = listaPreciosMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);

        return listaPreciosMapper.toDto(listaPreciosJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ListaPreciosDto actualizar(Long id, UpdateListaPreciosDto dto, Integer empresaId) {
        ListaPreciosEntity entity = listaPreciosJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lista de precios no encontrada"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                listaPreciosRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso");

        listaPreciosMapper.updateEntityFromDto(dto, entity);
        return listaPreciosMapper.toDto(listaPreciosJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ListaPreciosEntity entity = listaPreciosJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lista de precios no encontrada"));

        entity.setActiva(false);
        listaPreciosJPARepository.save(entity);
    }
    @Override
    public List<ListaPreciosDto> list(Integer empresaId) {
        return listaPreciosRepository.listar(empresaId);
    }
}
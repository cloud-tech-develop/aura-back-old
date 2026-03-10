package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.reglas_descuento.CreateReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoTableDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.UpdateReglaDescuentoDto;
import com.cloud_technological.aura_pos.entity.CategoriaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ReglaDescuentoEntity;
import com.cloud_technological.aura_pos.mappers.ReglaDescuentoMapper;
import com.cloud_technological.aura_pos.repositories.categorias.CategoriaJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.reglas_descuento.ReglaDescuentoJPARepository;
import com.cloud_technological.aura_pos.repositories.reglas_descuento.ReglaDescuentoQueryRepository;
import com.cloud_technological.aura_pos.services.ReglaDescuentoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ReglaDescuentoServiceImpl implements ReglaDescuentoService {

    private final ReglaDescuentoQueryRepository reglaRepository;
    private final ReglaDescuentoJPARepository reglaJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final CategoriaJPARepository categoriaJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final ReglaDescuentoMapper reglaMapper;

    @Autowired
    public ReglaDescuentoServiceImpl(
            ReglaDescuentoQueryRepository reglaRepository,
            ReglaDescuentoJPARepository reglaJPARepository,
            EmpresaJPARepository empresaRepository,
            CategoriaJPARepository categoriaJPARepository,
            ProductoJPARepository productoJPARepository,
            ReglaDescuentoMapper reglaMapper) {
        this.reglaRepository = reglaRepository;
        this.reglaJPARepository = reglaJPARepository;
        this.empresaRepository = empresaRepository;
        this.categoriaJPARepository = categoriaJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.reglaMapper = reglaMapper;
    }

    @Override
    public PageImpl<ReglaDescuentoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return reglaRepository.listar(pageable, empresaId);
    }

    @Override
    public ReglaDescuentoDto obtenerPorId(Long id, Integer empresaId) {
        ReglaDescuentoEntity entity = reglaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Regla de descuento no encontrada"));
        return reglaMapper.toDto(entity);
    }

    @Override
    @Transactional
    public ReglaDescuentoDto crear(CreateReglaDescuentoDto dto, Integer empresaId) {
        if (reglaRepository.existeNombre(dto.getNombre(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una regla con este nombre");

        // Validar que no aplique a categoría Y producto al mismo tiempo
        if (dto.getCategoriaId() != null && dto.getProductoId() != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La regla debe aplicar a categoría O producto, no ambos");

        // Validar rango de fechas
        if (dto.getFechaInicio() != null && dto.getFechaFin() != null &&
                dto.getFechaInicio().isAfter(dto.getFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede ser mayor a la fecha fin");

        // Validar rango de horas
        if (dto.getHoraInicio() != null && dto.getHoraFin() != null &&
                dto.getHoraInicio().isAfter(dto.getHoraFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La hora de inicio no puede ser mayor a la hora fin");

        ReglaDescuentoEntity entity = reglaMapper.toEntity(dto);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));
        entity.setEmpresa(empresa);

        if (dto.getCategoriaId() != null) {
            CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            entity.setCategoria(categoria);
        }

        if (dto.getProductoId() != null) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));
            entity.setProducto(producto);
        }

        return reglaMapper.toDto(reglaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ReglaDescuentoDto actualizar(Long id, UpdateReglaDescuentoDto dto, Integer empresaId) {
        ReglaDescuentoEntity entity = reglaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Regla de descuento no encontrada"));

        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                reglaRepository.existeNombreExcluyendo(dto.getNombre(), empresaId, id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso");

        if (dto.getCategoriaId() != null && dto.getProductoId() != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La regla debe aplicar a categoría O producto, no ambos");

        if (dto.getFechaInicio() != null && dto.getFechaFin() != null &&
                dto.getFechaInicio().isAfter(dto.getFechaFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede ser mayor a la fecha fin");

        if (dto.getHoraInicio() != null && dto.getHoraFin() != null &&
                dto.getHoraInicio().isAfter(dto.getHoraFin()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La hora de inicio no puede ser mayor a la hora fin");

        reglaMapper.updateEntityFromDto(dto, entity);

        if (dto.getCategoriaId() != null) {
            CategoriaEntity categoria = categoriaJPARepository.findByIdAndEmpresaId(dto.getCategoriaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            entity.setCategoria(categoria);
        } else {
            entity.setCategoria(null);
        }

        if (dto.getProductoId() != null) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));
            entity.setProducto(producto);
        } else {
            entity.setProducto(null);
        }

        return reglaMapper.toDto(reglaJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ReglaDescuentoEntity entity = reglaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Regla de descuento no encontrada"));

        entity.setActivo(false);
        reglaJPARepository.save(entity);
    }
}
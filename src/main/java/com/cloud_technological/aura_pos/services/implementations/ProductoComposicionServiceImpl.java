package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.mappers.ProductoComposicionMapper;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionQueryRepository;
import com.cloud_technological.aura_pos.services.ProductoComposicionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProductoComposicionServiceImpl implements ProductoComposicionService {

    private final ProductoComposicionQueryRepository composicionRepository;
    private final ProductoComposicionJPARepository composicionJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final ProductoComposicionMapper composicionMapper;

    @Autowired
    public ProductoComposicionServiceImpl(
            ProductoComposicionQueryRepository composicionRepository,
            ProductoComposicionJPARepository composicionJPARepository,
            ProductoJPARepository productoJPARepository,
            ProductoComposicionMapper composicionMapper) {
        this.composicionRepository = composicionRepository;
        this.composicionJPARepository = composicionJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.composicionMapper = composicionMapper;
    }

    @Override
    public PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return composicionRepository.listar(pageable, empresaId);
    }

    @Override
    public ProductoComposicionDto obtenerPorId(Long id, Integer empresaId) {
        ProductoComposicionEntity entity = composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));
        return composicionMapper.toDto(entity);
    }

    @Override
    public List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId) {
        return composicionRepository.listarPorPadre(productoPadreId);
    }

    @Override
    @Transactional
    public ProductoComposicionDto crear(CreateProductoComposicionDto dto, Integer empresaId) {
        // No puede ser el mismo producto
        if (dto.getProductoPadreId().equals(dto.getProductoHijoId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El producto padre y el hijo no pueden ser el mismo");

        // Validar que no exista ya la relación
        if (composicionJPARepository.existsByProductoPadreIdAndProductoHijoId(dto.getProductoPadreId(), dto.getProductoHijoId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto hijo ya hace parte de la composición");

        ProductoEntity padre = productoJPARepository.findByIdAndEmpresaId(dto.getProductoPadreId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto padre no encontrado"));

        ProductoEntity hijo = productoJPARepository.findByIdAndEmpresaId(dto.getProductoHijoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto hijo no encontrado"));

        ProductoComposicionEntity entity = composicionMapper.toEntity(dto);
        entity.setProductoPadre(padre);
        entity.setProductoHijo(hijo);

        return composicionMapper.toDto(composicionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoComposicionDto actualizar(Long id, UpdateProductoComposicionDto dto, Integer empresaId) {
        ProductoComposicionEntity entity = composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));

        composicionMapper.updateEntityFromDto(dto, entity);
        return composicionMapper.toDto(composicionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ProductoComposicionEntity entity = composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));
        composicionJPARepository.deleteById(id);
    }
}
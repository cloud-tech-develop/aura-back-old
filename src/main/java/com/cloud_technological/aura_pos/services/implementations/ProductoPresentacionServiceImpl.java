package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.producto_presentacion.CreateProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.ProductoPresentacionTableDto;
import com.cloud_technological.aura_pos.dto.producto_presentacion.UpdateProductoPresentacionDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.mappers.ProductoPresentacionMapper;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.services.ProductoPresentacionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProductoPresentacionServiceImpl implements ProductoPresentacionService {

    private final ProductoPresentacionQueryRepository presentacionRepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final ProductoPresentacionMapper presentacionMapper;

    @Autowired
    public ProductoPresentacionServiceImpl(
            ProductoPresentacionQueryRepository presentacionRepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            ProductoJPARepository productoJPARepository,
            ProductoPresentacionMapper presentacionMapper) {
        this.presentacionRepository = presentacionRepository;
        this.presentacionJPARepository = presentacionJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.presentacionMapper = presentacionMapper;
    }

    @Override
    public PageImpl<ProductoPresentacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return presentacionRepository.listar(pageable, empresaId);
    }

    @Override
    public ProductoPresentacionDto obtenerPorId(Long id, Integer empresaId) {
        ProductoPresentacionEntity entity = presentacionJPARepository.findByIdAndProductoEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));
        return presentacionMapper.toDto(entity);
    }

    @Override
    public List<ProductoPresentacionTableDto> listarPorProducto(Long productoId) {
        return presentacionRepository.listarPorProducto(productoId);
    }

    @Override
    @Transactional
    public ProductoPresentacionDto crear(CreateProductoPresentacionDto dto, Integer empresaId) {
        // Validar código de barras duplicado
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank() &&
                presentacionRepository.existeCodigoBarras(dto.getCodigoBarras()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está registrado");

        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        // Validar factor de conversión duplicado en el mismo producto
        if (presentacionJPARepository.existsByProductoIdAndFactorConversion(
                dto.getProductoId(), dto.getFactorConversion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe una presentación con ese factor de conversión para este producto");

        // Si se marca como default compra → desmarcar las demás del mismo producto
        if (Boolean.TRUE.equals(dto.getEsDefaultCompra())) {
            desmarcarDefaultCompra(dto.getProductoId());
        }

        // Si se marca como default venta → desmarcar las demás del mismo producto
        if (Boolean.TRUE.equals(dto.getEsDefaultVenta())) {
            desmarcarDefaultVenta(dto.getProductoId());
        }

        ProductoPresentacionEntity entity = presentacionMapper.toEntity(dto);
        entity.setProducto(producto);

        return presentacionMapper.toDto(presentacionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoPresentacionDto actualizar(Long id, UpdateProductoPresentacionDto dto, Integer empresaId) {
        ProductoPresentacionEntity entity = presentacionJPARepository.findByIdAndProductoEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));

        // Validar código de barras duplicado excluyendo la actual
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank() &&
                presentacionRepository.existeCodigoBarrasExcluyendo(dto.getCodigoBarras(), id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de barras ya está en uso");

        // Validar factor duplicado excluyendo la actual
        if (presentacionJPARepository.existsByProductoIdAndFactorConversionAndIdNot(
                entity.getProducto().getId(), dto.getFactorConversion(), id))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe una presentación con ese factor de conversión para este producto");

        Long productoId = entity.getProducto().getId();

        // Si se marca como default compra → desmarcar las demás
        if (Boolean.TRUE.equals(dto.getEsDefaultCompra()) &&
                !Boolean.TRUE.equals(entity.getEsDefaultCompra())) {
            desmarcarDefaultCompra(productoId);
        }

        // Si se marca como default venta → desmarcar las demás
        if (Boolean.TRUE.equals(dto.getEsDefaultVenta()) &&
                !Boolean.TRUE.equals(entity.getEsDefaultVenta())) {
            desmarcarDefaultVenta(productoId);
        }

        presentacionMapper.updateEntityFromDto(dto, entity);
        return presentacionMapper.toDto(presentacionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ProductoPresentacionEntity entity = presentacionJPARepository.findByIdAndProductoEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));

        // No permitir eliminar la default de compra o venta si es la única
        if (Boolean.TRUE.equals(entity.getEsDefaultCompra()) ||
                Boolean.TRUE.equals(entity.getEsDefaultVenta())) {
            long total = presentacionJPARepository.countByProductoIdAndActivoTrue(entity.getProducto().getId());
            if (total <= 1)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No puedes eliminar la única presentación activa del producto");
        }

        entity.setActivo(false);
        presentacionJPARepository.save(entity);
    }

    // ─── Helpers privados ────────────────────────────────────────────

    private void desmarcarDefaultCompra(Long productoId) {
        presentacionJPARepository.findByProductoIdAndEsDefaultCompraTrue(productoId)
                .forEach(p -> {
                    p.setEsDefaultCompra(false);
                    presentacionJPARepository.save(p);
                });
    }

    private void desmarcarDefaultVenta(Long productoId) {
        presentacionJPARepository.findByProductoIdAndEsDefaultVentaTrue(productoId)
                .forEach(p -> {
                    p.setEsDefaultVenta(false);
                    presentacionJPARepository.save(p);
                });
    }
}
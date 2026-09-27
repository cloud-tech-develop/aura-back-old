package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
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
        validarFactor(dto.getFactorConversion());
        validarCodigoBarras(dto.getCodigoBarras(), empresaId, null);

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
        if (entity.getSeVende() == null)
            entity.setSeVende(true);

        return presentacionMapper.toDto(presentacionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoPresentacionDto actualizar(Long id, UpdateProductoPresentacionDto dto, Integer empresaId) {
        ProductoPresentacionEntity entity = presentacionJPARepository.findByIdAndProductoEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Presentación no encontrada"));

        validarFactor(dto.getFactorConversion());
        validarCodigoBarras(dto.getCodigoBarras(), empresaId, id);

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

        // Las pantallas que no manejan "se vende" no lo mandan: se conserva.
        Boolean seVendeActual = entity.getSeVende();
        presentacionMapper.updateEntityFromDto(dto, entity);
        if (dto.getSeVende() == null)
            entity.setSeVende(seVendeActual != null ? seVendeActual : true);
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

    /** El factor son las unidades base que contiene la presentación (V159). */
    private void validarFactor(BigDecimal factor) {
        if (factor != null && factor.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cantidad que contiene la presentación debe ser mayor que cero");
    }

    private void validarCodigoBarras(String codigoBarras, Integer empresaId, Long presentacionId) {
        if (codigoBarras == null || codigoBarras.isBlank()) return;
        if (presentacionRepository.codigoBarrasEnUso(codigoBarras, empresaId, presentacionId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El código " + codigoBarras.trim() + " ya lo usa otro producto o presentación de la empresa");
    }

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
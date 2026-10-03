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

    /**
     * Sección "Unidades y conversiones": llega la lista completa y se deja la
     * base igual a ella. Una conversión quitada se desactiva (los documentos
     * viejos la siguen mostrando); una nueva con el factor de una desactivada
     * la reactiva en vez de chocar con "ya existe ese factor".
     */
    @Override
    @Transactional
    public List<ProductoPresentacionTableDto> guardarConversiones(Long productoId,
            com.cloud_technological.aura_pos.dto.producto_presentacion.GuardarConversionesDto dto, Integer empresaId) {
        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(productoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        var filas = dto.getConversiones() != null ? dto.getConversiones()
                : List.<com.cloud_technological.aura_pos.dto.producto_presentacion.GuardarConversionesDto.Conversion>of();

        // Validaciones de la lista completa, antes de tocar nada.
        java.util.Set<BigDecimal> factores = new java.util.HashSet<>();
        int defaultsCompra = 0;
        boolean algunaSeVende = false;
        for (var f : filas) {
            if (f.getNombre() == null || f.getNombre().isBlank())
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Cada conversión necesita un nombre (Paca, Caja, Libra…)");
            validarFactor(f.getFactor());
            if (f.getFactor() == null || f.getFactor().compareTo(BigDecimal.ONE) == 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        f.getNombre().trim() + ": una conversión de 1 es la misma unidad base");
            if (!factores.add(f.getFactor().stripTrailingZeros()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Dos conversiones tienen la misma cantidad (" + f.getFactor().stripTrailingZeros().toPlainString()
                                + "): deje solo una");
            if (Boolean.TRUE.equals(f.getEsDefaultCompra())) defaultsCompra++;
            if (!Boolean.FALSE.equals(f.getSeVende())) algunaSeVende = true;
            validarCodigoBarras(f.getCodigoBarras(), empresaId, f.getId());
        }
        if (defaultsCompra > 1)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo una conversión puede ser la de compra");
        boolean vendeSuelto = !Boolean.FALSE.equals(dto.getVendePorUnidad());
        if (!vendeSuelto && !algunaSeVende && "PRODUCTO".equals(producto.getClasificacion()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El producto no se vende suelto ni en ninguna conversión: marque al menos una forma de venta");

        var existentes = presentacionRepository.todasDelProducto(productoId);
        java.util.Set<Long> conservadas = new java.util.HashSet<>();
        // La presentación que se vende por defecto: si no se vende suelto, la
        // primera que se vende (el POS la ofrece primero).
        boolean defaultVentaAsignada = false;

        for (var f : filas) {
            ProductoPresentacionEntity e = null;
            if (f.getId() != null) {
                e = presentacionJPARepository.findByIdAndProductoEmpresaId(f.getId(), empresaId)
                        .filter(x -> x.getProducto().getId().equals(productoId))
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Conversión no encontrada"));
            } else {
                // Misma cantidad que una existente (activa o no): se reutiliza.
                Long reutilizable = existentes.stream()
                        .filter(x -> x.factor() != null && x.factor().compareTo(f.getFactor()) == 0
                                && !conservadas.contains(x.id()))
                        .map(ProductoPresentacionQueryRepository.PresentacionFactor::id)
                        .findFirst().orElse(null);
                if (reutilizable != null) {
                    e = presentacionJPARepository.findById(reutilizable).orElse(null);
                }
                if (e == null) {
                    e = new ProductoPresentacionEntity();
                    e.setProducto(producto);
                }
            }
            boolean seVende = !Boolean.FALSE.equals(f.getSeVende());
            e.setNombre(f.getNombre().trim());
            e.setFactorConversion(f.getFactor());
            e.setCodigoBarras(f.getCodigoBarras() != null && !f.getCodigoBarras().isBlank()
                    ? f.getCodigoBarras().trim() : null);
            e.setPrecio(f.getPrecio() != null ? f.getPrecio() : BigDecimal.ZERO);
            e.setCosto(f.getCosto() != null ? f.getCosto() : BigDecimal.ZERO);
            e.setSeVende(seVende);
            e.setEsDefaultCompra(Boolean.TRUE.equals(f.getEsDefaultCompra()));
            boolean defaultVenta = !vendeSuelto && seVende && !defaultVentaAsignada;
            e.setEsDefaultVenta(defaultVenta);
            if (defaultVenta) defaultVentaAsignada = true;
            e.setActivo(true);
            e = presentacionJPARepository.save(e);
            conservadas.add(e.getId());
        }

        // Lo que ya no está en la lista se desactiva.
        for (var x : existentes) {
            if (x.activo() && !conservadas.contains(x.id())) {
                presentacionJPARepository.findById(x.id()).ifPresent(e -> {
                    e.setActivo(false);
                    e.setEsDefaultCompra(false);
                    e.setEsDefaultVenta(false);
                    presentacionJPARepository.save(e);
                });
            }
        }

        producto.setVendePorUnidad(vendeSuelto);
        productoJPARepository.save(producto);
        return presentacionRepository.listarPorProducto(productoId);
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
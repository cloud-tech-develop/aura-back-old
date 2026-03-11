package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.lista_precios.CreateProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioDto;
import com.cloud_technological.aura_pos.dto.lista_precios.ProductoPrecioTableDto;
import com.cloud_technological.aura_pos.dto.lista_precios.UpdateProductoPrecioDto;
import com.cloud_technological.aura_pos.entity.ListaPreciosEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ProductoPrecioEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.mappers.ProductoPrecioMapper;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ListaPreciosJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ProductoPrecioJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ProductoPrecioQueryRepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.services.ProductoPrecioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class ProductoPrecioServiceImpl implements ProductoPrecioService {

    private final ProductoPrecioQueryRepository productoPrecioRepository;
    private final ProductoPrecioJPARepository productoPrecioJPARepository;
    private final ListaPreciosJPARepository listaPreciosJPARepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final ProductoPrecioMapper productoPrecioMapper;

    @Autowired
    public ProductoPrecioServiceImpl(
            ProductoPrecioQueryRepository productoPrecioRepository,
            ProductoPrecioJPARepository productoPrecioJPARepository,
            ListaPreciosJPARepository listaPreciosJPARepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            ProductoJPARepository productoJPARepository,
            ProductoPrecioMapper productoPrecioMapper) {
        this.productoPrecioRepository = productoPrecioRepository;
        this.productoPrecioJPARepository = productoPrecioJPARepository;
        this.listaPreciosJPARepository = listaPreciosJPARepository;
        this.presentacionJPARepository = presentacionJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.productoPrecioMapper = productoPrecioMapper;
    }

    @Override
    public PageImpl<ProductoPrecioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return productoPrecioRepository.listar(pageable, empresaId);
    }

    @Override
    public ProductoPrecioDto obtenerPorId(Long id, Integer empresaId) {
        ProductoPrecioEntity entity = productoPrecioJPARepository.findByIdAndListaPrecioEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio no encontrado"));
        return productoPrecioMapper.toDto(entity);
    }

    @Override
    public List<ProductoPrecioTableDto> listarPorLista(Long listaPrecioId) {
        return productoPrecioRepository.listarPorLista(listaPrecioId);
    }

    @Override
    @Transactional
    public ProductoPrecioDto crear(CreateProductoPrecioDto dto, Integer empresaId) {
        if (dto.getProductoPresentacionId() == null && dto.getProductoId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe indicar el producto o la presentación");

        ListaPreciosEntity lista = listaPreciosJPARepository.findByIdAndEmpresaId(dto.getListaPrecioId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lista de precios no encontrada"));

        ProductoPrecioEntity entity = productoPrecioMapper.toEntity(dto);
        entity.setListaPrecio(lista);

        if (dto.getProductoPresentacionId() != null) {
            if (productoPrecioJPARepository.existsByListaPrecioIdAndProductoPresentacionId(
                    dto.getListaPrecioId(), dto.getProductoPresentacionId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto ya tiene precio en esta lista");

            ProductoPresentacionEntity presentacion = presentacionJPARepository
                    .findByIdAndProductoEmpresaId(dto.getProductoPresentacionId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación no encontrada"));
            entity.setProductoPresentacion(presentacion);
        } else {
            if (productoPrecioJPARepository.existsByListaPrecioIdAndProductoId(
                    dto.getListaPrecioId(), dto.getProductoId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto ya tiene precio en esta lista");

            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));
            entity.setProducto(producto);
        }

        return productoPrecioMapper.toDto(productoPrecioJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoPrecioDto actualizar(Long id, UpdateProductoPrecioDto dto, Integer empresaId) {
        ProductoPrecioEntity entity = productoPrecioJPARepository.findByIdAndListaPrecioEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio no encontrado"));

        productoPrecioMapper.updateEntityFromDto(dto, entity);
        return productoPrecioMapper.toDto(productoPrecioJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ProductoPrecioEntity entity = productoPrecioJPARepository.findByIdAndListaPrecioEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Precio no encontrado"));
        productoPrecioJPARepository.deleteById(id);
    }
}
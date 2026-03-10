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
import com.cloud_technological.aura_pos.entity.ProductoPrecioEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.mappers.ProductoPrecioMapper;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ListaPreciosJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ProductoPrecioJPARepository;
import com.cloud_technological.aura_pos.repositories.precios_listas_productos.ProductoPrecioQueryRepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.services.ProductoPrecioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class ProductoPrecioServiceImpl implements ProductoPrecioService {

    private final ProductoPrecioQueryRepository productoPrecioRepository;
    private final ProductoPrecioJPARepository productoPrecioJPARepository;
    private final ListaPreciosJPARepository listaPreciosJPARepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final ProductoPrecioMapper productoPrecioMapper;

    @Autowired
    public ProductoPrecioServiceImpl(
            ProductoPrecioQueryRepository productoPrecioRepository,
            ProductoPrecioJPARepository productoPrecioJPARepository,
            ListaPreciosJPARepository listaPreciosJPARepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            ProductoPrecioMapper productoPrecioMapper) {
        this.productoPrecioRepository = productoPrecioRepository;
        this.productoPrecioJPARepository = productoPrecioJPARepository;
        this.listaPreciosJPARepository = listaPreciosJPARepository;
        this.presentacionJPARepository = presentacionJPARepository;
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
        if (productoPrecioJPARepository.existsByListaPrecioIdAndProductoPresentacionId(
                dto.getListaPrecioId(), dto.getProductoPresentacionId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto ya tiene precio en esta lista");

        ListaPreciosEntity lista = listaPreciosJPARepository.findByIdAndEmpresaId(dto.getListaPrecioId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lista de precios no encontrada"));

        ProductoPresentacionEntity presentacion = presentacionJPARepository
                .findByIdAndProductoEmpresaId(dto.getProductoPresentacionId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación no encontrada"));

        ProductoPrecioEntity entity = productoPrecioMapper.toEntity(dto);
        entity.setListaPrecio(lista);
        entity.setProductoPresentacion(presentacion);

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
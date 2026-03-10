package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.inventario.CreateInventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.dto.inventario.UpdateInventarioDto;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.InventarioMapper;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.services.InventarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class InventarioServiceImpl implements InventarioService {
    
    private final InventarioQueryRepository inventarioRepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final InventarioMapper inventarioMapper;

    @Autowired
    public InventarioServiceImpl(InventarioQueryRepository inventarioRepository,
            InventarioJPARepository inventarioJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            InventarioMapper inventarioMapper) {
        this.inventarioRepository = inventarioRepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.inventarioMapper = inventarioMapper;
    }

    @Override
    public PageImpl<InventarioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return inventarioRepository.listar(pageable, empresaId);
    }

    @Override
    public InventarioDto obtenerPorId(Long id, Integer empresaId) {
        InventarioEntity entity = inventarioJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Inventario no encontrado"));
        return inventarioMapper.toDto(entity);
    }

    @Override
    public List<InventarioTableDto> listarStockBajo(Integer empresaId) {
        return inventarioRepository.listarStockBajo(empresaId);
    }

    @Override
    @Transactional
    public InventarioDto crear(CreateInventarioDto dto, Integer empresaId) {
        // Validar que no exista ya ese producto en esa sucursal
        if (inventarioJPARepository.findBySucursalIdAndProductoId(dto.getSucursalId(), dto.getProductoId()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto ya tiene inventario en esta sucursal");

        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        InventarioEntity entity = inventarioMapper.toEntity(dto);
        entity.setProducto(producto);
        entity.setSucursal(sucursal);
        entity.setUpdatedAt(LocalDateTime.now());

        return inventarioMapper.toDto(inventarioJPARepository.save(entity));
    }

    @Override
    @Transactional
    public InventarioDto actualizar(Long id, UpdateInventarioDto dto, Integer empresaId) {
        InventarioEntity entity = inventarioJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Inventario no encontrado"));

        inventarioMapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return inventarioMapper.toDto(inventarioJPARepository.save(entity));
    }
}

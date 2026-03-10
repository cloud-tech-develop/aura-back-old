package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.inventario.CreateSerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoDto;
import com.cloud_technological.aura_pos.dto.inventario.SerialProductoTableDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SerialProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.SerialProductoMapper;
import com.cloud_technological.aura_pos.repositories.inventario.SerialProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.SerialQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.services.SerialProductoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class SerialProductoServiceImpl implements SerialProductoService {
    
    private final SerialQueryRepository serialRepository;
    private final SerialProductoJPARepository serialJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final SerialProductoMapper serialMapper;

    @Autowired
    public SerialProductoServiceImpl(SerialQueryRepository serialRepository,
            SerialProductoJPARepository serialJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            SerialProductoMapper serialMapper) {
        this.serialRepository = serialRepository;
        this.serialJPARepository = serialJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.serialMapper = serialMapper;
    }

    @Override
    public PageImpl<SerialProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return serialRepository.listar(pageable, empresaId);
    }

    @Override
    public SerialProductoDto obtenerPorId(Long id, Integer empresaId) {
        SerialProductoEntity entity = serialJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Serial no encontrado"));
        return serialMapper.toDto(entity);
    }

    @Override
    public List<SerialProductoTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId) {
        return serialRepository.listarDisponiblesPorProducto(productoId, sucursalId);
    }

    @Override
    @Transactional
    public SerialProductoDto crear(CreateSerialProductoDto dto, Integer empresaId) {
        if (serialJPARepository.findBySerial(dto.getSerial()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este serial ya está registrado");

        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        SerialProductoEntity entity = serialMapper.toEntity(dto);
        entity.setProducto(producto);
        entity.setSucursal(sucursal);

        return serialMapper.toDto(serialJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        SerialProductoEntity entity = serialJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Serial no encontrado"));
        serialJPARepository.deleteById(id);
    }
}

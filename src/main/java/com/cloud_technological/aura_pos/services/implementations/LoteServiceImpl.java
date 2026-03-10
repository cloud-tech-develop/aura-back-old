package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.inventario.CreateLoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteDto;
import com.cloud_technological.aura_pos.dto.inventario.LoteTableDto;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.LoteMapper;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class LoteServiceImpl implements LoteService {
    private final LoteQueryRepository loteRepository;
    private final LoteJPARepository loteJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final LoteMapper loteMapper;

    @Autowired
    public LoteServiceImpl(LoteQueryRepository loteRepository,
            LoteJPARepository loteJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            LoteMapper loteMapper) {
        this.loteRepository = loteRepository;
        this.loteJPARepository = loteJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.loteMapper = loteMapper;
    }

    @Override
    public PageImpl<LoteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return loteRepository.listar(pageable, empresaId);
    }

    @Override
    public LoteDto obtenerPorId(Long id, Integer empresaId) {
        LoteEntity entity = loteJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lote no encontrado"));
        return loteMapper.toDto(entity);
    }

    @Override
    public List<LoteTableDto> listarPorVencer(Integer empresaId) {
        return loteRepository.listarPorVencer(empresaId);
    }

    @Override
    public List<LoteTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId) {
        return loteRepository.listarDisponiblesPorProducto(productoId, sucursalId);
    }

    @Override
    @Transactional
    public LoteDto crear(CreateLoteDto dto, Integer empresaId) {
        // Validar que no exista el mismo código de lote para ese producto y sucursal
        if (loteJPARepository.findByProductoIdAndSucursalIdAndCodigoLote(
                dto.getProductoId(), dto.getSucursalId(), dto.getCodigoLote()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un lote con este código para este producto");

        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        LoteEntity entity = loteMapper.toEntity(dto);
        entity.setProducto(producto);
        entity.setSucursal(sucursal);

        return loteMapper.toDto(loteJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        LoteEntity entity = loteJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Lote no encontrado"));

        entity.setActivo(false);
        loteJPARepository.save(entity);
    }
}

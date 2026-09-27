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
import com.cloud_technological.aura_pos.repositories.venta_detalle_serial.VentaDetalleSerialJPARepository;
import com.cloud_technological.aura_pos.services.SerialProductoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class SerialProductoServiceImpl implements SerialProductoService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.BodegaService bodegaService;
    
    private final SerialQueryRepository serialRepository;
    private final SerialProductoJPARepository serialJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final SerialProductoMapper serialMapper;
    private final VentaDetalleSerialJPARepository ventaDetalleSerialJPARepository;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository inventarioJPARepository;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.inventario.DocumentoSerialJPARepository documentoSerialRepository;

    @Autowired
    public SerialProductoServiceImpl(SerialQueryRepository serialRepository,
            SerialProductoJPARepository serialJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            SerialProductoMapper serialMapper,
            VentaDetalleSerialJPARepository ventaDetalleSerialJPARepository) {
        this.serialRepository = serialRepository;
        this.serialJPARepository = serialJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.serialMapper = serialMapper;
        this.ventaDetalleSerialJPARepository = ventaDetalleSerialJPARepository;
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
    public List<SerialProductoTableDto> listarDisponiblesPorProducto(Long productoId, Long sucursalId,
            Integer empresaId) {
        return serialRepository.listarDisponiblesPorProducto(productoId, sucursalId, empresaId);
    }

    @Override
    public List<com.cloud_technological.aura_pos.dto.inventario.SerialBuscadoDto> buscarDisponible(String codigo, Long sucursalId,
            Integer empresaId) {
        if (codigo == null || codigo.isBlank()) return List.of();
        return serialRepository.buscarDisponible(codigo, sucursalId, empresaId);
    }

    @Override
    public List<SerialProductoTableDto> vendidosEnLinea(Long ventaDetalleId, Integer empresaId) {
        return serialRepository.vendidosEnLinea(ventaDetalleId, empresaId);
    }

    @Override
    public List<com.cloud_technological.aura_pos.dto.inventario.SerialTrazaDto> trazabilidad(String serial, Integer empresaId) {
        if (serial == null || serial.trim().length() < 3)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 caracteres del serial");
        return serialRepository.trazabilidad(serial.trim(), empresaId);
    }

    @Override
    @Transactional
    public SerialProductoDto crear(CreateSerialProductoDto dto, Integer empresaId) {
        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        // El serial no se repite dentro del mismo producto. Antes se buscaba en
        // toda la tabla: el mismo serial en otra empresa bloqueaba el registro.
        if (dto.getSerial() == null || dto.getSerial().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El serial es obligatorio");
        dto.setSerial(dto.getSerial().trim());
        if (serialJPARepository.existsByProductoIdAndSerialIgnoreCase(producto.getId(), dto.getSerial()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este serial ya está registrado");

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(dto.getBodegaId(), sucursal.getId(), empresaId);

        // A mano solo se registran seriales del stock que ya existe: lo que entra
        // nuevo trae sus seriales en la compra. Nunca más DISPONIBLES que stock.
        java.math.BigDecimal stock = inventarioJPARepository
                .findByBodegaIdAndProductoId(bodega.getId(), producto.getId())
                .map(i -> i.getStockActual() != null ? i.getStockActual() : java.math.BigDecimal.ZERO)
                .orElse(java.math.BigDecimal.ZERO);
        long disponibles = serialJPARepository.countByProductoIdAndBodegaIdAndEstado(
                producto.getId(), bodega.getId(), "DISPONIBLE");
        if (java.math.BigDecimal.valueOf(disponibles + 1).compareTo(stock) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "'" + producto.getNombre() + "' ya tiene " + disponibles + " seriales para "
                            + stock.stripTrailingZeros().toPlainString() + " unidades en la bodega "
                            + bodega.getNombre() + ". Los seriales nuevos se registran en la compra.");

        SerialProductoEntity entity = serialMapper.toEntity(dto);
        entity.setProducto(producto);
        entity.setSucursal(sucursal);
        entity.setBodega(bodega);
        entity.setEstado("DISPONIBLE");
        entity.setEmpresaId(empresaId);
        entity.setCosto(producto.getCosto());
        entity.setFechaIngreso(java.time.LocalDateTime.now());

        return serialMapper.toDto(serialJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        SerialProductoEntity entity = serialJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Serial no encontrado"));
        // Un serial que ya salió en una venta es su trazabilidad: no se borra.
        if (!"DISPONIBLE".equals(entity.getEstado())
                || ventaDetalleSerialJPARepository.existsBySerialProductoId(id)
                || documentoSerialRepository.countBySerialId(id) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El serial " + entity.getSerial() + " ya tiene movimientos y no se puede eliminar");
        serialJPARepository.deleteById(id);
    }
}

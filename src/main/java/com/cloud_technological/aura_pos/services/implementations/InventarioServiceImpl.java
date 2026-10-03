package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.inventario.CreateInventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.HistorialMovimientoDto;
import com.cloud_technological.aura_pos.dto.inventario.HistorialProductoResponseDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioDto;
import com.cloud_technological.aura_pos.dto.inventario.InventarioTableDto;
import com.cloud_technological.aura_pos.dto.inventario.UpdateInventarioDto;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.mappers.InventarioMapper;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.services.InventarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class InventarioServiceImpl implements InventarioService {

    /** Bloquea el saldo (bodega, producto) antes de moverlo: ver InventarioStockService. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.InventarioStockService inventarioStock;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.BodegaService bodegaService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.LoteStockService loteStock;
    
    private final InventarioQueryRepository inventarioRepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final MovimientoInventarioJPARepository movimientoInventarioRepository;
    private final InventarioMapper inventarioMapper;

    @Autowired
    public InventarioServiceImpl(InventarioQueryRepository inventarioRepository,
            InventarioJPARepository inventarioJPARepository,
            ProductoJPARepository productoJPARepository,
            SucursalJPARepository sucursalJPARepository,
            MovimientoInventarioJPARepository movimientoInventarioRepository,
            InventarioMapper inventarioMapper) {
        this.inventarioRepository = inventarioRepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
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
    public List<com.cloud_technological.aura_pos.dto.inventario.SugeridoCompraDto> sugeridoCompra(
            Integer empresaId, Long sucursalId, Long bodegaId) {
        return inventarioRepository.sugeridoCompra(empresaId, sucursalId, bodegaId);
    }

    @Override
    public List<InventarioTableDto> listarStockBajo(Integer empresaId) {
        return inventarioRepository.listarStockBajo(empresaId);
    }

    @Override
    @Transactional
    public InventarioDto crear(CreateInventarioDto dto, Integer empresaId) {
        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(dto.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto no encontrado"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(dto.getBodegaId(), sucursal.getId(), empresaId);

        // Un saldo por producto y bodega, no por sucursal (V172).
        if (inventarioStock.bloquear(bodega.getId(), dto.getProductoId()).isPresent())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Este producto ya tiene inventario en la bodega " + bodega.getNombre());

        InventarioEntity entity = inventarioMapper.toEntity(dto);
        validarNiveles(entity);
        entity.setProducto(producto);
        entity.setSucursal(sucursal);
        entity.setBodega(bodega);
        entity.setUpdatedAt(LocalDateTime.now());
        InventarioEntity guardado = inventarioJPARepository.save(entity);

        // El saldo inicial también deja rastro: sin esta fila el kardex arranca
        // en cero y nunca cuadra con el stock.
        registrarAjusteManual(guardado, java.math.BigDecimal.ZERO, "Saldo inicial");

        // Stock inicial de un producto con lotes: entra a SIN-LOTE para que cuadre.
        loteStock.entradaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.AJUSTE_INVENTARIO, guardado.getId(), producto, bodega,
                empresaId, guardado.getStockActual(), null, producto.getCosto());

        return inventarioMapper.toDto(guardado);
    }

    @Override
    @Transactional
    public InventarioDto actualizar(Long id, UpdateInventarioDto dto, Integer empresaId) {
        InventarioEntity encontrado = inventarioJPARepository.findByIdAndSucursalEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Inventario no encontrado"));
        // Se bloquea el saldo: una venta simultánea no puede quedar en medio del ajuste.
        InventarioEntity entity = inventarioStock
                .bloquear(encontrado.getBodega().getId(), encontrado.getProducto().getId())
                .orElse(encontrado);

        java.math.BigDecimal stockAntes = entity.getStockActual() != null
                ? entity.getStockActual() : java.math.BigDecimal.ZERO;
        boolean cambiaStock = dto.getStockActual() != null
                && dto.getStockActual().compareTo(stockAntes) != 0;
        String motivo = dto.getMotivoAjuste() != null ? dto.getMotivoAjuste().trim() : "";
        if (cambiaStock && motivo.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Escribe el motivo del ajuste de stock: queda registrado en el kardex");
        }
        // Máximo y punto de reorden (V185): una pantalla que no los manda no los borra.
        java.math.BigDecimal maximoAntes = entity.getStockMaximo();
        java.math.BigDecimal reordenAntes = entity.getPuntoReorden();
        inventarioMapper.updateEntityFromDto(dto, entity);
        if (dto.getStockMaximo() == null) entity.setStockMaximo(maximoAntes);
        if (dto.getPuntoReorden() == null) entity.setPuntoReorden(reordenAntes);
        validarNiveles(entity);
        entity.setUpdatedAt(LocalDateTime.now());
        InventarioEntity guardado = inventarioJPARepository.save(entity);

        // Cambiar el stock a mano en un producto con lotes: el faltante sale del
        // que vence primero y el sobrante entra a SIN-LOTE, para que siga cuadrando.
        java.math.BigDecimal diferencia = (guardado.getStockActual() != null
                ? guardado.getStockActual() : java.math.BigDecimal.ZERO).subtract(stockAntes);
        if (dto.getStockActual() == null) {
            // No cambió el stock: nada que mover en los lotes.
        } else if (diferencia.signum() < 0) {
            loteStock.salidaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.AJUSTE_INVENTARIO, guardado.getId(), guardado.getProducto(),
                    guardado.getBodega(), empresaId, diferencia.abs(), null, true, true);
        } else if (diferencia.signum() > 0) {
            loteStock.entradaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.AJUSTE_INVENTARIO, guardado.getId(), guardado.getProducto(),
                    guardado.getBodega(), empresaId, diferencia, null, guardado.getProducto().getCosto());
        }
        if (cambiaStock) {
            registrarAjusteManual(guardado, stockAntes, motivo);
        }
        return inventarioMapper.toDto(guardado);
    }

    /** Mínimo ≤ punto de reorden ≤ máximo, cuando vienen. Cero en máximo o reorden = sin definir. */
    private static void validarNiveles(InventarioEntity inv) {
        java.math.BigDecimal minimo = inv.getStockMinimo() != null ? inv.getStockMinimo() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal reorden = inv.getPuntoReorden();
        java.math.BigDecimal maximo = inv.getStockMaximo();
        if ((reorden != null && reorden.signum() < 0) || (maximo != null && maximo.signum() < 0))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El punto de reorden y el máximo no pueden ser negativos");
        if (reorden != null && reorden.signum() > 0 && reorden.compareTo(minimo) < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El punto de reorden no puede ser menor que el stock mínimo: se pide antes de llegar al mínimo");
        java.math.BigDecimal piso = reorden != null && reorden.signum() > 0 ? reorden : minimo;
        if (maximo != null && maximo.signum() > 0 && maximo.compareTo(piso) <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El máximo debe ser mayor que el punto de reorden (o que el mínimo)");
    }

    /**
     * Deja en el kardex el cambio escrito a mano (o el saldo inicial). Antes el
     * stock cambiaba sin rastro y el kardex dejaba de cuadrar con el saldo. Va
     * al costo promedio vigente: un ajuste no cambia el costo.
     */
    private void registrarAjusteManual(InventarioEntity inv, java.math.BigDecimal stockAntes, String motivo) {
        java.math.BigDecimal stockDespues = inv.getStockActual() != null
                ? inv.getStockActual() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal diferencia = stockDespues.subtract(stockAntes);
        if (diferencia.signum() == 0) return;

        MovimientoInventarioEntity mov = new MovimientoInventarioEntity();
        mov.setSucursal(inv.getSucursal());
        mov.setBodega(inv.getBodega());
        mov.setProducto(inv.getProducto());
        mov.setTipoMovimiento(diferencia.signum() > 0
                ? com.cloud_technological.aura_pos.utils.TipoMovimientoInventario.AJUSTE_MANUAL_ENTRADA.codigo()
                : com.cloud_technological.aura_pos.utils.TipoMovimientoInventario.AJUSTE_MANUAL_SALIDA.codigo());
        // Mismo signo que los demás orígenes: la salida va negativa.
        mov.setCantidad(diferencia);
        mov.setSaldoAnterior(stockAntes);
        mov.setSaldoNuevo(stockDespues);
        mov.setCostoHistorico(inv.getProducto() != null ? inv.getProducto().getCosto() : null);
        String ref = "Ajuste manual: " + motivo;
        mov.setReferenciaOrigen(ref.length() > 250 ? ref.substring(0, 250) : ref);
        mov.setCreatedAt(LocalDateTime.now());
        movimientoInventarioRepository.save(mov);
    }

    @Override
    public HistorialProductoResponseDto historialProducto(Long productoId, Long sucursalId, Integer empresaId) {
        // Buscar producto
        ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(productoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        
        // Buscar sucursal
        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(sucursalId.intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Sucursal no encontrada"));
        
        // Obtener movimientos
        List<MovimientoInventarioEntity> movimientos = movimientoInventarioRepository
                .findByProductoIdAndSucursalIdOrderByCreatedAtDesc(productoId, sucursalId);
        
        // Convertir a DTO
        List<HistorialMovimientoDto> movimientosDto = movimientos.stream()
                .map(m -> {
                    HistorialMovimientoDto dto = new HistorialMovimientoDto();
                    dto.setId(m.getId());
                    dto.setTipo(m.getTipoMovimiento());
                    dto.setDocumentoId(m.getId()); // Usar el mismo ID como referencia
                    dto.setDocumentoNumero(m.getReferenciaOrigen());
                    dto.setFecha(m.getCreatedAt());
                    dto.setCantidad(m.getCantidad());
                    dto.setCostoUnitario(m.getCostoHistorico());
                    dto.setPrecioUnitario(null); // Por ahora null
                    dto.setSaldoAnterior(m.getSaldoAnterior());
                    dto.setSaldoNuevo(m.getSaldoNuevo());
                    dto.setTerceroNombre(null); // Por ahora null
                    dto.setSucursalNombre(sucursal.getNombre());
                    return dto;
                })
                .toList();
        
        HistorialProductoResponseDto response = new HistorialProductoResponseDto();
        response.setProductoId(producto.getId().longValue());
        response.setProductoNombre(producto.getNombre());
        response.setSku(producto.getSku());
        response.setMovimientos(movimientosDto);
        
        return response;
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.traslados.CreateTrasladoDetalleDto;
import com.cloud_technological.aura_pos.dto.traslados.CreateTrasladoDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoDto;
import com.cloud_technological.aura_pos.dto.traslados.TrasladoTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TrasladoDetalleEntity;
import com.cloud_technological.aura_pos.entity.TrasladoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.TrasladoDetalleMapper;
import com.cloud_technological.aura_pos.mappers.TrasladoMapper;
import com.cloud_technological.aura_pos.repositories.detalle_traslados.TrasladoDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.traslados.TrasladoJPARepository;
import com.cloud_technological.aura_pos.repositories.traslados.TrasladoQueryRepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.TrasladoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;


@Service
public class TrasladoServiceImpl implements TrasladoService{
    private final TrasladoQueryRepository trasladoRepository;
    private final TrasladoJPARepository trasladoJPARepository;
    private final TrasladoDetalleJPARepository detalleJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final TrasladoMapper trasladoMapper;
    private final TrasladoDetalleMapper detalleMapper;

    @Autowired
    public TrasladoServiceImpl(TrasladoQueryRepository trasladoRepository,
            TrasladoJPARepository trasladoJPARepository,
            TrasladoDetalleJPARepository detalleJPARepository,
            ProductoJPARepository productoJPARepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            SucursalJPARepository sucursalJPARepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository,
            TrasladoMapper trasladoMapper,
            TrasladoDetalleMapper detalleMapper) {
        this.trasladoRepository = trasladoRepository;
        this.trasladoJPARepository = trasladoJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.empresaRepository = empresaRepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
        this.trasladoMapper = trasladoMapper;
        this.detalleMapper = detalleMapper;
    }

    @Override
    public PageImpl<TrasladoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return trasladoRepository.listar(pageable, empresaId);
    }

    @Override
    public TrasladoDto obtenerPorId(Long id, Integer empresaId) {
        TrasladoEntity entity = trasladoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Traslado no encontrado"));
        TrasladoDto dto = trasladoMapper.toDto(entity);
        dto.setDetalles(trasladoRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public TrasladoDto crear(CreateTrasladoDto dto, Integer empresaId, Long usuarioId) {
        if (dto.getSucursalOrigenId().equals(dto.getSucursalDestinoId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La sucursal origen y destino no pueden ser la misma");

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity origen = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalOrigenId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal origen no encontrada"));

        SucursalEntity destino = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalDestinoId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal destino no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        // 1. Crear cabecera
        TrasladoEntity traslado = trasladoMapper.toEntity(dto);
        traslado.setEmpresa(empresa);
        traslado.setSucursalOrigen(origen);
        traslado.setSucursalDestino(destino);
        traslado.setUsuario(usuario);
        traslado.setFecha(LocalDateTime.now());
        traslado.setEstado("COMPLETADO");
        traslado.setCreatedAt(LocalDateTime.now());
        traslado = trasladoJPARepository.save(traslado);

        // 2. Procesar cada detalle
        for (CreateTrasladoDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // 2.1 Validar stock en origen
            InventarioEntity invOrigen = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(origen.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + producto.getNombre() + " no tiene inventario en la sucursal origen"));

            if (invOrigen.getStockActual().compareTo(item.getCantidad()) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente en origen para: " + producto.getNombre()
                        + ". Disponible: " + invOrigen.getStockActual());

            // 2.2 Crear detalle
            TrasladoDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setTraslado(traslado);
            detalle.setProducto(producto);

            // 2.3 Manejar lote si aplica
            LoteEntity loteOrigen = null;
            if (item.getLoteId() != null) {
                loteOrigen = loteJPARepository.findById(item.getLoteId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lote no encontrado"));

                if (loteOrigen.getStockActual().compareTo(item.getCantidad()) < 0)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Stock insuficiente en lote: " + loteOrigen.getCodigoLote());

                // Restar del lote origen
                loteOrigen.setStockActual(loteOrigen.getStockActual().subtract(item.getCantidad()));
                loteJPARepository.save(loteOrigen);

                // Crear o actualizar lote en destino con mismo código
                LoteEntity loteDestino = resolverLoteDestino(producto, destino, loteOrigen, item.getCantidad());
                detalle.setLote(loteOrigen);
            }

            detalleJPARepository.save(detalle);

            // 2.4 Restar stock origen
            BigDecimal saldoAnteriorOrigen = invOrigen.getStockActual();
            BigDecimal saldoNuevoOrigen = saldoAnteriorOrigen.subtract(item.getCantidad());
            invOrigen.setStockActual(saldoNuevoOrigen);
            invOrigen.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invOrigen);

            // Kardex salida
            registrarMovimiento(origen, producto, loteOrigen,
                    item.getCantidad().negate(), saldoAnteriorOrigen, saldoNuevoOrigen,
                    item.getCostoUnitario(), "TRASLADO_SALIDA",
                    "Traslado #" + traslado.getId() + " → " + destino.getNombre());

            // 2.5 Sumar stock destino
            InventarioEntity invDestino = resolverInventarioDestino(destino, producto);
            BigDecimal saldoAnteriorDestino = invDestino.getStockActual();
            BigDecimal saldoNuevoDestino = saldoAnteriorDestino.add(item.getCantidad());
            invDestino.setStockActual(saldoNuevoDestino);
            invDestino.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invDestino);

            // Kardex entrada
            registrarMovimiento(destino, producto, loteOrigen,
                    item.getCantidad(), saldoAnteriorDestino, saldoNuevoDestino,
                    item.getCostoUnitario(), "TRASLADO_ENTRADA",
                    "Traslado #" + traslado.getId() + " ← " + origen.getNombre());
        }

        return obtenerPorId(traslado.getId(), empresaId);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        TrasladoEntity traslado = trasladoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Traslado no encontrado"));

        if (traslado.getEstado().equals("ANULADO"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El traslado ya está anulado");

        List<TrasladoDetalleEntity> detalles = detalleJPARepository.findByTrasladoId(id);

        for (TrasladoDetalleEntity detalle : detalles) {
            ProductoEntity producto = detalle.getProducto();
            SucursalEntity origen = traslado.getSucursalOrigen();
            SucursalEntity destino = traslado.getSucursalDestino();

            // Devolver stock al origen
            InventarioEntity invOrigen = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(origen.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario origen no encontrado para: " + producto.getNombre()));

            BigDecimal saldoAnteriorOrigen = invOrigen.getStockActual();
            BigDecimal saldoNuevoOrigen = saldoAnteriorOrigen.add(detalle.getCantidad());
            invOrigen.setStockActual(saldoNuevoOrigen);
            invOrigen.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invOrigen);

            // Kardex devolución origen
            registrarMovimiento(origen, producto, detalle.getLote(),
                    detalle.getCantidad(), saldoAnteriorOrigen, saldoNuevoOrigen,
                    detalle.getCostoUnitario(), "ANULACION_TRASLADO",
                    "Anulación Traslado #" + traslado.getId());

            // Restar stock del destino
            InventarioEntity invDestino = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(destino.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario destino no encontrado para: " + producto.getNombre()));

            BigDecimal saldoAnteriorDestino = invDestino.getStockActual();
            BigDecimal saldoNuevoDestino = saldoAnteriorDestino.subtract(detalle.getCantidad());

            if (saldoNuevoDestino.compareTo(BigDecimal.ZERO) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede anular, el producto " + producto.getNombre()
                        + " ya fue consumido en la sucursal destino");

            invDestino.setStockActual(saldoNuevoDestino);
            invDestino.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invDestino);

            // Kardex devolución destino
            registrarMovimiento(destino, producto, detalle.getLote(),
                    detalle.getCantidad().negate(), saldoAnteriorDestino, saldoNuevoDestino,
                    detalle.getCostoUnitario(), "ANULACION_TRASLADO",
                    "Anulación Traslado #" + traslado.getId());

            // Revertir lotes si aplica
            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteJPARepository.save(lote);
            }
        }

        traslado.setEstado("ANULADO");
        trasladoJPARepository.save(traslado);
    }

    // ─── Métodos privados ────────────────────────────────────────────────────

    private LoteEntity resolverLoteDestino(ProductoEntity producto, SucursalEntity destino,
            LoteEntity loteOrigen, BigDecimal cantidad) {
        return loteJPARepository
                .findByProductoIdAndSucursalIdAndCodigoLote(
                        producto.getId(), Long.valueOf(destino.getId()), loteOrigen.getCodigoLote())
                .map(loteExistente -> {
                    loteExistente.setStockActual(loteExistente.getStockActual().add(cantidad));
                    return loteJPARepository.save(loteExistente);
                })
                .orElseGet(() -> {
                    LoteEntity nuevoLote = new LoteEntity();
                    nuevoLote.setProducto(producto);
                    nuevoLote.setSucursal(destino);
                    nuevoLote.setCodigoLote(loteOrigen.getCodigoLote());
                    nuevoLote.setFechaVencimiento(loteOrigen.getFechaVencimiento());
                    nuevoLote.setStockActual(cantidad);
                    nuevoLote.setCostoUnitario(loteOrigen.getCostoUnitario());
                    nuevoLote.setActivo(true);
                    return loteJPARepository.save(nuevoLote);
                });
    }

    private InventarioEntity resolverInventarioDestino(SucursalEntity destino, ProductoEntity producto) {
        return inventarioJPARepository
                .findBySucursalIdAndProductoId(Long.valueOf(destino.getId()), producto.getId())
                .orElseGet(() -> {
                    InventarioEntity nuevo = new InventarioEntity();
                    nuevo.setSucursal(destino);
                    nuevo.setProducto(producto);
                    nuevo.setStockActual(BigDecimal.ZERO);
                    nuevo.setStockMinimo(BigDecimal.ZERO);
                    nuevo.setUpdatedAt(LocalDateTime.now());
                    return inventarioJPARepository.save(nuevo);
                });
    }

    private void registrarMovimiento(SucursalEntity sucursal, ProductoEntity producto,
            LoteEntity lote, BigDecimal cantidad, BigDecimal saldoAnterior,
            BigDecimal saldoNuevo, BigDecimal costo, String tipo, String referencia) {
        MovimientoInventarioEntity movimiento = new MovimientoInventarioEntity();
        movimiento.setSucursal(sucursal);
        movimiento.setProducto(producto);
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(saldoNuevo);
        movimiento.setCostoHistorico(costo);
        movimiento.setReferenciaOrigen(referencia);
        movimiento.setCreatedAt(LocalDateTime.now());
        movimientoJPARepository.save(movimiento);
    }
}

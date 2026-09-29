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
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;


@Service
public class TrasladoServiceImpl implements TrasladoService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.BodegaService bodegaService;
    private final TrasladoQueryRepository trasladoRepository;
    private final TrasladoJPARepository trasladoJPARepository;
    private final TrasladoDetalleJPARepository detalleJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.LoteStockService loteStock;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.SerialStockService serialStock;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final TrasladoMapper trasladoMapper;
    private final TrasladoDetalleMapper detalleMapper;

    @Autowired
    private com.cloud_technological.aura_pos.services.CambioUnidadProductoService cambioUnidadProducto;

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

        // El traslado es entre bodegas. Dos bodegas de la misma sucursal es
        // un caso valido: pasar de la bodega de atras a la vitrina.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodegaOrigen =
                bodegaService.resolver(dto.getBodegaOrigenId(), origen.getId(), empresaId);
        com.cloud_technological.aura_pos.entity.BodegaEntity bodegaDestino =
                bodegaService.resolver(dto.getBodegaDestinoId(), destino.getId(), empresaId);

        if (bodegaOrigen.getId().equals(bodegaDestino.getId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El origen y el destino son la misma bodega");

        // 1. Crear cabecera
        TrasladoEntity traslado = trasladoMapper.toEntity(dto);
        traslado.setEmpresa(empresa);
        traslado.setSucursalOrigen(origen);
        traslado.setSucursalDestino(destino);
        traslado.setBodegaOrigen(bodegaOrigen);
        traslado.setBodegaDestino(bodegaDestino);
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
                    .findByBodegaIdAndProductoId(bodegaOrigen.getId(), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + producto.getNombre() + " no tiene inventario en la bodega "
                            + bodegaOrigen.getNombre()));

            if (invOrigen.getStockActual().compareTo(item.getCantidad()) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente en origen para: " + producto.getNombre()
                        + ". Disponible: " + invOrigen.getStockActual());

            // 2.2 Crear detalle
            TrasladoDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setTraslado(traslado);
            detalle.setProducto(producto);

            detalleJPARepository.save(detalle);

            // 2.3 Lotes: sale del elegido o del que vence primero (un traslado
            //     sí mueve vencidos) y entra al destino con el mismo código,
            //     vencimiento y costo.
            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotesSalida = loteStock.salidaDocumento(
                    com.cloud_technological.aura_pos.services.LoteStockService.TRASLADO_SALIDA, detalle.getId(), producto, bodegaOrigen, empresaId,
                    item.getCantidad(), item.getLoteId(), true, false);
            if (lotesSalida.size() == 1) {
                detalle.setLote(lotesSalida.get(0).lote());
                detalleJPARepository.save(detalle);
            }
            // Seriales: pasan a la bodega destino y siguen DISPONIBLES.
            serialStock.traslado(detalle.getId(), producto, bodegaOrigen, bodegaDestino, empresaId, item.getCantidad(),
                    item.getSerialIds());
            final String refSalida = "Traslado #" + traslado.getId() + " → " + bodegaDestino.getNombre();
            final String refEntrada = "Traslado #" + traslado.getId() + " ← " + bodegaOrigen.getNombre();

            // 2.4 Restar stock origen
            BigDecimal saldoAnteriorOrigen = invOrigen.getStockActual();
            BigDecimal saldoNuevoOrigen = saldoAnteriorOrigen.subtract(item.getCantidad());
            invOrigen.setStockActual(saldoNuevoOrigen);
            invOrigen.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invOrigen);

            // Kardex salida
            loteStock.kardex(lotesSalida, item.getCantidad().negate(), saldoAnteriorOrigen,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodegaOrigen, producto, lote, cant, ant, nuevo,
                            item.getCostoUnitario(), TipoMovimientoInventario.TRASLADO_SALIDA.codigo(), refSalida));

            // 2.5 Sumar stock destino
            InventarioEntity invDestino = resolverInventarioDestino(bodegaDestino, producto);
            BigDecimal saldoAnteriorDestino = invDestino.getStockActual();
            BigDecimal saldoNuevoDestino = saldoAnteriorDestino.add(item.getCantidad());
            invDestino.setStockActual(saldoNuevoDestino);
            invDestino.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invDestino);

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotesEntrada = loteStock.manejaLotes(producto)
                    ? loteStock.entradaEspejo(com.cloud_technological.aura_pos.services.LoteStockService.TRASLADO_ENTRADA, detalle.getId(), producto,
                            bodegaDestino, empresaId, lotesSalida)
                    : List.of();

            // Kardex entrada
            loteStock.kardex(lotesEntrada, item.getCantidad(), saldoAnteriorDestino,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodegaDestino, producto, lote, cant, ant, nuevo,
                            item.getCostoUnitario(), TipoMovimientoInventario.TRASLADO_ENTRADA.codigo(), refEntrada));
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
        cambioUnidadProducto.validarDocumentoPrevio("TRASLADO", id,
                detalles.stream().map(d -> d.getProducto().getId()).toList(), "anular el traslado");

        // Las bodegas del documento: la mercancia vuelve por donde vino.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodegaOrigen = bodegaService.resolver(
                traslado.getBodegaOrigen() != null ? traslado.getBodegaOrigen().getId() : null,
                traslado.getSucursalOrigen().getId(), empresaId);
        com.cloud_technological.aura_pos.entity.BodegaEntity bodegaDestino = bodegaService.resolver(
                traslado.getBodegaDestino() != null ? traslado.getBodegaDestino().getId() : null,
                traslado.getSucursalDestino().getId(), empresaId);

        for (TrasladoDetalleEntity detalle : detalles) {
            ProductoEntity producto = detalle.getProducto();

            // Devolver stock al origen
            InventarioEntity invOrigen = inventarioJPARepository
                    .findByBodegaIdAndProductoId(bodegaOrigen.getId(), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario origen no encontrado para: " + producto.getNombre()));

            BigDecimal saldoAnteriorOrigen = invOrigen.getStockActual();
            BigDecimal saldoNuevoOrigen = saldoAnteriorOrigen.add(detalle.getCantidad());
            invOrigen.setStockActual(saldoNuevoOrigen);
            invOrigen.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invOrigen);

            serialStock.revertirTraslado(detalle.getId(), bodegaOrigen, bodegaDestino);

            // Lotes: vuelve al origen lo que salió y sale del destino lo que entró.
            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotesOrigen = loteStock.revertirDocumento(
                    com.cloud_technological.aura_pos.services.LoteStockService.TRASLADO_SALIDA, detalle.getId(), true, "anular el traslado");
            final String refAnulacion = "Anulación Traslado #" + traslado.getId();

            // Kardex devolución origen
            loteStock.kardex(lotesOrigen, detalle.getCantidad(), saldoAnteriorOrigen,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodegaOrigen, producto,
                            lote != null ? lote : detalle.getLote(), cant, ant, nuevo,
                            detalle.getCostoUnitario(), TipoMovimientoInventario.ANULACION_TRASLADO.codigo(),
                            refAnulacion));

            // Restar stock del destino
            InventarioEntity invDestino = inventarioJPARepository
                    .findByBodegaIdAndProductoId(bodegaDestino.getId(), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario destino no encontrado para: " + producto.getNombre()));

            BigDecimal saldoAnteriorDestino = invDestino.getStockActual();
            BigDecimal saldoNuevoDestino = saldoAnteriorDestino.subtract(detalle.getCantidad());

            if (saldoNuevoDestino.compareTo(BigDecimal.ZERO) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede anular, el producto " + producto.getNombre()
                        + " ya salio de la bodega destino");

            invDestino.setStockActual(saldoNuevoDestino);
            invDestino.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(invDestino);

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotesDestino = loteStock.revertirDocumento(
                    com.cloud_technological.aura_pos.services.LoteStockService.TRASLADO_ENTRADA, detalle.getId(), false, "anular el traslado");

            // Kardex devolución destino
            loteStock.kardex(lotesDestino, detalle.getCantidad().negate(), saldoAnteriorDestino,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodegaDestino, producto, lote, cant, ant, nuevo,
                            detalle.getCostoUnitario(), TipoMovimientoInventario.ANULACION_TRASLADO.codigo(),
                            refAnulacion));

            // Traslados anteriores a F3: el lote elegido solo se había descontado en origen.
            if (lotesOrigen.isEmpty() && detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteJPARepository.save(lote);
            }
        }

        traslado.setEstado("ANULADO");
        trasladoJPARepository.save(traslado);
    }

    // ─── Métodos privados ────────────────────────────────────────────────────

    private InventarioEntity resolverInventarioDestino(com.cloud_technological.aura_pos.entity.BodegaEntity destino, ProductoEntity producto) {
        return inventarioJPARepository
                .findByBodegaIdAndProductoId(destino.getId(), producto.getId())
                .orElseGet(() -> {
                    InventarioEntity nuevo = new InventarioEntity();
                    nuevo.setBodega(destino);
                    nuevo.setSucursal(destino.getSucursal());
                    nuevo.setProducto(producto);
                    nuevo.setStockActual(BigDecimal.ZERO);
                    nuevo.setStockMinimo(BigDecimal.ZERO);
                    nuevo.setUpdatedAt(LocalDateTime.now());
                    return inventarioJPARepository.save(nuevo);
                });
    }

    private void registrarMovimiento(com.cloud_technological.aura_pos.entity.BodegaEntity bodega, ProductoEntity producto,
            LoteEntity lote, BigDecimal cantidad, BigDecimal saldoAnterior,
            BigDecimal saldoNuevo, BigDecimal costo, String tipo, String referencia) {
        MovimientoInventarioEntity movimiento = new MovimientoInventarioEntity();
        movimiento.setBodega(bodega);
        movimiento.setSucursal(bodega.getSucursal());
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

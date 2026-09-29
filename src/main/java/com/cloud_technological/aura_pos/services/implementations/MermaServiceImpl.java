package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.merma.CreateMermaDetalleDto;
import com.cloud_technological.aura_pos.dto.merma.CreateMermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaDto;
import com.cloud_technological.aura_pos.dto.merma.MermaTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MermaDetalleEntity;
import com.cloud_technological.aura_pos.entity.MermaEntity;
import com.cloud_technological.aura_pos.entity.MotivoMermaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.MermaDetalleMapper;
import com.cloud_technological.aura_pos.mappers.MermaMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.merma.MermaJPARepository;
import com.cloud_technological.aura_pos.repositories.merma.MermaQueryRepository;
import com.cloud_technological.aura_pos.repositories.merma_detalle.MermaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.motivo_merma.MotivoMermaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.services.MermaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;


@Service
public class MermaServiceImpl implements MermaService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final MermaQueryRepository mermaRepository;
    private final MermaJPARepository mermaJPARepository;
    private final MermaDetalleJPARepository detalleJPARepository;
    private final MotivoMermaJPARepository motivoJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final MermaMapper mermaMapper;
    private final MermaDetalleMapper detalleMapper;
    private final ConsumoComposicionService consumoComposicion;

    @Autowired
    private com.cloud_technological.aura_pos.services.BodegaService bodegaService;

    @Autowired
    private com.cloud_technological.aura_pos.services.LoteStockService loteStock;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.SerialStockService serialStock;

    @Autowired
    private com.cloud_technological.aura_pos.services.CambioUnidadProductoService cambioUnidadProducto;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository presentacionJPARepository;

    @Autowired
    public MermaServiceImpl(MermaQueryRepository mermaRepository,
            MermaJPARepository mermaJPARepository,
            MermaDetalleJPARepository detalleJPARepository,
            MotivoMermaJPARepository motivoJPARepository,
            ProductoJPARepository productoJPARepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            SucursalJPARepository sucursalJPARepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository,
            MermaMapper mermaMapper,
            MermaDetalleMapper detalleMapper,
            ConsumoComposicionService consumoComposicion) {
        this.mermaRepository = mermaRepository;
        this.mermaJPARepository = mermaJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.motivoJPARepository = motivoJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.empresaRepository = empresaRepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
        this.mermaMapper = mermaMapper;
        this.detalleMapper = detalleMapper;
        this.consumoComposicion = consumoComposicion;
    }

    @Override
    public PageImpl<MermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return mermaRepository.listar(pageable, empresaId);
    }

    @Override
    public MermaDto obtenerPorId(Long id, Integer empresaId) {
        MermaEntity entity = mermaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Merma no encontrada"));
        MermaDto dto = mermaMapper.toDto(entity);
        dto.setDetalles(mermaRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public MermaDto crear(CreateMermaDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        MotivoMermaEntity motivo = motivoJPARepository.findByIdAndEmpresaId(dto.getMotivoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Motivo no encontrado"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        // 1. Crear cabecera
        // De qué bodega sale. Si el documento no la trae, la principal.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(dto.getBodegaId(), sucursal.getId(), empresaId);

        MermaEntity merma = mermaMapper.toEntity(dto);
        merma.setEmpresa(empresa);
        merma.setSucursal(sucursal);
        merma.setBodega(bodega);
        merma.setUsuario(usuario);
        merma.setMotivo(motivo);
        merma.setFecha(LocalDateTime.now());
        merma.setEstado("APROBADA");
        merma = mermaJPARepository.save(merma);

        BigDecimal costoTotal = BigDecimal.ZERO;

        // 2. Procesar cada detalle
        for (CreateMermaDetalleDto item : dto.getDetalles()) {
            if (item.getCantidad() == null || item.getCantidad().signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");

            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // Línea escrita en una presentación (1 Paca): se pasa a unidad base.
            var presentacion = resolverPresentacion(item.getProductoPresentacionId(), producto, empresaId);
            BigDecimal cantidadPresentacion = null;
            if (presentacion != null) {
                if (consumoComposicion.tieneComposicion(producto.getId()))
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "'" + producto.getNombre() + "' se descuenta por su receta: regístralo por unidad");
                cantidadPresentacion = item.getCantidad();
                item.setCantidad(com.cloud_technological.aura_pos.utils.PresentacionConversion
                        .aBase(item.getCantidad(), presentacion));
            }

            if (consumoComposicion.tieneComposicion(producto.getId())) {
                costoTotal = costoTotal.add(registrarLineaPorReceta(merma, bodega, producto, item, empresaId));
                continue;
            }

            // 2.1 Validar stock
            InventarioEntity inventario = inventarioJPARepository
                    .findByBodegaIdAndProductoId(bodega.getId(), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + producto.getNombre() + " no tiene inventario en la bodega "
                            + bodega.getNombre()));

            if (inventario.getStockActual().compareTo(item.getCantidad()) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente para: " + producto.getNombre()
                        + ". Disponible: " + inventario.getStockActual());

            // 2.2 Crear detalle
            MermaDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setMerma(merma);
            detalle.setProducto(producto);
            detalle.setProductoPresentacion(presentacion);
            detalle.setCantidadPresentacion(cantidadPresentacion);

            detalleJPARepository.save(detalle);

            // 2.3 Lotes: el elegido o el que vence primero. La merma sí saca
            //     vencidos: es justo lo que más se merma.
            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = loteStock.salidaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.MERMA,
                    detalle.getId(), producto, bodega, empresaId, item.getCantidad(), item.getLoteId(),
                    true, false);
            if (lotes.size() == 1) {
                detalle.setLote(lotes.get(0).lote());
                detalleJPARepository.save(detalle);
            }

            serialStock.salida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_MERMA, detalle.getId(), producto, bodega, empresaId,
                    item.getCantidad(), item.getSerialIds(), com.cloud_technological.aura_pos.services.SerialStockService.MERMA);

            // 2.4 Actualizar inventario
            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(item.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            // 2.5 Kardex (un movimiento por lote)
            final String refMerma = "Merma #" + merma.getId();
            loteStock.kardex(lotes, item.getCantidad().negate(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, producto, lote, cant, ant, nuevo,
                            item.getCostoUnitario(), TipoMovimientoInventario.MERMA.codigo(), refMerma));

            costoTotal = costoTotal.add(item.getCantidad().multiply(item.getCostoUnitario()));
        }

        // 3. Actualizar costo total
        merma.setCostoTotal(costoTotal);
        mermaJPARepository.save(merma);

        // Asiento contable de la merma tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.OperacionContabilizableEvent(
                        "MERMA", merma.getId(), empresaId, usuarioId != null ? usuarioId.intValue() : null));

        return obtenerPorId(merma.getId(), empresaId);
    }

    /** Presentación de la línea, validada contra el producto; null si la línea va en unidad base. */
    private com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity resolverPresentacion(
            Long presentacionId, ProductoEntity producto, Integer empresaId) {
        if (presentacionId == null) return null;
        return presentacionJPARepository.findByIdAndProductoEmpresaId(presentacionId, empresaId)
                .filter(p -> p.getProducto().getId().equals(producto.getId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La presentación no pertenece a " + producto.getNombre()));
    }

    /**
     * Un producto con receta no tiene stock propio: lo que se pierde son sus
     * componentes, igual que al venderlo. El costo que manda el front se
     * ignora — el de un compuesto es lo que valen hoy sus componentes.
     *
     * @return costo de la línea
     */
    private BigDecimal registrarLineaPorReceta(MermaEntity merma,
            com.cloud_technological.aura_pos.entity.BodegaEntity bodega,
            ProductoEntity producto, CreateMermaDetalleDto item, Integer empresaId) {
        if (item.getLoteId() != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "'" + producto.getNombre() + "' se descuenta por su receta: no admite lote");

        List<ConsumoComposicionService.Consumo> consumos = consumoComposicion.explotar(
                producto.getId(), item.getCantidad(), bodega.getId());
        consumoComposicion.validarStock(producto, consumos);

        MermaDetalleEntity detalle = new MermaDetalleEntity();
        detalle.setMerma(merma);
        detalle.setProducto(producto);
        detalle.setCantidad(item.getCantidad());
        detalle.setCostoUnitario(BigDecimal.ZERO);
        // Se guarda antes de consumir: el registro de componentes cuelga de su id.
        detalle = detalleJPARepository.save(detalle);

        BigDecimal costoLinea = consumoComposicion.consumir(ConsumoComposicionService.ORIGEN_MERMA,
                detalle.getId(), empresaId, bodega, producto, consumos,
                TipoMovimientoInventario.MERMA.codigo(), "Merma #" + merma.getId());

        detalle.setCostoUnitario(costoLinea.divide(item.getCantidad(), 2, RoundingMode.HALF_UP));
        detalleJPARepository.save(detalle);
        return costoLinea;
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        MermaEntity merma = mermaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Merma no encontrada"));

        if (merma.getEstado().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La merma ya está anulada");

        List<MermaDetalleEntity> detalles = detalleJPARepository.findByMermaId(id);

        // La bodega del documento, no la principal de hoy: la mercancía vuelve
        // exactamente de donde salió.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(merma.getBodega() != null ? merma.getBodega().getId() : null,
                        merma.getSucursal().getId(), empresaId);

        // Lo que salió por receta también cuenta: el componente pudo cambiar de unidad.
        List<Long> productosMerma = new java.util.ArrayList<>();
        for (MermaDetalleEntity d : detalles) {
            productosMerma.add(d.getProducto().getId());
            consumoComposicion.consumosDe(ConsumoComposicionService.ORIGEN_MERMA, d.getId())
                    .forEach(c -> productosMerma.add(c.getProductoHijo().getId()));
        }
        cambioUnidadProducto.validarDocumentoPrevio("MERMA", id, productosMerma, "anular la merma");

        for (MermaDetalleEntity detalle : detalles) {
            // Si salió por receta se devuelve lo que se consumió entonces, no lo
            // que diga la receta hoy.
            if (consumoComposicion.revertir(ConsumoComposicionService.ORIGEN_MERMA, detalle.getId(),
                    bodega, detalle.getProducto(),
                    TipoMovimientoInventario.ANULACION_MERMA.codigo(), "Anulación Merma #" + merma.getId())) {
                continue;
            }

            InventarioEntity inventario = inventarioJPARepository
                    .findByBodegaIdAndProductoId(bodega.getId(), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());

            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            serialStock.revertirSalida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_MERMA, detalle.getId(), "anular la merma");

            // Vuelve a los mismos lotes de donde salió.
            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = loteStock.revertirDocumento(com.cloud_technological.aura_pos.services.LoteStockService.MERMA,
                    detalle.getId(), true, "anular la merma");
            if (lotes.isEmpty() && detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteJPARepository.save(lote);
            }

            loteStock.kardex(lotes, detalle.getCantidad(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, detalle.getProducto(),
                            lote != null ? lote : detalle.getLote(), cant, ant, nuevo,
                            detalle.getCostoUnitario(), TipoMovimientoInventario.ANULACION_MERMA.codigo(),
                            "Anulación Merma #" + merma.getId()));
        }

        merma.setEstado("ANULADA");
        mermaJPARepository.save(merma);

        // Reversar el asiento de la merma tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "MERMA", merma.getId(), empresaId, null));
    }

    private void registrarMovimiento(com.cloud_technological.aura_pos.entity.BodegaEntity bodega,
            ProductoEntity producto,
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

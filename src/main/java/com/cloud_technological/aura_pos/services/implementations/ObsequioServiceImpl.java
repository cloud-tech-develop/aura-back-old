package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.obsequio.CreateObsequioDetalleDto;
import com.cloud_technological.aura_pos.dto.obsequio.CreateObsequioDto;
import com.cloud_technological.aura_pos.dto.obsequio.ObsequioDto;
import com.cloud_technological.aura_pos.dto.obsequio.ObsequioTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ObsequioDetalleEntity;
import com.cloud_technological.aura_pos.entity.ObsequioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.event.ContabilidadReversaEvent;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioJPARepository;
import com.cloud_technological.aura_pos.repositories.obsequio.ObsequioQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.services.ObsequioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;

/**
 * Entrega de producto sin cobrar. Saca inventario y kardex como una merma,
 * pero contabiliza como gasto de promoción y causa el IVA por retiro.
 */
@Service
public class ObsequioServiceImpl implements ObsequioService {

    /** Bloquea el saldo (bodega, producto) antes de moverlo: ver InventarioStockService. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.InventarioStockService inventarioStock;

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ObsequioQueryRepository queryRepository;
    private final ObsequioJPARepository obsequioRepository;
    private final ObsequioDetalleJPARepository detalleRepository;
    private final ProductoJPARepository productoRepository;
    private final InventarioJPARepository inventarioRepository;
    private final LoteJPARepository loteRepository;
    private final SucursalJPARepository sucursalRepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final TerceroJPARepository terceroRepository;
    private final MovimientoInventarioJPARepository movimientoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ConsumoComposicionService consumoComposicion;

    @Autowired
    private com.cloud_technological.aura_pos.services.BodegaService bodegaService;

    @Autowired
    private com.cloud_technological.aura_pos.services.CambioUnidadProductoService cambioUnidadProducto;

    @Autowired
    private com.cloud_technological.aura_pos.services.LoteStockService loteStock;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.SerialStockService serialStock;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository presentacionJPARepository;

    @Autowired
    public ObsequioServiceImpl(ObsequioQueryRepository queryRepository,
            ObsequioJPARepository obsequioRepository,
            ObsequioDetalleJPARepository detalleRepository,
            ProductoJPARepository productoRepository,
            InventarioJPARepository inventarioRepository,
            LoteJPARepository loteRepository,
            SucursalJPARepository sucursalRepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioRepository,
            TerceroJPARepository terceroRepository,
            MovimientoInventarioJPARepository movimientoRepository,
            ApplicationEventPublisher eventPublisher,
            ConsumoComposicionService consumoComposicion) {
        this.queryRepository = queryRepository;
        this.obsequioRepository = obsequioRepository;
        this.detalleRepository = detalleRepository;
        this.productoRepository = productoRepository;
        this.inventarioRepository = inventarioRepository;
        this.loteRepository = loteRepository;
        this.sucursalRepository = sucursalRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.terceroRepository = terceroRepository;
        this.movimientoRepository = movimientoRepository;
        this.eventPublisher = eventPublisher;
        this.consumoComposicion = consumoComposicion;
    }

    @Override
    public PageImpl<ObsequioTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public ObsequioDto obtenerPorId(Long id, Integer empresaId) {
        ObsequioEntity entity = obsequioRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Obsequio no encontrado"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public ObsequioDto crear(CreateObsequioDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalRepository
                .findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        TerceroEntity tercero = null;
        if (dto.getTerceroId() != null) {
            tercero = terceroRepository.findByIdAndEmpresaId(dto.getTerceroId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Tercero no encontrado"));
        }

        boolean generaIva = !Boolean.FALSE.equals(dto.getGeneraIva());
        // Regalar un producto vencido no: la regla de la empresa manda.
        final boolean permitirVencidos = !loteStock.bloqueaVencidos(empresaId);

        ObsequioEntity obsequio = new ObsequioEntity();
        obsequio.setEmpresa(empresa);
        obsequio.setSucursal(sucursal);
        // De qué bodega sale. Sin bodega en el documento, la principal.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(dto.getBodegaId(), sucursal.getId(), empresaId);
        obsequio.setBodega(bodega);
        obsequio.setUsuario(usuario);
        obsequio.setTercero(tercero);
        obsequio.setFecha(LocalDateTime.now());
        obsequio.setMotivo(dto.getMotivo());
        obsequio.setObservacion(dto.getObservacion());
        obsequio.setGeneraIva(generaIva);
        obsequio.setEstado(ObsequioEntity.ESTADO_APROBADO);
        obsequio.setCostoTotal(BigDecimal.ZERO);
        obsequio.setBaseComercialTotal(BigDecimal.ZERO);
        obsequio.setIvaTotal(BigDecimal.ZERO);
        obsequio = obsequioRepository.save(obsequio);

        BigDecimal costoTotal = BigDecimal.ZERO;
        BigDecimal baseTotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;

        for (CreateObsequioDetalleDto item : dto.getDetalles()) {
            if (item.getCantidad() == null || item.getCantidad().signum() <= 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
            }

            ProductoEntity producto = productoRepository
                    .findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // Línea escrita en una presentación (1 Paca): se pasa a unidad base.
            // La base comercial que venga (o el precio de la presentación) es por
            // presentación y se reparte entre las unidades.
            var presentacion = resolverPresentacion(item.getProductoPresentacionId(), producto, empresaId);
            BigDecimal cantidadPresentacion = null;
            BigDecimal basePresentacion = null;
            if (presentacion != null) {
                if (consumoComposicion.tieneComposicion(producto.getId()))
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "'" + producto.getNombre() + "' se descuenta por su receta: regístralo por unidad");
                cantidadPresentacion = item.getCantidad();
                basePresentacion = baseComercialDePresentacion(item, presentacion, producto);
                item.setCantidad(com.cloud_technological.aura_pos.utils.PresentacionConversion
                        .aBase(item.getCantidad(), presentacion));
            }

            // Un producto con receta no tiene stock propio: lo que se regala
            // sale de sus componentes, igual que al venderlo.
            boolean porReceta = consumoComposicion.tieneComposicion(producto.getId());
            List<ConsumoComposicionService.Consumo> consumos = List.of();
            InventarioEntity inventario = null;

            if (porReceta) {
                if (item.getLoteId() != null) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "'" + producto.getNombre() + "' se descuenta por su receta: no admite lote");
                }
                consumos = consumoComposicion.explotar(
                        producto.getId(), item.getCantidad(), bodega.getId());
                consumoComposicion.validarStock(producto, consumos);
            } else {
                inventario = inventarioStock
                        .bloquear(bodega.getId(), producto.getId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                                "El producto " + producto.getNombre() + " no tiene inventario en la bodega "
                                + bodega.getNombre()));

                if (!Boolean.TRUE.equals(producto.getPermitirStockNegativo())
                        && inventario.getStockActual().compareTo(item.getCantidad()) < 0) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Stock insuficiente para: " + producto.getNombre()
                            + ". Disponible: " + inventario.getStockActual());
                }
            }

            // El costo se congela aquí: si mañana cambia el costo del producto,
            // el asiento de este obsequio no puede moverse. Para uno con receta
            // se fija después de consumir, con lo que valen sus componentes.
            BigDecimal costoUnitario = !porReceta && producto.getCosto() != null
                    ? producto.getCosto() : BigDecimal.ZERO;
            // Base e IVA son del producto regalado, tenga o no receta: el retiro
            // gravado es el del pan, no el de la harina.
            BigDecimal baseUnitaria = basePresentacion != null
                    ? basePresentacion.multiply(cantidadPresentacion)
                            .divide(item.getCantidad(), 2, RoundingMode.HALF_UP)
                    : resolverBaseComercial(item, producto);
            BigDecimal tarifaIva = producto.getIvaPorcentaje() != null
                    ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
            BigDecimal ivaLinea = generaIva
                    ? baseUnitaria.multiply(item.getCantidad()).multiply(tarifaIva)
                            .divide(CIEN, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            ObsequioDetalleEntity detalle = new ObsequioDetalleEntity();
            detalle.setObsequio(obsequio);
            detalle.setProducto(producto);
            detalle.setProductoPresentacion(presentacion);
            detalle.setCantidadPresentacion(cantidadPresentacion);
            detalle.setCantidad(item.getCantidad());
            detalle.setCostoUnitario(costoUnitario);
            detalle.setBaseComercialUnitaria(baseUnitaria);
            detalle.setIvaValor(ivaLinea);

            detalle = detalleRepository.save(detalle);

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = porReceta ? List.of()
                    : loteStock.salidaDocumento(com.cloud_technological.aura_pos.services.LoteStockService.OBSEQUIO, detalle.getId(), producto, bodega,
                            empresaId, item.getCantidad(), item.getLoteId(), permitirVencidos,
                            Boolean.TRUE.equals(producto.getPermitirStockNegativo()));
            if (lotes.size() == 1) {
                detalle.setLote(lotes.get(0).lote());
                detalle = detalleRepository.save(detalle);
            }
            if (!porReceta) {
                serialStock.salida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_OBSEQUIO, detalle.getId(), producto, bodega, empresaId,
                        item.getCantidad(), item.getSerialIds(), com.cloud_technological.aura_pos.services.SerialStockService.OBSEQUIADO);
            }

            BigDecimal costoLinea;
            if (porReceta) {
                costoLinea = consumoComposicion.consumir(ConsumoComposicionService.ORIGEN_OBSEQUIO,
                        detalle.getId(), empresaId, bodega, producto, consumos,
                        TipoMovimientoInventario.OBSEQUIO.codigo(), "Obsequio #" + obsequio.getId());
                detalle.setCostoUnitario(costoLinea.divide(item.getCantidad(), 2, RoundingMode.HALF_UP));
                detalleRepository.save(detalle);
            } else {
                BigDecimal saldoAnterior = inventario.getStockActual();
                BigDecimal saldoNuevo = saldoAnterior.subtract(item.getCantidad());
                inventario.setStockActual(saldoNuevo);
                inventario.setUpdatedAt(LocalDateTime.now());
                inventarioRepository.save(inventario);

                final String refObsequio = "Obsequio #" + obsequio.getId();
                final BigDecimal costoKardex = costoUnitario;
                loteStock.kardex(lotes, item.getCantidad().negate(), saldoAnterior,
                        (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, producto, lote, cant, ant, nuevo,
                                costoKardex, TipoMovimientoInventario.OBSEQUIO.codigo(), refObsequio));

                costoLinea = item.getCantidad().multiply(costoUnitario);
            }

            costoTotal = costoTotal.add(costoLinea);
            baseTotal = baseTotal.add(item.getCantidad().multiply(baseUnitaria));
            ivaTotal = ivaTotal.add(ivaLinea);
        }

        obsequio.setCostoTotal(costoTotal.setScale(2, RoundingMode.HALF_UP));
        obsequio.setBaseComercialTotal(baseTotal.setScale(2, RoundingMode.HALF_UP));
        obsequio.setIvaTotal(ivaTotal.setScale(2, RoundingMode.HALF_UP));
        obsequio = obsequioRepository.save(obsequio);

        // Sin costo ni IVA no hay asiento que generar (p. ej. solo servicios sin
        // costo cargado): publicar el evento solo dejaría un fallo en el
        // PostingLog por un asiento vacío que nunca debió existir.
        if (obsequio.getCostoTotal().signum() > 0 || obsequio.getIvaTotal().signum() > 0) {
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.contabilidad.infrastructure.event
                            .DocumentoContabilizableEvent(
                            "OBSEQUIO", obsequio.getId(), empresaId,
                            usuarioId != null ? usuarioId.intValue() : null));
        }

        return obtenerPorId(obsequio.getId(), empresaId);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        ObsequioEntity obsequio = obsequioRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Obsequio no encontrado"));

        if (ObsequioEntity.ESTADO_ANULADO.equals(obsequio.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El obsequio ya está anulado");
        }

        List<ObsequioDetalleEntity> detalles = detalleRepository.findByObsequioId(id);

        // Lo que salió por receta también cuenta: el componente pudo cambiar de unidad.
        List<Long> productosObsequio = new java.util.ArrayList<>();
        for (ObsequioDetalleEntity d : detalles) {
            productosObsequio.add(d.getProducto().getId());
            consumoComposicion.consumosDe(ConsumoComposicionService.ORIGEN_OBSEQUIO, d.getId())
                    .forEach(c -> productosObsequio.add(c.getProductoHijo().getId()));
        }
        cambioUnidadProducto.validarDocumentoPrevio("OBSEQUIO", id, productosObsequio, "anular el obsequio");

        // La bodega del documento: la mercancía vuelve de donde salió.
        com.cloud_technological.aura_pos.entity.BodegaEntity bodega =
                bodegaService.resolver(obsequio.getBodega() != null ? obsequio.getBodega().getId() : null,
                        obsequio.getSucursal().getId(), empresaId);

        for (ObsequioDetalleEntity detalle : detalles) {
            // Si salió por receta se devuelve lo que se consumió entonces, no lo
            // que diga la receta hoy.
            if (consumoComposicion.revertir(ConsumoComposicionService.ORIGEN_OBSEQUIO, detalle.getId(),
                    bodega, detalle.getProducto(),
                    TipoMovimientoInventario.ANULACION_OBSEQUIO.codigo(),
                    "Anulación Obsequio #" + obsequio.getId())) {
                continue;
            }

            InventarioEntity inventario = inventarioStock
                    .bloquear(bodega.getId(), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            serialStock.revertirSalida(com.cloud_technological.aura_pos.services.SerialStockService.ORIGEN_OBSEQUIO, detalle.getId(), "anular el obsequio");

            List<com.cloud_technological.aura_pos.services.LoteStockService.Asignacion> lotes = loteStock.revertirDocumento(com.cloud_technological.aura_pos.services.LoteStockService.OBSEQUIO,
                    detalle.getId(), true, "anular el obsequio");
            if (lotes.isEmpty() && detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteRepository.save(lote);
            }

            loteStock.kardex(lotes, detalle.getCantidad(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, detalle.getProducto(),
                            lote != null ? lote : detalle.getLote(), cant, ant, nuevo,
                            detalle.getCostoUnitario(), TipoMovimientoInventario.ANULACION_OBSEQUIO.codigo(),
                            "Anulación Obsequio #" + obsequio.getId()));
        }

        obsequio.setEstado(ObsequioEntity.ESTADO_ANULADO);
        obsequioRepository.save(obsequio);

        eventPublisher.publishEvent(
                new ContabilidadReversaEvent("OBSEQUIO", obsequio.getId(), empresaId, null));
    }

    /**
     * Base gravable del retiro. El precio del producto se maneja con IVA
     * incluido (es el precio al público), así que hay que sacarle el impuesto
     * para obtener la base. Si el caller manda su propio valor comercial, ese
     * manda: hay obsequios cuyo valor no es el precio de lista.
     */
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
     * Base comercial de UNA presentación: la que manda el caller o el precio de
     * la presentación sin IVA (el precio de presentación se guarda con IVA).
     */
    private BigDecimal baseComercialDePresentacion(CreateObsequioDetalleDto item,
            com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity presentacion,
            ProductoEntity producto) {
        if (item.getBaseComercialUnitaria() != null) {
            return item.getBaseComercialUnitaria();
        }
        BigDecimal precio = presentacion.getPrecio() != null ? presentacion.getPrecio() : BigDecimal.ZERO;
        BigDecimal tarifa = producto.getIvaPorcentaje() != null
                ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
        if (tarifa.signum() <= 0) {
            return precio;
        }
        return precio.divide(BigDecimal.ONE.add(tarifa.divide(CIEN, 6, RoundingMode.HALF_UP)),
                6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolverBaseComercial(CreateObsequioDetalleDto item, ProductoEntity producto) {
        if (item.getBaseComercialUnitaria() != null) {
            return item.getBaseComercialUnitaria().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal precio = producto.getPrecio() != null ? producto.getPrecio() : BigDecimal.ZERO;
        BigDecimal tarifa = producto.getIvaPorcentaje() != null
                ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
        if (tarifa.signum() <= 0) {
            return precio.setScale(2, RoundingMode.HALF_UP);
        }
        return precio.divide(BigDecimal.ONE.add(tarifa.divide(CIEN, 6, RoundingMode.HALF_UP)),
                2, RoundingMode.HALF_UP);
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
        movimientoRepository.save(movimiento);
    }

    private ObsequioDto toDto(ObsequioEntity entity) {
        ObsequioDto dto = new ObsequioDto();
        dto.setId(entity.getId());
        dto.setFecha(entity.getFecha());
        dto.setMotivo(entity.getMotivo());
        dto.setObservacion(entity.getObservacion());
        dto.setCostoTotal(entity.getCostoTotal());
        dto.setBaseComercialTotal(entity.getBaseComercialTotal());
        dto.setIvaTotal(entity.getIvaTotal());
        dto.setGeneraIva(entity.getGeneraIva());
        dto.setEstado(entity.getEstado());

        if (entity.getSucursal() != null) {
            dto.setSucursalId(Long.valueOf(entity.getSucursal().getId()));
            dto.setSucursalNombre(entity.getSucursal().getNombre());
        }
        if (entity.getTercero() != null) {
            dto.setTerceroId(entity.getTercero().getId());
            dto.setTerceroNombre(nombreTercero(entity.getTercero()));
        }
        if (entity.getUsuario() != null) {
            dto.setUsuarioNombre(entity.getUsuario().getUsername());
        }

        dto.setDetalles(queryRepository.obtenerDetalles(entity.getId()));
        return dto;
    }

    private String nombreTercero(TerceroEntity t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) {
            return t.getRazonSocial();
        }
        String nombres = t.getNombres() != null ? t.getNombres() : "";
        String apellidos = t.getApellidos() != null ? t.getApellidos() : "";
        return (nombres + " " + apellidos).trim();
    }
}

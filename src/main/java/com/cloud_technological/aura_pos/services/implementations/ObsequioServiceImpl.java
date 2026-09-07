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
            ApplicationEventPublisher eventPublisher) {
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

        ObsequioEntity obsequio = new ObsequioEntity();
        obsequio.setEmpresa(empresa);
        obsequio.setSucursal(sucursal);
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

            InventarioEntity inventario = inventarioRepository
                    .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + producto.getNombre() + " no tiene inventario en esta sucursal"));

            if (!Boolean.TRUE.equals(producto.getPermitirStockNegativo())
                    && inventario.getStockActual().compareTo(item.getCantidad()) < 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente para: " + producto.getNombre()
                        + ". Disponible: " + inventario.getStockActual());
            }

            // El costo se congela aquí: si mañana cambia el costo del producto,
            // el asiento de este obsequio no puede moverse.
            BigDecimal costoUnitario = producto.getCosto() != null
                    ? producto.getCosto() : BigDecimal.ZERO;
            BigDecimal baseUnitaria = resolverBaseComercial(item, producto);
            BigDecimal tarifaIva = producto.getIvaPorcentaje() != null
                    ? producto.getIvaPorcentaje() : BigDecimal.ZERO;
            BigDecimal ivaLinea = generaIva
                    ? baseUnitaria.multiply(item.getCantidad()).multiply(tarifaIva)
                            .divide(CIEN, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            ObsequioDetalleEntity detalle = new ObsequioDetalleEntity();
            detalle.setObsequio(obsequio);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setCostoUnitario(costoUnitario);
            detalle.setBaseComercialUnitaria(baseUnitaria);
            detalle.setIvaValor(ivaLinea);

            if (item.getLoteId() != null) {
                LoteEntity lote = loteRepository.findById(item.getLoteId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lote no encontrado"));
                detalle.setLote(lote);
                lote.setStockActual(lote.getStockActual().subtract(item.getCantidad()));
                loteRepository.save(lote);
            }

            detalleRepository.save(detalle);

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(item.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            registrarMovimiento(sucursal, producto, detalle.getLote(),
                    item.getCantidad().negate(), saldoAnterior, saldoNuevo,
                    costoUnitario, TipoMovimientoInventario.OBSEQUIO.codigo(), "Obsequio #" + obsequio.getId());

            costoTotal = costoTotal.add(item.getCantidad().multiply(costoUnitario));
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

        for (ObsequioDetalleEntity detalle : detalles) {
            InventarioEntity inventario = inventarioRepository
                    .findBySucursalIdAndProductoId(
                            Long.valueOf(obsequio.getSucursal().getId()), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                loteRepository.save(lote);
            }

            registrarMovimiento(obsequio.getSucursal(), detalle.getProducto(), detalle.getLote(),
                    detalle.getCantidad(), saldoAnterior, saldoNuevo,
                    detalle.getCostoUnitario(), TipoMovimientoInventario.ANULACION_OBSEQUIO.codigo(),
                    "Anulación Obsequio #" + obsequio.getId());
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

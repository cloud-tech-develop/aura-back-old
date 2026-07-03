package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionAgregadoDto;
import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDetalleDto;
import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDetalleDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionTableDto;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.DevolucionDetalleEntity;
import com.cloud_technological.aura_pos.entity.DevolucionEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.TesoreriaMovimientoEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionQueryRepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.TesoreriaMovimientoJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.ComprobanteCajaService;
import com.cloud_technological.aura_pos.services.DevolucionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class DevolucionServiceImpl implements DevolucionService {

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private DevolucionJPARepository devolucionRepository;

    @Autowired
    private DevolucionDetalleJPARepository devolucionDetalleRepository;

    @Autowired
    private DevolucionQueryRepository devolucionQueryRepository;

    @Autowired
    private VentaJPARepository ventaRepository;

    @Autowired
    private VentaDetalleJPARepository ventaDetalleRepository;

    @Autowired
    private InventarioJPARepository inventarioRepository;

    @Autowired
    private MovimientoInventarioJPARepository movimientoRepository;

    @Autowired
    private ProductoJPARepository productoRepository;

    @Autowired
    private UsuarioJPARepository usuarioRepository;

    @Autowired
    private CuentaCobrarJPARepository cuentaCobrarRepository;

    @Autowired
    private MovimientoCajaJPARepository movimientoCajaRepository;

    @Autowired
    private TesoreriaMovimientoJPARepository tesoreriaMovimientoRepository;

    @Autowired
    private CuentaBancariaJPARepository cuentaBancariaRepository;

    @Autowired
    private TurnoCajaJPARepository turnoCajaRepository;

    @Autowired
    private ComprobanteCajaService comprobanteCajaService;

    @Override
    public PageImpl<DevolucionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return devolucionQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public DevolucionDto obtenerPorId(Long id, Integer empresaId) {
        DevolucionEntity dev = devolucionRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Devolución no encontrada"));
        return toDto(dev);
    }

    @Override
    @Transactional
    public DevolucionDto crear(CreateDevolucionDto dto, Integer empresaId, Long usuarioId) {
        // 1. Validar venta
        VentaEntity venta = ventaRepository.findByIdAndEmpresaId(dto.getVentaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

        if ("ANULADA".equals(venta.getEstadoVenta())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede devolver una venta anulada");
        }
        if ("DEVUELTA".equals(venta.getEstadoDevolucion())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Esta venta ya fue devuelta en su totalidad");
        }

        // 2. Cargar detalles de la venta
        List<VentaDetalleEntity> ventaDetalles = ventaDetalleRepository.findByVentaId(dto.getVentaId());

        // 3. Usuario
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // 4. Construir devolución
        DevolucionEntity devolucion = new DevolucionEntity();
        devolucion.setEmpresa(venta.getEmpresa());
        devolucion.setSucursal(venta.getSucursal());
        devolucion.setVenta(venta);
        devolucion.setCliente(venta.getCliente());
        devolucion.setUsuario(usuario);
        devolucion.setTipo(dto.getTipo() != null ? dto.getTipo() : "PARCIAL");
        devolucion.setEstado("COMPLETADA");
        devolucion.setMotivo(dto.getMotivo());
        devolucion.setObservaciones(dto.getObservaciones());
        devolucion.setFechaDevolucion(dto.getFechaDevolucion() != null
                ? dto.getFechaDevolucion() : LocalDate.now());
        devolucion.setReintegraInventario(dto.getReintegraInventario() != null ? dto.getReintegraInventario() : true);
        devolucion.setCreatedAt(LocalDateTime.now());
        devolucion.setUpdatedAt(LocalDateTime.now());

        // Consecutivo
        Long count = devolucionRepository.countByEmpresaId(empresaId);
        devolucion.setConsecutivo(count + 1);

        // 5. Procesar detalles
        List<DevolucionDetalleEntity> detalles = new ArrayList<>();
        BigDecimal totalDevolucion = BigDecimal.ZERO;

        for (CreateDevolucionDetalleDto detalleDto : dto.getDetalles()) {
            // Buscar el detalle original en la venta
            VentaDetalleEntity ventaDetalle = ventaDetalles.stream()
                    .filter(vd -> vd.getProducto().getId().equals(detalleDto.getProductoId()))
                    .findFirst()
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El producto " + detalleDto.getProductoId() + " no pertenece a esta venta"));

            // Validar cantidad
            if (detalleDto.getCantidad().compareTo(ventaDetalle.getCantidad()) > 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cantidad a devolver (" + detalleDto.getCantidad() +
                                ") supera la cantidad original (" + ventaDetalle.getCantidad() + ") del producto "
                                + ventaDetalle.getProducto().getNombre());
            }

            // Calcular montos proporcionales
            BigDecimal cantidadOriginal = ventaDetalle.getCantidad();
            BigDecimal proporcion = detalleDto.getCantidad().divide(cantidadOriginal, 8, RoundingMode.HALF_UP);
            BigDecimal impuestoValor = ventaDetalle.getImpuestoValor() != null
                    ? ventaDetalle.getImpuestoValor().multiply(proporcion).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal subtotalLinea = ventaDetalle.getPrecioUnitario()
                    .multiply(detalleDto.getCantidad())
                    .add(impuestoValor)
                    .setScale(2, RoundingMode.HALF_UP);

            DevolucionDetalleEntity detalle = new DevolucionDetalleEntity();
            detalle.setDevolucion(devolucion);
            detalle.setProducto(ventaDetalle.getProducto());
            detalle.setProductoPresentacionId(detalleDto.getProductoPresentacionId());
            detalle.setLoteId(detalleDto.getLoteId() != null ? detalleDto.getLoteId()
                    : (ventaDetalle.getLote() != null ? ventaDetalle.getLote().getId() : null));
            detalle.setCantidad(detalleDto.getCantidad());
            detalle.setPrecioUnitario(ventaDetalle.getPrecioUnitario());
            detalle.setImpuestoValor(impuestoValor);
            detalle.setSubtotalLinea(subtotalLinea);

            detalles.add(detalle);
            totalDevolucion = totalDevolucion.add(subtotalLinea);

            // 6. Reintegrar inventario
            if (Boolean.TRUE.equals(devolucion.getReintegraInventario())) {
                reintegrarStock(venta.getSucursal().getId().longValue(), ventaDetalle.getProducto(),
                        detalleDto.getCantidad(), ventaDetalle.getPrecioUnitario(),
                        dto.getVentaId());
            }

            // 6.b Actualizar el venta_detalle: reducir cantidad y montos proporcionalmente
            BigDecimal cantNuevaVD = cantidadOriginal.subtract(detalleDto.getCantidad());
            BigDecimal montoDescOrig = ventaDetalle.getMontoDescuento() != null
                    ? ventaDetalle.getMontoDescuento() : BigDecimal.ZERO;
            BigDecimal subtotalOrig = ventaDetalle.getSubtotalLinea() != null
                    ? ventaDetalle.getSubtotalLinea() : BigDecimal.ZERO;
            BigDecimal impuestoOrig = ventaDetalle.getImpuestoValor() != null
                    ? ventaDetalle.getImpuestoValor() : BigDecimal.ZERO;

            if (cantNuevaVD.compareTo(BigDecimal.ZERO) <= 0) {
                ventaDetalle.setCantidad(BigDecimal.ZERO);
                ventaDetalle.setMontoDescuento(BigDecimal.ZERO);
                ventaDetalle.setImpuestoValor(BigDecimal.ZERO);
                ventaDetalle.setSubtotalLinea(BigDecimal.ZERO);
            } else {
                BigDecimal factor = cantNuevaVD.divide(cantidadOriginal, 8, RoundingMode.HALF_UP);
                ventaDetalle.setCantidad(cantNuevaVD);
                ventaDetalle.setMontoDescuento(
                        montoDescOrig.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                ventaDetalle.setImpuestoValor(
                        impuestoOrig.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                ventaDetalle.setSubtotalLinea(
                        subtotalOrig.multiply(factor).setScale(2, RoundingMode.HALF_UP));
            }
            ventaDetalleRepository.save(ventaDetalle);
        }

        // 6.5 Productos agregados (cambio): se SUMAN a la venta original.
        //     Crean nuevos venta_detalle, descuentan inventario y su valor se neta
        //     contra lo devuelto para el cálculo del faltante/sobrante.
        BigDecimal totalAgregado = BigDecimal.ZERO;
        BigDecimal ivaAgregado = BigDecimal.ZERO;
        BigDecimal costoAgregado = BigDecimal.ZERO;
        if (dto.getProductosAgregados() != null) {
            for (CreateDevolucionAgregadoDto ag : dto.getProductosAgregados()) {
                ProductoEntity prod = productoRepository.findById(ag.getProductoId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                                "Producto agregado " + ag.getProductoId() + " no encontrado"));

                BigDecimal cant = ag.getCantidad();
                BigDecimal precio = ag.getPrecioUnitario() != null ? ag.getPrecioUnitario() : BigDecimal.ZERO;
                BigDecimal iva = (ag.getImpuestoValor() != null ? ag.getImpuestoValor() : BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal subtotal = precio.multiply(cant).add(iva).setScale(2, RoundingMode.HALF_UP);
                BigDecimal costoUnit = prod.getCosto() != null ? prod.getCosto() : BigDecimal.ZERO;
                BigDecimal costoLinea = costoUnit.multiply(cant).setScale(2, RoundingMode.HALF_UP);

                VentaDetalleEntity nuevo = new VentaDetalleEntity();
                nuevo.setVenta(venta);
                nuevo.setProducto(prod);
                nuevo.setCantidad(cant);
                nuevo.setPrecioUnitario(precio);
                nuevo.setMontoDescuento(BigDecimal.ZERO);
                nuevo.setImpuestoValor(iva);
                nuevo.setSubtotalLinea(subtotal);
                nuevo.setCostoLinea(costoLinea);
                ventaDetalleRepository.save(nuevo);
                ventaDetalles.add(nuevo);

                // Salida de inventario por el producto que se lleva el cliente
                descontarStock(venta.getSucursal().getId().longValue(), prod, cant, costoUnit, dto.getVentaId());

                totalAgregado = totalAgregado.add(subtotal);
                ivaAgregado = ivaAgregado.add(iva);
                costoAgregado = costoAgregado.add(costoLinea);
            }
        }

        // 6.c Recalcular totales de la venta a partir de los detalles actualizados
        recalcularTotalesVenta(venta, ventaDetalles);
        ventaRepository.save(venta);

        devolucion.setTotalDevolucion(totalDevolucion.setScale(2, RoundingMode.HALF_UP));
        devolucion.setTotalAgregado(totalAgregado.setScale(2, RoundingMode.HALF_UP));
        devolucion.setIvaAgregado(ivaAgregado.setScale(2, RoundingMode.HALF_UP));
        devolucion.setCostoAgregado(costoAgregado.setScale(2, RoundingMode.HALF_UP));

        // Neto: positivo = a favor del cliente (reembolso); negativo = faltante que paga el cliente.
        BigDecimal neto = totalDevolucion.subtract(totalAgregado).setScale(2, RoundingMode.HALF_UP);
        devolucion.setNetoDiferencia(neto);
        BigDecimal montoAFavor = neto.compareTo(BigDecimal.ZERO) > 0 ? neto : BigDecimal.ZERO;
        BigDecimal faltante = neto.compareTo(BigDecimal.ZERO) < 0 ? neto.negate() : BigDecimal.ZERO;

        // 7. Método de devolución de dinero
        devolucion.setMetodoDevolucion(dto.getMetodoDevolucion() != null
                ? dto.getMetodoDevolucion() : "SIN_DEVOLUCION");

        // 8. Coordinación cartera + reembolso.
        //    - Venta a crédito (tiene CxC): la devolución primero rebaja el saldo
        //      pendiente; solo el excedente (= lo que el cliente ya había abonado)
        //      se reembolsa en efectivo/transferencia.
        //    - Venta de contado: se reembolsa el total devuelto.
        Optional<CuentaCobrarEntity> optCxC = cuentaCobrarRepository
                .findByVentaIdAndEmpresaId(venta.getId(), empresaId);
        boolean esCredito = optCxC.isPresent();
        BigDecimal descuentoCartera = BigDecimal.ZERO;

        if (esCredito) {
            CuentaCobrarEntity cxc = optCxC.get();
            BigDecimal saldoAntes = cxc.getSaldoPendiente() != null
                    ? cxc.getSaldoPendiente() : BigDecimal.ZERO;
            if (saldoAntes.compareTo(BigDecimal.ZERO) > 0) {
                descuentoCartera = montoAFavor.min(saldoAntes);
                BigDecimal nuevoSaldo = saldoAntes.subtract(descuentoCartera)
                        .setScale(2, RoundingMode.HALF_UP);
                // Reducir la deuda total: el saldo mostrado se calcula como
                // (totalDeuda - totalAbonado), por eso hay que bajar totalDeuda.
                BigDecimal nuevaDeuda = (cxc.getTotalDeuda() != null
                        ? cxc.getTotalDeuda() : BigDecimal.ZERO)
                        .subtract(descuentoCartera).setScale(2, RoundingMode.HALF_UP);
                cxc.setTotalDeuda(nuevaDeuda);
                cxc.setSaldoPendiente(nuevoSaldo);
                if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
                    cxc.setEstado("pagada");
                }
                cuentaCobrarRepository.save(cxc);
                devolucion.setAfectoCartera(true);
                devolucion.setMontoCarteraAfectado(descuentoCartera.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (devolucion.getAfectoCartera() == null) {
            devolucion.setAfectoCartera(false);
        }

        // Monto efectivamente reembolsable al cliente (sobre el neto a favor).
        BigDecimal montoReembolso = esCredito
                ? montoAFavor.subtract(descuentoCartera).setScale(2, RoundingMode.HALF_UP)
                : montoAFavor;

        // 9. Si es devolución TOTAL, marcar la venta
        if ("TOTAL".equals(devolucion.getTipo())) {
            venta.setEstadoDevolucion("DEVUELTA");
            ventaRepository.save(venta);
        }

        // 10. Guardar
        DevolucionEntity saved = devolucionRepository.save(devolucion);
        for (DevolucionDetalleEntity d : detalles) {
            d.setDevolucion(saved);
        }
        devolucionDetalleRepository.saveAll(detalles);
        saved.setDetalles(detalles);

        // 11. Movimientos de caja y tesorería.
        //     Se fechan en la fecha de la VENTA original (afecta la operación de ese día).
        LocalDate fechaMov = venta.getFechaEmision() != null
                ? venta.getFechaEmision().toLocalDate() : LocalDate.now();
        if (montoReembolso != null && montoReembolso.compareTo(BigDecimal.ZERO) > 0) {
            registrarMovimientosDinero(saved, usuario, usuarioId, empresaId, montoReembolso, fechaMov);
        } else if (faltante.compareTo(BigDecimal.ZERO) > 0) {
            registrarIngresoFaltante(saved, usuario, usuarioId, empresaId, faltante, fechaMov);
        }

        // 12. Generar el asiento contable de la devolución tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.DevolucionContabilizableEvent(
                        saved.getId(), empresaId, usuarioId != null ? usuarioId.intValue() : null));

        return toDto(saved);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        DevolucionEntity devolucion = devolucionRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Devolución no encontrada"));

        if ("ANULADA".equals(devolucion.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La devolución ya está anulada");
        }

        // Revertir inventario si corresponde
        if (Boolean.TRUE.equals(devolucion.getReintegraInventario())) {
            List<DevolucionDetalleEntity> detalles = devolucionDetalleRepository
                    .findByDevolucionId(devolucion.getId());
            for (DevolucionDetalleEntity detalle : detalles) {
                revertirStock(devolucion.getSucursal().getId().longValue(), detalle.getProducto(),
                        detalle.getCantidad(), detalle.getPrecioUnitario(), devolucion.getId());
            }
        }

        // Revertir movimientos de caja y tesorería
        revertirMovimientosDinero(devolucion, empresaId);

        // Restaurar venta: sumar de vuelta cantidades y montos a venta_detalle
        if (devolucion.getVenta() != null) {
            VentaEntity venta = devolucion.getVenta();
            List<DevolucionDetalleEntity> detallesDev = devolucionDetalleRepository
                    .findByDevolucionId(devolucion.getId());
            List<VentaDetalleEntity> ventaDetalles = ventaDetalleRepository
                    .findByVentaId(venta.getId());
            for (DevolucionDetalleEntity dd : detallesDev) {
                Long productoId = dd.getProducto().getId();
                Optional<VentaDetalleEntity> optVD = ventaDetalles.stream()
                        .filter(vd -> vd.getProducto().getId().equals(productoId))
                        .findFirst();
                if (optVD.isPresent()) {
                    VentaDetalleEntity vd = optVD.get();
                    BigDecimal cantActual = vd.getCantidad() != null ? vd.getCantidad() : BigDecimal.ZERO;
                    BigDecimal subActual = vd.getSubtotalLinea() != null ? vd.getSubtotalLinea() : BigDecimal.ZERO;
                    BigDecimal impActual = vd.getImpuestoValor() != null ? vd.getImpuestoValor() : BigDecimal.ZERO;
                    vd.setCantidad(cantActual.add(dd.getCantidad() != null ? dd.getCantidad() : BigDecimal.ZERO));
                    vd.setSubtotalLinea(subActual.add(
                            dd.getSubtotalLinea() != null ? dd.getSubtotalLinea() : BigDecimal.ZERO));
                    vd.setImpuestoValor(impActual.add(
                            dd.getImpuestoValor() != null ? dd.getImpuestoValor() : BigDecimal.ZERO));
                    ventaDetalleRepository.save(vd);
                }
            }
            recalcularTotalesVenta(venta, ventaDetalleRepository.findByVentaId(venta.getId()));
            ventaRepository.save(venta);
        }

        // Restaurar cartera si se había afectado
        if (Boolean.TRUE.equals(devolucion.getAfectoCartera())
                && devolucion.getMontoCarteraAfectado() != null
                && devolucion.getVenta() != null) {
            cuentaCobrarRepository
                .findByVentaIdAndEmpresaId(devolucion.getVenta().getId(), empresaId)
                .ifPresent(cxc -> {
                    BigDecimal montoCartera = devolucion.getMontoCarteraAfectado();
                    BigDecimal saldoRestaurado = (cxc.getSaldoPendiente() != null
                            ? cxc.getSaldoPendiente() : BigDecimal.ZERO)
                            .add(montoCartera)
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal deudaRestaurada = (cxc.getTotalDeuda() != null
                            ? cxc.getTotalDeuda() : BigDecimal.ZERO)
                            .add(montoCartera)
                            .setScale(2, RoundingMode.HALF_UP);
                    cxc.setTotalDeuda(deudaRestaurada);
                    cxc.setSaldoPendiente(saldoRestaurado);
                    if (saldoRestaurado.compareTo(BigDecimal.ZERO) > 0) {
                        cxc.setEstado("activa");
                    }
                    cuentaCobrarRepository.save(cxc);
                });
        }

        // Revertir estado de venta si era TOTAL
        if ("TOTAL".equals(devolucion.getTipo())) {
            VentaEntity venta = devolucion.getVenta();
            venta.setEstadoDevolucion(null);
            ventaRepository.save(venta);
        }

        devolucion.setEstado("ANULADA");
        devolucion.setUpdatedAt(LocalDateTime.now());
        devolucionRepository.save(devolucion);

        // Reversar el asiento contable de la devolución tras el commit.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "DEVOLUCION", devolucion.getId(), empresaId, null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void reintegrarStock(Long sucursalId, ProductoEntity producto, BigDecimal cantidad,
            BigDecimal costoUnitario, Long ventaId) {
        Optional<InventarioEntity> optInv = inventarioRepository.findBySucursalIdAndProductoId(sucursalId,
                producto.getId());
        if (optInv.isPresent()) {
            InventarioEntity inv = optInv.get();
            BigDecimal saldoAnterior = inv.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(cantidad);
            inv.setStockActual(saldoNuevo);
            inv.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inv);

            registrarMovimiento(inv.getSucursal(), producto, cantidad, saldoAnterior, saldoNuevo,
                    costoUnitario, "DEVOLUCION", "Devolución de Venta #" + ventaId);
        }
    }

    private void revertirStock(Long sucursalId, ProductoEntity producto, BigDecimal cantidad,
            BigDecimal costoUnitario, Long devolucionId) {
        Optional<InventarioEntity> optInv = inventarioRepository.findBySucursalIdAndProductoId(sucursalId,
                producto.getId());
        if (optInv.isPresent()) {
            InventarioEntity inv = optInv.get();
            BigDecimal saldoAnterior = inv.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(cantidad);
            inv.setStockActual(saldoNuevo);
            inv.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inv);

            registrarMovimiento(inv.getSucursal(), producto, cantidad.negate(), saldoAnterior, saldoNuevo,
                    costoUnitario, "ANULACION_DEVOLUCION", "Anulación Devolución #" + devolucionId);
        }
    }

    /**
     * Recalcula los totales (subtotal, descuento, impuestos, totalPagar) de la venta
     * a partir de los detalles actualizados, y escala las bases de IVA por tarifa.
     */
    private void recalcularTotalesVenta(VentaEntity venta, List<VentaDetalleEntity> detalles) {
        BigDecimal nuevoTotalPagar = BigDecimal.ZERO;
        BigDecimal nuevoImpuestos = BigDecimal.ZERO;
        BigDecimal nuevoDescuento = BigDecimal.ZERO;
        for (VentaDetalleEntity d : detalles) {
            nuevoTotalPagar = nuevoTotalPagar.add(safe(d.getSubtotalLinea()));
            nuevoImpuestos = nuevoImpuestos.add(safe(d.getImpuestoValor()));
            nuevoDescuento = nuevoDescuento.add(safe(d.getMontoDescuento()));
        }
        BigDecimal nuevoSubtotal = nuevoTotalPagar.add(nuevoDescuento).subtract(nuevoImpuestos);

        BigDecimal totalPrevio = safe(venta.getTotalPagar());
        BigDecimal factor = totalPrevio.compareTo(BigDecimal.ZERO) > 0
                ? nuevoTotalPagar.divide(totalPrevio, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        venta.setTotalPagar(nuevoTotalPagar.setScale(2, RoundingMode.HALF_UP));
        venta.setImpuestosTotal(nuevoImpuestos.setScale(2, RoundingMode.HALF_UP));
        venta.setDescuentoTotal(nuevoDescuento.setScale(2, RoundingMode.HALF_UP));
        venta.setSubtotal(nuevoSubtotal.setScale(2, RoundingMode.HALF_UP));

        venta.setIvaBase0(safe(venta.getIvaBase0()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
        venta.setIvaBase5(safe(venta.getIvaBase5()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
        venta.setIvaValor5(safe(venta.getIvaValor5()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
        venta.setIvaBase19(safe(venta.getIvaBase19()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
        venta.setIvaValor19(safe(venta.getIvaValor19()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void registrarMovimiento(com.cloud_technological.aura_pos.entity.SucursalEntity sucursal,
            ProductoEntity producto, BigDecimal cantidad, BigDecimal saldoAnterior,
            BigDecimal saldoNuevo, BigDecimal costoHistorico, String tipo, String referencia) {
        MovimientoInventarioEntity mov = new MovimientoInventarioEntity();
        mov.setSucursal(sucursal);
        mov.setProducto(producto);
        mov.setCantidad(cantidad);
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoNuevo(saldoNuevo);
        mov.setCostoHistorico(costoHistorico);
        mov.setTipoMovimiento(tipo);
        mov.setReferenciaOrigen(referencia);
        mov.setCreatedAt(LocalDateTime.now());
        movimientoRepository.save(mov);
    }

    /**
     * Registra los movimientos de caja y tesorería cuando el método de devolución
     * implica salida de dinero (EFECTIVO o TRANSFERENCIA).
     * Guarda los IDs de los movimientos en la entidad y persiste los cambios.
     */
    private void registrarMovimientosDinero(DevolucionEntity dev, UsuarioEntity usuario,
            Long usuarioId, Integer empresaId, BigDecimal montoReembolso, LocalDate fechaMov) {
        String metodo = dev.getMetodoDevolucion();
        if (metodo == null || "SIN_DEVOLUCION".equals(metodo) || "NOTA_CREDITO".equals(metodo)) {
            return;
        }

        // Sin excedente que reembolsar (p.ej. crédito absorbido totalmente por la cartera).
        if (montoReembolso == null || montoReembolso.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal monto = montoReembolso;
        String concepto = "Devolución de venta DEV-" + dev.getConsecutivo();
        String referencia = "DEV-" + dev.getConsecutivo();
        boolean cambios = false;

        Long turnoCajaIdParaComprobante = null;

        if ("EFECTIVO".equals(metodo)) {
            // ── MovimientoCaja ──────────────────────────────────────────────
            Optional<TurnoCajaEntity> turno = turnoCajaRepository
                    .findByUsuarioIdAndEstado(usuarioId, "ABIERTA");
            if (turno.isPresent()) {
                MovimientoCajaEntity mc = MovimientoCajaEntity.builder()
                        .turnoCaja(turno.get())
                        .usuario(usuario)
                        .tipo("EGRESO")
                        .concepto(concepto)
                        .monto(monto)
                        .build();
                dev.setMovimientoCajaId(movimientoCajaRepository.save(mc).getId());
                turnoCajaIdParaComprobante = turno.get().getId();
                cambios = true;
            }

            // ── TesoreriaMovimiento — cuenta tipo CAJA ─────────────────────
            Optional<CuentaBancariaEntity> cuentaCaja = cuentaBancariaRepository
                    .findFirstByEmpresaIdAndTipoAndActivaIsTrue(empresaId, "CAJA");
            if (cuentaCaja.isPresent()) {
                CuentaBancariaEntity cb = cuentaCaja.get();
                cb.setSaldoActual(cb.getSaldoActual().subtract(monto).setScale(2, RoundingMode.HALF_UP));
                cuentaBancariaRepository.save(cb);

                TesoreriaMovimientoEntity tm = TesoreriaMovimientoEntity.builder()
                        .empresaId(empresaId)
                        .cuentaBancariaId(cb.getId())
                        .tipo("EGRESO")
                        .monto(monto)
                        .concepto(concepto)
                        .referencia(referencia)
                        .fecha(fechaMov)
                        .categoria("DEVOLUCION")
                        .usuarioId(usuarioId.intValue())
                        .build();
                dev.setTesoreriaMovimientoId(tesoreriaMovimientoRepository.save(tm).getId());
                cambios = true;
            }

        } else if ("TRANSFERENCIA".equals(metodo)) {
            // ── TesoreriaMovimiento — primera cuenta tipo BANCO activa ─────
            Optional<CuentaBancariaEntity> cuentaBanco = cuentaBancariaRepository
                    .findFirstByEmpresaIdAndTipoAndActivaIsTrue(empresaId, "BANCO");
            if (cuentaBanco.isPresent()) {
                CuentaBancariaEntity cb = cuentaBanco.get();
                cb.setSaldoActual(cb.getSaldoActual().subtract(monto).setScale(2, RoundingMode.HALF_UP));
                cuentaBancariaRepository.save(cb);

                TesoreriaMovimientoEntity tm = TesoreriaMovimientoEntity.builder()
                        .empresaId(empresaId)
                        .cuentaBancariaId(cb.getId())
                        .tipo("EGRESO")
                        .monto(monto)
                        .concepto(concepto)
                        .referencia(referencia)
                        .fecha(fechaMov)
                        .categoria("DEVOLUCION")
                        .usuarioId(usuarioId.intValue())
                        .build();
                dev.setTesoreriaMovimientoId(tesoreriaMovimientoRepository.save(tm).getId());
                cambios = true;
            }
        }

        // ── Comprobante de egreso ──────────────────────────────────────────
        String clienteNombre = dev.getCliente() != null
                ? (dev.getCliente().getNombres() != null ? dev.getCliente().getNombres() : "Consumidor Final")
                : "Consumidor Final";
        comprobanteCajaService.generar(
            empresaId,
            dev.getUsuario().getId(),
            "EGRESO",
            "Devolución DEV-" + dev.getConsecutivo() + " — " + dev.getMotivo(),
            monto,
            metodo,
            clienteNombre,
            "DEVOLUCION",
            dev.getId(),
            turnoCajaIdParaComprobante
        );

        if (cambios) {
            devolucionRepository.save(dev);
        }
    }

    /**
     * Registra el INGRESO por el faltante que paga el cliente cuando el cambio deja
     * saldo a favor del negocio (productos agregados valen más que lo devuelto).
     */
    private void registrarIngresoFaltante(DevolucionEntity dev, UsuarioEntity usuario,
            Long usuarioId, Integer empresaId, BigDecimal faltante, LocalDate fechaMov) {
        String metodo = dev.getMetodoDevolucion();
        if (metodo == null || "SIN_DEVOLUCION".equals(metodo) || "NOTA_CREDITO".equals(metodo)) {
            // El faltante queda registrado en el neto pero sin movimiento de dinero.
            return;
        }

        String concepto = "Cobro faltante cambio DEV-" + dev.getConsecutivo();
        String referencia = "DEV-" + dev.getConsecutivo();
        Long turnoCajaIdParaComprobante = null;
        boolean cambios = false;

        if ("EFECTIVO".equals(metodo)) {
            Optional<TurnoCajaEntity> turno = turnoCajaRepository
                    .findByUsuarioIdAndEstado(usuarioId, "ABIERTA");
            if (turno.isPresent()) {
                MovimientoCajaEntity mc = MovimientoCajaEntity.builder()
                        .turnoCaja(turno.get())
                        .usuario(usuario)
                        .tipo("INGRESO")
                        .concepto(concepto)
                        .monto(faltante)
                        .build();
                dev.setMovimientoCajaId(movimientoCajaRepository.save(mc).getId());
                turnoCajaIdParaComprobante = turno.get().getId();
                cambios = true;
            }
            Optional<CuentaBancariaEntity> cuentaCaja = cuentaBancariaRepository
                    .findFirstByEmpresaIdAndTipoAndActivaIsTrue(empresaId, "CAJA");
            if (cuentaCaja.isPresent()) {
                CuentaBancariaEntity cb = cuentaCaja.get();
                cb.setSaldoActual(cb.getSaldoActual().add(faltante).setScale(2, RoundingMode.HALF_UP));
                cuentaBancariaRepository.save(cb);
                dev.setTesoreriaMovimientoId(tesoreriaMovimientoRepository.save(
                        TesoreriaMovimientoEntity.builder()
                                .empresaId(empresaId).cuentaBancariaId(cb.getId())
                                .tipo("INGRESO").monto(faltante).concepto(concepto)
                                .referencia(referencia).fecha(fechaMov).categoria("DEVOLUCION")
                                .usuarioId(usuarioId.intValue()).build()).getId());
                cambios = true;
            }
        } else if ("TRANSFERENCIA".equals(metodo)) {
            Optional<CuentaBancariaEntity> cuentaBanco = cuentaBancariaRepository
                    .findFirstByEmpresaIdAndTipoAndActivaIsTrue(empresaId, "BANCO");
            if (cuentaBanco.isPresent()) {
                CuentaBancariaEntity cb = cuentaBanco.get();
                cb.setSaldoActual(cb.getSaldoActual().add(faltante).setScale(2, RoundingMode.HALF_UP));
                cuentaBancariaRepository.save(cb);
                dev.setTesoreriaMovimientoId(tesoreriaMovimientoRepository.save(
                        TesoreriaMovimientoEntity.builder()
                                .empresaId(empresaId).cuentaBancariaId(cb.getId())
                                .tipo("INGRESO").monto(faltante).concepto(concepto)
                                .referencia(referencia).fecha(fechaMov).categoria("DEVOLUCION")
                                .usuarioId(usuarioId.intValue()).build()).getId());
                cambios = true;
            }
        }

        String clienteNombre = dev.getCliente() != null
                ? (dev.getCliente().getNombres() != null ? dev.getCliente().getNombres() : "Consumidor Final")
                : "Consumidor Final";
        comprobanteCajaService.generar(empresaId, dev.getUsuario().getId(), "INGRESO",
                concepto, faltante, metodo, clienteNombre, "DEVOLUCION", dev.getId(),
                turnoCajaIdParaComprobante);

        if (cambios) {
            devolucionRepository.save(dev);
        }
    }

    /** Descuenta inventario por un producto agregado (cambio) que se lleva el cliente. */
    private void descontarStock(Long sucursalId, ProductoEntity producto, BigDecimal cantidad,
            BigDecimal costoUnitario, Long ventaId) {
        Optional<InventarioEntity> optInv = inventarioRepository.findBySucursalIdAndProductoId(sucursalId,
                producto.getId());
        if (optInv.isPresent()) {
            InventarioEntity inv = optInv.get();
            BigDecimal saldoAnterior = inv.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(cantidad);
            inv.setStockActual(saldoNuevo);
            inv.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inv);

            registrarMovimiento(inv.getSucursal(), producto, cantidad.negate(), saldoAnterior, saldoNuevo,
                    costoUnitario, "DEVOLUCION_CAMBIO", "Cambio en Devolución de Venta #" + ventaId);
        }
    }

    /**
     * Revierte los movimientos de caja y tesorería al anular una devolución.
     * Anula el movimiento de tesorería y restaura el saldo de la cuenta bancaria.
     * Crea un INGRESO en caja en el turno actual del anuador (si lo hay).
     */
    private void revertirMovimientosDinero(DevolucionEntity dev, Integer empresaId) {
        // Si el neto fue negativo, se registró un INGRESO (cobro del faltante):
        // la reversión debe hacer lo contrario (quitar saldo / EGRESO en caja).
        boolean fueIngreso = dev.getNetoDiferencia() != null
                && dev.getNetoDiferencia().compareTo(BigDecimal.ZERO) < 0;

        // Revertir tesorería
        if (dev.getTesoreriaMovimientoId() != null) {
            tesoreriaMovimientoRepository.findById(dev.getTesoreriaMovimientoId())
                    .ifPresent(tm -> {
                        if (!Boolean.TRUE.equals(tm.getAnulado())) {
                            tm.setAnulado(true);
                            tesoreriaMovimientoRepository.save(tm);
                            // Restaurar saldo de la cuenta bancaria (opuesto al movimiento original)
                            cuentaBancariaRepository.findById(tm.getCuentaBancariaId())
                                    .ifPresent(cb -> {
                                        BigDecimal nuevo = fueIngreso
                                                ? cb.getSaldoActual().subtract(tm.getMonto())
                                                : cb.getSaldoActual().add(tm.getMonto());
                                        cb.setSaldoActual(nuevo.setScale(2, RoundingMode.HALF_UP));
                                        cuentaBancariaRepository.save(cb);
                                    });
                        }
                    });
        }

        // Revertir caja — crear INGRESO en el turno actual del usuario que anula,
        // si el movimiento original existía y el turno sigue accesible.
        if (dev.getMovimientoCajaId() != null) {
            movimientoCajaRepository.findById(dev.getMovimientoCajaId())
                    .ifPresent(mc -> {
                        // Intentar usar el mismo turno si sigue abierto
                        TurnoCajaEntity turnoTarget = mc.getTurnoCaja();
                        if (turnoTarget == null || !"ABIERTO".equals(turnoTarget.getEstado())) {
                            // Buscar turno abierto del usuario que registró la devolución
                            turnoTarget = turnoCajaRepository
                                    .findByUsuarioIdAndEstado(
                                            dev.getUsuario().getId().longValue(), "ABIERTO")
                                    .orElse(null);
                        }
                        if (turnoTarget != null) {
                            MovimientoCajaEntity reverso = MovimientoCajaEntity.builder()
                                    .turnoCaja(turnoTarget)
                                    .usuario(dev.getUsuario())
                                    .tipo(fueIngreso ? "EGRESO" : "INGRESO")
                                    .concepto("Reverso anulación DEV-" + dev.getConsecutivo())
                                    .monto(mc.getMonto())
                                    .build();
                            movimientoCajaRepository.save(reverso);
                        }
                    });
        }
    }

    private DevolucionDto toDto(DevolucionEntity dev) {
        DevolucionDto dto = new DevolucionDto();
        dto.setId(dev.getId());
        dto.setEmpresaId(dev.getEmpresa() != null ? dev.getEmpresa().getId() : null);
        dto.setSucursalId(dev.getSucursal() != null ? dev.getSucursal().getId() : null);
        dto.setVentaId(dev.getVenta() != null ? dev.getVenta().getId() : null);
        if (dev.getVenta() != null) {
            dto.setNumeroVenta(
                    (dev.getVenta().getPrefijo() != null ? dev.getVenta().getPrefijo() : "") +
                            (dev.getVenta().getConsecutivo() != null ? dev.getVenta().getConsecutivo() : ""));
        }
        dto.setClienteId(dev.getCliente() != null ? dev.getCliente().getId() : null);
        dto.setClienteNombre(dev.getCliente() != null ? dev.getCliente().getNombres() : "Consumidor Final");
        dto.setUsuarioId(dev.getUsuario() != null ? dev.getUsuario().getId() : null);
        dto.setConsecutivo(dev.getConsecutivo());
        dto.setTipo(dev.getTipo());
        dto.setEstado(dev.getEstado());
        dto.setMotivo(dev.getMotivo());
        dto.setTotalDevolucion(dev.getTotalDevolucion());
        dto.setTotalAgregado(dev.getTotalAgregado());
        dto.setNetoDiferencia(dev.getNetoDiferencia());
        dto.setFechaDevolucion(dev.getFechaDevolucion());
        dto.setReintegraInventario(dev.getReintegraInventario());
        dto.setObservaciones(dev.getObservaciones());
        dto.setMetodoDevolucion(dev.getMetodoDevolucion());
        dto.setAfectoCartera(dev.getAfectoCartera());
        dto.setMontoCarteraAfectado(dev.getMontoCarteraAfectado());
        dto.setCreatedAt(dev.getCreatedAt());

        if (dev.getDetalles() != null) {
            List<DevolucionDetalleDto> detallesDto = new ArrayList<>();
            for (DevolucionDetalleEntity d : dev.getDetalles()) {
                DevolucionDetalleDto dd = new DevolucionDetalleDto();
                dd.setId(d.getId());
                dd.setProductoId(d.getProducto() != null ? d.getProducto().getId() : null);
                dd.setProductoNombre(d.getProducto() != null ? d.getProducto().getNombre() : null);
                dd.setProductoPresentacionId(d.getProductoPresentacionId());
                dd.setLoteId(d.getLoteId());
                dd.setCantidad(d.getCantidad());
                dd.setPrecioUnitario(d.getPrecioUnitario());
                dd.setImpuestoValor(d.getImpuestoValor());
                dd.setSubtotalLinea(d.getSubtotalLinea());
                detallesDto.add(dd);
            }
            dto.setDetalles(detallesDto);
        }
        return dto;
    }
}

package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDetalleDto;
import com.cloud_technological.aura_pos.dto.devolucion.CreateDevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDetalleDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionDto;
import com.cloud_technological.aura_pos.dto.devolucion.DevolucionTableDto;
import com.cloud_technological.aura_pos.entity.DevolucionDetalleEntity;
import com.cloud_technological.aura_pos.entity.DevolucionEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import java.time.LocalDate;

import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.TesoreriaMovimientoEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.TesoreriaMovimientoJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionQueryRepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
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
        }

        devolucion.setTotalDevolucion(totalDevolucion.setScale(2, RoundingMode.HALF_UP));

        // 7. Método de devolución de dinero
        devolucion.setMetodoDevolucion(dto.getMetodoDevolucion() != null
                ? dto.getMetodoDevolucion() : "SIN_DEVOLUCION");

        // 8. Afectación de cartera — solo si la venta era a crédito (tiene CxC activa)
        Optional<CuentaCobrarEntity> optCxC = cuentaCobrarRepository
                .findByVentaIdAndEmpresaId(venta.getId(), empresaId);
        if (optCxC.isPresent()) {
            CuentaCobrarEntity cxc = optCxC.get();
            if (cxc.getSaldoPendiente() != null
                    && cxc.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal saldoAntes = cxc.getSaldoPendiente();
                BigDecimal descuento = totalDevolucion.min(saldoAntes);
                BigDecimal nuevoSaldo = saldoAntes.subtract(descuento).setScale(2, RoundingMode.HALF_UP);
                cxc.setSaldoPendiente(nuevoSaldo);
                if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
                    cxc.setEstado("pagada");
                }
                cuentaCobrarRepository.save(cxc);
                devolucion.setAfectoCartera(true);
                devolucion.setMontoCarteraAfectado(descuento.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (devolucion.getAfectoCartera() == null) {
            devolucion.setAfectoCartera(false);
        }

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

        // 11. Movimientos de caja y tesorería
        registrarMovimientosDinero(saved, usuario, usuarioId, empresaId);

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

        // Restaurar cartera si se había afectado
        if (Boolean.TRUE.equals(devolucion.getAfectoCartera())
                && devolucion.getMontoCarteraAfectado() != null
                && devolucion.getVenta() != null) {
            cuentaCobrarRepository
                .findByVentaIdAndEmpresaId(devolucion.getVenta().getId(), empresaId)
                .ifPresent(cxc -> {
                    BigDecimal saldoRestaurado = (cxc.getSaldoPendiente() != null
                            ? cxc.getSaldoPendiente() : BigDecimal.ZERO)
                            .add(devolucion.getMontoCarteraAfectado())
                            .setScale(2, RoundingMode.HALF_UP);
                    cxc.setSaldoPendiente(saldoRestaurado);
                    if (!"pagada".equals(cxc.getEstado())) {
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
            Long usuarioId, Integer empresaId) {
        String metodo = dev.getMetodoDevolucion();
        if (metodo == null || "SIN_DEVOLUCION".equals(metodo) || "NOTA_CREDITO".equals(metodo)) {
            return;
        }

        BigDecimal monto = dev.getTotalDevolucion();
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
                        .fecha(LocalDate.now())
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
                        .fecha(LocalDate.now())
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
            dev.getTotalDevolucion(),
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
     * Revierte los movimientos de caja y tesorería al anular una devolución.
     * Anula el movimiento de tesorería y restaura el saldo de la cuenta bancaria.
     * Crea un INGRESO en caja en el turno actual del anuador (si lo hay).
     */
    private void revertirMovimientosDinero(DevolucionEntity dev, Integer empresaId) {
        // Revertir tesorería
        if (dev.getTesoreriaMovimientoId() != null) {
            tesoreriaMovimientoRepository.findById(dev.getTesoreriaMovimientoId())
                    .ifPresent(tm -> {
                        if (!Boolean.TRUE.equals(tm.getAnulado())) {
                            tm.setAnulado(true);
                            tesoreriaMovimientoRepository.save(tm);
                            // Restaurar saldo de la cuenta bancaria
                            cuentaBancariaRepository.findById(tm.getCuentaBancariaId())
                                    .ifPresent(cb -> {
                                        cb.setSaldoActual(cb.getSaldoActual()
                                                .add(tm.getMonto())
                                                .setScale(2, RoundingMode.HALF_UP));
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
                                    .tipo("INGRESO")
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

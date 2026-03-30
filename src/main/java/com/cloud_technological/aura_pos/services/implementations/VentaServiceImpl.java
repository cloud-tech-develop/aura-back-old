package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.facturacion.FacturaDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaTableDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SerialProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleSerialEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.entity.VentaPagoEntity;
import com.cloud_technological.aura_pos.mappers.VentaDetalleMapper;
import com.cloud_technological.aura_pos.mappers.VentaMapper;
import com.cloud_technological.aura_pos.mappers.VentaPagoMapper;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.SerialProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionJPARepository;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_detalle_serial.VentaDetalleSerialJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaQueryRepository;
import com.cloud_technological.aura_pos.dto.cartera.ValidacionCreditoDto;
import com.cloud_technological.aura_pos.services.CarteraService;
import com.cloud_technological.aura_pos.services.ComisionService;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.services.FacturaService;
import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class VentaServiceImpl implements VentaService{
    private final VentaQueryRepository ventaRepository;
    private final VentaJPARepository ventaJPARepository;
    private final VentaDetalleJPARepository detalleJPARepository;
    private final VentaPagoJPARepository pagoJPARepository;
    private final VentaDetalleSerialJPARepository serialVentaJPARepository;
    private final TurnoCajaJPARepository turnoJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final SerialProductoJPARepository serialJPARepository;
    private final TerceroJPARepository terceroJPARepository;
    private final UsuarioJPARepository usuarioJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final VentaMapper ventaMapper;
    private final VentaDetalleMapper detalleMapper;
    private final VentaPagoMapper pagoMapper;
    private final ProductoComposicionJPARepository composicionJPARepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final FacturaService facturaService;
    private final CuentaCobrarService cuentaCobrarService;
    private final ComisionService comisionService;
    private final CarteraService carteraService;
    private final CuentaBancariaJPARepository cuentaBancariaJPARepository;

    @Autowired
    public VentaServiceImpl(VentaQueryRepository ventaRepository,
            VentaJPARepository ventaJPARepository,
            VentaDetalleJPARepository detalleJPARepository,
            VentaPagoJPARepository pagoJPARepository,
            VentaDetalleSerialJPARepository serialVentaJPARepository,
            TurnoCajaJPARepository turnoJPARepository,
            ProductoJPARepository productoJPARepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            SerialProductoJPARepository serialJPARepository,
            TerceroJPARepository terceroJPARepository,
            UsuarioJPARepository usuarioJPARepository,
            EmpresaJPARepository empresaRepository,
            SucursalJPARepository sucursalJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository,
            VentaMapper ventaMapper,
            ProductoComposicionJPARepository composicionJPARepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            VentaDetalleMapper detalleMapper,
            VentaPagoMapper pagoMapper,
            FacturaService facturaService,
            CuentaCobrarService cuentaCobrarService,
            ComisionService comisionService,
            CarteraService carteraService,
            CuentaBancariaJPARepository cuentaBancariaJPARepository) {
        this.ventaRepository = ventaRepository;
        this.ventaJPARepository = ventaJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.pagoJPARepository = pagoJPARepository;
        this.serialVentaJPARepository = serialVentaJPARepository;
        this.turnoJPARepository = turnoJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.serialJPARepository = serialJPARepository;
        this.terceroJPARepository = terceroJPARepository;
        this.usuarioJPARepository = usuarioJPARepository;
        this.composicionJPARepository = composicionJPARepository;
        this.presentacionJPARepository = presentacionJPARepository;
        this.empresaRepository = empresaRepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
        this.ventaMapper = ventaMapper;
        this.detalleMapper = detalleMapper;
        this.pagoMapper = pagoMapper;
        this.facturaService = facturaService;
        this.cuentaCobrarService = cuentaCobrarService;
        this.comisionService = comisionService;
        this.carteraService = carteraService;
        this.cuentaBancariaJPARepository = cuentaBancariaJPARepository;
    }

    @Override
    public PageImpl<VentaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return ventaRepository.listar(pageable, empresaId);
    }

    @Override
    public VentaDto obtenerPorId(Long id, Integer empresaId) {
        VentaEntity entity = ventaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

        VentaDto dto = ventaMapper.toDto(entity);
        dto.setDetalles(ventaRepository.obtenerDetalles(entity.getId()));
        dto.setPagos(ventaRepository.obtenerPagos(entity.getId()));
        return dto;
    }

    @Override
    @Transactional
    public VentaDto crear(CreateVentaDto dto, Integer empresaId, Long usuarioId) {

        // 1. Validar turno abierto
        TurnoCajaEntity turno = turnoJPARepository.findByIdAndCajaSucursalEmpresaId(dto.getTurnoCajaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Turno no encontrado"));

        if (!turno.getEstado().equals("ABIERTA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El turno de caja no está abierto");

        SucursalEntity sucursal = turno.getCaja().getSucursal();

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        UsuarioEntity usuario = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));

        // 2. Validar que el total pagado cubra el total de la venta
        BigDecimal totalPagado = dto.getPagos().stream()
                .map(CreateVentaPagoDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2.1. Validar cliente obligatorio para ventas a crédito (HU-006)
        boolean tienePagoCredito = dto.getPagos().stream()
                .anyMatch(p -> "credito".equalsIgnoreCase(p.getMetodoPago()));
        
        if (tienePagoCredito && dto.getClienteId() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, 
                    "Las ventas a crédito requieren un cliente asociado");
        }

        // 2.2. Validar que el cliente existe si se proporcionó
        TerceroEntity cliente = null;
        if (dto.getClienteId() != null) {
            cliente = terceroJPARepository.findByIdAndEmpresaId(dto.getClienteId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        // 2.3. Validar cupo de crédito disponible
        if (tienePagoCredito && cliente != null) {
            BigDecimal montoCredito = dto.getPagos().stream()
                    .filter(p -> "CREDITO".equalsIgnoreCase(p.getMetodoPago()))
                    .map(CreateVentaPagoDto::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ValidacionCreditoDto validacion = carteraService.validarVenta(cliente.getId(), montoCredito, empresaId);
            if (!validacion.isPermitido()) {
                String motivo = validacion.getMotivoBloqueo() != null
                        ? validacion.getMotivoBloqueo()
                        : "Crédito no permitido para este cliente";
                throw new GlobalException(HttpStatus.BAD_REQUEST, motivo);
            }
        }

        // 3. Crear cabecera
        VentaEntity venta = new VentaEntity();
        venta.setEmpresa(empresa);
        venta.setSucursal(sucursal);
        venta.setUsuario(usuario);
        venta.setTurnoCaja(turno);
        venta.setTipoDocumento(dto.getTipoDocumento());
        venta.setPrefijo(sucursal.getPrefijoFacturacion());
        /**
         * TODO: posible error al tratar de obtener el siguiente consecutivo
         * * porque varias instancias de la aplicacion podrian estar usando el mismo
         * * consecutivo
         */
        venta.setConsecutivo(ventaRepository.obtenerSiguienteConsecutivo(Long.valueOf(sucursal.getId())));
        venta.setFechaEmision(LocalDateTime.now(ZoneId.of("America/Bogota")));
        // venta.setFechaEmision(LocalDateTime.now(ZoneOffset.UTC)); TODO: revisar zona horaria
        venta.setObservaciones(dto.getObservaciones());
        venta.setEstadoVenta("COMPLETADA");

        // Cliente ya validado arriba (obligatorio si hay pago a crédito)
        if (cliente != null) {
            venta.setCliente(cliente);
        }

        venta = ventaJPARepository.save(venta);

        BigDecimal subtotalAcumulado = BigDecimal.ZERO;
        BigDecimal descuentoAcumulado = BigDecimal.ZERO;
        BigDecimal impuestosAcumulado = BigDecimal.ZERO;

        BigDecimal descGral = dto.getDescuentoGeneral() != null ? dto.getDescuentoGeneral() : BigDecimal.ZERO;

        // 4. Procesar cada detalle
        for (CreateVentaDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // Resolver presentación y calcular cantidad real a descontar del inventario base
            ProductoPresentacionEntity presentacion = null;
            BigDecimal cantidadBase = item.getCantidad(); // cantidad en unidades base por defecto

            if (item.getProductoPresentacionId() != null) {
                presentacion = presentacionJPARepository
                    .findByIdAndProductoEmpresaId(item.getProductoPresentacionId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Presentación no encontrada: " + item.getProductoPresentacionId()));
                // factorConversion = unidades base por presentación (ej: 1 caja = 12 unidades)
                // ó unidades base que contiene 1 presentación (ej: 1 kg = 1/50 bulto → factor=50 → divide)
                cantidadBase = item.getCantidad().divide(
                    presentacion.getFactorConversion(), 6, java.math.RoundingMode.HALF_UP);
            }

            // Validación de stock (4.1)
            List<ProductoComposicionEntity> compValidacion =
                composicionJPARepository.findByProductoPadreId(producto.getId());

            if (!compValidacion.isEmpty()) {
                for (ProductoComposicionEntity comp : compValidacion) {
                    ProductoEntity hijo = comp.getProductoHijo();
                    if (!Boolean.TRUE.equals(hijo.getManejaInventario())) continue;

                    BigDecimal cantidadRequerida = cantidadBase.multiply(comp.getCantidad());

                    InventarioEntity invHijo = inventarioJPARepository
                        .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), hijo.getId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El componente '" + hijo.getNombre() + "' no tiene inventario en esta sucursal"));

                    if (!Boolean.TRUE.equals(hijo.getPermitirStockNegativo())
                            && invHijo.getStockActual().compareTo(cantidadRequerida) < 0)
                        throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Stock insuficiente del componente: " + hijo.getNombre()
                            + ". Disponible: " + invHijo.getStockActual()
                            + " | Requerido: " + cantidadRequerida);
                }
            } else if (Boolean.TRUE.equals(producto.getManejaInventario())) {
                InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "El producto " + producto.getNombre() + " no tiene inventario en esta sucursal"));

                if (!Boolean.TRUE.equals(producto.getPermitirStockNegativo())
                        && inventario.getStockActual().compareTo(cantidadBase) < 0)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente para: " + producto.getNombre()
                        + ". Disponible: " + inventario.getStockActual()
                        + (presentacion != null ? " bultos (solicitado: " + cantidadBase + " bultos)" : ""));
            }

            // 4.2 Base neta (precio × cantidad − descuento)
            BigDecimal baseNetaOriginal = item.getPrecioUnitario()
                .multiply(item.getCantidad())
                .subtract(item.getDescuentoValor())
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal impuestoLinea = item.getImpuestoValor().setScale(2, RoundingMode.HALF_UP);

            // 4.4 Crear detalle
            BigDecimal subtotalLinea = baseNetaOriginal.add(impuestoLinea).setScale(2, RoundingMode.HALF_UP);
            VentaDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setMontoDescuento(item.getDescuentoValor());
            detalle.setImpuestoValor(impuestoLinea);


            detalle.setSubtotalLinea(subtotalLinea);

            // Asignar presentación al detalle si aplica
            if (presentacion != null) {
                detalle.setProductoPresentacion(presentacion);
            }

            // Lote opcional
            if (item.getLoteId() != null) {
                LoteEntity lote = loteJPARepository.findById(item.getLoteId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lote no encontrado"));
                detalle.setLote(lote);

                // Descontar del lote
                lote.setStockActual(lote.getStockActual().subtract(item.getCantidad()));
                loteJPARepository.save(lote);
            }

            detalleJPARepository.save(detalle);

            // 4.4.1 Registrar comisión si el producto es SERVICIO
            comisionService.procesarComisionVenta(detalle, empresaId);

            // 4.5 Manejar seriales
            if (Boolean.TRUE.equals(producto.getManejaSerial()) && item.getSerialIds() != null) {
                for (Long serialId : item.getSerialIds()) {
                    SerialProductoEntity serial = serialJPARepository.findById(serialId)
                            .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                                    "Serial no encontrado: " + serialId));

                    if (!serial.getEstado().equals("DISPONIBLE"))
                        throw new GlobalException(HttpStatus.BAD_REQUEST,
                                "El serial " + serial.getSerial() + " no está disponible");

                    serial.setEstado("VENDIDO");
                    serialJPARepository.save(serial);

                    VentaDetalleSerialEntity ds = new VentaDetalleSerialEntity();
                    ds.setVentaDetalle(detalle);
                    ds.setSerialProducto(serial);
                    serialVentaJPARepository.save(ds);
                }
            }

            // 4.6 Descontar inventario (usa cantidadBase ya convertida por factorConversion)
            List<ProductoComposicionEntity> componentes =
                composicionJPARepository.findByProductoPadreId(producto.getId());

            if (!componentes.isEmpty()) {
                // Producto compuesto → descontar componentes
                for (ProductoComposicionEntity comp : componentes) {
                    ProductoEntity hijo = comp.getProductoHijo();
                    if (!Boolean.TRUE.equals(hijo.getManejaInventario())) continue;

                    BigDecimal cantidadDescontar = cantidadBase.multiply(comp.getCantidad());

                    InventarioEntity invHijo = inventarioJPARepository
                        .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), hijo.getId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "El componente '" + hijo.getNombre() + "' no tiene inventario en esta sucursal"));

                    BigDecimal saldoAnt   = invHijo.getStockActual();
                    BigDecimal saldoNuevo = saldoAnt.subtract(cantidadDescontar);

                    invHijo.setStockActual(saldoNuevo);
                    invHijo.setUpdatedAt(LocalDateTime.now());
                    inventarioJPARepository.save(invHijo);

                    registrarMovimiento(sucursal, hijo, null,
                        cantidadDescontar.negate(), saldoAnt, saldoNuevo,
                        item.getPrecioUnitario(), "VENTA",
                        "Venta #" + venta.getId() + " [componente de " + producto.getNombre() + "]");
                }

            } else if (Boolean.TRUE.equals(producto.getManejaInventario())) {
                // Producto simple → descontar cantidadBase (ya dividida por factorConversion si hay presentación)
                InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), producto.getId()).get();

                BigDecimal saldoAnterior = inventario.getStockActual();
                BigDecimal saldoNuevo    = saldoAnterior.subtract(cantidadBase);

                inventario.setStockActual(saldoNuevo);
                inventario.setUpdatedAt(LocalDateTime.now());
                inventarioJPARepository.save(inventario);

                String refMovimiento = presentacion != null
                    ? "Venta #" + venta.getId() + " [presentación: " + presentacion.getNombre() + "]"
                    : "Venta #" + venta.getId();

                registrarMovimiento(sucursal, producto, detalle.getLote(),
                    cantidadBase.negate(), saldoAnterior, saldoNuevo,
                    item.getPrecioUnitario(), "VENTA", refMovimiento);
            }
            subtotalAcumulado = subtotalAcumulado.add(baseNetaOriginal);
            descuentoAcumulado = descuentoAcumulado.add(item.getDescuentoValor());
            impuestosAcumulado = impuestosAcumulado.add(impuestoLinea);
        }

        // 5. Validar que el pago cubra el total (excepto si hay método CREDITO)
        BigDecimal totalFinal = subtotalAcumulado.add(impuestosAcumulado).subtract(descGral).setScale(2, RoundingMode.HALF_UP);
        
        // Detectar si es venta a crédito
        boolean hayCredito = false;
        for (CreateVentaPagoDto pago : dto.getPagos()) {
            if ("CREDITO".equalsIgnoreCase(pago.getMetodoPago())) {
                hayCredito = true;
                break;
            }
        }
        
        // Si no hay crédito, validar que el pago cubra el total (tolerancia de 1 peso por decimales)
        BigDecimal tolerancia = BigDecimal.ONE;
        if (!hayCredito && totalPagado.add(tolerancia).compareTo(totalFinal) < 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El pago recibido (" + totalPagado + ") es menor al total (" + totalFinal + ")");
        }

        // 6. Guardar pagos
        // monto         = lo que se aplica realmente a la venta (capped at saldo)
        // montoRecibido = lo que entregó el cliente (para calcular cambio en tirilla)
        BigDecimal saldo = totalFinal;
        for (CreateVentaPagoDto pago : dto.getPagos()) {
            VentaPagoEntity pagoEntity = pagoMapper.toEntity(pago);
            pagoEntity.setVenta(venta);
            pagoEntity.setMontoRecibido(pago.getMonto());                         // guarda lo tendido
            BigDecimal montoEfectivo = pago.getMonto().min(saldo);               // cap al saldo restante
            pagoEntity.setMonto(montoEfectivo);
            pagoEntity.setCuentaBancariaId(pago.getCuentaBancariaId());
            saldo = saldo.subtract(montoEfectivo);
            pagoJPARepository.save(pagoEntity);

            // Acreditar saldo en cuenta bancaria si el pago va a una cuenta específica
            if (pago.getCuentaBancariaId() != null) {
                cuentaBancariaJPARepository.findByIdAndEmpresaId(pago.getCuentaBancariaId(), empresaId)
                        .ifPresent(cuenta -> {
                            cuenta.setSaldoActual(cuenta.getSaldoActual().add(montoEfectivo));
                            cuentaBancariaJPARepository.save(cuenta);
                        });
            }
        }

        // 7. Actualizar totales y calcular pagos parciales (HU-004)
        // Detectar si es venta a crédito
        boolean esCredito = false;
        BigDecimal saldoPendiente = BigDecimal.ZERO;
        
        for (CreateVentaPagoDto pago : dto.getPagos()) {
            if ("CREDITO".equalsIgnoreCase(pago.getMetodoPago())) {
                esCredito = true;
                saldoPendiente = totalFinal.subtract(totalPagado);
                break;
            }
        }
        
        // Si no es crédito pero hay saldo pendiente, es pago parcial (tolerancia de 1 peso)
        boolean esPagoParcial = !esCredito && totalPagado.add(tolerancia).compareTo(totalFinal) < 0;
        if (esPagoParcial) {
            saldoPendiente = totalFinal.subtract(totalPagado);
        }
        
        venta.setSubtotal(subtotalAcumulado);
        venta.setDescuentoTotal(descuentoAcumulado.add(descGral));
        venta.setImpuestosTotal(impuestosAcumulado);
        venta.setTotalPagar(totalFinal);
        
        // Campos de pago parcial
        venta.setPagoParcial(esPagoParcial || esCredito);
        venta.setSaldoPendiente(saldoPendiente);
        
        // Si es crédito o pago parcial, el estado sigue como PAGO_PARCIAL, sino COMPLETADA
        if (esPagoParcial || esCredito) {
            venta.setEstadoVenta("PAGO_PARCIAL");
        }

        
        ventaJPARepository.save(venta);

        // 8. Crear cuenta por cobrar si es crédito o pago parcial (HU-014)
        if ((esCredito || esPagoParcial) && dto.getClienteId() != null) {
            CreateCuentaCobrarDto cuentaCobrarDto = new CreateCuentaCobrarDto();
            cuentaCobrarDto.setClienteId(dto.getClienteId());
            cuentaCobrarDto.setVentaId(venta.getId());
            cuentaCobrarDto.setTotalDeuda(totalFinal);
            cuentaCobrarDto.setFechaEmision(venta.getFechaEmision());
            cuentaCobrarDto.setFechaVencimiento(dto.getFechaVencimiento() != null ? 
                dto.getFechaVencimiento() : venta.getFechaEmision().plusDays(30));
            cuentaCobrarDto.setObservaciones(esCredito ? 
                "Venta #" + venta.getId() + " - Venta a crédito" : 
                "Venta #" + venta.getId() + " - Pago parcial");

            cuentaCobrarService.crear(cuentaCobrarDto, empresaId, usuarioId);
        }

        // 9. Crear factura automáticamente desde la venta
        FacturaDto facturaDto = facturaService.crearDesdeVenta(venta.getId(), empresaId, usuarioId.intValue());
        
        // 9. Obtener venta con factura asignada
        VentaDto ventaDto = obtenerPorId(venta.getId(), empresaId);
        ventaDto.setFacturaId(facturaDto.getId());
        
        return ventaDto;
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        VentaEntity venta = ventaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

        if (venta.getEstadoVenta().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La venta ya está anulada");

        List<VentaDetalleEntity> detalles = detalleJPARepository.findByVentaId(id);

        for (VentaDetalleEntity detalle : detalles) {
            ProductoEntity producto = detalle.getProducto();

        // En anular(), reemplazar el bloque if (Boolean.TRUE.equals(producto.getManejaInventario()))
        if (Boolean.TRUE.equals(producto.getManejaInventario())) {

            List<ProductoComposicionEntity> componentes =
                composicionJPARepository.findByProductoPadreId(producto.getId());

            if (!componentes.isEmpty()) {
                // Devolver cada componente al inventario
                for (ProductoComposicionEntity comp : componentes) {
                    ProductoEntity hijo = comp.getProductoHijo();
                    if (!Boolean.TRUE.equals(hijo.getManejaInventario())) continue;

                    BigDecimal cantidadDevolver = detalle.getCantidad().multiply(comp.getCantidad());

                    InventarioEntity invHijo = inventarioJPARepository
                        .findBySucursalIdAndProductoId(Long.valueOf(venta.getSucursal().getId()), hijo.getId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para componente: " + hijo.getNombre()));

                    BigDecimal saldoAnt = invHijo.getStockActual();
                    BigDecimal saldoNuevo = saldoAnt.add(cantidadDevolver);

                    invHijo.setStockActual(saldoNuevo);
                    invHijo.setUpdatedAt(LocalDateTime.now());
                    inventarioJPARepository.save(invHijo);

                    registrarMovimiento(venta.getSucursal(), hijo, null,
                        cantidadDevolver, saldoAnt, saldoNuevo,
                        detalle.getPrecioUnitario(), "ANULACION_VENTA",
                        "Anulación Venta #" + venta.getId() + " [componente de " + producto.getNombre() + "]");
                }
            } else {
                // Producto simple — lógica actual sin cambios
                InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(venta.getSucursal().getId()), producto.getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Inventario no encontrado para: " + producto.getNombre()));

                BigDecimal saldoAnterior = inventario.getStockActual();
                BigDecimal saldoNuevo = saldoAnterior.add(detalle.getCantidad());

                inventario.setStockActual(saldoNuevo);
                inventario.setUpdatedAt(LocalDateTime.now());
                inventarioJPARepository.save(inventario);

                if (detalle.getLote() != null) {
                    LoteEntity lote = detalle.getLote();
                    lote.setStockActual(lote.getStockActual().add(detalle.getCantidad()));
                    loteJPARepository.save(lote);
                }

                registrarMovimiento(venta.getSucursal(), producto, detalle.getLote(),
                    detalle.getCantidad(), saldoAnterior, saldoNuevo,
                    detalle.getPrecioUnitario(), "ANULACION_VENTA",
                    "Anulación Venta #" + venta.getId());
            }
        }
            // Devolver seriales a DISPONIBLE
            if (Boolean.TRUE.equals(producto.getManejaSerial())) {
                serialJPARepository.findAll().stream()
                        .filter(s -> s.getEstado().equals("VENDIDO"))
                        .forEach(s -> {
                            s.setEstado("DISPONIBLE");
                            serialJPARepository.save(s);
                        });
            }
        }

        venta.setEstadoVenta("ANULADA");
        ventaJPARepository.save(venta);
    }

    // ─── Métodos privados ────────────────────────────────────────────────────

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

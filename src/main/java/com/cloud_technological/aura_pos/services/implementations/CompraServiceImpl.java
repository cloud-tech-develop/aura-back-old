package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
import com.cloud_technological.aura_pos.dto.compras.CompraPagoDto;
import com.cloud_technological.aura_pos.dto.compras.CompraTableDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDetalleDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraDto;
import com.cloud_technological.aura_pos.dto.compras.CreateCompraPagoDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.CompraPagoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.MovimientoCajaEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.mappers.CompraDetalleMapper;
import com.cloud_technological.aura_pos.mappers.CompraMapper;
import com.cloud_technological.aura_pos.repositories.compras.CompraJPARepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraQueryRepository;
import com.cloud_technological.aura_pos.repositories.detalle_compras.CompraDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.turno_caja.TurnoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CompraService;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.services.NotaContableService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;
import com.cloud_technological.aura_pos.utils.Terceros;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class CompraServiceImpl implements CompraService {
    private final CompraQueryRepository compraRepository;
    private final CompraJPARepository compraJPARepository;
    private final CompraPagoJPARepository compraPagoJPARepository;
    private final CompraDetalleJPARepository detalleJPARepository;
    private final MovimientoInventarioJPARepository movimientoJPARepository;
    private final InventarioJPARepository inventarioJPARepository;
    private final LoteJPARepository loteJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final TerceroJPARepository terceroJPARepository;
    private final SucursalJPARepository sucursalJPARepository;
    private final EmpresaJPARepository empresaRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final CompraMapper compraMapper;
    private final CompraDetalleMapper detalleMapper;
    private final NotaContableService notaContableService;
    private final CuentaPagarService cuentaPagarService;
    private final MovimientoCajaJPARepository movimientoCajaJPARepository;
    private final com.cloud_technological.aura_pos.services.OrigenFondosService origenFondosService;
    private final com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService controlFechaRetroactiva;
    @Autowired
    private com.cloud_technological.aura_pos.services.TesoreriaService tesoreriaService;
    @Autowired
    private com.cloud_technological.aura_pos.services.ComprobanteCajaService comprobanteCajaService;
    @Autowired
    private com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository cuentaPagarJPARepository;
    private final CuentaBancariaJPARepository cuentaBancariaJPARepository;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository planCuentaRepo;

    /**
     * Guardarraíl del destino contable de la compra (E2): la cuenta débito
     * alternativa debe ser auxiliar, activa y de clase 1/5/6/7 (activo,
     * gasto o costo) — nunca pasivo, patrimonio ni ingreso.
     */
    private void validarCuentaDestino(Integer empresaId, Long cuentaContableId) {
        if (cuentaContableId == null) {
            return;
        }
        var cuenta = planCuentaRepo.findByIdAndEmpresaId(cuentaContableId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta contable destino de la compra no existe."));
        if (!Boolean.TRUE.equals(cuenta.getActiva()) || !Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta destino debe ser una cuenta de movimiento activa.");
        }
        String codigo = cuenta.getCodigo() != null ? cuenta.getCodigo() : "";
        if (!(codigo.startsWith("1") || codigo.startsWith("5")
                || codigo.startsWith("6") || codigo.startsWith("7"))) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta destino de una compra debe ser de activo (1xxx), gasto (5xxx) "
                            + "o costo (6xxx/7xxx); la cuenta " + codigo + " no aplica.");
        }
    }

    @Autowired
    public CompraServiceImpl(CompraQueryRepository compraRepository,
            CompraJPARepository compraJPARepository,
            CompraPagoJPARepository compraPagoJPARepository,
            CompraDetalleJPARepository detalleJPARepository,
            MovimientoInventarioJPARepository movimientoJPARepository,
            InventarioJPARepository inventarioJPARepository,
            LoteJPARepository loteJPARepository,
            ProductoJPARepository productoJPARepository,
            TerceroJPARepository terceroJPARepository,
            SucursalJPARepository sucursalJPARepository,
            EmpresaJPARepository empresaRepository,
            UsuarioJPARepository usuarioRepository,
            CompraMapper compraMapper,
            CompraDetalleMapper detalleMapper,
            NotaContableService notaContableService,
            CuentaPagarService cuentaPagarService,
            MovimientoCajaJPARepository movimientoCajaJPARepository,
            CuentaBancariaJPARepository cuentaBancariaJPARepository,
            com.cloud_technological.aura_pos.services.OrigenFondosService origenFondosService,
            com.cloud_technological.aura_pos.services.ControlFechaRetroactivaService controlFechaRetroactiva) {
        this.origenFondosService = origenFondosService;
        this.controlFechaRetroactiva = controlFechaRetroactiva;
        this.compraRepository = compraRepository;
        this.compraJPARepository = compraJPARepository;
        this.compraPagoJPARepository = compraPagoJPARepository;
        this.detalleJPARepository = detalleJPARepository;
        this.movimientoJPARepository = movimientoJPARepository;
        this.inventarioJPARepository = inventarioJPARepository;
        this.loteJPARepository = loteJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.terceroJPARepository = terceroJPARepository;
        this.sucursalJPARepository = sucursalJPARepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.compraMapper = compraMapper;
        this.detalleMapper = detalleMapper;
        this.notaContableService = notaContableService;
        this.cuentaPagarService = cuentaPagarService;
        this.movimientoCajaJPARepository = movimientoCajaJPARepository;
        this.cuentaBancariaJPARepository = cuentaBancariaJPARepository;
    }

    @Override
    public PageImpl<CompraTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return compraRepository.listar(pageable, empresaId);
    }

    @Override
    public CompraDto obtenerPorId(Long id, Integer empresaId) {
        CompraEntity entity = compraJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        CompraDto dto = compraMapper.toDto(entity);
        dto.setDetalles(compraRepository.obtenerDetalles(entity.getId()));

        List<CompraPagoDto> pagos = compraPagoJPARepository
                .findByCompraIdAndActivoTrue(entity.getId())
                .stream()
                .map(p -> {
                    CompraPagoDto pd = new CompraPagoDto();
                    pd.setId(p.getId());
                    pd.setMetodoPago(p.getMetodoPago());
                    pd.setMonto(p.getMonto());
                    pd.setBanco(p.getBanco());
                    return pd;
                })
                .toList();
        dto.setPagos(pagos);

        return dto;
    }

    @Override
    @Transactional
    public CompraDto crear(CreateCompraDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        TerceroEntity proveedor = terceroJPARepository.findByIdAndEmpresaId(dto.getProveedorId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado"));

        // 1. Crear cabecera
        CompraEntity compra = compraMapper.toEntity(dto);
        compra.setEmpresa(empresa);
        compra.setSucursal(sucursal);
        compra.setProveedor(proveedor);
        compra.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDateTime.now());
        compra.setEstado("RECIBIDA");
        compra.setFormaPago(dto.getFormaPago() != null ? dto.getFormaPago() : "CONTADO");
        compra.setTipoDocumento(dto.getTipoDocumento() != null ? dto.getTipoDocumento() : "FACTURA_COMPRA");
        compra.setFletes(dto.getFletes() != null ? dto.getFletes() : BigDecimal.ZERO);
        validarCuentaDestino(empresaId, dto.getCuentaContableId());
        compra.setCentroCostoId(dto.getCentroCostoId());
        compra.setCuentaContableId(dto.getCuentaContableId());
        compra.setProyectoId(dto.getProyectoId());
        compra.setFrenteId(dto.getFrenteId());
        compra.setSalidaCajaOtroDia(Boolean.TRUE.equals(dto.getSalidaCajaOtroDia()));
        compra.setCreatedAt(LocalDateTime.now());

        // Freno de documentos viejos, antes de tocar inventario: si la compra
        // no puede pagarse desde la caja, no debe existir a medias con el stock
        // ya movido. Solo mira la vía CAJA — las demás no descuadran a nadie.
        Integer autorizadoPor = controlFechaRetroactiva.validar(
                empresaId,
                compra.getFecha() != null ? compra.getFecha().toLocalDate() : null,
                saleDeCaja(dto),
                dto.getMotivoRetroactivo(),
                usuarioId,
                "compra");
        if (autorizadoPor != null) {
            compra.setMotivoRetroactivo(dto.getMotivoRetroactivo().trim());
            compra.setAutorizadoPor(autorizadoPor);
        }

        compra = compraJPARepository.save(compra);

        BigDecimal subtotalBruto = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        BigDecimal impuestosTotal = BigDecimal.ZERO;

        // 2. Procesar cada detalle
        for (CreateCompraDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            // 2.1 Crear detalle con descuento
            BigDecimal descPct = item.getDescuentoPct() != null ? item.getDescuentoPct() : BigDecimal.ZERO;
            BigDecimal brutoLinea = item.getCantidad().multiply(item.getCostoUnitario());
            BigDecimal descValor = brutoLinea.multiply(descPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal netoLinea = brutoLinea.subtract(descValor);

            CompraDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setDescuentoPct(descPct);
            detalle.setDescuentoValor(descValor);
            detalle.setSubtotalLinea(netoLinea);

            subtotalBruto = subtotalBruto.add(brutoLinea);
            descuentoTotal = descuentoTotal.add(descValor);
            impuestosTotal = impuestosTotal.add(item.getImpuestoValor());

            // 2.2 Precios de venta en el detalle
            detalle.setLote(null);
            detalle.setPrecioVenta1(item.getPrecioVenta1());
            detalle.setPrecioVenta2(item.getPrecioVenta2());
            detalle.setPrecioVenta3(item.getPrecioVenta3());
            detalleJPARepository.save(detalle);

            // 2.3 Actualizar costo y precios de venta en el producto
            actualizarPreciosProducto(producto, item);

            // 2.4 Actualizar inventario
            InventarioEntity inventario = resolverInventario(sucursal, producto);
            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(item.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            // 2.5 Kardex
            registrarMovimiento(sucursal, producto, null, item.getCantidad(),
                    saldoAnterior, saldoNuevo, item.getCostoUnitario(), "COMPRA",
                    "Compra #" + compra.getId());
        }

        // 3. Actualizar totales
        BigDecimal subtotalNeto = subtotalBruto.subtract(descuentoTotal);
        BigDecimal fletes = compra.getFletes() != null ? compra.getFletes() : BigDecimal.ZERO;
        BigDecimal totalBruto = subtotalNeto.add(impuestosTotal).add(fletes);
        compra.setSubtotal(subtotalBruto);
        compra.setDescuentoTotal(descuentoTotal);
        compra.setImpuestosTotal(impuestosTotal);
        compra.setTotal(totalBruto);

        // 3.1 Calcular retenciones si se enviaron
        BigDecimal retefuentePct   = dto.getRetefuentePct()   != null ? dto.getRetefuentePct()   : BigDecimal.ZERO;
        BigDecimal reteivaPct      = dto.getReteivaPct()      != null ? dto.getReteivaPct()      : BigDecimal.ZERO;
        BigDecimal reteicaPct      = dto.getReteicaPct()      != null ? dto.getReteicaPct()      : BigDecimal.ZERO;
        BigDecimal retefuenteValor = subtotalNeto.multiply(retefuentePct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal reteivaValor    = impuestosTotal.multiply(reteivaPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal reteicaValor    = subtotalNeto.multiply(reteicaPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalRetenciones = retefuenteValor.add(reteivaValor).add(reteicaValor);
        compra.setRetefuentePct(retefuentePct);
        compra.setRetefuenteValor(retefuenteValor);
        compra.setReteivaPct(reteivaPct);
        compra.setReteivaValor(reteivaValor);
        compra.setReteicaPct(reteicaPct);
        compra.setReteicaValor(reteicaValor);
        compra.setTotalRetenciones(totalRetenciones);
        compra.setNetaAPagar(totalBruto.subtract(totalRetenciones));
        compraJPARepository.save(compra);

        // 4. Procesar pagos y generar notas contables (HU-008).
        // Una compra a CRÉDITO no tiene salida de dinero al momento de la compra:
        // toda la obligación queda con el proveedor. Por eso se ignoran los pagos
        // que pudieran venir en el payload (evita líneas a Bancos/Caja en el asiento,
        // descontar saldo bancario sin movimiento real y CxP inconsistentes).
        boolean esCredito = "CREDITO".equalsIgnoreCase(compra.getFormaPago());
        registrarPagos(compra, dto, empresaId, usuarioId);

        // 5. Registrar cuenta por pagar solo si la forma de pago es CRÉDITO.
        if (esCredito) {
            crearCuentaPorPagar(compra, dto, empresaId, usuarioId);
        }

        // 6. Egreso de caja, uno por cada pago que salió del cajón.
        //
        //    Antes se registraba un solo egreso por la neta a pagar con solo ver
        //    que la compra fuera CONTADO: una compra pagada por transferencia
        //    descontaba de la caja física dinero que nunca salió de ahí. Y como
        //    el turno se buscaba por usuario con ifPresent, si quien compraba no
        //    tenía caja abierta el egreso se perdía sin aviso.
        registrarEgresosDeCaja(compra, dto, empresaId, usuarioId);

        // 7. Comprobante de egreso que soporta el pago al proveedor.
        sincronizarComprobanteEgreso(compra, dto, empresaId, usuarioId);

        // Disparar la generación del asiento contable tras el commit de la compra.
        // Si la contabilización falla, la compra NO se revierte (se registra en ErrorLog).
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.CompraContabilizableEvent(
                        compra.getId(), empresaId, usuarioId.intValue()));

        return obtenerPorId(compra.getId(), empresaId);
    }

    /**
     * Guarda los pagos de la compra y mueve la plata que sale por banco.
     *
     * <p>Una compra a CRÉDITO no tiene salida de dinero al momento de la compra:
     * toda la obligación queda con el proveedor. Por eso se ignoran los pagos
     * que pudieran venir en el payload (evita líneas a Bancos/Caja en el asiento,
     * descontar saldo bancario sin movimiento real y CxP inconsistentes).
     */
    private void registrarPagos(CompraEntity compra, CreateCompraDto dto,
            Integer empresaId, Long usuarioId) {
        boolean esCredito = "CREDITO".equalsIgnoreCase(compra.getFormaPago());
        if (!esCredito) {
            UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

            BigDecimal montoPagado = BigDecimal.ZERO;

            for (CreateCompraPagoDto pagoDto : pagosDeContado(dto, compra)) {
                // Guardar pago
                CompraPagoEntity pagoEntity = new CompraPagoEntity();
                pagoEntity.setCompra(compra);
                pagoEntity.setMetodoPago(MediosPago.normalizar(pagoDto.getMetodoPago()));
                pagoEntity.setMonto(pagoDto.getMonto());
                pagoEntity.setBanco(pagoDto.getBanco());
                pagoEntity.setFechaPago(LocalDateTime.now());
                pagoEntity.setUsuario(usuario);
                pagoEntity.setActivo(true);
                pagoEntity.setCuentaBancariaId(pagoDto.getCuentaBancariaId());
                pagoEntity.setCuentaContableId(pagoDto.getCuentaContableId());
                compraPagoJPARepository.save(pagoEntity);

                // Salida de la cuenta bancaria. Antes solo se bajaba el saldo,
                // sin dejar movimiento: la cuenta perdía plata y en su extracto
                // no aparecía nada que lo explicara.
                tesoreriaService.registrarMovimientoDeDocumento(empresaId, usuarioId.intValue(),
                        new com.cloud_technological.aura_pos.services.TesoreriaService.MovimientoDocumento(
                                pagoDto.getCuentaBancariaId(), true, pagoDto.getMonto(),
                                "Compra #" + compra.getId() + " - Pago a proveedor",
                                Terceros.nombreVisible(compra.getProveedor()),
                                referenciaDelPago(pagoDto, compra),
                                "COMPRA"));

                montoPagado = montoPagado.add(pagoDto.getMonto());

                // Generar nota contable de DÉBITO por cada pago
                String nombreUsuario = usuario.getTercero() != null ? usuario.getTercero().getNombres() : usuario.getUsername();
                String descripcion;
                if (montoPagado.compareTo(compra.getTotal()) < 0) {
                    // Pago parcial
                    descripcion = "Pago parcial Compra #" + compra.getId() + " - Usuario: " + nombreUsuario;
                } else {
                    // Pago total
                    descripcion = "Pago total Compra #" + compra.getId();
                }

                notaContableService.generarNotaDebitoPagoCompra(
                        compra.getId(),
                        pagoDto.getMonto(),
                        usuarioId.intValue(),
                        pagoDto.getMetodoPago(),
                        descripcion
                );
            }
        }
    }

    /**
     * La deuda con el proveedor es la neta a pagar (total menos retenciones
     * practicadas), igual que la línea de Proveedores del asiento contable.
     */
    private void crearCuentaPorPagar(CompraEntity compra, CreateCompraDto dto,
            Integer empresaId, Long usuarioId) {
        CreateCuentaPagarDto cuentaPagarDto = new CreateCuentaPagarDto();
        cuentaPagarDto.setProveedorId(compra.getProveedor().getId());
        cuentaPagarDto.setCompraId(compra.getId());
        cuentaPagarDto.setTotalDeuda(compra.getNetaAPagar() != null
                ? compra.getNetaAPagar() : compra.getTotal());
        cuentaPagarDto.setFechaEmision(compra.getFecha());
        cuentaPagarDto.setFechaVencimiento(dto.getFechaVencimiento() != null
                ? dto.getFechaVencimiento() : compra.getFecha().plusDays(30));
        cuentaPagarDto.setObservaciones("Compra #" + compra.getId() + " - Compra a crédito");

        cuentaPagarService.crear(cuentaPagarDto, empresaId, usuarioId);
    }

    private void registrarEgresosDeCaja(CompraEntity compra, CreateCompraDto dto,
            Integer empresaId, Long usuarioId) {
        if ("CONTADO".equalsIgnoreCase(compra.getFormaPago())) {
            UsuarioEntity usuarioEgreso = usuarioRepository.findById(usuarioId.intValue()).orElse(null);
            Integer sucursalIdCompra = compra.getSucursal() != null ? compra.getSucursal().getId() : null;

            for (PagoEfectivo pago : pagosQueSalenDeCaja(dto, compra)) {
                var origen = origenFondosService.resolver(empresaId,
                        new com.cloud_technological.aura_pos.services.OrigenFondosService.Solicitud(
                                pago.metodoPago(), null, pago.cuentaBancariaId(),
                                pago.cuentaContableId(), sucursalIdCompra, "pago de la compra",
                                Boolean.TRUE.equals(dto.getSalidaCajaOtroDia())));

                if (!origen.generaMovimientoCaja()) {
                    continue;
                }
                MovimientoCajaEntity egreso = MovimientoCajaEntity.builder()
                        .turnoCaja(origen.turno())
                        .usuario(usuarioEgreso)
                        .tipo("EGRESO")
                        .concepto("Compra #" + compra.getId() + " - Pago contado a proveedor")
                        .monto(pago.monto())
                        // El egreso es de hoy — hoy salió la plata del cajón —
                        // aunque la factura del proveedor sea de días atrás. La
                        // fecha del documento va aparte para que el cierre pueda
                        // mostrar el desfase en vez de diluirlo en el total.
                        .fecha(java.time.LocalDate.now())
                        .fechaDocumento(compra.getFecha() != null
                                ? compra.getFecha().toLocalDate() : null)
                        .origenTipo(MovimientoCajaEntity.ORIGEN_COMPRA)
                        .origenId(compra.getId())
                        .origenInferido(origen.turnoInferido())
                        .metodoPago(MediosPago.normalizar(pago.metodoPago()))
                        .build();
                movimientoCajaJPARepository.save(egreso);
            }
        }
    }

    /**
     * Emite (o pone al día) el comprobante de egreso de la compra.
     *
     * <p>Toda salida de dinero necesita su soporte, sin importar por dónde salió:
     * antes no se generaba ninguno, ni con caja abierta ni pagando por banco. El
     * comprobante es uno solo por compra y se conserva al editarla — emitir uno
     * nuevo dejaría dos soportes para el mismo pago.
     *
     * <p>Solo aplica a compras de CONTADO: a crédito no hay egreso todavía, la
     * plata sale después con el abono a la cuenta por pagar, que lleva el suyo.
     */
    private void sincronizarComprobanteEgreso(CompraEntity compra, CreateCompraDto dto,
            Integer empresaId, Long usuarioId) {
        if (!"CONTADO".equalsIgnoreCase(compra.getFormaPago())) {
            // Se registró de contado y al editarla se corrigió a crédito —
            // equivocarse de medio de pago es lo más común. El egreso ya no
            // ocurrió: la plata saldrá después con el abono a la cuenta por
            // pagar, así que el comprobante emitido queda anulado.
            comprobanteCajaService.anularDeDocumento(empresaId, "COMPRA", compra.getId(),
                    "La compra pasó a crédito: el pago no se realizó");
            return;
        }
        List<PagoEfectivo> pagos = pagosQueSalenDeCaja(dto, compra);
        BigDecimal totalPagado = pagos.stream()
                .map(PagoEfectivo::monto)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Con varios medios el comprobante es uno solo, así que el método deja
        // de ser uno: MIXTO evita afirmar que todo salió por el primero.
        String metodo = pagos.size() == 1
                ? MediosPago.normalizar(pagos.get(0).metodoPago())
                : "MIXTO";

        comprobanteCajaService.sincronizarDeDocumento(empresaId,
                usuarioId != null ? usuarioId.intValue() : null,
                "EGRESO",
                "Pago compra #" + compra.getId()
                        + (compra.getNumeroCompra() != null ? " (" + compra.getNumeroCompra() + ")" : ""),
                totalPagado,
                metodo,
                Terceros.nombreVisible(compra.getProveedor()),
                "COMPRA", compra.getId(), null);
    }

    /**
     * Referencia del pago para el extracto bancario. Si el front no mandó una
     * (lo habitual en efectivo), se usa el número de factura del proveedor y,
     * en último caso, el consecutivo de la compra: sin referencia el movimiento
     * queda imposible de cruzar contra el extracto real del banco.
     */
    private String referenciaDelPago(CreateCompraPagoDto pagoDto, CompraEntity compra) {
        if (pagoDto.getReferencia() != null && !pagoDto.getReferencia().isBlank()) {
            return pagoDto.getReferencia().trim();
        }
        if (compra.getNumeroCompra() != null && !compra.getNumeroCompra().isBlank()) {
            return compra.getNumeroCompra().trim();
        }
        return "COMPRA-" + compra.getId();
    }

    /** Un pago visto desde la caja: cuánto, por qué medio y contra qué cuenta. */
    private record PagoEfectivo(String metodoPago, BigDecimal monto, Long cuentaBancariaId,
            Long cuentaContableId) {
    }

    /**
     * Los pagos de una compra de contado, siempre con al menos uno.
     *
     * <p>Si el documento no trae desglose se asume uno en efectivo por la neta a
     * pagar. Antes ese pago sintético solo existía para el egreso de caja y no
     * se guardaba: el asiento no encontraba pagos, mandaba todo el saldo a
     * Proveedores y no se creaba cuenta por pagar, así que la caja bajaba
     * mientras contablemente la deuda seguía viva. Al materializarlo como un
     * `compra_pago` real, el documento, la caja y el asiento dicen lo mismo.
     */
    private List<CreateCompraPagoDto> pagosDeContado(CreateCompraDto dto, CompraEntity compra) {
        if (dto.getPagos() != null && !dto.getPagos().isEmpty()) {
            return dto.getPagos();
        }
        CreateCompraPagoDto sintetico = new CreateCompraPagoDto();
        sintetico.setMetodoPago(MediosPago.EFECTIVO);
        sintetico.setMonto(compra.getNetaAPagar() != null
                ? compra.getNetaAPagar() : compra.getTotal());
        return List.of(sintetico);
    }

    /** Los mismos pagos, vistos desde la caja. */
    /**
     * Algún pago de la compra va a salir del cajón.
     *
     * <p>Se decide con los mismos datos que usará después el resolutor de origen
     * — efectivo, sin cuenta bancaria y sin cuenta contable elegida — para que
     * el freno y el movimiento de caja no puedan discrepar.
     */
    private boolean saleDeCaja(CreateCompraDto dto) {
        if (MediosPago.CREDITO.equalsIgnoreCase(dto.getFormaPago()) || dto.getPagos() == null) {
            return false;
        }
        // "Ya salió otro día" no mueve ningún arqueo, así que no hay a quién
        // proteger: el freno de documentos viejos no tiene nada que frenar.
        if (Boolean.TRUE.equals(dto.getSalidaCajaOtroDia())) {
            return false;
        }
        return dto.getPagos().stream().anyMatch(p ->
                MediosPago.esEfectivo(p.getMetodoPago())
                        && p.getCuentaBancariaId() == null
                        && p.getCuentaContableId() == null);
    }

    private List<PagoEfectivo> pagosQueSalenDeCaja(CreateCompraDto dto, CompraEntity compra) {
        return pagosDeContado(dto, compra).stream()
                .map(p -> new PagoEfectivo(p.getMetodoPago(), p.getMonto(),
                        p.getCuentaBancariaId(), p.getCuentaContableId()))
                .toList();
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        CompraEntity compra = compraJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        if (compra.getEstado().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La compra ya está anulada");

        // El soporte del pago no puede seguir vigente si la compra no existe.
        comprobanteCajaService.anularDeDocumento(empresaId, "COMPRA", id,
                "Compra anulada");

        List<CompraDetalleEntity> detalles = detalleJPARepository.findByCompraId(id);

        for (CompraDetalleEntity detalle : detalles) {
            InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(compra.getSucursal().getId()), detalle.getProducto().getId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inventario no encontrado para: " + detalle.getProducto().getNombre()));

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(detalle.getCantidad());

            if (saldoNuevo.compareTo(BigDecimal.ZERO) < 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede anular, stock insuficiente en: " + detalle.getProducto().getNombre());

            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            // Revertir lote
            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().subtract(detalle.getCantidad()));
                loteJPARepository.save(lote);
            }

            // Kardex anulación
            registrarMovimiento(compra.getSucursal(), detalle.getProducto(), detalle.getLote(),
                    detalle.getCantidad().negate(), saldoAnterior, saldoNuevo,
                    detalle.getCostoUnitario(), "ANULACION_COMPRA",
                    "Anulación Compra #" + compra.getId());
        }

        compra.setEstado("ANULADA");
        compraJPARepository.save(compra);

        // Reversar el asiento contable de la compra tras el commit de la anulación.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.ContabilidadReversaEvent(
                        "COMPRA", compra.getId(), empresaId, null));
    }

    @Override
    @Transactional
    public CompraDto actualizar(Long id, CreateCompraDto dto, Integer empresaId, Long usuarioId) {
        CompraEntity compra = compraJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        if (compra.getEstado().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede editar una compra anulada");

        SucursalEntity sucursal = sucursalJPARepository.findByIdAndEmpresaId(dto.getSucursalId().intValue(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));

        TerceroEntity proveedor = terceroJPARepository.findByIdAndEmpresaId(dto.getProveedorId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado"));

        // 1. Revertir inventario de los detalles existentes
        List<CompraDetalleEntity> detallesAnteriores = detalleJPARepository.findByCompraId(id);
        for (CompraDetalleEntity detalle : detallesAnteriores) {
            InventarioEntity inventario = inventarioJPARepository
                    .findBySucursalIdAndProductoId(Long.valueOf(compra.getSucursal().getId()), detalle.getProducto().getId())
                    .orElse(null);
            if (inventario != null) {
                BigDecimal saldoAnterior = inventario.getStockActual();
                BigDecimal saldoNuevo = saldoAnterior.subtract(detalle.getCantidad());
                // El kardex debe ser secuencial: el stock puede quedar negativo de forma
                // transitoria tras la reversión para que el siguiente movimiento continúe
                // desde este saldo (no se clava en 0).
                inventario.setStockActual(saldoNuevo);
                inventario.setUpdatedAt(LocalDateTime.now());
                inventarioJPARepository.save(inventario);
                registrarMovimiento(compra.getSucursal(), detalle.getProducto(), detalle.getLote(),
                        detalle.getCantidad().negate(), saldoAnterior, saldoNuevo,
                        detalle.getCostoUnitario(), "EDICION_COMPRA_REVERSION",
                        "Edición Compra #" + compra.getId());
            }
            if (detalle.getLote() != null) {
                LoteEntity lote = detalle.getLote();
                lote.setStockActual(lote.getStockActual().subtract(detalle.getCantidad()).max(BigDecimal.ZERO));
                loteJPARepository.save(lote);
            }
        }

        // 2. Eliminar detalles anteriores
        detalleJPARepository.deleteAll(detallesAnteriores);

        // 3. Actualizar cabecera
        compra.setProveedor(proveedor);
        compra.setSucursal(sucursal);
        compra.setNumeroCompra(dto.getNumeroCompra());
        if (dto.getFecha() != null) compra.setFecha(dto.getFecha());
        compra.setObservaciones(dto.getObservaciones());
        compra.setTipoDocumento(dto.getTipoDocumento() != null ? dto.getTipoDocumento() : "FACTURA_COMPRA");
        compra.setFletes(dto.getFletes() != null ? dto.getFletes() : BigDecimal.ZERO);
        validarCuentaDestino(empresaId, dto.getCuentaContableId());
        compra.setCentroCostoId(dto.getCentroCostoId());
        compra.setCuentaContableId(dto.getCuentaContableId());
        compra.setProyectoId(dto.getProyectoId());
        compra.setFrenteId(dto.getFrenteId());

        BigDecimal subtotalBruto = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        BigDecimal impuestosTotal = BigDecimal.ZERO;

        // 4. Procesar nuevos detalles
        for (CreateCompraDetalleDto item : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findByIdAndEmpresaId(item.getProductoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.getProductoId()));

            BigDecimal descPct = item.getDescuentoPct() != null ? item.getDescuentoPct() : BigDecimal.ZERO;
            BigDecimal brutoLinea = item.getCantidad().multiply(item.getCostoUnitario());
            BigDecimal descValor = brutoLinea.multiply(descPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal netoLinea = brutoLinea.subtract(descValor);

            CompraDetalleEntity detalle = detalleMapper.toEntity(item);
            detalle.setCompra(compra);
            detalle.setProducto(producto);
            detalle.setDescuentoPct(descPct);
            detalle.setDescuentoValor(descValor);
            detalle.setSubtotalLinea(netoLinea);

            subtotalBruto = subtotalBruto.add(brutoLinea);
            descuentoTotal = descuentoTotal.add(descValor);
            impuestosTotal = impuestosTotal.add(item.getImpuestoValor());

            detalle.setLote(null);
            detalle.setPrecioVenta1(item.getPrecioVenta1());
            detalle.setPrecioVenta2(item.getPrecioVenta2());
            detalle.setPrecioVenta3(item.getPrecioVenta3());
            detalleJPARepository.save(detalle);

            actualizarPreciosProducto(producto, item);

            InventarioEntity inventario = resolverInventario(sucursal, producto);
            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(item.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioJPARepository.save(inventario);

            registrarMovimiento(sucursal, producto, null, item.getCantidad(),
                    saldoAnterior, saldoNuevo, item.getCostoUnitario(), "EDICION_COMPRA",
                    "Edición Compra #" + compra.getId());
        }

        // 5. Actualizar totales
        BigDecimal subtotalNeto = subtotalBruto.subtract(descuentoTotal);
        BigDecimal fletesAct = compra.getFletes() != null ? compra.getFletes() : BigDecimal.ZERO;
        BigDecimal retefuentePct   = dto.getRetefuentePct()   != null ? dto.getRetefuentePct()   : BigDecimal.ZERO;
        BigDecimal reteivaPct      = dto.getReteivaPct()      != null ? dto.getReteivaPct()      : BigDecimal.ZERO;
        BigDecimal reteicaPct      = dto.getReteicaPct()      != null ? dto.getReteicaPct()      : BigDecimal.ZERO;
        BigDecimal retefuenteValor = subtotalNeto.multiply(retefuentePct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal reteivaValor    = impuestosTotal.multiply(reteivaPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal reteicaValor    = subtotalNeto.multiply(reteicaPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalRetenciones = retefuenteValor.add(reteivaValor).add(reteicaValor);
        BigDecimal totalFinal = subtotalNeto.add(impuestosTotal).add(fletesAct);
        compra.setSubtotal(subtotalBruto);
        compra.setDescuentoTotal(descuentoTotal);
        compra.setImpuestosTotal(impuestosTotal);
        compra.setTotal(totalFinal);
        compra.setRetefuentePct(retefuentePct);
        compra.setRetefuenteValor(retefuenteValor);
        compra.setReteivaPct(reteivaPct);
        compra.setReteivaValor(reteivaValor);
        compra.setReteicaPct(reteicaPct);
        compra.setReteicaValor(reteicaValor);
        compra.setTotalRetenciones(totalRetenciones);
        compra.setNetaAPagar(totalFinal.subtract(totalRetenciones));
        if (dto.getFormaPago() != null) compra.setFormaPago(dto.getFormaPago());
        compraJPARepository.save(compra);

        // 6. Rehacer la forma de pago. Equivocarse aquí es lo más común —
        //    registrar de contado algo que era a crédito — y hasta ahora editar
        //    no tocaba nada de esto: los pagos quedaban vivos, la cuenta por
        //    pagar nunca se creaba y el comprobante seguía diciendo "efectivo".
        revertirPagosAnteriores(compra, empresaId, usuarioId);
        registrarPagos(compra, dto, empresaId, usuarioId);
        registrarEgresosDeCaja(compra, dto, empresaId, usuarioId);
        sincronizarCuentaPorPagar(compra, dto, empresaId, usuarioId);
        sincronizarComprobanteEgreso(compra, dto, empresaId, usuarioId);

        // 7. Rehacer el asiento contable. El listener reversa el vigente y
        //    genera el nuevo: hasta ahora editar dejaba el asiento con los
        //    valores originales, así que el documento y la contabilidad decían
        //    cosas distintas.
        eventPublisher.publishEvent(
                new com.cloud_technological.aura_pos.event.CompraContabilizableEvent(
                        compra.getId(), empresaId, usuarioId.intValue()));

        return obtenerPorId(compra.getId(), empresaId);
    }

    /**
     * Deshace los pagos que tenía la compra antes de editarla: los desactiva y
     * devuelve la plata a donde salió.
     *
     * <p>Sin esto, corregir una compra deja el dinero descontado dos veces (o
     * descontado sin razón, si pasó a crédito): el banco y la caja ya se
     * movieron cuando se registró la primera vez.
     */
    private void revertirPagosAnteriores(CompraEntity compra, Integer empresaId, Long usuarioId) {
        List<CompraPagoEntity> anteriores = compraPagoJPARepository
                .findByCompraIdAndActivoTrue(compra.getId());
        if (anteriores.isEmpty()) {
            return;
        }
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue()).orElse(null);

        for (CompraPagoEntity pago : anteriores) {
            // Devolver al banco lo que había salido de él.
            tesoreriaService.registrarMovimientoDeDocumento(empresaId, usuarioId.intValue(),
                    new com.cloud_technological.aura_pos.services.TesoreriaService.MovimientoDocumento(
                            pago.getCuentaBancariaId(), false, pago.getMonto(),
                            "Reverso pago compra #" + compra.getId() + " (edición)",
                            Terceros.nombreVisible(compra.getProveedor()),
                            "COMPRA-" + compra.getId(), "COMPRA"));

            // Y a la caja, si había salido del cajón.
            if (MediosPago.esEfectivo(pago.getMetodoPago()) && pago.getCuentaBancariaId() == null) {
                Integer sucursalId = compra.getSucursal() != null ? compra.getSucursal().getId() : null;
                var origen = origenFondosService.resolver(empresaId,
                        new com.cloud_technological.aura_pos.services.OrigenFondosService.Solicitud(
                                pago.getMetodoPago(), null, null, pago.getCuentaContableId(),
                                sucursalId, "reverso del pago de la compra"));
                if (origen.generaMovimientoCaja()) {
                    movimientoCajaJPARepository.save(MovimientoCajaEntity.builder()
                            .turnoCaja(origen.turno())
                            .usuario(usuario)
                            .tipo("INGRESO")
                            .concepto("Reverso pago compra #" + compra.getId() + " (edición)")
                            .monto(pago.getMonto())
                            // El reverso entra hoy a la caja que lo recibe, que
                            // no tiene por qué ser la que pagó: si aquel turno
                            // ya cerró, devolverle la plata no lo reabre.
                            .fecha(java.time.LocalDate.now())
                            .fechaDocumento(compra.getFecha() != null
                                    ? compra.getFecha().toLocalDate() : null)
                            .origenTipo(MovimientoCajaEntity.ORIGEN_COMPRA)
                            .origenId(compra.getId())
                            .origenInferido(origen.turnoInferido())
                            .metodoPago(MediosPago.normalizar(pago.getMetodoPago()))
                            .build());
                }
            }

            pago.setActivo(false);
            compraPagoJPARepository.save(pago);
        }
    }

    /**
     * Pone la cuenta por pagar de acuerdo con la forma de pago vigente.
     *
     * <p>Es el caso que faltaba: una compra registrada de contado y corregida a
     * crédito no generaba la deuda con el proveedor, así que el pasivo
     * desaparecía. Y al revés, pasar de crédito a contado dejaba viva una cuenta
     * que ya nadie debe.
     */
    private void sincronizarCuentaPorPagar(CompraEntity compra, CreateCompraDto dto,
            Integer empresaId, Long usuarioId) {
        boolean esCredito = "CREDITO".equalsIgnoreCase(compra.getFormaPago());
        com.cloud_technological.aura_pos.entity.CuentaPagarEntity existente = cuentaPagarJPARepository
                .findFirstByCompraIdAndEmpresaId(compra.getId(), empresaId).orElse(null);

        if (esCredito) {
            BigDecimal deuda = compra.getNetaAPagar() != null
                    ? compra.getNetaAPagar() : compra.getTotal();
            if (existente == null) {
                crearCuentaPorPagar(compra, dto, empresaId, usuarioId);
                return;
            }
            // Si ya tiene abonos no se puede recalcular sin descuadrar la
            // cartera: el saldo dejaría de coincidir con lo abonado.
            if (existente.getTotalAbonado() != null
                    && existente.getTotalAbonado().signum() > 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta por pagar de esta compra ya tiene abonos: anule los abonos "
                                + "antes de cambiar el valor o la forma de pago");
            }
            existente.setTotalDeuda(deuda);
            existente.setSaldoPendiente(deuda);
            existente.setEstado("pendiente");
            cuentaPagarJPARepository.save(existente);
            return;
        }

        // Pasó a contado: la deuda ya no existe.
        if (existente != null) {
            if (existente.getTotalAbonado() != null
                    && existente.getTotalAbonado().signum() > 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La cuenta por pagar de esta compra ya tiene abonos: no se puede "
                                + "pasar a contado sin anularlos primero");
            }
            existente.setEstado("anulada");
            existente.setSaldoPendiente(BigDecimal.ZERO);
            cuentaPagarJPARepository.save(existente);
        }
    }

    // ─── Métodos privados de apoyo ───────────────────────────────────────────

    private void actualizarPreciosProducto(ProductoEntity producto, CreateCompraDetalleDto item) {
        producto.setCosto(item.getCostoUnitario());
        if (item.getPrecioVenta1() != null) producto.setPrecio(item.getPrecioVenta1());
        if (item.getPrecioVenta2() != null) producto.setPrecio2(item.getPrecioVenta2());
        if (item.getPrecioVenta3() != null) producto.setPrecio3(item.getPrecioVenta3());
        productoJPARepository.save(producto);
    }

    // private LoteEntity resolverLote(ProductoEntity producto, SucursalEntity sucursal, CreateCompraDetalleDto item) {
    //     if (!Boolean.TRUE.equals(producto.getManejaLotes()) || item.getCodigoLote() == null)
    //         return null;

    //     return loteJPARepository
    //             .findByProductoIdAndSucursalIdAndCodigoLote(producto.getId(), Long.valueOf(sucursal.getId()), item.getCodigoLote())
    //             .orElseGet(() -> {
    //                 LoteEntity nuevoLote = new LoteEntity();
    //                 nuevoLote.setProducto(producto);
    //                 nuevoLote.setSucursal(sucursal);
    //                 nuevoLote.setCodigoLote(item.getCodigoLote());
    //                 nuevoLote.setFechaVencimiento(item.getFechaVencimiento());
    //                 nuevoLote.setStockActual(item.getCantidad());
    //                 nuevoLote.setCostoUnitario(item.getCostoUnitario());
    //                 nuevoLote.setActivo(true);
    //                 return loteJPARepository.save(nuevoLote);
    //             });
    // }

    private InventarioEntity resolverInventario(SucursalEntity sucursal, ProductoEntity producto) {
        return inventarioJPARepository
                .findBySucursalIdAndProductoId(Long.valueOf(sucursal.getId()), producto.getId())
                .orElseGet(() -> {
                    InventarioEntity nuevo = new InventarioEntity();
                    nuevo.setSucursal(sucursal);
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

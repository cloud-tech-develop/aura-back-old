package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.compras.CompraDto;
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
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.CompraService;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.services.NotaContableService;
import com.cloud_technological.aura_pos.utils.GlobalException;
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
    private final TurnoCajaJPARepository turnoCajaJPARepository;
    private final MovimientoCajaJPARepository movimientoCajaJPARepository;

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
            TurnoCajaJPARepository turnoCajaJPARepository,
            MovimientoCajaJPARepository movimientoCajaJPARepository) {
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
        this.turnoCajaJPARepository = turnoCajaJPARepository;
        this.movimientoCajaJPARepository = movimientoCajaJPARepository;
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
        compra.setCreatedAt(LocalDateTime.now());
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

        // 4. Procesar pagos y generar notas contables (HU-008)
        if (dto.getPagos() != null && !dto.getPagos().isEmpty()) {
            UsuarioEntity usuario = usuarioRepository.findById(usuarioId.intValue())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

            BigDecimal montoPagado = BigDecimal.ZERO;

            for (CreateCompraPagoDto pagoDto : dto.getPagos()) {
                // Guardar pago
                CompraPagoEntity pagoEntity = new CompraPagoEntity();
                pagoEntity.setCompra(compra);
                pagoEntity.setMetodoPago(pagoDto.getMetodoPago());
                pagoEntity.setMonto(pagoDto.getMonto());
                pagoEntity.setBanco(pagoDto.getBanco());
                pagoEntity.setFechaPago(LocalDateTime.now());
                pagoEntity.setUsuario(usuario);
                pagoEntity.setActivo(true);
                compraPagoJPARepository.save(pagoEntity);

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

        // 5. Registrar cuenta por pagar solo si la forma de pago es CRÉDITO
        if ("CREDITO".equalsIgnoreCase(compra.getFormaPago())) {
            CreateCuentaPagarDto cuentaPagarDto = new CreateCuentaPagarDto();
            cuentaPagarDto.setProveedorId(dto.getProveedorId());
            cuentaPagarDto.setCompraId(compra.getId());
            cuentaPagarDto.setTotalDeuda(compra.getTotal());
            cuentaPagarDto.setFechaEmision(compra.getFecha());
            cuentaPagarDto.setFechaVencimiento(dto.getFechaVencimiento() != null ? dto.getFechaVencimiento() : compra.getFecha().plusDays(30));
            cuentaPagarDto.setObservaciones("Compra #" + compra.getId() + " - Compra a crédito");

            cuentaPagarService.crear(cuentaPagarDto, empresaId, usuarioId);
        }

        // 6. Si es CONTADO y el usuario tiene turno abierto → egreso de caja
        if ("CONTADO".equalsIgnoreCase(compra.getFormaPago())) {
            UsuarioEntity usuarioEgreso = usuarioRepository.findById(usuarioId.intValue()).orElse(null);
            if (usuarioEgreso != null) {
                final CompraEntity compraFinal = compra;
                final UsuarioEntity usuarioFinal = usuarioEgreso;
                turnoCajaJPARepository.findByUsuarioIdAndEstado(usuarioId, "ABIERTA")
                    .ifPresent(turno -> {
                        BigDecimal montoEgreso = compraFinal.getNetaAPagar() != null ? compraFinal.getNetaAPagar() : compraFinal.getTotal();
                        MovimientoCajaEntity egreso = MovimientoCajaEntity.builder()
                            .turnoCaja(turno)
                            .usuario(usuarioFinal)
                            .tipo("EGRESO")
                            .concepto("Compra #" + compraFinal.getId() + " - Pago contado a proveedor")
                            .monto(montoEgreso)
                            .build();
                        movimientoCajaJPARepository.save(egreso);
                    });
            }
        }

        return obtenerPorId(compra.getId(), empresaId);
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        CompraEntity compra = compraJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        if (compra.getEstado().equals("ANULADA"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La compra ya está anulada");

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
                inventario.setStockActual(saldoNuevo.max(BigDecimal.ZERO));
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

        return obtenerPorId(compra.getId(), empresaId);
    }

    // ─── Métodos privados de apoyo ───────────────────────────────────────────

    private void actualizarPreciosProducto(ProductoEntity producto, CreateCompraDetalleDto item) {
        producto.setCosto(item.getCostoUnitario());
        if (item.getPrecioVenta1() != null) producto.setPrecio(item.getPrecioVenta1());
        if (item.getPrecioVenta2() != null) producto.setPrecio2(item.getPrecioVenta2());
        if (item.getPrecioVenta3() != null) producto.setPrecio3(item.getPrecioVenta3());
        productoJPARepository.save(producto);
    }

    private LoteEntity resolverLote(ProductoEntity producto, SucursalEntity sucursal, CreateCompraDetalleDto item) {
        if (!Boolean.TRUE.equals(producto.getManejaLotes()) || item.getCodigoLote() == null)
            return null;

        return loteJPARepository
                .findByProductoIdAndSucursalIdAndCodigoLote(producto.getId(), Long.valueOf(sucursal.getId()), item.getCodigoLote())
                .orElseGet(() -> {
                    LoteEntity nuevoLote = new LoteEntity();
                    nuevoLote.setProducto(producto);
                    nuevoLote.setSucursal(sucursal);
                    nuevoLote.setCodigoLote(item.getCodigoLote());
                    nuevoLote.setFechaVencimiento(item.getFechaVencimiento());
                    nuevoLote.setStockActual(item.getCantidad());
                    nuevoLote.setCostoUnitario(item.getCostoUnitario());
                    nuevoLote.setActivo(true);
                    return loteJPARepository.save(nuevoLote);
                });
    }

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

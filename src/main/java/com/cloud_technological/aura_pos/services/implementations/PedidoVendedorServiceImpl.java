package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.pedidos_vendedor.CreatePedidoVendedorDetalleDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.CreatePedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorDetalleDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorPageableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.PedidoVendedorTableDto;
import com.cloud_technological.aura_pos.dto.pedidos_vendedor.RegistrarCobroPedidoDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.PedidoVendedorDetalleEntity;
import com.cloud_technological.aura_pos.entity.PedidoVendedorEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.pedidos_vendedor.PedidoVendedorDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.pedidos_vendedor.PedidoVendedorJPARepository;
import com.cloud_technological.aura_pos.repositories.pedidos_vendedor.PedidoVendedorQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.PedidoVendedorService;
import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.transaction.Transactional;

@Service
public class PedidoVendedorServiceImpl implements PedidoVendedorService {

    @Autowired
    private PedidoVendedorJPARepository pedidoJPARepository;

    @Autowired
    private PedidoVendedorDetalleJPARepository detalleJPARepository;

    @Autowired
    private PedidoVendedorQueryRepository pedidoQueryRepository;

    @Autowired
    private EmpresaJPARepository empresaJPARepository;

    @Autowired
    private SucursalJPARepository sucursalJPARepository;

    @Autowired
    private UsuarioJPARepository usuarioJPARepository;

    @Autowired
    private TerceroJPARepository terceroJPARepository;

    @Autowired
    private ProductoJPARepository productoJPARepository;

    @Autowired
    private CuentaCobrarJPARepository cuentaCobrarJPARepository;

    @Autowired
    private AbonoCobrarJPARepository abonoCobrarJPARepository;

    @Autowired
    private VentaJPARepository ventaJPARepository;

    @Lazy
    @Autowired
    private VentaService ventaService;

    @Override
    public PageImpl<PedidoVendedorTableDto> listar(PedidoVendedorPageableDto pageable, Integer empresaId) {
        return pedidoQueryRepository.listar(pageable, empresaId);
    }

    @Override
    public PedidoVendedorDto obtenerPorId(Long id, Integer empresaId) {
        PedidoVendedorEntity pedido = pedidoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        PedidoVendedorDto dto = toDto(pedido);
        List<PedidoVendedorDetalleDto> detalles = pedidoQueryRepository.obtenerDetalles(id);
        dto.setDetalles(detalles);
        return dto;
    }

    @Override
    @Transactional
    public PedidoVendedorDto crear(CreatePedidoVendedorDto dto, Integer empresaId, Long usuarioId) {
        EmpresaEntity empresa = empresaJPARepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        SucursalEntity sucursal = sucursalJPARepository
                .findFirstByEmpresaIdOrderByIdAsc(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "No hay sucursal configurada"));

        UsuarioEntity vendedor = usuarioJPARepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Vendedor no encontrado"));

        TerceroEntity cliente = null;
        if (dto.getClienteId() != null) {
            cliente = terceroJPARepository.findById(dto.getClienteId().intValue())
                    .orElse(null);
        }

        PedidoVendedorEntity pedido = new PedidoVendedorEntity();
        pedido.setEmpresa(empresa);
        pedido.setSucursal(sucursal);
        pedido.setVendedor(vendedor);
        pedido.setCliente(cliente);
        pedido.setObservaciones(dto.getObservaciones());
        pedido.setEstado("CREADA");
        pedido.setCreatedAt(LocalDateTime.now());

        // Calcular totales
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        BigDecimal impuestoTotal = BigDecimal.ZERO;

        PedidoVendedorEntity savedPedido = pedidoJPARepository.save(pedido);

        // Número de pedido
        savedPedido.setNumeroPedido("PV-" + String.format("%06d", savedPedido.getId()));

        for (CreatePedidoVendedorDetalleDto det : dto.getDetalles()) {
            ProductoEntity producto = productoJPARepository.findById(det.getProductoId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                            "Producto no encontrado: " + det.getProductoId()));

            BigDecimal descuento = det.getDescuentoValor() != null ? det.getDescuentoValor() : BigDecimal.ZERO;
            BigDecimal impuesto = det.getImpuestoValor() != null ? det.getImpuestoValor() : BigDecimal.ZERO;
            BigDecimal baseNeta = det.getPrecioUnitario().multiply(det.getCantidad()).subtract(descuento);
            BigDecimal subtotalLinea = baseNeta.add(impuesto);

            PedidoVendedorDetalleEntity detalle = new PedidoVendedorDetalleEntity();
            detalle.setPedidoVendedor(savedPedido);
            detalle.setProducto(producto);
            detalle.setCantidad(det.getCantidad());
            detalle.setPrecioUnitario(det.getPrecioUnitario());
            detalle.setDescuentoValor(descuento);
            detalle.setImpuestoValor(impuesto);
            detalle.setSubtotalLinea(subtotalLinea);
            detalleJPARepository.save(detalle);

            subtotal = subtotal.add(det.getPrecioUnitario().multiply(det.getCantidad()));
            descuentoTotal = descuentoTotal.add(descuento);
            impuestoTotal = impuestoTotal.add(impuesto);
        }

        BigDecimal total = subtotal.subtract(descuentoTotal).add(impuestoTotal);
        savedPedido.setSubtotal(subtotal);
        savedPedido.setDescuentoTotal(descuentoTotal);
        savedPedido.setImpuestoTotal(impuestoTotal);
        savedPedido.setTotal(total);
        pedidoJPARepository.save(savedPedido);

        return obtenerPorId(savedPedido.getId(), empresaId);
    }

    @Override
    @Transactional
    public void despachar(Long id, Integer empresaId) {
        PedidoVendedorEntity pedido = pedidoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if (!"CREADA".equals(pedido.getEstado()) && !"PENDIENTE_DESPACHO".equals(pedido.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden despachar pedidos en estado CREADA o PENDIENTE_DESPACHO");
        }

        pedido.setEstado("DESPACHADA");
        pedidoJPARepository.save(pedido);
    }

    @Override
    @Transactional
    public void registrarCobro(Long id, RegistrarCobroPedidoDto dto, Integer empresaId) {
        PedidoVendedorEntity pedido = pedidoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if ("ANULADA".equals(pedido.getEstado()) || "COBRADA".equals(pedido.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El pedido ya fue cobrado o anulado");
        }

        pedido.setEstado("COBRADA");
        pedido.setMetodoPago(dto.getMetodoPago());
        pedido.setReferenciaPago(dto.getReferencia());
        pedido.setFechaCobro(LocalDateTime.now());
        pedidoJPARepository.save(pedido);

        // Si el pedido tiene venta vinculada, cerrar la cuenta por cobrar y marcar venta COMPLETADA
        if (pedido.getVenta() != null) {
            cerrarCuentaCobrarDeVenta(pedido.getVenta(), dto.getMetodoPago(), dto.getReferencia(), empresaId);
        }
    }

    /**
     * Cierra la cuenta por cobrar asociada a la venta y actualiza el estado de la venta a COMPLETADA.
     * Se registra un abono por el saldo pendiente completo.
     */
    private void cerrarCuentaCobrarDeVenta(VentaEntity venta, String metodoPago, String referencia, Integer empresaId) {
        // Actualizar cuenta por cobrar
        cuentaCobrarJPARepository.findByVentaIdAndEmpresaId(venta.getId(), empresaId)
                .ifPresent(cuenta -> {
                    BigDecimal saldo = cuenta.getSaldoPendiente() != null
                            ? cuenta.getSaldoPendiente()
                            : cuenta.getTotalDeuda();

                    if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                        // Registrar abono por el saldo pendiente
                        AbonoCobrarEntity abono = new AbonoCobrarEntity();
                        abono.setCuentaCobrar(cuenta);
                        abono.setMonto(saldo);
                        abono.setMetodoPago(metodoPago);
                        abono.setReferencia(referencia);
                        abono.setFechaPago(LocalDateTime.now());
                        abonoCobrarJPARepository.save(abono);

                        // Cerrar la cuenta
                        cuenta.setTotalAbonado(cuenta.getTotalDeuda());
                        cuenta.setSaldoPendiente(BigDecimal.ZERO);
                        cuenta.setEstado("pagada");
                        cuentaCobrarJPARepository.save(cuenta);
                    }
                });

        // Marcar la venta como COMPLETADA si estaba en PAGO_PARCIAL
        ventaJPARepository.findByIdAndEmpresaId(venta.getId(), empresaId)
                .ifPresent(v -> {
                    if ("PAGO_PARCIAL".equals(v.getEstadoVenta())) {
                        v.setEstadoVenta("COMPLETADA");
                        v.setSaldoPendiente(BigDecimal.ZERO);
                        v.setPagoParcial(false);
                        ventaJPARepository.save(v);
                    }
                });
    }

    @Override
    @Transactional
    public void anular(Long id, Integer empresaId) {
        PedidoVendedorEntity pedido = pedidoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if ("COBRADA".equals(pedido.getEstado())) {
            // Solo se puede anular si vino de una venta automática; en ese caso también se anula la venta
            if (pedido.getVenta() == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede anular un pedido ya cobrado");
            }
            // Anular la venta vinculada (revierte inventario, etc.)
            ventaService.anular(pedido.getVenta().getId(), empresaId);
        }
        if ("ANULADA".equals(pedido.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El pedido ya está anulado");
        }

        pedido.setEstado("ANULADA");
        pedidoJPARepository.save(pedido);
    }

    private PedidoVendedorDto toDto(PedidoVendedorEntity p) {
        PedidoVendedorDto dto = new PedidoVendedorDto();
        dto.setId(p.getId());
        dto.setNumeroPedido(p.getNumeroPedido());
        dto.setEstado(p.getEstado());
        dto.setSubtotal(p.getSubtotal());
        dto.setDescuentoTotal(p.getDescuentoTotal());
        dto.setImpuestoTotal(p.getImpuestoTotal());
        dto.setTotal(p.getTotal());
        dto.setObservaciones(p.getObservaciones());
        dto.setMetodoPago(p.getMetodoPago());
        dto.setReferenciaPago(p.getReferenciaPago());
        dto.setFechaCobro(p.getFechaCobro());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setVentaId(p.getVenta() != null ? p.getVenta().getId() : null);
        if (p.getVendedor() != null && p.getVendedor().getTercero() != null) {
            TerceroEntity tv = p.getVendedor().getTercero();
            String vNombre = (tv.getRazonSocial() != null && !tv.getRazonSocial().isBlank())
                    ? tv.getRazonSocial()
                    : tv.getNombres() + " " + tv.getApellidos();
            dto.setVendedorNombre(vNombre);
        } else if (p.getVendedor() != null) {
            dto.setVendedorNombre(p.getVendedor().getUsername());
        }
        if (p.getCliente() != null) {
            dto.setClienteId(p.getCliente().getId());
            String nombre = (p.getCliente().getRazonSocial() != null && !p.getCliente().getRazonSocial().isBlank())
                    ? p.getCliente().getRazonSocial()
                    : p.getCliente().getNombres() + " " + p.getCliente().getApellidos();
            dto.setClienteNombre(nombre);
        }
        return dto;
    }
}

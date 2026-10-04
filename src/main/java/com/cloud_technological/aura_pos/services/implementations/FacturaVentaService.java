package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaPagoDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.entity.CondicionPagoEntity;
import com.cloud_technological.aura_pos.entity.FacturaVentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.factura_venta.CondicionPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.factura_venta.FacturaVentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.factura_venta.FacturaVentaJPARepository;
import com.cloud_technological.aura_pos.repositories.factura_venta.FacturaVentaQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/**
 * Facturación: la factura de venta fuera del POS (FV1 de docs/PLAN_FACTURACION.md).
 *
 * <p>El borrador vive solo en factura_venta: se edita y se borra sin tocar
 * consecutivo, stock ni contabilidad. Emitir arma la venta y la crea con el
 * mismo {@link VentaService#crear} del POS —stock por bodega, lotes, seriales,
 * kardex, cartera, tesorería y asiento salen de un solo lugar— marcada como
 * {@code desdeFacturacion}: sin turno de caja (el efectivo entra a la caja
 * general por la forma de pago), sin los límites de descuento del cajero y sin
 * pedido de vendedor espejo. Anular una emitida anula su venta.
 */
@Service
@RequiredArgsConstructor
public class FacturaVentaService {

    static final Set<String> FORMAS = Set.of("CREDITO", "CONTADO");
    static final Set<String> METODOS_CONTADO = Set.of("EFECTIVO", "TRANSFERENCIA", "CONSIGNACION", "TARJETA", "CHEQUE");

    private final FacturaVentaJPARepository repo;
    private final FacturaVentaDetalleJPARepository detalleRepo;
    private final CondicionPagoJPARepository condicionRepo;
    private final FacturaVentaQueryRepository query;
    private final ProductoJPARepository productoRepo;
    private final TerceroJPARepository terceroRepo;
    private final SucursalJPARepository sucursalRepo;
    private final CuentaBancariaJPARepository cuentaBancariaRepo;
    private final UsuarioJPARepository usuarioRepo;
    private final VentaService ventaService;
    private final FacturaAiuService aiu;
    private final com.cloud_technological.aura_pos.services.CotizacionService cotizacionService;
    private final com.cloud_technological.aura_pos.repositories.pedidos_vendedor.PedidoVendedorJPARepository pedidoRepo;
    private final com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository ventaRepo;
    private final com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository cxcRepo;
    private final com.cloud_technological.aura_pos.repositories.centros_costos.CentroCostoJPARepository centroCostoRepo;
    private final com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.AnticipoService anticipoService;

    // ── Consultas ──────────────────────────────────────────────────────

    public PageImpl<FacturaVentaDtos.Fila> listar(PageableDto<Object> pageable, Integer empresaId) {
        return query.listar(pageable, empresaId);
    }

    public FacturaVentaDtos.Detalle obtener(Long id, Integer empresaId) {
        FacturaVentaDtos.Detalle d = query.detalle(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
        d.setLineas(query.lineas(id));
        return d;
    }

    // ── Borrador ───────────────────────────────────────────────────────

    @Transactional
    public FacturaVentaDtos.Detalle crear(FacturaVentaDtos.Guardar dto, Integer empresaId, Long usuarioId) {
        return crear(dto, empresaId, usuarioId, null, null);
    }

    private FacturaVentaDtos.Detalle crear(FacturaVentaDtos.Guardar dto, Integer empresaId, Long usuarioId,
            Long cotizacionId, Long pedidoVendedorId) {
        FacturaVentaEntity f = new FacturaVentaEntity();
        f.setEmpresaId(empresaId);
        f.setCotizacionId(cotizacionId);
        f.setPedidoVendedorId(pedidoVendedorId);
        f.setUsuarioId(usuarioId != null ? usuarioId.intValue() : null);
        f.setEstado(FacturaVentaEntity.BORRADOR);
        aplicar(f, dto, empresaId);
        return obtener(f.getId(), empresaId);
    }

    @Transactional
    public FacturaVentaDtos.Detalle actualizar(Long id, FacturaVentaDtos.Guardar dto, Integer empresaId) {
        FacturaVentaEntity f = borrador(id, empresaId);
        aplicar(f, dto, empresaId);
        return obtener(id, empresaId);
    }

    /** Un borrador se borra sin rastro: no consumió nada. */
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        FacturaVentaEntity f = borrador(id, empresaId);
        detalleRepo.deleteAllById(query.idsLineas(id));
        detalleRepo.flush();
        repo.delete(f);
    }

    private FacturaVentaEntity borrador(Long id, Integer empresaId) {
        FacturaVentaEntity f = cargar(id, empresaId);
        if (!FacturaVentaEntity.BORRADOR.equals(f.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La factura ya fue emitida: no se puede modificar. Anúlela o haga una nota crédito");
        return f;
    }

    private FacturaVentaEntity cargar(Long id, Integer empresaId) {
        return repo.findById(id)
                .filter(x -> empresaId.equals(x.getEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada"));
    }

    /** Valida y guarda cabecera y líneas, recalculando impuestos y totales. */
    private void aplicar(FacturaVentaEntity f, FacturaVentaDtos.Guardar dto, Integer empresaId) {
        if (dto.getSucursalId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija la sucursal que factura");
        sucursalRepo.findByIdAndEmpresaId(dto.getSucursalId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada"));
        if (dto.getClienteId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija el cliente de la factura");
        terceroRepo.findByIdAndEmpresaId(dto.getClienteId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));
        if (dto.getVendedorId() != null)
            usuarioRepo.findByIdAndEmpresaId(dto.getVendedorId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor no encontrado"));

        String forma = dto.getFormaPago() == null ? "CREDITO" : dto.getFormaPago().trim().toUpperCase();
        if (!FORMAS.contains(forma))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Forma de pago inválida: use CREDITO o CONTADO");

        CondicionPagoEntity condicion = null;
        if (dto.getCondicionPagoId() != null) {
            condicion = condicionRepo.findById(dto.getCondicionPagoId())
                    .filter(c -> empresaId.equals(c.getEmpresaId()))
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Condición de pago no encontrada"));
        }

        String metodo = null;
        Long cuentaBancaria = null;
        LocalDate vence = null;
        if ("CONTADO".equals(forma)) {
            metodo = dto.getMetodoPago() == null ? "TRANSFERENCIA" : dto.getMetodoPago().trim().toUpperCase();
            if (!METODOS_CONTADO.contains(metodo))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Método de contado inválido: use EFECTIVO, TRANSFERENCIA, CONSIGNACION, TARJETA o CHEQUE");
            if (!"EFECTIVO".equals(metodo)) {
                if (dto.getCuentaBancariaId() == null)
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Elija la cuenta bancaria donde entró el pago");
                cuentaBancariaRepo.findByIdAndEmpresaId(dto.getCuentaBancariaId(), empresaId)
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Cuenta bancaria no encontrada"));
                cuentaBancaria = dto.getCuentaBancariaId();
            }
        } else {
            vence = dto.getFechaVencimiento() != null ? dto.getFechaVencimiento()
                    : LocalDate.now().plusDays(condicion != null ? condicion.getDias() : 30);
            if (vence.isBefore(LocalDate.now()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El vencimiento no puede ser anterior a hoy");
        }

        if (dto.getLineas() == null || dto.getLineas().isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Agregue al menos una línea a la factura");

        f.setSucursalId(dto.getSucursalId());
        f.setBodegaId(dto.getBodegaId());
        f.setClienteId(dto.getClienteId());
        f.setVendedorId(dto.getVendedorId());
        f.setCondicionPagoId(dto.getCondicionPagoId());
        f.setFormaPago(forma);
        f.setMetodoPago(metodo);
        f.setCuentaBancariaId(cuentaBancaria);
        f.setFechaVencimiento(vence);
        f.setOrdenCompra(texto(dto.getOrdenCompra(), 60));
        f.setNotas(texto(dto.getNotas(), 1000));
        if (dto.getCentroCostoId() != null)
            centroCostoRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getCentroCostoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Centro de costo no encontrado"));
        f.setCentroCostoId(dto.getCentroCostoId());

        // AIU (construcción): las líneas de la obra van sin IVA y A, I y U se agregan solas.
        boolean esAiu = Boolean.TRUE.equals(dto.getAiu());
        f.setAiu(esAiu);
        f.setAiuAdministracionPct(esAiu ? nz(dto.getAiuAdministracionPct()) : BigDecimal.ZERO);
        f.setAiuImprevistosPct(esAiu ? nz(dto.getAiuImprevistosPct()) : BigDecimal.ZERO);
        f.setAiuUtilidadPct(esAiu ? nz(dto.getAiuUtilidadPct()) : BigDecimal.ZERO);
        f.setAiuIvaPct(dto.getAiuIvaPct() != null ? dto.getAiuIvaPct() : BigDecimal.valueOf(19));
        if (esAiu) aiu.validar(f);

        // Las líneas se reemplazan completas: el borrador es uno solo con su formulario.
        List<FacturaVentaDetalleEntity> lineas = new ArrayList<>();
        int orden = 0;
        for (FacturaVentaDtos.Linea l : dto.getLineas()) {
            lineas.add(linea(l, empresaId, ++orden, esAiu));
        }
        if (esAiu) {
            BigDecimal costoDirecto = sumar(lineas, Campo.BASE);
            lineas.addAll(aiu.lineas(f, costoDirecto, orden));
        }
        f.setSubtotal(sumar(lineas, Campo.BASE));
        f.setDescuentoTotal(sumar(lineas, Campo.DESCUENTO));
        f.setImpuestosTotal(sumar(lineas, Campo.IMPUESTO));
        f.setTotal(sumar(lineas, Campo.TOTAL));
        repo.save(f);

        if (f.getId() != null) detalleRepo.deleteAllById(query.idsLineas(f.getId()));
        for (FacturaVentaDetalleEntity d : lineas) {
            d.setFacturaVentaId(f.getId());
            detalleRepo.save(d);
        }
        // Las lecturas que siguen son por JDBC: sin flush verían las líneas viejas.
        detalleRepo.flush();
        repo.flush();
    }

    private FacturaVentaDetalleEntity linea(FacturaVentaDtos.Linea l, Integer empresaId, int orden, boolean esAiu) {
        if (l.getProductoId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Línea " + orden + ": elija el producto o servicio");
        ProductoEntity p = productoRepo.findByIdAndEmpresaId(l.getProductoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + orden + ": producto no encontrado"));
        BigDecimal cantidad = l.getCantidad();
        if (cantidad == null || cantidad.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + orden + ": la cantidad debe ser mayor a cero");
        BigDecimal precio = l.getPrecioUnitario();
        if (precio == null || precio.signum() < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + orden + ": el precio no puede ser negativo");
        BigDecimal descuento = l.getDescuentoValor() == null ? BigDecimal.ZERO : l.getDescuentoValor();
        BigDecimal bruto = precio.multiply(cantidad);
        if (descuento.signum() < 0 || descuento.compareTo(bruto) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Línea " + orden + ": el descuento no puede ser negativo ni mayor que el valor de la línea");
        // En AIU la obra es costo directo: el IVA va solo sobre la Utilidad.
        BigDecimal pct = esAiu ? BigDecimal.ZERO
                : l.getImpuestoPorcentaje() != null ? l.getImpuestoPorcentaje()
                : p.getIvaPorcentaje() != null ? p.getIvaPorcentaje() : BigDecimal.ZERO;

        Totales t = totales(cantidad, precio, descuento, pct);
        return FacturaVentaDetalleEntity.builder()
                .productoId(p.getId())
                .productoPresentacionId(l.getProductoPresentacionId())
                .descripcion(texto(l.getDescripcion(), 500))
                .cantidad(cantidad)
                .precioUnitario(precio)
                .descuentoValor(descuento.setScale(2, RoundingMode.HALF_UP))
                .impuestoPorcentaje(pct)
                .impuestoValor(t.impuesto())
                .subtotalLinea(t.total())
                .orden(orden)
                .cotizacionDetalleId(l.getCotizacionDetalleId())
                .build();
    }

    /** Base neta e impuesto de una línea, redondeados como los redondea la venta. */
    record Totales(BigDecimal base, BigDecimal impuesto, BigDecimal total) {
    }

    static Totales totales(BigDecimal cantidad, BigDecimal precio, BigDecimal descuento, BigDecimal pct) {
        BigDecimal base = precio.multiply(cantidad).subtract(descuento).setScale(2, RoundingMode.HALF_UP);
        BigDecimal impuesto = base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Totales(base, impuesto, base.add(impuesto));
    }

    private enum Campo { BASE, DESCUENTO, IMPUESTO, TOTAL }

    private static BigDecimal sumar(List<FacturaVentaDetalleEntity> lineas, Campo c) {
        BigDecimal s = BigDecimal.ZERO;
        for (FacturaVentaDetalleEntity d : lineas) {
            s = s.add(switch (c) {
                case BASE -> d.getSubtotalLinea().subtract(d.getImpuestoValor());
                case DESCUENTO -> d.getDescuentoValor();
                case IMPUESTO -> d.getImpuestoValor();
                case TOTAL -> d.getSubtotalLinea();
            });
        }
        return s.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String pct(BigDecimal v) {
        return nz(v).stripTrailingZeros().toPlainString();
    }

    private static String texto(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ── Desde otros documentos ─────────────────────────────────────────

    /**
     * Borrador con lo que le queda pendiente a una cotización (PENDIENTE o
     * PARCIAL, vigente). Cada línea lleva su línea de cotización: al emitir, la
     * cotización queda PARCIAL o CONVERTIDA, igual que cuando se vende en el POS.
     */
    @Transactional
    public FacturaVentaDtos.Detalle desdeCotizacion(Long cotizacionId, Integer sucursalId, Integer empresaId,
            Long usuarioId) {
        var c = cotizacionService.obtenerPorId(cotizacionId, empresaId);
        CotizacionConversionService.validarVendible(c.getNumero(), c.getEstado(), c.getFechaVencimiento());
        if (c.getTerceroId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cotización " + c.getNumero() + " no tiene cliente: asígnele uno para facturarla");
        List<FacturaVentaDtos.Linea> lineas = new ArrayList<>();
        for (var d : c.getDetalles()) {
            BigDecimal pendiente = d.getCantidadPendiente() != null ? d.getCantidadPendiente() : d.getCantidad();
            if (pendiente == null || pendiente.signum() <= 0) continue;
            FacturaVentaDtos.Linea l = new FacturaVentaDtos.Linea();
            l.setProductoId(d.getProductoId());
            l.setDescripcion(d.getDescripcion());
            l.setCantidad(pendiente);
            l.setPrecioUnitario(d.getPrecioUnitario());
            // El descuento cotizado se reparte en proporción a lo que falta facturar.
            BigDecimal desc = d.getDescuentoValor() == null || d.getCantidad() == null || d.getCantidad().signum() == 0
                    ? BigDecimal.ZERO
                    : d.getDescuentoValor().multiply(pendiente).divide(d.getCantidad(), 2, RoundingMode.HALF_UP);
            l.setDescuentoValor(desc);
            l.setImpuestoPorcentaje(d.getIvaPorcentaje());
            l.setCotizacionDetalleId(d.getId());
            lineas.add(l);
        }
        if (lineas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "A la cotización " + c.getNumero() + " no le queda nada pendiente por facturar");
        FacturaVentaDtos.Guardar g = new FacturaVentaDtos.Guardar();
        g.setSucursalId(sucursalId);
        g.setClienteId(c.getTerceroId());
        g.setFormaPago("CREDITO");
        g.setNotas(c.getObservaciones());
        g.setLineas(lineas);
        return crear(g, empresaId, usuarioId, cotizacionId, null);
    }

    /**
     * Borrador con un pedido de vendedor (CREADA o PENDIENTE_DESPACHO, sin
     * venta). Al emitir, el pedido queda enlazado a la venta y DESPACHADO: es
     * lo mismo que despacharlo, pero con la factura de Facturación.
     */
    @Transactional
    public FacturaVentaDtos.Detalle desdePedido(Long pedidoId, Integer empresaId, Long usuarioId) {
        var pedido = pedidoVendible(pedidoId, empresaId);
        if (pedido.getCliente() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El pedido " + pedido.getNumeroPedido() + " no tiene cliente: asígnele uno para facturarlo");
        List<FacturaVentaDtos.Linea> lineas = new ArrayList<>();
        for (var d : pedido.getDetalles()) {
            FacturaVentaDtos.Linea l = new FacturaVentaDtos.Linea();
            l.setProductoId(d.getProducto().getId());
            l.setCantidad(d.getCantidad());
            l.setPrecioUnitario(d.getPrecioUnitario());
            BigDecimal desc = d.getDescuentoValor() != null ? d.getDescuentoValor() : BigDecimal.ZERO;
            l.setDescuentoValor(desc);
            // El pedido guarda el IVA en pesos: se vuelve a tarifa sobre su base.
            BigDecimal base = d.getPrecioUnitario().multiply(d.getCantidad()).subtract(desc);
            BigDecimal iva = d.getImpuestoValor() != null ? d.getImpuestoValor() : BigDecimal.ZERO;
            l.setImpuestoPorcentaje(base.signum() > 0
                    ? iva.multiply(BigDecimal.valueOf(100)).divide(base, 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            lineas.add(l);
        }
        if (lineas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El pedido no tiene productos para facturar");
        FacturaVentaDtos.Guardar g = new FacturaVentaDtos.Guardar();
        g.setSucursalId(pedido.getSucursal().getId());
        g.setClienteId(pedido.getCliente().getId());
        g.setVendedorId(pedido.getVendedor() != null ? pedido.getVendedor().getId() : null);
        g.setFormaPago("CREDITO");
        g.setNotas(pedido.getObservaciones());
        g.setLineas(lineas);
        return crear(g, empresaId, usuarioId, null, pedidoId);
    }

    /** Anticipos activos del cliente con saldo, para elegir cuáles cruzar al emitir. */
    public List<java.util.Map<String, Object>> anticiposCliente(Long clienteId, Integer empresaId) {
        List<java.util.Map<String, Object>> r = new ArrayList<>();
        for (var a : anticipoService.listar(empresaId, clienteId)) {
            if (!"CLIENTE".equals(a.getTipo()) || a.getSaldo() == null || a.getSaldo().signum() <= 0) continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("fecha", a.getFecha());
            m.put("monto", a.getMonto());
            m.put("saldo", a.getSaldo());
            m.put("observaciones", a.getObservaciones());
            r.add(m);
        }
        return r;
    }

    private com.cloud_technological.aura_pos.entity.PedidoVendedorEntity pedidoVendible(Long pedidoId,
            Integer empresaId) {
        var pedido = pedidoRepo.findByIdAndEmpresaId(pedidoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (pedido.getVenta() != null)
            throw new GlobalException(HttpStatus.CONFLICT,
                    "El pedido " + pedido.getNumeroPedido() + " ya tiene venta: no se puede facturar otra vez");
        if (!"CREADA".equals(pedido.getEstado()) && !"PENDIENTE_DESPACHO".equals(pedido.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El pedido " + pedido.getNumeroPedido() + " está " + pedido.getEstado() + ": no se puede facturar");
        return pedido;
    }

    // ── Copiar ─────────────────────────────────────────────────────────

    /**
     * Borrador nuevo igual a otra factura (en cualquier estado): cliente, sede,
     * bodega, vendedor, condiciones, AIU, notas y líneas. El vencimiento se
     * recalcula desde hoy y la orden de compra no se copia (es de cada pedido).
     */
    @Transactional
    public FacturaVentaDtos.Detalle copiar(Long id, Integer empresaId, Long usuarioId) {
        FacturaVentaEntity o = cargar(id, empresaId);
        FacturaVentaDtos.Guardar g = new FacturaVentaDtos.Guardar();
        g.setSucursalId(o.getSucursalId());
        g.setBodegaId(o.getBodegaId());
        g.setClienteId(o.getClienteId());
        g.setVendedorId(o.getVendedorId());
        g.setCondicionPagoId(o.getCondicionPagoId());
        g.setFormaPago(o.getFormaPago());
        g.setMetodoPago(o.getMetodoPago());
        g.setCuentaBancariaId(o.getCuentaBancariaId());
        g.setCentroCostoId(o.getCentroCostoId());
        g.setNotas(o.getNotas());
        g.setAiu(o.getAiu());
        g.setAiuAdministracionPct(o.getAiuAdministracionPct());
        g.setAiuImprevistosPct(o.getAiuImprevistosPct());
        g.setAiuUtilidadPct(o.getAiuUtilidadPct());
        g.setAiuIvaPct(o.getAiuIvaPct());
        List<FacturaVentaDtos.Linea> lineas = new ArrayList<>();
        for (FacturaVentaDtos.LineaDto d : query.lineas(id)) {
            if (d.getAiuTipo() != null) continue; // A, I y U se vuelven a calcular
            FacturaVentaDtos.Linea l = new FacturaVentaDtos.Linea();
            l.setProductoId(d.getProductoId());
            l.setProductoPresentacionId(d.getProductoPresentacionId());
            l.setDescripcion(d.getDescripcion());
            l.setCantidad(d.getCantidad());
            l.setPrecioUnitario(d.getPrecioUnitario());
            l.setDescuentoValor(d.getDescuentoValor());
            l.setImpuestoPorcentaje(d.getImpuestoPorcentaje());
            lineas.add(l);
        }
        g.setLineas(lineas);
        return crear(g, empresaId, usuarioId);
    }

    // ── Emitir y anular ────────────────────────────────────────────────

    /**
     * Emite el borrador: crea la venta (consecutivo, stock, cartera, asiento) en
     * la misma transacción. Si algo falla —stock, cupo de crédito, período
     * cerrado— no queda nada a medias y el borrador sigue igual.
     */
    @Transactional
    public FacturaVentaDtos.Detalle emitir(Long id, Integer empresaId, Long usuarioId) {
        return emitir(id, empresaId, usuarioId, null);
    }

    /**
     * Emite y, si es a crédito, cruza los anticipos del cliente elegidos contra
     * la cuenta por cobrar de la factura (todo en la misma transacción).
     */
    @Transactional
    public FacturaVentaDtos.Detalle emitir(Long id, Integer empresaId, Long usuarioId,
            FacturaVentaDtos.Emitir opciones) {
        FacturaVentaEntity f = borrador(id, empresaId);
        if (f.getPedidoVendedorId() != null) pedidoVendible(f.getPedidoVendedorId(), empresaId);
        List<FacturaVentaDtos.AnticipoAplicar> anticipos = opciones != null && opciones.getAnticipos() != null
                ? opciones.getAnticipos().stream()
                        .filter(a -> a.getAnticipoId() != null && a.getMonto() != null && a.getMonto().signum() > 0)
                        .toList()
                : List.of();
        if (!anticipos.isEmpty() && !"CREDITO".equals(f.getFormaPago()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los anticipos se cruzan contra facturas a crédito: la de contado ya queda pagada");
        FacturaVentaDtos.Detalle actual = obtener(id, empresaId);
        if (actual.getLineas().isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Agregue al menos una línea a la factura");
        if ("CREDITO".equals(f.getFormaPago()) && f.getFechaVencimiento() != null
                && f.getFechaVencimiento().isBefore(LocalDate.now()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El vencimiento ya pasó: corríjalo en el borrador antes de emitir");

        CreateVentaDto venta = armarVenta(f, actual);
        VentaDto creada = ventaService.crear(venta, empresaId, usuarioId);

        // Centro de costo de la factura: el asiento (después del commit) lo lee de la venta.
        if (f.getCentroCostoId() != null) {
            ventaRepo.findById(creada.getId()).ifPresent(v -> {
                v.setCentroCostoId(f.getCentroCostoId());
                ventaRepo.save(v);
            });
        }
        // El pedido facturado queda despachado (crear() ya lo enlazó a la venta).
        if (f.getPedidoVendedorId() != null) {
            pedidoRepo.findByIdAndEmpresaId(f.getPedidoVendedorId(), empresaId).ifPresent(p -> {
                p.setEstado("DESPACHADA");
                pedidoRepo.save(p);
            });
        }
        // Anticipos del cliente contra la cuenta por cobrar de la factura.
        if (!anticipos.isEmpty()) {
            var cxc = cxcRepo.findByVentaIdAndEmpresaId(creada.getId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT,
                            "La factura no quedó en cartera: no hay contra qué cruzar los anticipos"));
            for (FacturaVentaDtos.AnticipoAplicar a : anticipos) {
                anticipoService.cruzar(empresaId, usuarioId, a.getAnticipoId(), cxc.getId(), null, a.getMonto());
            }
        }

        f.setEstado(FacturaVentaEntity.EMITIDA);
        f.setVentaId(creada.getId());
        f.setEmitidaPor(usuarioId != null ? usuarioId.intValue() : null);
        f.setEmitidaAt(LocalDateTime.now());
        repo.saveAndFlush(f);
        return obtener(id, empresaId);
    }

    /** La venta que equivale a la factura, para el mismo VentaService del POS. */
    static CreateVentaDto armarVenta(FacturaVentaEntity f, FacturaVentaDtos.Detalle actual) {
        CreateVentaDto v = new CreateVentaDto();
        v.setDesdeFacturacion(true);
        v.setTipoDocumento("FACTURA");
        v.setSucursalId(f.getSucursalId());
        v.setBodegaId(f.getBodegaId());
        v.setClienteId(f.getClienteId());
        v.setCotizacionId(f.getCotizacionId());
        v.setPedidoVendedorId(f.getPedidoVendedorId());
        StringBuilder obs = new StringBuilder();
        if (f.getOrdenCompra() != null) obs.append("OC cliente: ").append(f.getOrdenCompra());
        if (Boolean.TRUE.equals(f.getAiu())) {
            obs.append(obs.length() > 0 ? " · " : "").append("Contrato AIU: Administración ")
                    .append(pct(f.getAiuAdministracionPct())).append("%, Imprevistos ")
                    .append(pct(f.getAiuImprevistosPct())).append("%, Utilidad ")
                    .append(pct(f.getAiuUtilidadPct())).append("%; IVA ")
                    .append(pct(f.getAiuIvaPct())).append("% sobre la utilidad");
        }
        if (f.getNotas() != null) obs.append(obs.length() > 0 ? " · " : "").append(f.getNotas());
        v.setObservaciones(obs.length() > 0 ? obs.toString() : null);
        if (f.getFechaVencimiento() != null) v.setFechaVencimiento(f.getFechaVencimiento().atStartOfDay());

        List<CreateVentaDetalleDto> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FacturaVentaDtos.LineaDto l : actual.getLineas()) {
            CreateVentaDetalleDto d = new CreateVentaDetalleDto();
            d.setProductoId(l.getProductoId());
            d.setProductoPresentacionId(l.getProductoPresentacionId());
            d.setCantidad(l.getCantidad());
            d.setPrecioUnitario(l.getPrecioUnitario());
            d.setDescuentoValor(l.getDescuentoValor());
            d.setImpuestoValor(l.getImpuestoValor());
            d.setDescripcion(l.getDescripcion());
            d.setCotizacionDetalleId(l.getCotizacionDetalleId());
            detalles.add(d);
            total = total.add(l.getSubtotalLinea());
        }
        v.setDetalles(detalles);

        CreateVentaPagoDto pago = new CreateVentaPagoDto();
        pago.setMonto(total);
        if ("CONTADO".equals(f.getFormaPago())) {
            pago.setMetodoPago(f.getMetodoPago());
            pago.setCuentaBancariaId(f.getCuentaBancariaId());
            pago.setReferencia("FACTURA-" + f.getId());
        } else {
            pago.setMetodoPago("CREDITO");
        }
        v.setPagos(List.of(pago));
        return v;
    }

    /** Anula la factura emitida (y su venta: stock, cartera y asiento se reversan). */
    @Transactional
    public FacturaVentaDtos.Detalle anular(Long id, Integer empresaId) {
        FacturaVentaEntity f = cargar(id, empresaId);
        if (FacturaVentaEntity.BORRADOR.equals(f.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un borrador no se anula: elimínelo");
        if (FacturaVentaEntity.ANULADA.equals(f.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La factura ya está anulada");
        ventaService.anular(f.getVentaId(), empresaId);
        f.setEstado(FacturaVentaEntity.ANULADA);
        repo.saveAndFlush(f);
        return obtener(id, empresaId);
    }

    // ── Condiciones de pago ────────────────────────────────────────────

    public List<FacturaVentaDtos.CondicionPago> condiciones(Integer empresaId, boolean soloActivas) {
        return query.condiciones(empresaId, soloActivas);
    }

    @Transactional
    public FacturaVentaDtos.CondicionPago guardarCondicion(Long id, FacturaVentaDtos.CondicionPago dto,
            Integer empresaId) {
        if (dto.getNombre() == null || dto.getNombre().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el nombre de la condición");
        int dias = dto.getDias() == null ? 0 : dto.getDias();
        if (dias < 0 || dias > 3650)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los días deben estar entre 0 y 3650");
        if (query.existeCondicion(empresaId, dto.getNombre(), id))
            throw new GlobalException(HttpStatus.CONFLICT, "Ya existe una condición con ese nombre");
        CondicionPagoEntity c = id == null ? new CondicionPagoEntity()
                : condicionRepo.findById(id).filter(x -> empresaId.equals(x.getEmpresaId()))
                        .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Condición no encontrada"));
        c.setEmpresaId(empresaId);
        c.setNombre(dto.getNombre().trim());
        c.setDias(dias);
        c.setActiva(dto.getActiva() == null || dto.getActiva());
        c = condicionRepo.save(c);
        FacturaVentaDtos.CondicionPago r = new FacturaVentaDtos.CondicionPago();
        r.setId(c.getId());
        r.setNombre(c.getNombre());
        r.setDias(c.getDias());
        r.setActiva(c.getActiva());
        return r;
    }
}

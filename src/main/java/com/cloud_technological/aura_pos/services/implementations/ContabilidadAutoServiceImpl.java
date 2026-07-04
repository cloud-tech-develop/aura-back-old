package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.AsientoDetalleDto;
import com.cloud_technological.aura_pos.dto.contabilidad.SaldoCuentaDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.CompraPagoEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.entity.CuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.DevolucionEntity;
import com.cloud_technological.aura_pos.entity.DevolucionDetalleEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.entity.MermaEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.ObligacionFinancieraEntity;
import com.cloud_technological.aura_pos.entity.CuotaAmortizacionEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.entity.VentaPagoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraJPARepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.entity.TesoreriaMovimientoEntity;
import com.cloud_technological.aura_pos.repositories.tesoreria.CuentaBancariaJPARepository;
import com.cloud_technological.aura_pos.repositories.tesoreria.TesoreriaMovimientoJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionJPARepository;
import com.cloud_technological.aura_pos.repositories.devolucion.DevolucionDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.repositories.merma.MermaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.repositories.obligaciones.ObligacionFinancieraJPARepository;
import com.cloud_technological.aura_pos.repositories.obligaciones.CuotaAmortizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.periodo_contable.PeriodoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.utils.AsientoBalanceValidator;

@Service
public class ContabilidadAutoServiceImpl implements ContabilidadAutoService {

    @Autowired private VentaJPARepository ventaRepo;
    @Autowired private CompraJPARepository compraRepo;
    @Autowired private AsientoContableJPARepository asientoRepo;
    @Autowired private AsientoContableQueryRepository queryRepo;
    @Autowired private PeriodoContableJPARepository periodoRepo;
    @Autowired private VentaPagoJPARepository ventaPagoRepo;
    @Autowired private VentaDetalleJPARepository ventaDetalleRepo;
    @Autowired private CompraPagoJPARepository compraPagoRepo;
    @Autowired private DevolucionJPARepository devolucionRepo;
    @Autowired private DevolucionDetalleJPARepository devolucionDetalleRepo;
    @Autowired private AbonoCobrarJPARepository abonoCobrarRepo;
    @Autowired private AbonoPagarJPARepository abonoPagarRepo;
    @Autowired private GastoJPARepository gastoRepo;
    @Autowired private MermaJPARepository mermaRepo;
    @Autowired private NominaJPARepository nominaRepo;
    @Autowired private ObligacionFinancieraJPARepository obligacionRepo;
    @Autowired private CuotaAmortizacionJPARepository cuotaRepo;
    @Autowired private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private TesoreriaMovimientoJPARepository tesoreriaMovRepo;
    @Autowired private PlanCuentaJPARepository planRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository movimientoCajaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.conceptos_caja.ConceptoCajaJPARepository conceptoCajaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.comprobante_caja.ComprobanteCajaJPARepository comprobanteCajaRepo;
    @Autowired private ConfiguracionContableService config;

    // ────────────────────────────────────────────────────────────────────────
    //  Prefijos de comprobante (Regla 1)
    //  VT = Venta  |  CO = Compra  |  CD = Comprobante de Diario (manual)
    // ────────────────────────────────────────────────────────────────────────
    private static final String PREFIX_VENTA      = "VT";
    private static final String PREFIX_COMPRA     = "CO";
    private static final String PREFIX_REVERSA    = "RV";
    private static final String PREFIX_DEVOLUCION = "DV";
    private static final String PREFIX_RECAUDO    = "RC";
    private static final String PREFIX_EGRESO     = "EG";
    private static final String PREFIX_GASTO      = "GT";
    private static final String PREFIX_MERMA      = "MM";
    private static final String PREFIX_NOMINA     = "NO";
    private static final String PREFIX_NOMINA_PAGO = "PN";
    private static final String PREFIX_PRESTACION_PAGO = "PP";

    @Autowired
    private com.cloud_technological.aura_pos.repositories.nomina.LiquidacionPrestacionJPARepository prestacionRepo;
    private static final String PREFIX_CIERRE     = "CE";
    private static final String PREFIX_OBLIGACION = "OB";
    private static final String PREFIX_CUOTA      = "CU";
    private static final String PREFIX_TESORERIA  = "TS";

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeVenta(Long ventaId, Integer empresaId,
            Integer usuarioId) {
        // Idempotencia: no duplicar si ya existe
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("VENTA", ventaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la venta #" + ventaId);
        }

        // Validar período contable abierto
        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de generar asientos."));

        VentaEntity venta = ventaRepo.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Venta no encontrada"));

        BigDecimal total      = nz(venta.getTotalPagar());
        BigDecimal impuestos  = nz(venta.getImpuestosTotal());
        BigDecimal ingresoNeto = total.subtract(impuestos);              // ingreso = total − IVA (neto de descuentos)
        BigDecimal saldoPendiente = nz(venta.getSaldoPendiente());       // lo que queda a crédito → cartera
        Long clienteId = venta.getCliente() != null ? venta.getCliente().getId() : null;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITOS · recaudo de contado (caja/bancos) ────────────────────────
        // Cada pago no-crédito entra a Bancos (si tiene cuenta) o Caja.
        for (VentaPagoEntity pago : ventaPagoRepo.findByVentaId(ventaId)) {
            if ("CREDITO".equalsIgnoreCase(pago.getMetodoPago())) {
                continue; // la cartera se registra una sola vez vía saldoPendiente
            }
            BigDecimal monto = nz(pago.getMonto());
            if (monto.signum() <= 0) continue;
            PlanCuentaEntity cuenta = resolverCuentaPago(empresaId, pago.getMetodoPago(), pago.getCuentaBancariaId());
            detalles.add(linea(cuenta.getId(),
                    "Recaudo venta (" + pago.getMetodoPago() + ")", monto, BigDecimal.ZERO));
        }

        // ── DÉBITO · cartera (clientes) por el saldo a crédito/parcial ────────
        if (saldoPendiente.signum() > 0) {
            PlanCuentaEntity clientes = config.resolverCuenta(empresaId, ConceptoContable.CLIENTES);
            detalles.add(linea(clientes.getId(), "Cartera venta a crédito",
                    saldoPendiente, BigDecimal.ZERO, clienteId));
        }

        // ── CRÉDITO · ingreso por ventas (neto de IVA) ────────────────────────
        if (ingresoNeto.signum() != 0) {
            PlanCuentaEntity ingresos = config.resolverCuenta(empresaId, ConceptoContable.INGRESOS_VENTAS);
            detalles.add(linea(ingresos.getId(), "Ingresos venta", BigDecimal.ZERO, ingresoNeto));
        }

        // ── CRÉDITO · IVA generado ────────────────────────────────────────────
        if (impuestos.signum() > 0) {
            PlanCuentaEntity iva = config.resolverCuenta(empresaId, ConceptoContable.IVA_GENERADO);
            detalles.add(linea(iva.getId(), "IVA generado", BigDecimal.ZERO, impuestos));
        }

        // ── Costo de venta / salida de inventario (par balanceado aparte) ─────
        BigDecimal costoTotal = ventaDetalleRepo.findByVentaId(ventaId).stream()
                .map(d -> nz(d.getCostoLinea()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (costoTotal.signum() > 0) {
            PlanCuentaEntity costo = config.resolverCuenta(empresaId, ConceptoContable.COSTO_VENTAS);
            PlanCuentaEntity inventario = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
            detalles.add(linea(costo.getId(), "Costo de venta", costoTotal, BigDecimal.ZERO));
            detalles.add(linea(inventario.getId(), "Salida de inventario", BigDecimal.ZERO, costoTotal));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La venta #" + ventaId + " no produjo movimientos contables.");
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_VENTA);
        String docVenta = venta.getConsecutivo() != null
                ? " — " + (venta.getPrefijo() != null ? venta.getPrefijo() + "-" : "") + venta.getConsecutivo()
                : "";
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                venta.getFechaEmision().toLocalDate(), comprobante,
                "Venta #" + ventaId + docVenta,
                "VENTA", ventaId, periodo.getId(), detalles);
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeCompra(Long compraId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("COMPRA", compraId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la compra #" + compraId);
        }

        // Validar período contable abierto
        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de generar asientos."));

        CompraEntity compra = compraRepo.findByIdAndEmpresaId(compraId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Compra no encontrada"));

        BigDecimal subtotal   = nz(compra.getSubtotal());
        BigDecimal descuento  = nz(compra.getDescuentoTotal());
        BigDecimal fletes     = nz(compra.getFletes());
        BigDecimal impuestos  = nz(compra.getImpuestosTotal());            // IVA descontable
        BigDecimal netaAPagar = compra.getNetaAPagar() != null
                ? compra.getNetaAPagar() : nz(compra.getTotal());
        // Costo capitalizado en inventario: subtotal neto de descuento + fletes
        BigDecimal costoInventario = subtotal.subtract(descuento).add(fletes);
        Long proveedorId = compra.getProveedor() != null ? compra.getProveedor().getId() : null;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITO · inventario (costo + fletes − descuento) ──────────────────
        if (costoInventario.signum() != 0) {
            PlanCuentaEntity inv = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
            detalles.add(linea(inv.getId(), "Inventario compra", costoInventario, BigDecimal.ZERO));
        }

        // ── DÉBITO · IVA descontable ──────────────────────────────────────────
        if (impuestos.signum() > 0) {
            PlanCuentaEntity iva = config.resolverCuenta(empresaId, ConceptoContable.IVA_DESCONTABLE);
            detalles.add(linea(iva.getId(), "IVA descontable", impuestos, BigDecimal.ZERO));
        }

        // ── CRÉDITO · retenciones practicadas al proveedor ────────────────────
        BigDecimal retefuente = nz(compra.getRetefuenteValor());
        if (retefuente.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEFUENTE_PRACTICADA);
            detalles.add(linea(c.getId(), "Retención en la fuente", BigDecimal.ZERO, retefuente, proveedorId));
        }
        BigDecimal reteiva = nz(compra.getReteivaValor());
        if (reteiva.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEIVA_PRACTICADA);
            detalles.add(linea(c.getId(), "ReteIVA", BigDecimal.ZERO, reteiva, proveedorId));
        }
        BigDecimal reteica = nz(compra.getReteicaValor());
        if (reteica.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEICA_PRACTICADA);
            detalles.add(linea(c.getId(), "ReteICA", BigDecimal.ZERO, reteica, proveedorId));
        }

        // ── CRÉDITO · pago de contado (caja/bancos) ───────────────────────────
        BigDecimal pagado = BigDecimal.ZERO;
        for (CompraPagoEntity pago : compraPagoRepo.findByCompraIdAndActivoTrue(compraId)) {
            BigDecimal monto = nz(pago.getMonto());
            if (monto.signum() <= 0) continue;
            PlanCuentaEntity cuenta = resolverCuentaPago(empresaId, pago.getMetodoPago(), pago.getCuentaBancariaId());
            detalles.add(linea(cuenta.getId(),
                    "Pago compra (" + pago.getMetodoPago() + ")", BigDecimal.ZERO, monto));
            pagado = pagado.add(monto);
        }

        // ── CRÉDITO · saldo a crédito al proveedor ────────────────────────────
        BigDecimal saldoProveedor = netaAPagar.subtract(pagado);
        if (saldoProveedor.signum() > 0) {
            PlanCuentaEntity prov = config.resolverCuenta(empresaId, ConceptoContable.PROVEEDORES);
            detalles.add(linea(prov.getId(), "Cuenta por pagar proveedor",
                    BigDecimal.ZERO, saldoProveedor, proveedorId));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La compra #" + compraId + " no produjo movimientos contables.");
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_COMPRA);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                compra.getFecha().toLocalDate(), comprobante,
                "Compra #" + compraId + (compra.getNumeroCompra() != null
                        ? " — " + compra.getNumeroCompra() : ""),
                "COMPRA", compraId, periodo.getId(), detalles);
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto reversar(String origenTipo, Long origenId,
            Integer empresaId, Integer usuarioId) {
        String tipoReversa = "ANULACION_" + origenTipo;

        // Idempotencia: no reversar dos veces
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId(tipoReversa, origenId, empresaId)) {
            return null;
        }

        // Asiento original a reversar. Si no existe (operación previa al auto-posting
        // o cuyo asiento nunca se generó), no hay nada que reversar → no-op.
        AsientoContableEntity original = asientoRepo
                .findByTipoOrigenAndOrigenIdAndEmpresaId(origenTipo, origenId, empresaId)
                .orElse(null);
        if (original == null) {
            return null;
        }

        // El contraasiento se registra en el período abierto actual (no en el del
        // asiento original, que podría estar cerrado).
        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de reversar."));

        // Construir el contraasiento intercambiando débito ↔ crédito de cada línea.
        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        for (AsientoDetalleDto d : queryRepo.obtenerDetalles(original.getId())) {
            detalles.add(AsientoDetalleEntity.builder()
                    .cuentaId(d.getCuentaId())
                    .descripcion("Reversa: " + (d.getDescripcion() != null ? d.getDescripcion() : ""))
                    .debito(nz(d.getCredito()))
                    .credito(nz(d.getDebito()))
                    .terceroId(d.getTerceroId())
                    .centroCostoId(d.getCentroCostoId())
                    .build());
        }
        if (detalles.isEmpty()) {
            return null;
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_REVERSA);
        String etiqueta = origenTipo != null ? origenTipo.toLowerCase() : "operación";
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                java.time.LocalDate.now(), comprobante,
                "Anulación " + etiqueta + " #" + origenId
                        + " (reversa de " + original.getNumeroComprobante() + ")",
                tipoReversa, origenId, periodo.getId(), detalles);
        detalles.forEach(x -> x.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeDevolucion(Long devolucionId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("DEVOLUCION", devolucionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la devolución #" + devolucionId);
        }

        PeriodoContableEntity periodo = periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de generar asientos."));

        DevolucionEntity dev = devolucionRepo.findByIdAndEmpresaId(devolucionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Devolución no encontrada"));

        List<DevolucionDetalleEntity> dets = devolucionDetalleRepo.findByDevolucionId(devolucionId);

        BigDecimal total = nz(dev.getTotalDevolucion());
        BigDecimal iva = dets.stream().map(d -> nz(d.getImpuestoValor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal base = total.subtract(iva);                              // ingreso revertido

        // Productos agregados en un cambio (se suman a la venta original).
        BigDecimal totalAgregado = nz(dev.getTotalAgregado());
        BigDecimal ivaAgregado = nz(dev.getIvaAgregado());
        BigDecimal baseAgregada = totalAgregado.subtract(ivaAgregado);      // ingreso reconocido
        BigDecimal costoAgregado = nz(dev.getCostoAgregado());

        // Neto: + a favor del cliente (reembolso), - faltante que paga el cliente.
        BigDecimal neto = dev.getNetoDiferencia() != null
                ? dev.getNetoDiferencia() : total.subtract(totalAgregado);
        BigDecimal montoAFavor = neto.signum() > 0 ? neto : BigDecimal.ZERO;
        BigDecimal faltante = neto.signum() < 0 ? neto.negate() : BigDecimal.ZERO;

        BigDecimal carteraAfectada = Boolean.TRUE.equals(dev.getAfectoCartera())
                ? nz(dev.getMontoCarteraAfectado()) : BigDecimal.ZERO;
        BigDecimal reembolso = montoAFavor.subtract(carteraAfectada);      // devuelto en caja
        Long clienteId = dev.getCliente() != null ? dev.getCliente().getId()
                : (dev.getVenta() != null && dev.getVenta().getCliente() != null
                        ? dev.getVenta().getCliente().getId() : null);

        // Costo de lo devuelto (solo si reingresa al inventario)
        BigDecimal costoDevuelto = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(dev.getReintegraInventario())) {
            for (DevolucionDetalleEntity d : dets) {
                BigDecimal costo = d.getProducto() != null && d.getProducto().getCosto() != null
                        ? d.getProducto().getCosto() : BigDecimal.ZERO;
                costoDevuelto = costoDevuelto.add(nz(d.getCantidad()).multiply(costo));
            }
            costoDevuelto = costoDevuelto.setScale(2, java.math.RoundingMode.HALF_UP);
        }

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITO · reversa del ingreso e IVA ────────────────────────────────
        if (base.signum() != 0) {
            PlanCuentaEntity ingresos = config.resolverCuenta(empresaId, ConceptoContable.INGRESOS_VENTAS);
            detalles.add(linea(ingresos.getId(), "Devolución en ventas", base, BigDecimal.ZERO));
        }
        if (iva.signum() > 0) {
            PlanCuentaEntity ivaCta = config.resolverCuenta(empresaId, ConceptoContable.IVA_GENERADO);
            detalles.add(linea(ivaCta.getId(), "IVA devolución", iva, BigDecimal.ZERO));
        }

        // ── CRÉDITO · ingreso reconocido por los productos del cambio ─────────
        if (baseAgregada.signum() != 0) {
            PlanCuentaEntity ingresos = config.resolverCuenta(empresaId, ConceptoContable.INGRESOS_VENTAS);
            detalles.add(linea(ingresos.getId(), "Venta por cambio", BigDecimal.ZERO, baseAgregada));
        }
        if (ivaAgregado.signum() > 0) {
            PlanCuentaEntity ivaCta = config.resolverCuenta(empresaId, ConceptoContable.IVA_GENERADO);
            detalles.add(linea(ivaCta.getId(), "IVA venta por cambio", BigDecimal.ZERO, ivaAgregado));
        }

        // ── CRÉDITO · devolución de dinero (cartera y/o caja) ─────────────────
        if (carteraAfectada.signum() > 0) {
            PlanCuentaEntity clientes = config.resolverCuenta(empresaId, ConceptoContable.CLIENTES);
            detalles.add(linea(clientes.getId(), "Devolución afecta cartera",
                    BigDecimal.ZERO, carteraAfectada, clienteId));
        }
        if (reembolso.signum() > 0) {
            PlanCuentaEntity caja = config.resolverCuenta(empresaId, ConceptoContable.CAJA);
            detalles.add(linea(caja.getId(), "Reembolso devolución", BigDecimal.ZERO, reembolso));
        }

        // ── DÉBITO · cobro del faltante del cambio (entra dinero a caja) ──────
        if (faltante.signum() > 0) {
            PlanCuentaEntity caja = config.resolverCuenta(empresaId, ConceptoContable.CAJA);
            detalles.add(linea(caja.getId(), "Cobro faltante cambio", faltante, BigDecimal.ZERO));
        }

        // ── Reingreso de inventario / reversa de costo (par balanceado) ───────
        if (costoDevuelto.signum() > 0) {
            PlanCuentaEntity inv = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
            PlanCuentaEntity costo = config.resolverCuenta(empresaId, ConceptoContable.COSTO_VENTAS);
            detalles.add(linea(inv.getId(), "Reingreso inventario", costoDevuelto, BigDecimal.ZERO));
            detalles.add(linea(costo.getId(), "Reversa costo de venta", BigDecimal.ZERO, costoDevuelto));
        }

        // ── Salida de inventario / costo de los productos del cambio ──────────
        if (costoAgregado.signum() > 0) {
            PlanCuentaEntity inv = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
            PlanCuentaEntity costo = config.resolverCuenta(empresaId, ConceptoContable.COSTO_VENTAS);
            detalles.add(linea(costo.getId(), "Costo venta por cambio", costoAgregado, BigDecimal.ZERO));
            detalles.add(linea(inv.getId(), "Salida inventario por cambio", BigDecimal.ZERO, costoAgregado));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La devolución #" + devolucionId + " no produjo movimientos contables.");
        }

        // El asiento se fecha en la fecha de la VENTA original (afecta ese día/operación).
        java.time.LocalDate fecha = dev.getVenta() != null && dev.getVenta().getFechaEmision() != null
                ? dev.getVenta().getFechaEmision().toLocalDate()
                : (dev.getCreatedAt() != null ? dev.getCreatedAt().toLocalDate() : java.time.LocalDate.now());
        Long ventaId = dev.getVenta() != null ? dev.getVenta().getId() : null;
        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_DEVOLUCION);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId, fecha, comprobante,
                "Devolución #" + devolucionId + (ventaId != null ? " — venta #" + ventaId : ""),
                "DEVOLUCION", devolucionId, periodo.getId(), detalles);
        detalles.forEach(d -> d.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeAbonoCobro(Long abonoId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("ABONO_COBRAR", abonoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el abono de cobro #" + abonoId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        AbonoCobrarEntity abono = abonoCobrarRepo.findById(abonoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abono no encontrado"));

        BigDecimal monto = nz(abono.getMonto());
        Long terceroId = abono.getCuentaCobrar() != null && abono.getCuentaCobrar().getTercero() != null
                ? abono.getCuentaCobrar().getTercero().getId() : null;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        PlanCuentaEntity caja = resolverCuentaPago(empresaId, abono.getMetodoPago(), null);
        PlanCuentaEntity clientes = config.resolverCuenta(empresaId, ConceptoContable.CLIENTES);
        detalles.add(linea(caja.getId(), "Recaudo cartera (" + abono.getMetodoPago() + ")", monto, BigDecimal.ZERO));
        detalles.add(linea(clientes.getId(), "Abono cartera cliente", BigDecimal.ZERO, monto, terceroId));

        return persistir(empresaId, usuarioId, java.time.LocalDate.now(), PREFIX_RECAUDO,
                "Recaudo cartera — abono #" + abonoId, "ABONO_COBRAR", abonoId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeAbonoPago(Long abonoId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("ABONO_PAGAR", abonoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el abono de pago #" + abonoId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        AbonoPagarEntity abono = abonoPagarRepo.findById(abonoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abono no encontrado"));

        BigDecimal monto = nz(abono.getMonto());
        Long terceroId = abono.getCuentaPagar() != null && abono.getCuentaPagar().getTercero() != null
                ? abono.getCuentaPagar().getTercero().getId() : null;

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        PlanCuentaEntity proveedores = config.resolverCuenta(empresaId, ConceptoContable.PROVEEDORES);
        PlanCuentaEntity caja = resolverCuentaPago(empresaId, abono.getMetodoPago(), abono.getCuentaBancariaId());
        detalles.add(linea(proveedores.getId(), "Pago a proveedor", monto, BigDecimal.ZERO, terceroId));
        detalles.add(linea(caja.getId(), "Egreso pago (" + abono.getMetodoPago() + ")", BigDecimal.ZERO, monto));

        return persistir(empresaId, usuarioId, java.time.LocalDate.now(), PREFIX_EGRESO,
                "Pago a proveedor — abono #" + abonoId, "ABONO_PAGAR", abonoId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeGasto(Long gastoId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("GASTO", gastoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el gasto #" + gastoId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        GastoEntity gasto = gastoRepo.findByIdAndEmpresaId(gastoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        BigDecimal monto      = nz(gasto.getMonto());           // valor del gasto (base)
        BigDecimal iva        = nz(gasto.getValorIva());        // IVA descontable
        BigDecimal retefuente = nz(gasto.getValorRetefuente());
        BigDecimal reteica    = nz(gasto.getValorReteica());
        BigDecimal neto       = monto.add(iva).subtract(retefuente).subtract(reteica); // a pagar
        Long terceroId = gasto.getTerceroId();

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // DB · cuenta de gasto (la que eligió el usuario; fallback a gasto general)
        Long cuentaGastoId = gasto.getCuentaContableId() != null
                ? gasto.getCuentaContableId()
                : config.resolverCuenta(empresaId, ConceptoContable.GASTO_GENERAL).getId();
        AsientoDetalleEntity lineaGasto = linea(cuentaGastoId, "Gasto: "
                + (gasto.getDescripcion() != null ? gasto.getDescripcion() : gasto.getCategoria()),
                monto, BigDecimal.ZERO, terceroId);
        lineaGasto.setCentroCostoId(gasto.getCentroCostoId());
        detalles.add(lineaGasto);

        // DB · IVA descontable
        if (iva.signum() > 0) {
            PlanCuentaEntity ivaCta = config.resolverCuenta(empresaId, ConceptoContable.IVA_DESCONTABLE);
            detalles.add(linea(ivaCta.getId(), "IVA descontable gasto", iva, BigDecimal.ZERO));
        }
        // CR · retenciones practicadas
        if (retefuente.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEFUENTE_PRACTICADA);
            detalles.add(linea(c.getId(), "Retefuente gasto", BigDecimal.ZERO, retefuente, terceroId));
        }
        if (reteica.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEICA_PRACTICADA);
            detalles.add(linea(c.getId(), "ReteICA gasto", BigDecimal.ZERO, reteica, terceroId));
        }
        // CR · pago de contado (caja)
        if (neto.signum() > 0) {
            PlanCuentaEntity caja = config.resolverCuenta(empresaId, ConceptoContable.CAJA);
            detalles.add(linea(caja.getId(), "Pago gasto", BigDecimal.ZERO, neto));
        }

        java.time.LocalDate fecha = gasto.getFecha() != null ? gasto.getFecha() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_GASTO,
                "Gasto #" + gastoId + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""),
                "GASTO", gastoId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeMerma(Long mermaId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("MERMA", mermaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la merma #" + mermaId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        MermaEntity merma = mermaRepo.findByIdAndEmpresaId(mermaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merma no encontrada"));

        BigDecimal costo = nz(merma.getCostoTotal());
        if (costo.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La merma #" + mermaId + " no tiene costo a contabilizar.");
        }

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        PlanCuentaEntity perdida = config.resolverCuenta(empresaId, ConceptoContable.PERDIDA_MERMA);
        PlanCuentaEntity inventario = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
        detalles.add(linea(perdida.getId(), "Pérdida por merma", costo, BigDecimal.ZERO));
        detalles.add(linea(inventario.getId(), "Baja de inventario por merma", BigDecimal.ZERO, costo));

        java.time.LocalDate fecha = merma.getFecha() != null ? merma.getFecha().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_MERMA,
                "Merma #" + mermaId, "MERMA", mermaId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeNomina(Long nominaId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("NOMINA", nominaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la nómina #" + nominaId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        NominaEntity n = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITOS · gasto de personal desglosado por auxiliar (5105xx) ──────
        // El sueldo absorbe todo el devengado (salario proporcional + auxilio + novedades).
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_SUELDOS,
                "Sueldos y devengados", nz(n.getTotalDevengado()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_APORTE_SALUD,
                "Aporte patronal salud", nz(n.getAporteSalud()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_APORTE_PENSION,
                "Aporte patronal pensión", nz(n.getAportePension()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_ARL,
                "ARL", nz(n.getAporteArl()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_CAJA,
                "Caja de compensación", nz(n.getAporteCaja()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_SENA_ICBF,
                "SENA e ICBF", nz(n.getAporteSena()).add(nz(n.getAporteIcbf())));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_PRIMA,
                "Provisión prima de servicios", nz(n.getProvisionPrima()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_CESANTIAS,
                "Provisión cesantías", nz(n.getProvisionCesantias()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_INT_CESANTIAS,
                "Provisión intereses de cesantías", nz(n.getProvisionIntCesantias()));
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_VACACIONES,
                "Provisión vacaciones", nz(n.getProvisionVacaciones()));

        // ── CRÉDITOS · pasivos por pagar ──────────────────────────────────────
        // Salarios netos al empleado.
        addCreditoNomina(detalles, empresaId, ConceptoContable.SALARIOS_POR_PAGAR,
                "Salarios netos por pagar", nz(n.getNetoPagar()));
        // Seguridad social + parafiscales (deducciones del empleado + aportes patronales).
        BigDecimal seguridadSocial = nz(n.getDeduccionSalud()).add(nz(n.getDeduccionPension()))
                .add(nz(n.getAporteSalud())).add(nz(n.getAportePension())).add(nz(n.getAporteArl()))
                .add(nz(n.getAporteCaja())).add(nz(n.getAporteIcbf())).add(nz(n.getAporteSena()));
        addCreditoNomina(detalles, empresaId, ConceptoContable.SEGURIDAD_SOCIAL_POR_PAGAR,
                "Seguridad social y parafiscales por pagar", seguridadSocial);
        // Otras deducciones del empleado (préstamos/embargos).
        addCreditoNomina(detalles, empresaId, ConceptoContable.OTRAS_DEDUCCIONES_POR_PAGAR,
                "Otras deducciones por pagar", nz(n.getDeduccionOtros()));
        // Prestaciones sociales por pagar.
        addCreditoNomina(detalles, empresaId, ConceptoContable.CESANTIAS_POR_PAGAR,
                "Cesantías por pagar", nz(n.getProvisionCesantias()));
        addCreditoNomina(detalles, empresaId, ConceptoContable.INT_CESANTIAS_POR_PAGAR,
                "Intereses de cesantías por pagar", nz(n.getProvisionIntCesantias()));
        addCreditoNomina(detalles, empresaId, ConceptoContable.PRIMA_POR_PAGAR,
                "Prima de servicios por pagar", nz(n.getProvisionPrima()));
        addCreditoNomina(detalles, empresaId, ConceptoContable.VACACIONES_POR_PAGAR,
                "Vacaciones por pagar", nz(n.getProvisionVacaciones()));

        if (detalles.size() < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La nómina #" + nominaId + " no produjo movimientos contables.");
        }

        java.time.LocalDate fecha = n.getCreatedAt() != null
                ? n.getCreatedAt().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_NOMINA,
                "Nómina #" + nominaId
                        + (n.getEmpleado() != null && n.getEmpleado().getId() != null
                                ? " — empleado " + n.getEmpleado().getId() : ""),
                "NOMINA", nominaId, periodo.getId(), detalles);
    }

    /**
     * Asiento del PAGO de la nómina: DB salarios por pagar (neto) / CR banco o caja.
     * Cierra el pasivo generado al aprobar y refleja de dónde salió el dinero.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoNomina(Long nominaId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("NOMINA_PAGO", nominaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento de pago para la nómina #" + nominaId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        NominaEntity n = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        BigDecimal neto = nz(n.getNetoPagar());
        if (neto.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La nómina #" + nominaId + " no tiene neto a pagar.");
        }

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        // DB · salarios por pagar (cancela el pasivo)
        PlanCuentaEntity salarios = config.resolverCuenta(empresaId, ConceptoContable.SALARIOS_POR_PAGAR);
        detalles.add(linea(salarios.getId(), "Pago de nómina — salarios por pagar", neto, BigDecimal.ZERO));
        // CR · banco o caja de donde salió el dinero
        PlanCuentaEntity origen = resolverCuentaPago(empresaId, n.getMedioPago(), n.getCuentaBancariaId());
        detalles.add(linea(origen.getId(), "Pago de nómina", BigDecimal.ZERO, neto));

        java.time.LocalDate fecha = n.getFechaPago() != null
                ? n.getFechaPago().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_NOMINA_PAGO,
                "Pago nómina #" + nominaId
                        + (n.getEmpleado() != null && n.getEmpleado().getId() != null
                                ? " — empleado " + n.getEmpleado().getId() : ""),
                "NOMINA_PAGO", nominaId, periodo.getId(), detalles);
    }

    /**
     * Asiento del pago de una prestación en EFECTIVO: DB pasivo por pagar (25xx) / CR Caja.
     * (En transferencia el asiento lo genera el egreso de tesorería, no este método.)
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoPrestacion(Long prestacionId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("PRESTACION_PAGO", prestacionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento de pago para la prestación #" + prestacionId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        var p = prestacionRepo.findByIdAndEmpresaId(prestacionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestación no encontrada"));

        BigDecimal valor = nz(p.getValor());
        if (valor.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La prestación #" + prestacionId + " no tiene valor a pagar.");
        }

        Long empId = p.getEmpleado().getId();
        String tipo = p.getTipo();
        String tipoLower = tipo.toLowerCase();

        // Conceptos y saldo provisionado según el tipo de prestación.
        ConceptoContable conceptoPasivo;
        ConceptoContable conceptoGasto;
        BigDecimal provisionado;
        switch (tipo) {
            case "VACACIONES" -> {
                conceptoPasivo = ConceptoContable.VACACIONES_POR_PAGAR;
                conceptoGasto  = ConceptoContable.NOMINA_VACACIONES;
                provisionado   = nz(nominaRepo.sumProvisionVacaciones(empresaId, empId));
            }
            case "CESANTIAS" -> {
                conceptoPasivo = ConceptoContable.CESANTIAS_POR_PAGAR;
                conceptoGasto  = ConceptoContable.NOMINA_CESANTIAS;
                provisionado   = nz(nominaRepo.sumProvisionCesantias(empresaId, empId));
            }
            case "INTERESES_CESANTIAS" -> {
                conceptoPasivo = ConceptoContable.INT_CESANTIAS_POR_PAGAR;
                conceptoGasto  = ConceptoContable.NOMINA_INT_CESANTIAS;
                provisionado   = nz(nominaRepo.sumProvisionIntCesantias(empresaId, empId));
            }
            case "INDEMNIZACION" -> {
                // La indemnización no se provisiona: va 100% a gasto (provisionado = 0).
                conceptoPasivo = ConceptoContable.PRIMA_POR_PAGAR; // no se usa (dbPasivo será 0)
                conceptoGasto  = ConceptoContable.NOMINA_INDEMNIZACION;
                provisionado   = BigDecimal.ZERO;
            }
            default -> { // PRIMA
                conceptoPasivo = ConceptoContable.PRIMA_POR_PAGAR;
                conceptoGasto  = ConceptoContable.NOMINA_PRIMA;
                provisionado   = nz(nominaRepo.sumProvisionPrima(empresaId, empId));
            }
        }

        // Saldo provisionado disponible = provisiones acumuladas − prestaciones ya pagadas del tipo.
        BigDecimal pagadoAntes = nz(prestacionRepo.sumPagadoByEmpleadoTipo(empresaId, empId, tipo, prestacionId));
        BigDecimal disponible = provisionado.subtract(pagadoAntes).max(BigDecimal.ZERO);

        // Se consume primero el pasivo provisionado; el faltante va a gasto.
        BigDecimal dbPasivo = valor.min(disponible);
        BigDecimal dbGasto = valor.subtract(dbPasivo);

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        if (dbPasivo.signum() > 0) {
            PlanCuentaEntity pasivo = config.resolverCuenta(empresaId, conceptoPasivo);
            detalles.add(linea(pasivo.getId(), "Pago " + tipoLower + " — consumo de provisión", dbPasivo, BigDecimal.ZERO));
        }
        if (dbGasto.signum() > 0) {
            PlanCuentaEntity gasto = config.resolverCuenta(empresaId, conceptoGasto);
            detalles.add(linea(gasto.getId(), "Pago " + tipoLower + " — faltante de provisión a gasto", dbGasto, BigDecimal.ZERO));
        }
        // CR · banco o caja de donde sale el dinero
        PlanCuentaEntity origen = resolverCuentaPago(empresaId, p.getMedioPago(), p.getCuentaBancariaId());
        detalles.add(linea(origen.getId(), "Pago de " + tipoLower, BigDecimal.ZERO, valor));

        java.time.LocalDate fecha = p.getFechaPago() != null
                ? p.getFechaPago().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_PRESTACION_PAGO,
                "Pago " + tipoLower + " #" + prestacionId, "PRESTACION_PAGO",
                prestacionId, periodo.getId(), detalles);
    }

    @Override
    @Transactional
    public AsientoContableTableDto generarCierre(Long periodoId, Integer empresaId,
            Integer usuarioId) {
        // Idempotencia: no cerrar dos veces el mismo período.
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("CIERRE", periodoId, empresaId)) {
            return null;
        }

        List<SaldoCuentaDto> saldos = queryRepo.saldosResultadoPorPeriodo(empresaId, periodoId);

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        BigDecimal totalDb = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;

        // Cancela cada cuenta de resultado por su saldo neto.
        for (SaldoCuentaDto s : saldos) {
            BigDecimal netCredito = nz(s.getCredito()).subtract(nz(s.getDebito()));
            if (netCredito.signum() > 0) {
                // Saldo crédito (ingreso) → debitar para cancelar.
                detalles.add(linea(s.getCuentaId(), "Cancelación cuenta de resultado",
                        netCredito, BigDecimal.ZERO));
                totalDb = totalDb.add(netCredito);
            } else if (netCredito.signum() < 0) {
                // Saldo débito (costo/gasto) → acreditar para cancelar.
                BigDecimal netDebito = netCredito.negate();
                detalles.add(linea(s.getCuentaId(), "Cancelación cuenta de resultado",
                        BigDecimal.ZERO, netDebito));
                totalCr = totalCr.add(netDebito);
            }
        }

        if (detalles.isEmpty()) {
            return null; // sin movimientos de resultado en el período
        }

        // Diferencia → utilidad (crédito) o pérdida (débito) del ejercicio.
        BigDecimal utilidad = totalDb.subtract(totalCr);
        PlanCuentaEntity cuentaUtilidad = config.resolverCuenta(empresaId, ConceptoContable.UTILIDAD_EJERCICIO);
        if (utilidad.signum() > 0) {
            detalles.add(linea(cuentaUtilidad.getId(), "Utilidad del ejercicio",
                    BigDecimal.ZERO, utilidad));
        } else if (utilidad.signum() < 0) {
            detalles.add(linea(cuentaUtilidad.getId(), "Pérdida del ejercicio",
                    utilidad.negate(), BigDecimal.ZERO));
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_CIERRE);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                java.time.LocalDate.now(), comprobante,
                "Cierre del período #" + periodoId, "CIERRE", periodoId, periodoId, detalles);
        detalles.forEach(x -> x.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeObligacion(Long obligacionId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("OBLIGACION", obligacionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la obligación #" + obligacionId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        ObligacionFinancieraEntity o = obligacionRepo.findByIdAndEmpresaId(obligacionId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));

        BigDecimal monto = nz(o.getMontoPrincipal());
        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        // El dinero entra a la cuenta bancaria elegida (su cuenta contable real).
        PlanCuentaEntity banco = resolverCuentaPago(empresaId, null, o.getCuentaBancariaId());
        PlanCuentaEntity obligacion = config.resolverCuenta(empresaId, ConceptoContable.OBLIGACIONES_FINANCIERAS);
        detalles.add(linea(banco.getId(), "Desembolso préstamo " + o.getEntidad(), monto, BigDecimal.ZERO));
        detalles.add(linea(obligacion.getId(), "Obligación financiera " + o.getEntidad(),
                BigDecimal.ZERO, monto, o.getTerceroId()));

        return persistir(empresaId, usuarioId, o.getFechaDesembolso(), PREFIX_OBLIGACION,
                "Desembolso obligación #" + obligacionId + " — " + o.getEntidad(),
                "OBLIGACION", obligacionId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoCuota(Long cuotaId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("CUOTA_OBLIGACION", cuotaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la cuota #" + cuotaId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        CuotaAmortizacionEntity cuota = cuotaRepo.findById(cuotaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuota no encontrada"));
        ObligacionFinancieraEntity o = cuota.getObligacion();
        Long terceroId = o != null ? o.getTerceroId() : null;

        BigDecimal capital = nz(cuota.getAbonoCapital());
        BigDecimal interes = nz(cuota.getInteres());
        BigDecimal total   = nz(cuota.getCuota());

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        PlanCuentaEntity obligacion = config.resolverCuenta(empresaId, ConceptoContable.OBLIGACIONES_FINANCIERAS);
        detalles.add(linea(obligacion.getId(), "Abono a capital", capital, BigDecimal.ZERO, terceroId));
        if (interes.signum() > 0) {
            PlanCuentaEntity gastoFin = config.resolverCuenta(empresaId, ConceptoContable.GASTOS_FINANCIEROS);
            detalles.add(linea(gastoFin.getId(), "Intereses del préstamo", interes, BigDecimal.ZERO));
        }
        // El pago sale del activo elegido al pagar la cuota (caja o cualquier cuenta
        // bancaria), no necesariamente la del desembolso. Se relee de la cuota; si no
        // se registró origen, cae a la cuenta bancaria del préstamo (comportamiento previo).
        Long cuentaOrigenId = cuota.getCuentaBancariaIdPago() != null
                ? cuota.getCuentaBancariaIdPago()
                : (o != null ? o.getCuentaBancariaId() : null);
        PlanCuentaEntity banco = resolverCuentaPago(empresaId, cuota.getMetodoPago(), cuentaOrigenId);
        detalles.add(linea(banco.getId(), "Pago cuota préstamo", BigDecimal.ZERO, total));

        java.time.LocalDate fecha = cuota.getFechaPago() != null ? cuota.getFechaPago() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_CUOTA,
                "Pago cuota #" + cuota.getNumeroCuota()
                        + (o != null ? " — obligación #" + o.getId() : ""),
                "CUOTA_OBLIGACION", cuotaId, periodo.getId(), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeMovimientoCaja(Long movimientoId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("MOVIMIENTO_CAJA", movimientoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el movimiento de caja #" + movimientoId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        com.cloud_technological.aura_pos.entity.MovimientoCajaEntity mov = movimientoCajaRepo.findById(movimientoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento de caja no encontrado"));
        if (mov.getConceptoCajaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El movimiento de caja #" + movimientoId + " no tiene concepto contable, no se puede contabilizar.");
        }

        com.cloud_technological.aura_pos.entity.ConceptoCajaEntity concepto =
                conceptoCajaRepo.findByIdAndEmpresaId(mov.getConceptoCajaId(), empresaId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "El concepto de caja del movimiento no existe."));
        PlanCuentaEntity cuentaConcepto = planRepo.findByIdAndEmpresaId(concepto.getCuentaContableId(), empresaId)
                .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "La cuenta contable del concepto '" + concepto.getNombre() + "' no existe o está inactiva."));
        // El dinero entra/sale de Caja (efectivo) o Bancos (transferencia).
        PlanCuentaEntity cuentaCaja = resolverCuentaPago(empresaId, mov.getMetodoPago(), null);

        BigDecimal monto = nz(mov.getMonto());
        boolean ingreso = "INGRESO".equalsIgnoreCase(mov.getTipo());
        String desc = mov.getConcepto() != null ? mov.getConcepto() : concepto.getNombre();

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        if (ingreso) {
            detalles.add(linea(cuentaCaja.getId(), desc, monto, BigDecimal.ZERO));
            detalles.add(linea(cuentaConcepto.getId(), desc, BigDecimal.ZERO, monto));
        } else {
            detalles.add(linea(cuentaConcepto.getId(), desc, monto, BigDecimal.ZERO));
            detalles.add(linea(cuentaCaja.getId(), desc, BigDecimal.ZERO, monto));
        }

        String tipoComprobante = ingreso ? "RC" : "CE";
        java.time.LocalDate fecha = mov.getCreatedAt() != null
                ? mov.getCreatedAt().toLocalDate() : java.time.LocalDate.now();

        // Un solo número por hecho: el asiento reutiliza el número del comprobante
        // de caja del mismo movimiento (origen MANUAL). Si no lo encuentra, genera uno.
        String numero = comprobanteCajaRepo
                .findFirstByEmpresaIdAndOrigenAndOrigenId(empresaId, "MANUAL", movimientoId)
                .map(com.cloud_technological.aura_pos.entity.ComprobanteCajaEntity::getNumeroComprobante)
                .orElse(null);

        return persistirComprobante(empresaId, usuarioId, fecha, tipoComprobante, numero,
                (ingreso ? "Ingreso de caja — " : "Egreso de caja — ") + concepto.getNombre(),
                "MOVIMIENTO_CAJA", movimientoId, periodo.getId(), tipoComprobante, detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeTesoreria(Long movimientoId, Integer empresaId,
            Integer usuarioId) {
        if (asientoRepo.existsByTipoOrigenAndOrigenIdAndEmpresaId("TESORERIA", movimientoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el movimiento de tesorería #" + movimientoId);
        }
        PeriodoContableEntity periodo = periodoAbierto(empresaId);

        TesoreriaMovimientoEntity mov = tesoreriaMovRepo.findByIdAndEmpresaId(movimientoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Movimiento de tesorería no encontrado"));

        if (Boolean.TRUE.equals(mov.getAnulado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El movimiento de tesorería #" + movimientoId + " está anulado, no se contabiliza.");
        }
        if (mov.getContrapartidaCuentaId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El movimiento de tesorería #" + movimientoId
                            + " no tiene cuenta de contrapartida, no se puede contabilizar.");
        }

        // Lado del banco: cuenta contable de la cuenta bancaria (fallback Bancos genérico).
        PlanCuentaEntity banco = resolverCuentaPago(empresaId, null, mov.getCuentaBancariaId());
        // Lado de la contrapartida: la cuenta elegida en el movimiento.
        PlanCuentaEntity contrapartida = planRepo.findByIdAndEmpresaId(mov.getContrapartidaCuentaId(), empresaId)
                .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "La cuenta de contrapartida del movimiento de tesorería no existe o está inactiva."));

        BigDecimal monto = nz(mov.getMonto());
        String desc = mov.getConcepto();
        boolean entrada = "RECAUDO".equals(mov.getTipo())
                || "TRANSFERENCIA_ENTRADA".equals(mov.getTipo());

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        if (entrada) {
            // Entra dinero al banco: DB Banco · CR contrapartida.
            detalles.add(linea(banco.getId(), desc, monto, BigDecimal.ZERO));
            detalles.add(linea(contrapartida.getId(), desc, BigDecimal.ZERO, monto));
        } else {
            // Sale dinero del banco: DB contrapartida · CR Banco.
            detalles.add(linea(contrapartida.getId(), desc, monto, BigDecimal.ZERO));
            detalles.add(linea(banco.getId(), desc, BigDecimal.ZERO, monto));
        }

        String tipoComprobante = entrada ? "RC" : "CE";
        java.time.LocalDate fecha = mov.getFecha() != null ? mov.getFecha() : java.time.LocalDate.now();

        return persistirComprobante(empresaId, usuarioId, fecha, PREFIX_TESORERIA, null,
                (entrada ? "Recaudo tesorería — " : "Egreso tesorería — ") + desc,
                "TESORERIA", movimientoId, periodo.getId(), tipoComprobante, detalles);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Igual que {@link #persistir} pero marca el {@code tipoComprobante} (RC/CE/CD)
     * en la cabecera, para los comprobantes de caja/manuales.
     */
    private AsientoContableTableDto persistirComprobante(Integer empresaId, Integer usuarioId,
            java.time.LocalDate fecha, String prefijo, String numeroPreset, String descripcion,
            String tipoOrigen, Long origenId, Long periodoId, String tipoComprobante,
            List<AsientoDetalleEntity> detalles) {
        // Si viene un número pre-asignado (p.ej. reutiliza el del comprobante de caja),
        // se respeta; si no, se toma el siguiente de la serie unificada del prefijo.
        String comprobante = (numeroPreset != null && !numeroPreset.isBlank())
                ? numeroPreset : queryRepo.siguienteNumeroComprobante(empresaId, prefijo);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId, fecha, comprobante,
                descripcion, tipoOrigen, origenId, periodoId, detalles);
        asiento.setTipoComprobante(tipoComprobante);
        detalles.forEach(d -> d.setAsiento(asiento));
        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    /** Resuelve Caja vs Bancos según el método de pago (efectivo → Caja). */
    private ConceptoContable conceptoPago(String metodoPago) {
        if (metodoPago != null && metodoPago.toUpperCase().contains("EFECTIVO")) {
            return ConceptoContable.CAJA;
        }
        return ConceptoContable.BANCOS;
    }

    /**
     * Resuelve la cuenta contable de un movimiento de dinero, en este orden:
     * (1) la cuenta contable de la cuenta bancaria del pago, si la tiene;
     * (2) fallback por método de pago (efectivo→Caja, resto→Bancos genérico).
     * Aquí se insertará luego la parametrización de formas de pago (Pieza 2).
     */
    private PlanCuentaEntity resolverCuentaPago(Integer empresaId, String metodoPago, Long cuentaBancariaId) {
        if (cuentaBancariaId != null) {
            CuentaBancariaEntity cb = cuentaBancariaRepo.findByIdAndEmpresaId(cuentaBancariaId, empresaId)
                    .orElse(null);
            if (cb != null && cb.getCuentaContableId() != null) {
                PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cb.getCuentaContableId(), empresaId)
                        .filter(c -> Boolean.TRUE.equals(c.getActiva()))
                        .orElse(null);
                if (cuenta != null) {
                    return cuenta;
                }
            }
        }
        return config.resolverCuenta(empresaId, conceptoPago(metodoPago));
    }

    private PeriodoContableEntity periodoAbierto(Integer empresaId) {
        return periodoRepo.findByEmpresaIdAndEstado(empresaId, "ABIERTO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay un período contable ABIERTO. Abra un período antes de generar asientos."));
    }

    /** Construye, valida, persiste el asiento y devuelve su DTO con detalles. */
    private AsientoContableTableDto persistir(Integer empresaId, Integer usuarioId,
            java.time.LocalDate fecha, String prefijo, String descripcion,
            String tipoOrigen, Long origenId, Long periodoId, List<AsientoDetalleEntity> detalles) {
        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, prefijo);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId, fecha, comprobante,
                descripcion, tipoOrigen, origenId, periodoId, detalles);
        detalles.forEach(d -> d.setAsiento(asiento));
        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    private AsientoDetalleEntity linea(Long cuentaId, String desc,
            BigDecimal debito, BigDecimal credito) {
        return linea(cuentaId, desc, debito, credito, null);
    }

    /** Agrega una línea débito de nómina resolviendo la cuenta del concepto (solo si monto {@code > 0}). */
    private void addDebitoNomina(List<AsientoDetalleEntity> detalles, Integer empresaId,
            ConceptoContable concepto, String desc, BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) return;
        PlanCuentaEntity cuenta = config.resolverCuenta(empresaId, concepto);
        detalles.add(linea(cuenta.getId(), desc, monto, BigDecimal.ZERO));
    }

    /** Agrega una línea crédito de nómina resolviendo la cuenta del concepto (solo si monto {@code > 0}). */
    private void addCreditoNomina(List<AsientoDetalleEntity> detalles, Integer empresaId,
            ConceptoContable concepto, String desc, BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) return;
        PlanCuentaEntity cuenta = config.resolverCuenta(empresaId, concepto);
        detalles.add(linea(cuenta.getId(), desc, BigDecimal.ZERO, monto));
    }

    private AsientoDetalleEntity linea(Long cuentaId, String desc,
            BigDecimal debito, BigDecimal credito, Long terceroId) {
        return AsientoDetalleEntity.builder()
                .cuentaId(cuentaId)
                .descripcion(desc)
                .debito(debito)
                .credito(credito)
                .terceroId(terceroId)
                .build();
    }

    private AsientoContableEntity buildAsiento(Integer empresaId, Integer usuarioId,
            java.time.LocalDate fecha, String comprobante, String descripcion,
            String tipoOrigen, Long origenId, Long periodoId,
            List<AsientoDetalleEntity> detalles) {

        BigDecimal[] totales = AsientoBalanceValidator.sumarYValidar(detalles);
        BigDecimal totalDb = totales[0];
        BigDecimal totalCr = totales[1];

        return AsientoContableEntity.builder()
                .empresaId(empresaId)
                .fecha(fecha)
                .descripcion(descripcion)
                .tipoOrigen(tipoOrigen)
                .origenId(origenId)
                .periodoContableId(periodoId)
                .numeroComprobante(comprobante)
                .totalDebito(totalDb)
                .totalCredito(totalCr)
                .estado("CONTABILIZADO")
                .usuarioId(usuarioId)
                .detalles(detalles)
                .build();
    }

    private AsientoContableTableDto toDto(AsientoContableEntity e) {
        AsientoContableTableDto dto = new AsientoContableTableDto();
        dto.setId(e.getId());
        dto.setNumeroComprobante(e.getNumeroComprobante());
        dto.setFecha(e.getFecha() != null ? e.getFecha().toString() : null);
        dto.setDescripcion(e.getDescripcion());
        dto.setTipoOrigen(e.getTipoOrigen());
        dto.setOrigenId(e.getOrigenId());
        dto.setTotalDebito(e.getTotalDebito());
        dto.setTotalCredito(e.getTotalCredito());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return dto;
    }
}

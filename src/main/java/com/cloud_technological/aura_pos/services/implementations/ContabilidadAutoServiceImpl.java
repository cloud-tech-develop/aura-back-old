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
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
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
    private static final String PREFIX_CIERRE     = "CE";
    private static final String PREFIX_OBLIGACION = "OB";
    private static final String PREFIX_CUOTA      = "CU";

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
            ConceptoContable concepto = pago.getCuentaBancariaId() != null
                    ? ConceptoContable.BANCOS : ConceptoContable.CAJA;
            PlanCuentaEntity cuenta = config.resolverCuenta(empresaId, concepto);
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
            ConceptoContable concepto = pago.getCuentaBancariaId() != null
                    ? ConceptoContable.BANCOS : ConceptoContable.CAJA;
            PlanCuentaEntity cuenta = config.resolverCuenta(empresaId, concepto);
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
        String etiqueta = "VENTA".equals(origenTipo) ? "venta" : "compra";
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
        BigDecimal carteraAfectada = Boolean.TRUE.equals(dev.getAfectoCartera())
                ? nz(dev.getMontoCarteraAfectado()) : BigDecimal.ZERO;
        BigDecimal reembolso = total.subtract(carteraAfectada);            // devuelto en caja
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

        // ── Reingreso de inventario / reversa de costo (par balanceado) ───────
        if (costoDevuelto.signum() > 0) {
            PlanCuentaEntity inv = config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO);
            PlanCuentaEntity costo = config.resolverCuenta(empresaId, ConceptoContable.COSTO_VENTAS);
            detalles.add(linea(inv.getId(), "Reingreso inventario", costoDevuelto, BigDecimal.ZERO));
            detalles.add(linea(costo.getId(), "Reversa costo de venta", BigDecimal.ZERO, costoDevuelto));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La devolución #" + devolucionId + " no produjo movimientos contables.");
        }

        java.time.LocalDate fecha = dev.getCreatedAt() != null
                ? dev.getCreatedAt().toLocalDate() : java.time.LocalDate.now();
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
        PlanCuentaEntity caja = config.resolverCuenta(empresaId, conceptoPago(abono.getMetodoPago()));
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
        PlanCuentaEntity caja = config.resolverCuenta(empresaId, conceptoPago(abono.getMetodoPago()));
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

        BigDecimal devengado    = nz(n.getTotalDevengado());
        BigDecimal deducciones  = nz(n.getTotalDeducciones());
        BigDecimal neto         = nz(n.getNetoPagar());
        BigDecimal aportes = nz(n.getAporteSalud()).add(nz(n.getAportePension()))
                .add(nz(n.getAporteArl())).add(nz(n.getAporteCaja()))
                .add(nz(n.getAporteIcbf())).add(nz(n.getAporteSena()));
        BigDecimal provisiones = nz(n.getProvisionPrima()).add(nz(n.getProvisionCesantias()))
                .add(nz(n.getProvisionIntCesantias())).add(nz(n.getProvisionVacaciones()));

        BigDecimal gastoTotal = devengado.add(aportes).add(provisiones);

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // DB · gasto de personal (devengado + aportes patronales + provisiones)
        PlanCuentaEntity gasto = config.resolverCuenta(empresaId, ConceptoContable.GASTOS_PERSONAL);
        detalles.add(linea(gasto.getId(), "Gasto de nómina (devengado + aportes + provisiones)",
                gastoTotal, BigDecimal.ZERO));

        // CR · salarios netos por pagar
        if (neto.signum() > 0) {
            PlanCuentaEntity salarios = config.resolverCuenta(empresaId, ConceptoContable.SALARIOS_POR_PAGAR);
            detalles.add(linea(salarios.getId(), "Salarios netos por pagar", BigDecimal.ZERO, neto));
        }
        // CR · deducciones del empleado por pagar (salud/pensión/otros)
        if (deducciones.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.DEDUCCIONES_NOMINA_POR_PAGAR);
            detalles.add(linea(c.getId(), "Deducciones nómina por pagar", BigDecimal.ZERO, deducciones));
        }
        // CR · aportes patronales por pagar
        if (aportes.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.APORTES_NOMINA_POR_PAGAR);
            detalles.add(linea(c.getId(), "Aportes patronales por pagar", BigDecimal.ZERO, aportes));
        }
        // CR · provisiones de prestaciones por pagar
        if (provisiones.signum() > 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.PROVISIONES_NOMINA_POR_PAGAR);
            detalles.add(linea(c.getId(), "Provisiones prestaciones por pagar", BigDecimal.ZERO, provisiones));
        }

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
        PlanCuentaEntity banco = config.resolverCuenta(empresaId, ConceptoContable.BANCOS);
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
        PlanCuentaEntity banco = config.resolverCuenta(empresaId, ConceptoContable.BANCOS);
        detalles.add(linea(banco.getId(), "Pago cuota préstamo", BigDecimal.ZERO, total));

        java.time.LocalDate fecha = cuota.getFechaPago() != null ? cuota.getFechaPago() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_CUOTA,
                "Pago cuota #" + cuota.getNumeroCuota()
                        + (o != null ? " — obligación #" + o.getId() : ""),
                "CUOTA_OBLIGACION", cuotaId, periodo.getId(), detalles);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Resuelve Caja vs Bancos según el método de pago (efectivo → Caja). */
    private ConceptoContable conceptoPago(String metodoPago) {
        if (metodoPago != null && metodoPago.toUpperCase().contains("EFECTIVO")) {
            return ConceptoContable.CAJA;
        }
        return ConceptoContable.BANCOS;
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

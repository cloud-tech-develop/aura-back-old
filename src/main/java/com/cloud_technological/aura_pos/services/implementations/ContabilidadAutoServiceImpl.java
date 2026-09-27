package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.utils.Documentos;
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
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_pago.VentaPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.utils.AsientoBalanceValidator;
import com.cloud_technological.aura_pos.utils.MediosPago;

@Service
public class ContabilidadAutoServiceImpl implements ContabilidadAutoService {

    @Autowired private VentaJPARepository ventaRepo;
    @Autowired private CompraJPARepository compraRepo;
    @Autowired private AsientoContableJPARepository asientoRepo;
    @Autowired private AsientoContableQueryRepository queryRepo;
    @Autowired private PeriodoContableResolver periodoResolver;
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

    @Autowired
    private com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository nominaDetalleRepo;
    @Autowired private ObligacionFinancieraJPARepository obligacionRepo;
    @Autowired private CuotaAmortizacionJPARepository cuotaRepo;
    @Autowired private CuentaBancariaJPARepository cuentaBancariaRepo;
    @Autowired private TesoreriaMovimientoJPARepository tesoreriaMovRepo;
    @Autowired private PlanCuentaJPARepository planRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.movimiento_caja.MovimientoCajaJPARepository movimientoCajaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.conceptos_caja.ConceptoCajaJPARepository conceptoCajaRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.comprobante_caja.ComprobanteCajaJPARepository comprobanteCajaRepo;
    @Autowired private ConfiguracionContableService config;
    @Autowired private com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaPago resolucionCuentaPago;
    @Autowired private com.cloud_technological.aura_pos.contabilidad.application.port.ConfigContabilizacion configContabilizacion;
    @Autowired private com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto resolucionCuentaProducto;
    @Autowired private com.cloud_technological.aura_pos.repositories.detalle_compras.CompraDetalleJPARepository compraDetalleRepo;
    @Autowired private com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionImpuesto resolucionImpuesto;
    @Autowired private com.cloud_technological.aura_pos.repositories.merma_detalle.MermaDetalleJPARepository mermaDetalleRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteJPARepository consumoComponenteRepo;
    @Autowired private com.cloud_technological.aura_pos.repositories.asistencia.AsistenciaNovedadNominaJPARepository novedadNominaRepo;

    // ────────────────────────────────────────────────────────────────────────
    //  Prefijos de comprobante (Regla 1)
    //  VT = Venta  |  CO = Compra  |  CD = Comprobante de Diario (manual)
    // ────────────────────────────────────────────────────────────────────────
    /** Estados de un asiento: solo el CONTABILIZADO representa al documento. */
    private static final String ESTADO_CONTABILIZADO = "CONTABILIZADO";
    private static final String ESTADO_ANULADO       = "ANULADO";

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
    private static final String PREFIX_SOBREGIRO  = "SB";
    private static final String PREFIX_OBLIGACION = "OB";
    private static final String PREFIX_CUOTA      = "CU";
    private static final String PREFIX_TESORERIA  = "TS";

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeVenta(Long ventaId, Integer empresaId,
            Integer usuarioId) {
        // Idempotencia: no duplicar si ya existe
        if (yaContabilizado("VENTA", ventaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la venta #" + ventaId);
        }

        VentaEntity venta = ventaRepo.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Venta no encontrada"));

        // El asiento pertenece al mes de la venta, no al período que esté abierto (V171).
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId,
                venta.getFechaEmision() != null ? venta.getFechaEmision().toLocalDate() : null);

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
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                venta.getFechaEmision().toLocalDate(), comprobante,
                "Venta " + Documentos.numeroVenta(venta),
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
        if (yaContabilizado("COMPRA", compraId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la compra #" + compraId);
        }

        CompraEntity compra = compraRepo.findByIdAndEmpresaId(compraId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Compra no encontrada"));

        // El asiento pertenece al mes de la compra (V171).
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId,
                compra.getFecha() != null ? compra.getFecha().toLocalDate() : null);

        BigDecimal subtotal   = nz(compra.getSubtotal());
        BigDecimal descuento  = nz(compra.getDescuentoTotal());
        BigDecimal fletes     = nz(compra.getFletes());
        BigDecimal impuestos  = nz(compra.getImpuestosTotal());            // IVA descontable
        BigDecimal netaAPagar = compra.getNetaAPagar() != null
                ? compra.getNetaAPagar() : nz(compra.getTotal());
        // Costo capitalizado en inventario: subtotal neto de descuento + fletes
        BigDecimal costoInventario = subtotal.subtract(descuento).add(fletes);
        Long proveedorId = compra.getProveedor() != null ? compra.getProveedor().getId() : null;

        // Una nota crédito de compra se guarda como el documento negativo de la
        // factura que corrige. El asiento es el mismo, con cada línea del lado
        // contrario — eso lo resuelve lineaFirmada() a partir del signo, así que
        // aquí solo cambia el texto del comprobante.
        boolean esNotaCredito = "NOTA_CREDITO".equalsIgnoreCase(compra.getTipoDocumento());
        String etiqueta = esNotaCredito ? "Nota crédito compra" : "Compra";

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITO · destino de la compra ─────────────────────────────────────
        // Prioridad: cuenta destino explícita de la compra (E2) → agrupado por
        // categoría contable de cada producto (E4) → inventario por defecto.
        // El delta por fletes/descuento de cabecera se ajusta al grupo mayor.
        if (costoInventario.signum() != 0) {
            if (compra.getCuentaContableId() != null) {
                detalles.add(lineaFirmada(compra.getCuentaContableId(),
                        etiqueta + " — destino contable", costoInventario, true, null));
            } else {
                java.util.Map<Long, BigDecimal> porCuenta = new java.util.LinkedHashMap<>();
                for (var det : compraDetalleRepo.findByCompraId(compraId)) {
                    Long productoId = det.getProducto() != null ? det.getProducto().getId() : null;
                    Long cuentaId = resolucionCuentaProducto.resolver(productoId, empresaId)
                            .inventarioId();
                    BigDecimal base = nz(det.getSubtotalLinea()).subtract(nz(det.getDescuentoValor()));
                    porCuenta.merge(cuentaId, base, BigDecimal::add);
                }
                if (porCuenta.isEmpty()) {
                    porCuenta.put(config.resolverCuenta(empresaId, ConceptoContable.INVENTARIO).getId(),
                            costoInventario);
                } else {
                    BigDecimal suma = porCuenta.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal delta = costoInventario.subtract(suma);
                    if (delta.signum() != 0) {
                        // Con importes negativos (nota crédito) el grupo "mayor"
                        // es el de mayor valor absoluto, no el aritmético.
                        Long mayor = porCuenta.entrySet().stream()
                                .max(java.util.Comparator.comparing(e -> e.getValue().abs()))
                                .map(java.util.Map.Entry::getKey).orElseThrow();
                        porCuenta.merge(mayor, delta, BigDecimal::add);
                    }
                }
                porCuenta.forEach((cuentaId, monto) ->
                        detalles.add(lineaFirmada(cuentaId, "Inventario " + etiqueta.toLowerCase(),
                                monto, true, null)));
            }
        }

        // ── DÉBITO · IVA descontable agrupado por cuenta del impuesto (E5) ────
        if (impuestos.signum() != 0) {
            java.util.Map<Long, BigDecimal> ivaPorCuenta = new java.util.LinkedHashMap<>();
            for (var det : compraDetalleRepo.findByCompraId(compraId)) {
                BigDecimal ivaLinea = nz(det.getImpuestoValor());
                if (ivaLinea.signum() == 0) {
                    continue;
                }
                Long productoId = det.getProducto() != null ? det.getProducto().getId() : null;
                ivaPorCuenta.merge(resolucionImpuesto.resolverDescontable(productoId, empresaId),
                        ivaLinea, BigDecimal::add);
            }
            if (ivaPorCuenta.isEmpty()) {
                ivaPorCuenta.put(config.resolverCuenta(empresaId, ConceptoContable.IVA_DESCONTABLE).getId(),
                        impuestos);
            } else {
                BigDecimal suma = ivaPorCuenta.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal delta = impuestos.subtract(suma);
                if (delta.signum() != 0) {
                    // Con importes negativos (nota crédito) el grupo "mayor" es
                    // el de mayor valor absoluto, no el aritméticamente mayor.
                    Long mayor = ivaPorCuenta.entrySet().stream()
                            .max(java.util.Comparator.comparing(e -> e.getValue().abs()))
                            .map(java.util.Map.Entry::getKey).orElseThrow();
                    ivaPorCuenta.merge(mayor, delta, BigDecimal::add);
                }
            }
            ivaPorCuenta.forEach((cuentaId, monto) ->
                    detalles.add(lineaFirmada(cuentaId, "IVA descontable", monto, true, null)));
        }

        // ── CRÉDITO · retenciones practicadas al proveedor ────────────────────
        BigDecimal retefuente = nz(compra.getRetefuenteValor());
        if (retefuente.signum() != 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEFUENTE_PRACTICADA);
            detalles.add(lineaFirmada(c.getId(), "Retención en la fuente", retefuente, false, proveedorId));
        }
        BigDecimal reteiva = nz(compra.getReteivaValor());
        if (reteiva.signum() != 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEIVA_PRACTICADA);
            detalles.add(lineaFirmada(c.getId(), "ReteIVA", reteiva, false, proveedorId));
        }
        BigDecimal reteica = nz(compra.getReteicaValor());
        if (reteica.signum() != 0) {
            PlanCuentaEntity c = config.resolverCuenta(empresaId, ConceptoContable.RETEICA_PRACTICADA);
            detalles.add(lineaFirmada(c.getId(), "ReteICA", reteica, false, proveedorId));
        }

        // ── CRÉDITO · pago de contado (caja/bancos) ───────────────────────────
        BigDecimal pagado = BigDecimal.ZERO;
        for (CompraPagoEntity pago : compraPagoRepo.findByCompraIdAndActivoTrue(compraId)) {
            BigDecimal monto = nz(pago.getMonto());
            // En una nota crédito el movimiento está en negativo — es plata que
            // vuelve — y lineaFirmada lo manda al débito de caja/bancos.
            if (monto.signum() == 0) continue;
            PlanCuentaEntity cuenta = resolverCuentaPago(empresaId, pago.getMetodoPago(),
                    pago.getCuentaBancariaId(), pago.getCuentaContableId());
            detalles.add(lineaFirmada(cuenta.getId(),
                    (monto.signum() < 0 ? "Devolución del proveedor (" : "Pago compra (")
                            + pago.getMetodoPago() + ")", monto, false, null));
            pagado = pagado.add(monto);
        }

        // ── CRÉDITO · saldo a crédito al proveedor ────────────────────────────
        // En la nota crédito el saldo sale negativo y la línea cae al débito:
        // baja la deuda con el proveedor (o la deja a favor si ya no había).
        BigDecimal saldoProveedor = netaAPagar.subtract(pagado);
        if (saldoProveedor.signum() != 0) {
            PlanCuentaEntity prov = config.resolverCuenta(empresaId, ConceptoContable.PROVEEDORES);
            detalles.add(lineaFirmada(prov.getId(), "Cuenta por pagar proveedor",
                    saldoProveedor, false, proveedorId));
        }

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La compra #" + compraId + " no produjo movimientos contables.");
        }

        // El centro de costo (E2) y el proyecto/frente (E7) de la compra se
        // propagan a TODAS las líneas.
        if (compra.getCentroCostoId() != null) {
            detalles.forEach(d -> d.setCentroCostoId(compra.getCentroCostoId()));
        }
        if (compra.getProyectoId() != null) {
            detalles.forEach(d -> {
                d.setProyectoId(compra.getProyectoId());
                d.setFrenteId(compra.getFrenteId());
            });
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_COMPRA);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                compra.getFecha().toLocalDate(), comprobante,
                etiqueta + " #" + compraId + (compra.getNumeroCompra() != null
                        ? " — " + compra.getNumeroCompra() : "")
                        + (esNotaCredito && compra.getCompraOrigenId() != null
                                ? " (corrige compra #" + compra.getCompraOrigenId() + ")" : ""),
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

        // Asiento VIGENTE del documento. Si no hay (nunca se generó, o ya se
        // reversó antes), no hay nada que reversar → no-op.
        //
        // Un documento puede editarse varias veces y cada edición reversa el
        // asiento vigente y genera uno nuevo, así que la idempotencia no puede
        // anclarse al par (ANULACION_X, origenId): bloquearía la segunda edición.
        AsientoContableEntity original = asientoVigente(origenTipo, origenId, empresaId)
                .orElse(null);
        if (original == null) {
            // Borrador de un documento que ya no existe: nunca tocó saldos, así
            // que no lleva contraasiento, pero si queda vivo alguien puede
            // contabilizarlo después y meter a los libros un pago anulado.
            asientoRepo.findFirstByTipoOrigenAndOrigenIdAndEmpresaIdAndEstado(
                            origenTipo, origenId, empresaId, "BORRADOR")
                    .ifPresent(borrador -> {
                        borrador.setEstado(ESTADO_ANULADO);
                        asientoRepo.save(borrador);
                    });
            return null;
        }

        // El contraasiento lleva la fecha de hoy y cae en el período de hoy, no en el
        // del asiento original: reversar un documento de un mes cerrado no reabre ese mes.
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, java.time.LocalDate.now());

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

        // El original se queda CONTABILIZADO: es el contraasiento el que lo
        // neutraliza. Marcarlo ANULADO lo sacaba de los reportes (que solo leen
        // CONTABILIZADO) y dejaba viva únicamente la reversa: anular un gasto de
        // 100 lo mostraba en -100. Que ya no representa al documento lo sabe
        // asientoVigente() contando las reversas.

        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeDevolucion(Long devolucionId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("DEVOLUCION", devolucionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la devolución #" + devolucionId);
        }

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

        // ── DÉBITO · reversa del ingreso (cuenta de devolución por categoría,
        //    E4: proporcional por línea; delta de redondeo al grupo mayor) ────
        if (base.signum() != 0) {
            java.util.Map<Long, BigDecimal> porCuenta = new java.util.LinkedHashMap<>();
            for (DevolucionDetalleEntity d : dets) {
                Long productoId = d.getProducto() != null ? d.getProducto().getId() : null;
                Long cuentaId = resolucionCuentaProducto.resolver(productoId, empresaId)
                        .devolucionId();
                BigDecimal baseLinea = nz(d.getSubtotalLinea()).subtract(nz(d.getImpuestoValor()));
                porCuenta.merge(cuentaId, baseLinea, BigDecimal::add);
            }
            if (porCuenta.isEmpty()) {
                porCuenta.put(config.resolverCuenta(empresaId, ConceptoContable.INGRESOS_VENTAS).getId(), base);
            } else {
                BigDecimal suma = porCuenta.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal delta = base.subtract(suma);
                if (delta.signum() != 0) {
                    Long mayor = porCuenta.entrySet().stream()
                            .max(java.util.Map.Entry.comparingByValue())
                            .map(java.util.Map.Entry::getKey).orElseThrow();
                    porCuenta.merge(mayor, delta, BigDecimal::add);
                }
            }
            porCuenta.forEach((cuentaId, monto) ->
                    detalles.add(linea(cuentaId, "Devolución en ventas", monto, BigDecimal.ZERO)));
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
        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_DEVOLUCION);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId, fecha, comprobante,
                "Devolución #" + devolucionId
                        + (dev.getVenta() != null ? " — venta " + Documentos.numeroVenta(dev.getVenta()) : ""),
                "DEVOLUCION", devolucionId, periodoResolver.resolverId(empresaId, fecha), detalles);
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
        if (yaContabilizado("ABONO_COBRAR", abonoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el abono de cobro #" + abonoId);
        }

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
                "Recaudo cartera — abono #" + abonoId, "ABONO_COBRAR", abonoId,
                periodoResolver.resolverId(empresaId, java.time.LocalDate.now()), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeAbonoPago(Long abonoId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("ABONO_PAGAR", abonoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el abono de pago #" + abonoId);
        }

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
                "Pago a proveedor — abono #" + abonoId, "ABONO_PAGAR", abonoId,
                periodoResolver.resolverId(empresaId, java.time.LocalDate.now()), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeGasto(Long gastoId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("GASTO", gastoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el gasto #" + gastoId);
        }

        GastoEntity gasto = gastoRepo.findByIdAndEmpresaId(gastoId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));

        BigDecimal monto      = nz(gasto.getMonto());           // valor del gasto (base)
        BigDecimal iva        = nz(gasto.getValorIva());        // IVA descontable
        BigDecimal retefuente = nz(gasto.getValorRetefuente());
        BigDecimal reteica    = nz(gasto.getValorReteica());
        BigDecimal neto       = monto.add(iva).subtract(retefuente).subtract(reteica); // a pagar
        Long terceroId = gasto.getTerceroId();

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // DB · cuenta de gasto (la que eligió el usuario; fallback a gasto
        // general). E6: si es DIFERIDO, el pago va al activo 1705 y el gasto
        // real lo reconoce mes a mes el job de amortización.
        Long cuentaGastoId = Boolean.TRUE.equals(gasto.getEsDiferido())
                ? config.resolverCuenta(empresaId, ConceptoContable.GASTOS_PAGADOS_ANTICIPADO).getId()
                : (gasto.getCuentaContableId() != null
                        ? gasto.getCuentaContableId()
                        : config.resolverCuenta(empresaId, ConceptoContable.GASTO_GENERAL).getId());
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
        // CR · de dónde salió la plata (V142).
        //
        // Antes esta línea acreditaba CAJA siempre, sin mirar cómo se pagó: un
        // gasto por transferencia dejaba la caja contable en negativo y el banco
        // intacto. Ahora manda la cuenta que resolvió el origen de fondos; y si
        // el gasto es a crédito no hay salida de dinero, sino deuda al tercero.
        if (neto.signum() > 0) {
            if (MediosPago.CREDITO.equalsIgnoreCase(gasto.getFormaPago())) {
                PlanCuentaEntity prov = config.resolverCuenta(empresaId, ConceptoContable.PROVEEDORES);
                detalles.add(linea(prov.getId(), "Gasto por pagar", BigDecimal.ZERO, neto, terceroId));
            } else {
                Long cuentaPagoId = gasto.getCuentaPagoId() != null
                        ? gasto.getCuentaPagoId()
                        : resolucionCuentaPago.resolver(empresaId, gasto.getMetodoPago(),
                                gasto.getCuentaBancariaId());
                detalles.add(linea(cuentaPagoId, "Pago gasto", BigDecimal.ZERO, neto));
            }
        }

        // Dimensiones del gasto propagadas a TODAS las líneas (E7).
        if (gasto.getProyectoId() != null) {
            detalles.forEach(d -> {
                d.setProyectoId(gasto.getProyectoId());
                d.setFrenteId(gasto.getFrenteId());
            });
        }

        java.time.LocalDate fecha = gasto.getFecha() != null ? gasto.getFecha() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_GASTO,
                "Gasto #" + gastoId + (gasto.getCategoria() != null ? " — " + gasto.getCategoria() : ""),
                "GASTO", gastoId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeMerma(Long mermaId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("MERMA", mermaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la merma #" + mermaId);
        }

        MermaEntity merma = mermaRepo.findByIdAndEmpresaId(mermaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merma no encontrada"));

        // La baja acredita el inventario de cada producto con la misma cadena
        // que usa la venta (override del producto → categoría contable →
        // concepto INVENTARIO). Si la línea salió por receta, lo que se
        // descarga es el inventario de cada componente, no el del padre.
        java.util.Map<Long, BigDecimal> inventarioPorCuenta = new java.util.LinkedHashMap<>();
        for (com.cloud_technological.aura_pos.entity.MermaDetalleEntity d : mermaDetalleRepo.findByMermaId(mermaId)) {
            List<com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity> consumos =
                    consumoComponenteRepo.findByOrigenAndDetalleIdOrderByIdAsc("MERMA", d.getId());
            if (consumos.isEmpty()) {
                acumularInventario(inventarioPorCuenta, d.getProducto().getId(), empresaId,
                        nz(d.getCantidad()).multiply(nz(d.getCostoUnitario())));
            } else {
                for (com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity c : consumos) {
                    acumularInventario(inventarioPorCuenta, c.getProductoHijo().getId(), empresaId,
                            nz(c.getCantidad()).multiply(nz(c.getCostoUnitario())));
                }
            }
        }

        BigDecimal costo = inventarioPorCuenta.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (costo.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "La merma #" + mermaId + " no tiene costo a contabilizar.");
        }

        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        PlanCuentaEntity perdida = config.resolverCuenta(empresaId, ConceptoContable.PERDIDA_MERMA);
        detalles.add(linea(perdida.getId(), "Pérdida por merma", costo, BigDecimal.ZERO));
        inventarioPorCuenta.forEach((cuentaId, monto) ->
                detalles.add(linea(cuentaId, "Baja de inventario por merma", BigDecimal.ZERO, monto)));

        java.time.LocalDate fecha = merma.getFecha() != null ? merma.getFecha().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_MERMA,
                "Merma #" + mermaId, "MERMA", mermaId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    /** Suma el costo de un producto en la cuenta de inventario que le corresponde. */
    private void acumularInventario(java.util.Map<Long, BigDecimal> porCuenta, Long productoId,
            Integer empresaId, BigDecimal monto) {
        BigDecimal valor = monto.setScale(2, java.math.RoundingMode.HALF_UP);
        if (valor.signum() <= 0) {
            return;
        }
        Long cuentaId = resolucionCuentaProducto.resolver(productoId, empresaId).inventarioId();
        porCuenta.merge(cuentaId, valor, BigDecimal::add);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeNomina(Long nominaId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("NOMINA", nominaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la nómina #" + nominaId);
        }

        NominaEntity n = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        List<AsientoDetalleEntity> detalles = new ArrayList<>();

        // ── DÉBITOS · gasto de personal desglosado por auxiliar (5105xx) ──────
        // El sueldo absorbe todo el devengado (salario proporcional + auxilio + novedades).
        // E7: cada débito se reparte por proyecto/frente según horas de asistencia.
        List<RepartoFrente> reparto = calcularRepartoNomina(n, empresaId);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_SUELDOS,
                "Sueldos y devengados", nz(n.getTotalDevengado()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_APORTE_SALUD,
                "Aporte patronal salud", nz(n.getAporteSalud()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_APORTE_PENSION,
                "Aporte patronal pensión", nz(n.getAportePension()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_ARL,
                "ARL", nz(n.getAporteArl()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_CAJA,
                "Caja de compensación", nz(n.getAporteCaja()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_SENA_ICBF,
                "SENA e ICBF", nz(n.getAporteSena()).add(nz(n.getAporteIcbf())), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_PRIMA,
                "Provisión prima de servicios", nz(n.getProvisionPrima()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_CESANTIAS,
                "Provisión cesantías", nz(n.getProvisionCesantias()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_INT_CESANTIAS,
                "Provisión intereses de cesantías", nz(n.getProvisionIntCesantias()), reparto);
        addDebitoNomina(detalles, empresaId, ConceptoContable.NOMINA_VACACIONES,
                "Provisión vacaciones", nz(n.getProvisionVacaciones()), reparto);

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
                "NOMINA", nominaId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    /**
     * Asiento del PAGO de la nómina: DB salarios por pagar (neto) / CR banco o caja.
     * Cierra el pasivo generado al aprobar y refleja de dónde salió el dinero.
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoNomina(Long nominaId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("NOMINA_PAGO", nominaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento de pago para la nómina #" + nominaId);
        }

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
        // CR · banco o caja de donde salió el dinero. La cuenta que resolvió el
        // origen de fondos manda: distingue la caja general de la caja menor.
        PlanCuentaEntity origen = resolverCuentaPago(empresaId, n.getMedioPago(),
                n.getCuentaBancariaId(), n.getCuentaPagoId());
        detalles.add(linea(origen.getId(), "Pago de nómina", BigDecimal.ZERO, neto));

        java.time.LocalDate fecha = n.getFechaPago() != null
                ? n.getFechaPago().toLocalDate() : java.time.LocalDate.now();
        return persistir(empresaId, usuarioId, fecha, PREFIX_NOMINA_PAGO,
                "Pago nómina #" + nominaId
                        + (n.getEmpleado() != null && n.getEmpleado().getId() != null
                                ? " — empleado " + n.getEmpleado().getId() : ""),
                "NOMINA_PAGO", nominaId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    /**
     * Asiento del pago de una prestación en EFECTIVO: DB pasivo por pagar (25xx) / CR Caja.
     * (En transferencia el asiento lo genera el egreso de tesorería, no este método.)
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoPrestacion(Long prestacionId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("PRESTACION_PAGO", prestacionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento de pago para la prestación #" + prestacionId);
        }

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
                prestacionId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    @Override
    @Transactional
    public AsientoContableTableDto generarCierre(Long periodoId, Integer empresaId,
            Integer usuarioId) {
        // Idempotencia: no cerrar dos veces el mismo período.
        if (yaContabilizado("CIERRE", periodoId, empresaId)) {
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
        // El cierre se fecha el último día del mes que cierra, no el día en que el
        // contador lo corrió: un asiento de enero con fecha de marzo descuadra el
        // balance por fechas (V171).
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                ultimoDiaDelPeriodo(periodoId), comprobante,
                "Cierre del período #" + periodoId, "CIERRE", periodoId, periodoId, detalles);
        asiento.setEstado("CONTABILIZADO"); // el cierre es acto deliberado, nunca borrador
        detalles.forEach(x -> x.setAsiento(asiento));

        AsientoContableEntity saved = asientoRepo.save(asiento);
        AsientoContableTableDto result = toDto(saved);
        result.setDetalles(queryRepo.obtenerDetalles(saved.getId()));
        return result;
    }

    /**
     * Corre DENTRO de la transacción del cierre (REQUIRED): si la
     * reclasificación falla, el período no se cierra. Usa el saldo del mayor
     * de la cuenta contable de cada banco con sobregiro; para que no haya
     * dobles conteos cada banco debe tener su propia auxiliar 1110xx.
     */
    @Override
    @Transactional
    public AsientoContableTableDto generarReclasificacionSobregiro(Long periodoId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("SOBREGIRO", periodoId, empresaId)) {
            return null;
        }

        // La cuenta de sobregiros se resuelve solo si de verdad hay un banco en rojo:
        // pedirla siempre impedía cerrar el mes a cualquier empresa que no tenga 2105,
        // aunque no tuviera ni un sobregiro.
        PlanCuentaEntity sobregiros = null;
        List<AsientoDetalleEntity> detalles = new ArrayList<>();
        for (CuentaBancariaEntity cb : cuentaBancariaRepo.findByEmpresaIdOrderByNombreAsc(empresaId)) {
            if (!Boolean.TRUE.equals(cb.getPermiteSobregiro()) || cb.getCuentaContableId() == null) {
                continue;
            }
            BigDecimal saldo = nz(queryRepo.saldoCuenta(empresaId, cb.getCuentaContableId()));
            if (saldo.signum() < 0) { // saldo crédito en cuenta de activo = sobregiro
                BigDecimal monto = saldo.negate();
                if (sobregiros == null) {
                    sobregiros = config.resolverCuenta(empresaId, ConceptoContable.SOBREGIROS_BANCARIOS);
                }
                detalles.add(linea(cb.getCuentaContableId(),
                        "Reclasificación sobregiro — " + cb.getNombre(), monto, BigDecimal.ZERO));
                detalles.add(linea(sobregiros.getId(),
                        "Sobregiro bancario — " + cb.getNombre(), BigDecimal.ZERO, monto, cb.getTerceroId()));
            }
        }
        if (detalles.isEmpty()) {
            return null;
        }

        String comprobante = queryRepo.siguienteNumeroComprobante(empresaId, PREFIX_SOBREGIRO);
        AsientoContableEntity asiento = buildAsiento(empresaId, usuarioId,
                ultimoDiaDelPeriodo(periodoId), comprobante,
                "Reclasificación de sobregiros — cierre período #" + periodoId,
                "SOBREGIRO", periodoId, periodoId, detalles);
        asiento.setEstado("CONTABILIZADO"); // parte del cierre, nunca borrador
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
        if (yaContabilizado("OBLIGACION", obligacionId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la obligación #" + obligacionId);
        }

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
                "OBLIGACION", obligacionId,
                periodoResolver.resolverId(empresaId, o.getFechaDesembolso()), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdePagoCuota(Long cuotaId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("CUOTA_OBLIGACION", cuotaId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para la cuota #" + cuotaId);
        }

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
                "CUOTA_OBLIGACION", cuotaId, periodoResolver.resolverId(empresaId, fecha), detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeMovimientoCaja(Long movimientoId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("MOVIMIENTO_CAJA", movimientoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el movimiento de caja #" + movimientoId);
        }

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
                "MOVIMIENTO_CAJA", movimientoId,
                periodoResolver.resolverId(empresaId, fecha), tipoComprobante, detalles);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AsientoContableTableDto generarDesdeTesoreria(Long movimientoId, Integer empresaId,
            Integer usuarioId) {
        if (yaContabilizado("TESORERIA", movimientoId, empresaId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un asiento contable para el movimiento de tesorería #" + movimientoId);
        }

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
                "TESORERIA", movimientoId,
                periodoResolver.resolverId(empresaId, fecha), tipoComprobante, detalles);
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

    /**
     * Resuelve la cuenta contable de un movimiento de dinero delegando en el
     * puerto del módulo contable (E2): cuenta bancaria → forma de pago
     * parametrizada → fallback CAJA/BANCOS. Una sola fuente de verdad para
     * los flujos legacy y los migrados al registry.
     */
    private PlanCuentaEntity resolverCuentaPago(Integer empresaId, String metodoPago, Long cuentaBancariaId) {
        return resolverCuentaPago(empresaId, metodoPago, cuentaBancariaId, null);
    }

    /**
     * Cuenta contra la que se registra un movimiento de dinero. La prioridad
     * (cuenta elegida a mano → banco → forma de pago → CAJA/BANCOS) la define
     * {@link ResolucionCuentaPago}; aquí solo se materializa la entity.
     */
    private PlanCuentaEntity resolverCuentaPago(Integer empresaId, String metodoPago,
            Long cuentaBancariaId, Long cuentaContableId) {
        Long cuentaId = resolucionCuentaPago.resolver(empresaId, metodoPago,
                cuentaBancariaId, cuentaContableId);
        return planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "La cuenta contable resuelta para el pago no existe."));
    }

    /**
     * ¿El documento ya tiene asiento <b>vigente</b>?
     *
     * <p>Un asiento reversado no cuenta: cuando un documento se edita, su
     * asiento se reversa y el documento tiene que poder generar el nuevo que
     * refleja los valores corregidos.
     */
    private boolean yaContabilizado(String tipoOrigen, Long origenId, Integer empresaId) {
        return asientoVigente(tipoOrigen, origenId, empresaId).isPresent();
    }

    /**
     * El asiento que hoy representa al documento.
     *
     * <p>Los originales reversados siguen CONTABILIZADOS (su reversa los
     * neutraliza en los libros), así que la vigencia se deduce contando: cada
     * reversa no anulada cancela un original, y si sobran originales el vigente
     * es el más reciente. Una reversa en BORRADOR (modo revisión) ya cuenta: el
     * documento dejó de estar representado aunque el contador no la apruebe aún.
     */
    private java.util.Optional<AsientoContableEntity> asientoVigente(String tipoOrigen,
            Long origenId, Integer empresaId) {
        long originales = asientoRepo.countByTipoOrigenAndOrigenIdAndEmpresaIdAndEstado(
                tipoOrigen, origenId, empresaId, ESTADO_CONTABILIZADO);
        if (originales == 0) {
            return java.util.Optional.empty();
        }
        long reversas = asientoRepo.countByTipoOrigenAndOrigenIdAndEmpresaIdAndEstadoNot(
                "ANULACION_" + tipoOrigen, origenId, empresaId, ESTADO_ANULADO);
        if (originales <= reversas) {
            return java.util.Optional.empty();
        }
        return asientoRepo.findFirstByTipoOrigenAndOrigenIdAndEmpresaIdAndEstadoOrderByIdDesc(
                tipoOrigen, origenId, empresaId, ESTADO_CONTABILIZADO);
    }

    /** Construye, valida, persiste el asiento y devuelve su DTO con detalles. */
    /** Último día del mes que cubre el período: la fecha propia de sus asientos de cierre. */
    private java.time.LocalDate ultimoDiaDelPeriodo(Long periodoId) {
        return periodoResolver.ultimoDia(periodoId);
    }

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

    /**
     * Agrega una línea débito de nómina resolviendo la cuenta del concepto
     * (solo si monto {@code > 0}). Con reparto (E7), el gasto se divide por
     * (proyecto, frente) proporcional a las horas; la última porción absorbe
     * el residuo de redondeo para que el asiento cuadre exacto.
     */
    private void addDebitoNomina(List<AsientoDetalleEntity> detalles, Integer empresaId,
            ConceptoContable concepto, String desc, BigDecimal monto,
            List<RepartoFrente> reparto) {
        if (monto == null || monto.signum() <= 0) return;
        PlanCuentaEntity cuenta = config.resolverCuenta(empresaId, concepto);
        if (reparto == null || reparto.isEmpty()) {
            detalles.add(linea(cuenta.getId(), desc, monto, BigDecimal.ZERO));
            return;
        }
        BigDecimal asignado = BigDecimal.ZERO;
        for (int i = 0; i < reparto.size(); i++) {
            RepartoFrente parte = reparto.get(i);
            BigDecimal porcion = i == reparto.size() - 1
                    ? monto.subtract(asignado)
                    : monto.multiply(parte.proporcion())
                            .setScale(2, java.math.RoundingMode.HALF_UP);
            asignado = asignado.add(porcion);
            if (porcion.signum() <= 0) continue;
            AsientoDetalleEntity lineaGasto = linea(cuenta.getId(), desc, porcion, BigDecimal.ZERO);
            lineaGasto.setProyectoId(parte.proyectoId());
            lineaGasto.setFrenteId(parte.frenteId());
            detalles.add(lineaGasto);
        }
    }

    /** Participación de un proyecto/frente en las horas del período (E7). */
    private record RepartoFrente(Long proyectoId, Long frenteId, BigDecimal proporcion) {
    }

    /**
     * Reparto del gasto de nómina por proyecto/frente según las horas de las
     * novedades de asistencia del empleado en el período. DIAS se convierte a
     * horas (×8) para no sesgar la mezcla. Sin datos → lista vacía (una sola
     * línea, comportamiento previo).
     */
    /**
     * Reparto del costo por proyecto/frente.
     *
     * <p><b>Deriva de {@code nomina_detalle} (Fase 4), no de un cálculo propio.</b>
     *
     * <p>Antes leía {@code asistencia_novedad_nomina} y calculaba su propia
     * proporción. Eso funcionaba, pero convivía con el reparto que el motor de
     * liquidación hace sobre {@code nomina_detalle} — dos mecanismos con
     * fuentes distintas ({@code asistencia_novedad_nomina} vs
     * {@code asistencia_frente_detalle}) para el mismo propósito. Podían
     * divergir: el detalle de nómina diría una cosa y el asiento otra para la
     * misma obra.
     *
     * <p>Ahora {@code nomina_detalle} es la fuente única y el asiento deriva de
     * ella. Por construcción, coinciden.
     *
     * <p>Fallback: si la nómina no tiene detalle dimensionado (empresa sin
     * proyectos, o liquidada antes de la Fase 4), devuelve lista vacía y
     * {@code addDebitoNomina} arma la línea sin dimensión — el comportamiento
     * de siempre.
     */
    private List<RepartoFrente> calcularRepartoNomina(NominaEntity n, Integer empresaId) {
        if (n.getId() == null) return List.of();

        List<Object[]> filas = nominaDetalleRepo.distribucionPorProyecto(n.getId());
        if (filas.isEmpty()) return List.of();

        BigDecimal total = filas.stream()
                .map(f -> nz((BigDecimal) f[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) return List.of();

        return filas.stream()
                .map(f -> new RepartoFrente(
                        (Long) f[0],
                        (Long) f[1],
                        nz((BigDecimal) f[2]).divide(total, 6, java.math.RoundingMode.HALF_UP)))
                .toList();
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

    /**
     * Línea del asiento cuyo lado lo decide el signo del importe.
     *
     * <p>Una nota crédito de compra se guarda como el documento negativo de la
     * factura que corrige, así que sus importes llegan aquí en negativo y cada
     * línea va del lado contrario al de la factura: el inventario se acredita,
     * el IVA descontable se acredita y la deuda con el proveedor se debita.
     *
     * <p>Sin esto el asiento salía con débitos negativos — que ni existen en
     * contabilidad ni pasan el validador de balance.
     *
     * @param alDebito lado natural de la línea cuando el importe es positivo
     */
    private AsientoDetalleEntity lineaFirmada(Long cuentaId, String desc,
            BigDecimal monto, boolean alDebito, Long terceroId) {
        boolean debito = monto.signum() >= 0 ? alDebito : !alDebito;
        BigDecimal valor = monto.abs();
        return linea(cuentaId, desc,
                debito ? valor : BigDecimal.ZERO,
                debito ? BigDecimal.ZERO : valor,
                terceroId);
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
                // E3: en modo REVISION los asientos automáticos nacen BORRADOR.
                // CIERRE y SOBREGIRO fuerzan CONTABILIZADO tras el build.
                .estado(configContabilizacion.estadoInicial(empresaId).name())
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

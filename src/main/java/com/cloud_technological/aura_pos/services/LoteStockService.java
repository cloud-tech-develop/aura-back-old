package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.DocumentoLoteEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.DocumentoLoteJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * El único que mueve el stock de los lotes. Corre dentro de la transacción del
 * documento que mueve el inventario, para que siempre se cumpla, por bodega:
 *
 * <pre>Σ lote.stock_actual (activos) = inventario.stock_actual</pre>
 *
 * Todas las cantidades van en unidad base.
 */
@Service
@RequiredArgsConstructor
public class LoteStockService {

    /** Donde queda el stock de un producto con lotes que entró sin lote. */
    public static final String SIN_LOTE = "SIN-LOTE";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.0001");

    // ── Orígenes de documento_lote ──
    public static final String VENTA = "VENTA";
    public static final String VENTA_COMPONENTE = "VENTA_COMPONENTE";
    public static final String MERMA = "MERMA";
    public static final String OBSEQUIO = "OBSEQUIO";
    public static final String CONSUMO_INTERNO = "CONSUMO_INTERNO";
    /** Componente de receta en merma, obsequio o consumo interno: detalle = inventario_consumo_componente.id. */
    public static final String COMPONENTE = "COMPONENTE";
    public static final String TRASLADO_SALIDA = "TRASLADO_SALIDA";
    public static final String TRASLADO_ENTRADA = "TRASLADO_ENTRADA";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String DEVOLUCION_CAMBIO = "DEVOLUCION_CAMBIO";
    public static final String RECONTEO = "RECONTEO";
    public static final String AJUSTE_INVENTARIO = "AJUSTE_INVENTARIO";

    private final LoteJPARepository loteRepository;
    private final InventarioJPARepository inventarioRepository;
    private final DocumentoLoteJPARepository documentoLoteRepository;
    private final EmpresaJPARepository empresaRepository;

    /** Escribe una fila de kardex para un lote (cada servicio tiene su registrarMovimiento). */
    @FunctionalInterface
    public interface KardexLinea {
        void registrar(LoteEntity lote, BigDecimal cantidad, BigDecimal saldoAnterior, BigDecimal saldoNuevo);
    }

    /** Lo que entra a un lote: código y vencimiento escritos por el usuario. */
    public record Entrada(String codigo, LocalDate vencimiento, LocalDate fabricacion, BigDecimal cantidadBase) {
    }

    /** Cuánto se movió de cada lote. */
    public record Asignacion(LoteEntity lote, BigDecimal cantidadBase) {
    }

    /** Lote que el usuario eligió para una salida. */
    public record Elegido(Long loteId, BigDecimal cantidadBase) {
    }

    // ── Entradas ──────────────────────────────────────────────────────────

    /**
     * Crea el lote o le suma. Un código que ya existe con otro vencimiento se
     * rechaza: dos fechas en el mismo lote no se pueden sacar por FEFO.
     */
    public LoteEntity entrar(ProductoEntity producto, BodegaEntity bodega, Integer empresaId,
            Entrada entrada, BigDecimal costoUnitario, Long compraDetalleId) {
        String codigo = entrada.codigo() != null ? entrada.codigo().trim() : "";
        if (codigo.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Falta el código de lote de " + producto.getNombre());
        if (codigo.length() > 100)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código de lote no puede superar 100 caracteres");
        BigDecimal cantidad = nz(entrada.cantidadBase());
        if (cantidad.signum() <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cantidad del lote " + codigo + " debe ser mayor que cero");
        BigDecimal costo = nz(costoUnitario);

        LoteEntity lote = loteRepository.buscarPorCodigo(producto.getId(), bodega.getId(), codigo)
                .stream().findFirst().orElse(null);

        if (lote == null) {
            lote = new LoteEntity();
            lote.setProducto(producto);
            lote.setBodega(bodega);
            lote.setSucursal(bodega.getSucursal());
            lote.setEmpresaId(empresaId);
            lote.setCodigoLote(codigo);
            lote.setFechaVencimiento(entrada.vencimiento());
            lote.setFechaFabricacion(entrada.fabricacion());
            lote.setStockActual(cantidad);
            lote.setCostoUnitario(costo.setScale(2, RoundingMode.HALF_UP));
            lote.setCompraDetalleId(compraDetalleId);
            lote.setActivo(true);
            lote.setCreatedAt(LocalDateTime.now());
            return loteRepository.save(lote);
        }

        if (entrada.vencimiento() != null && lote.getFechaVencimiento() != null
                && !entrada.vencimiento().equals(lote.getFechaVencimiento()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El lote " + lote.getCodigoLote() + " de " + producto.getNombre()
                            + " ya existe con vencimiento " + lote.getFechaVencimiento().format(FECHA)
                            + ". Revisa la fecha o usa otro código.");
        if (lote.getFechaVencimiento() == null) lote.setFechaVencimiento(entrada.vencimiento());
        if (lote.getFechaFabricacion() == null) lote.setFechaFabricacion(entrada.fabricacion());
        if (lote.getEmpresaId() == null) lote.setEmpresaId(empresaId);

        // Costo promedio del lote: lo que había más lo que entra.
        BigDecimal stock = nz(lote.getStockActual()).max(BigDecimal.ZERO);
        BigDecimal total = stock.add(cantidad);
        if (total.signum() > 0) {
            lote.setCostoUnitario(stock.multiply(nz(lote.getCostoUnitario()))
                    .add(cantidad.multiply(costo))
                    .divide(total, 2, RoundingMode.HALF_UP));
        }
        lote.setStockActual(nz(lote.getStockActual()).add(cantidad));
        lote.setActivo(true);
        return loteRepository.save(lote);
    }

    /**
     * Deshace una entrada (anular o editar la compra). Si del lote ya salió
     * mercancía, no alcanza para devolver lo que entró y se bloquea.
     */
    public void retirarEntrada(LoteEntity lote, BigDecimal cantidadBase, String accion) {
        BigDecimal stock = nz(lote.getStockActual());
        if (stock.compareTo(nz(cantidadBase).subtract(TOLERANCIA)) < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede " + accion + ": del lote " + lote.getCodigoLote()
                            + " ya salió mercancía (quedan " + legible(stock) + " de "
                            + legible(cantidadBase) + "). Registra una nota crédito por lo que queda.");
        lote.setStockActual(stock.subtract(nz(cantidadBase)).max(BigDecimal.ZERO));
        loteRepository.save(lote);
    }

    // ── Salidas ───────────────────────────────────────────────────────────

    /**
     * Saca la cantidad de los lotes. Con lotes elegidos los respeta (y tienen
     * que sumar la cantidad); si no, sale primero el que vence antes.
     *
     * @param permitirVencidos una devolución al proveedor sí puede llevarse lo vencido
     */
    public List<Asignacion> salir(ProductoEntity producto, BodegaEntity bodega, Integer empresaId,
            BigDecimal cantidadBase, List<Elegido> elegidos, boolean permitirVencidos) {
        BigDecimal cantidad = nz(cantidadBase);
        List<Asignacion> asignaciones = new ArrayList<>();
        if (cantidad.signum() <= 0) return asignaciones;

        if (elegidos != null && !elegidos.isEmpty()) {
            BigDecimal suma = elegidos.stream().map(e -> nz(e.cantidadBase())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (suma.subtract(cantidad).abs().compareTo(TOLERANCIA) > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Los lotes elegidos de " + producto.getNombre() + " suman " + legible(suma)
                                + " y la línea tiene " + legible(cantidad));
            for (Elegido e : elegidos) {
                if (nz(e.cantidadBase()).signum() <= 0) continue;
                LoteEntity lote = loteRepository.buscarParaSalida(e.loteId(), empresaId,
                        producto.getId(), bodega.getId(), e.cantidadBase());
                if (!permitirVencidos && vencido(lote))
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "El lote " + lote.getCodigoLote() + " de " + producto.getNombre() + " está vencido");
                descontar(lote, e.cantidadBase());
                asignaciones.add(new Asignacion(lote, e.cantidadBase()));
            }
            return asignaciones;
        }

        BigDecimal falta = cantidad;
        BigDecimal vencidoSinUsar = BigDecimal.ZERO;
        for (LoteEntity lote : loteRepository.enOrdenFefo(producto.getId(), bodega.getId())) {
            if (falta.signum() <= 0) break;
            if (!permitirVencidos && vencido(lote)) {
                vencidoSinUsar = vencidoSinUsar.add(nz(lote.getStockActual()));
                continue;
            }
            BigDecimal toma = nz(lote.getStockActual()).min(falta);
            if (toma.signum() <= 0) continue;
            descontar(lote, toma);
            asignaciones.add(new Asignacion(lote, toma));
            falta = falta.subtract(toma);
        }
        if (falta.compareTo(TOLERANCIA) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Los lotes de " + producto.getNombre() + " no alcanzan: faltan " + legible(falta)
                            + (vencidoSinUsar.signum() > 0
                                    ? " (hay " + legible(vencidoSinUsar) + " en lotes vencidos)" : ""));
        return asignaciones;
    }

    /** Devuelve a cada lote lo que había salido de él (anular una salida). */
    public void devolver(List<Asignacion> asignaciones) {
        for (Asignacion a : asignaciones) {
            LoteEntity lote = a.lote();
            lote.setStockActual(nz(lote.getStockActual()).add(nz(a.cantidadBase())));
            lote.setActivo(true);
            loteRepository.save(lote);
        }
    }

    // ── Cuadre ────────────────────────────────────────────────────────────

    /**
     * Un producto que empieza a manejar lotes ya tiene stock: lo que no esté en
     * ningún lote pasa a SIN-LOTE, igual que hizo V164 con los existentes.
     */
    public void cuadrarSinLote(ProductoEntity producto, Integer empresaId) {
        for (InventarioEntity inv : inventarioRepository.findByProductoId(producto.getId())) {
            BodegaEntity bodega = inv.getBodega();
            if (bodega == null) continue;
            BigDecimal falta = nz(inv.getStockActual())
                    .subtract(nz(loteRepository.stockEnLotes(producto.getId(), bodega.getId())));
            if (falta.signum() > 0) {
                entrar(producto, bodega, empresaId,
                        new Entrada(SIN_LOTE, null, null, falta), producto.getCosto(), null);
            }
        }
    }

    // ── Por documento (con rastro en documento_lote) ───────────────────────

    public boolean manejaLotes(ProductoEntity producto) {
        return producto != null && Boolean.TRUE.equals(producto.getManejaLotes());
    }

    /** Regla de la empresa: la venta, el obsequio y el consumo interno no sacan de lotes vencidos. */
    public boolean bloqueaVencidos(Integer empresaId) {
        return empresaRepository.findById(empresaId)
                .map(e -> !Boolean.FALSE.equals(e.getLotesBloquearVencidos()))
                .orElse(true);
    }

    /**
     * Sale de los lotes y deja el rastro. Sin lotes en el producto no hace nada.
     *
     * @param loteElegidoId  el lote que eligió el usuario; null = FEFO
     * @param permitirFaltante el producto admite stock negativo: lo que no esté en
     *                         lotes sale solo del inventario
     */
    public List<Asignacion> salidaDocumento(String origen, Long detalleId, ProductoEntity producto,
            BodegaEntity bodega, Integer empresaId, BigDecimal cantidadBase, Long loteElegidoId,
            boolean permitirVencidos, boolean permitirFaltante) {
        if (!manejaLotes(producto) || nz(cantidadBase).signum() <= 0) return List.of();

        List<Asignacion> asignaciones;
        if (loteElegidoId != null) {
            asignaciones = salir(producto, bodega, empresaId, cantidadBase,
                    List.of(new Elegido(loteElegidoId, cantidadBase)), permitirVencidos);
        } else if (permitirFaltante) {
            BigDecimal disponible = BigDecimal.ZERO;
            for (LoteEntity l : loteRepository.enOrdenFefo(producto.getId(), bodega.getId()))
                if (permitirVencidos || !vencido(l)) disponible = disponible.add(nz(l.getStockActual()));
            asignaciones = salir(producto, bodega, empresaId, disponible.min(nz(cantidadBase)), null,
                    permitirVencidos);
        } else {
            asignaciones = salir(producto, bodega, empresaId, cantidadBase, null, permitirVencidos);
        }
        guardarRastro(origen, detalleId, asignaciones);
        return asignaciones;
    }

    /**
     * Entra a los lotes y deja el rastro. Sin {@code entradas} todo va a SIN-LOTE
     * (reconteo sobrante, ajuste, devolución de una venta sin rastro).
     */
    public List<Asignacion> entradaDocumento(String origen, Long detalleId, ProductoEntity producto,
            BodegaEntity bodega, Integer empresaId, BigDecimal cantidadBase, List<Entrada> entradas,
            BigDecimal costoUnitario) {
        if (!manejaLotes(producto) || nz(cantidadBase).signum() <= 0) return List.of();
        List<Entrada> lista = entradas != null && !entradas.isEmpty()
                ? entradas : List.of(new Entrada(SIN_LOTE, null, null, cantidadBase));
        List<Asignacion> asignaciones = new ArrayList<>();
        for (Entrada e : lista) {
            if (nz(e.cantidadBase()).signum() <= 0) continue;
            asignaciones.add(new Asignacion(
                    entrar(producto, bodega, empresaId, e, costoUnitario, null), e.cantidadBase()));
        }
        guardarRastro(origen, detalleId, asignaciones);
        return asignaciones;
    }

    /** Entrada a otra bodega con los mismos lotes (código, vencimiento y costo) de una salida. */
    public List<Asignacion> entradaEspejo(String origen, Long detalleId, ProductoEntity producto,
            BodegaEntity destino, Integer empresaId, List<Asignacion> salida) {
        List<Asignacion> asignaciones = new ArrayList<>();
        for (Asignacion a : salida) {
            LoteEntity o = a.lote();
            asignaciones.add(new Asignacion(entrar(producto, destino, empresaId,
                    new Entrada(o.getCodigoLote(), o.getFechaVencimiento(), o.getFechaFabricacion(), a.cantidadBase()),
                    o.getCostoUnitario(), null), a.cantidadBase()));
        }
        guardarRastro(origen, detalleId, asignaciones);
        return asignaciones;
    }

    /**
     * Deshace lo que movió la línea al anular: una salida devuelve a los mismos
     * lotes; una entrada retira de ellos (y se bloquea si ya salió mercancía).
     */
    public List<Asignacion> revertirDocumento(String origen, Long detalleId, boolean eraSalida, String accion) {
        List<Asignacion> asignaciones = asignacionesDe(origen, detalleId);
        if (eraSalida) {
            devolver(asignaciones);
        } else {
            for (Asignacion a : asignaciones) retirarEntrada(a.lote(), a.cantidadBase(), accion);
        }
        return asignaciones;
    }

    public List<Asignacion> asignacionesDe(String origen, Long detalleId) {
        return documentoLoteRepository.findByOrigenAndDetalleIdOrderByIdAsc(origen, detalleId).stream()
                .map(d -> new Asignacion(d.getLote(), d.getCantidadBase()))
                .toList();
    }

    /**
     * Una devolución de venta vuelve a los lotes de donde salió la línea: primero
     * al que vence más tarde, sin pasar de lo que salió de cada uno. Lo que no
     * tenga rastro (venta anterior a F3) entra a SIN-LOTE.
     */
    public List<Asignacion> entradaDevolucion(Long devolucionDetalleId, Long ventaDetalleId,
            ProductoEntity producto, BodegaEntity bodega, Integer empresaId, BigDecimal cantidadBase,
            BigDecimal costoUnitario) {
        if (!manejaLotes(producto) || nz(cantidadBase).signum() <= 0) return List.of();
        List<Asignacion> vendidas = new ArrayList<>(asignacionesDe(VENTA, ventaDetalleId));
        vendidas.sort((a, b) -> {
            LocalDate fa = a.lote().getFechaVencimiento(), fb = b.lote().getFechaVencimiento();
            if (fa == null && fb == null) return 0;
            if (fa == null) return -1;
            if (fb == null) return 1;
            return fb.compareTo(fa);
        });
        List<Asignacion> asignaciones = new ArrayList<>();
        BigDecimal falta = nz(cantidadBase);
        for (Asignacion v : vendidas) {
            if (falta.signum() <= 0) break;
            BigDecimal toma = nz(v.cantidadBase()).min(falta);
            LoteEntity lote = v.lote();
            lote.setStockActual(nz(lote.getStockActual()).add(toma));
            lote.setActivo(true);
            loteRepository.save(lote);
            asignaciones.add(new Asignacion(lote, toma));
            falta = falta.subtract(toma);
        }
        if (falta.signum() > 0) {
            asignaciones.add(new Asignacion(entrar(producto, bodega, empresaId,
                    new Entrada(SIN_LOTE, null, null, falta), costoUnitario, null), falta));
        }
        guardarRastro(DEVOLUCION, devolucionDetalleId, asignaciones);
        return asignaciones;
    }

    /**
     * Kardex de una línea: un movimiento por lote con los saldos encadenados, o
     * uno solo sin lote. {@code cantidad} trae el signo del movimiento.
     */
    public void kardex(List<Asignacion> asignaciones, BigDecimal cantidad, BigDecimal saldoAnterior,
            KardexLinea escritor) {
        BigDecimal total = nz(cantidad);
        if (asignaciones.isEmpty()) {
            escritor.registrar(null, total, saldoAnterior, saldoAnterior.add(total));
            return;
        }
        boolean resta = total.signum() < 0;
        BigDecimal saldo = saldoAnterior;
        BigDecimal enLotes = BigDecimal.ZERO;
        for (Asignacion a : asignaciones) {
            BigDecimal mov = resta ? a.cantidadBase().negate() : a.cantidadBase();
            escritor.registrar(a.lote(), mov, saldo, saldo.add(mov));
            saldo = saldo.add(mov);
            enLotes = enLotes.add(a.cantidadBase());
        }
        // Stock negativo permitido: lo que no salió de ningún lote va sin lote.
        BigDecimal resto = total.abs().subtract(enLotes);
        if (resto.compareTo(TOLERANCIA) > 0) {
            BigDecimal mov = resta ? resto.negate() : resto;
            escritor.registrar(null, mov, saldo, saldo.add(mov));
        }
    }

    /** Próximo vencimiento con stock y días de alerta, para avisar en pantalla. */
    public Map<String, Object> reglasEmpresa(Integer empresaId) {
        return empresaRepository.findById(empresaId)
                .map(e -> Map.<String, Object>of(
                        "bloquearVencidos", !Boolean.FALSE.equals(e.getLotesBloquearVencidos()),
                        "diasAlerta", e.getLotesDiasAlerta() != null ? e.getLotesDiasAlerta() : 30))
                .orElse(Map.of("bloquearVencidos", true, "diasAlerta", 30));
    }

    private void guardarRastro(String origen, Long detalleId, List<Asignacion> asignaciones) {
        for (Asignacion a : asignaciones) {
            if (nz(a.cantidadBase()).signum() <= 0) continue;
            DocumentoLoteEntity fila = new DocumentoLoteEntity();
            fila.setOrigen(origen);
            fila.setDetalleId(detalleId);
            fila.setLote(a.lote());
            fila.setCantidadBase(a.cantidadBase());
            documentoLoteRepository.save(fila);
        }
    }

    public boolean vencido(LoteEntity lote) {
        return lote.getFechaVencimiento() != null && lote.getFechaVencimiento().isBefore(LocalDate.now());
    }

    private void descontar(LoteEntity lote, BigDecimal cantidad) {
        lote.setStockActual(nz(lote.getStockActual()).subtract(nz(cantidad)));
        loteRepository.save(lote);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String legible(BigDecimal v) {
        return nz(v).stripTrailingZeros().toPlainString();
    }
}

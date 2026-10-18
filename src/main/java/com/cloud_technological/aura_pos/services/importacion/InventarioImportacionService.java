package com.cloud_technological.aura_pos.services.importacion;

import static com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.llave;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.ACTUALIZA;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.EXISTE;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.NUEVO;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.confirmar;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.error;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.fila;
import static com.cloud_technological.aura_pos.services.importacion.CatalogoImportacionService.texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.bodegas.CreateBodegaDto;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Bodegas;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.Conteo;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaBodega;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaConteo;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.FilaSaldoInventario;
import com.cloud_technological.aura_pos.dto.importacion.CatalogoImportacionDtos.SaldosInventario;
import com.cloud_technological.aura_pos.dto.importacion.ImportacionDtos.Resultado;
import com.cloud_technological.aura_pos.dto.reconteo.UpdateReconteoDetalleDto;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ReconteoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ReconteoEntity;
import com.cloud_technological.aura_pos.repositories.bodegas.BodegaJPARepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.importacion.CatalogoImportacionQueryRepository.BodegaFila;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.reconteo.ReconteoJPARepository;
import com.cloud_technological.aura_pos.services.BodegaService;
import com.cloud_technological.aura_pos.services.CostoPromedioService;
import com.cloud_technological.aura_pos.services.LoteStockService;
import com.cloud_technological.aura_pos.services.OrigenCosto;
import com.cloud_technological.aura_pos.services.ReconteoService;
import com.cloud_technological.aura_pos.services.SerialStockService;
import com.cloud_technological.aura_pos.utils.ClasificacionItem;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;

import lombok.RequiredArgsConstructor;

/**
 * Importar inventario desde Excel (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2c): bodegas,
 * saldos iniciales (con lotes y seriales) y el conteo físico de un reconteo abierto.
 *
 * <p><b>Saldos iniciales sin asiento:</b> el valor contable del inventario entra por
 * Contabilidad → Importar → Saldos iniciales (cuenta 1435). Si se contabilizara también
 * aquí, quedaría dos veces. Mueve stock, kardex, lotes, seriales y costo promedio, y
 * solo entra en un producto que no tenga existencias en esa bodega.
 */
@Service
@RequiredArgsConstructor
public class InventarioImportacionService {

    private final CatalogoImportacionQueryRepository query;
    private final BodegaService bodegaService;
    private final BodegaJPARepository bodegaRepo;
    private final ProductoJPARepository productoRepo;
    private final InventarioJPARepository inventarioRepo;
    private final MovimientoInventarioJPARepository movimientoRepo;
    private final LoteStockService loteStock;
    private final SerialStockService serialStock;
    private final CostoPromedioService costoPromedio;
    private final ReconteoJPARepository reconteoRepo;
    private final ReconteoService reconteoService;

    // ════════════════════════════════════════════════════════════════════════
    // Bodegas
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarBodegas(Integer empresaId, Long sedeSesion, Bodegas req) {
        return bodegas(empresaId, sedeSesion, req, false);
    }

    @Transactional
    public Resultado confirmarBodegas(Integer empresaId, Long sedeSesion, Bodegas req) {
        return confirmar(bodegas(empresaId, sedeSesion, req, false), () -> bodegas(empresaId, sedeSesion, req, true));
    }

    private Resultado bodegas(Integer empresaId, Long sedeSesion, Bodegas req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Long> sedes = query.sucursales(empresaId);
        Set<String> existentes = new HashSet<>();
        Set<String> codigos = new HashSet<>();
        for (BodegaFila b : query.bodegas(empresaId)) {
            existentes.add(b.sucursalId() + "|" + llave(b.nombre()));
            if (b.codigo() != null) codigos.add(llave(b.codigo()));
        }
        Set<String> enArchivo = new HashSet<>();
        for (FilaBodega f : req.getFilas()) {
            String nombre = texto(f.getNombre());
            String sede = texto(f.getSede());
            String codigo = texto(f.getCodigo());
            if (nombre == null) {
                error(r, f.getFila(), "", "Falta el nombre de la bodega");
                continue;
            }
            Long sedeId = sede != null ? sedes.get(llave(sede)) : sedeSesion;
            if (sedeId == null) {
                error(r, f.getFila(), nombre, sede != null ? "No hay una sede '" + sede + "'" : "Indique la sede");
                continue;
            }
            String clave = sedeId + "|" + llave(nombre);
            if (!enArchivo.add(clave)) {
                error(r, f.getFila(), nombre, "La bodega está repetida en el archivo");
                continue;
            }
            if (existentes.contains(clave)) {
                fila(r, f.getFila(), EXISTE, nombre, "Ya existe en esa sede");
                r.setExistentes(r.getExistentes() + 1);
                continue;
            }
            if (codigo != null && !codigos.add(llave(codigo))) {
                error(r, f.getFila(), nombre, "El código " + codigo + " ya lo usa otra bodega");
                continue;
            }
            if (grabar) {
                CreateBodegaDto dto = new CreateBodegaDto();
                dto.setSucursalId(sedeId.intValue());
                dto.setNombre(nombre);
                dto.setCodigo(codigo);
                dto.setPermiteVenta(f.getPermiteVenta() == null || f.getPermiteVenta());
                dto.setUbicacion(texto(f.getUbicacion()));
                dto.setEsPrincipal(false);
                bodegaService.crear(dto, empresaId);
            }
            fila(r, f.getFila(), NUEVO, nombre, sede != null ? "Sede " + sede : "");
            r.setNuevos(r.getNuevos() + 1);
        }
        r.setConfirmado(grabar);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Saldos iniciales de inventario
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarSaldos(Integer empresaId, Long sedeSesion, SaldosInventario req) {
        return saldos(empresaId, sedeSesion, req, false);
    }

    @Transactional
    public Resultado confirmarSaldos(Integer empresaId, Long sedeSesion, SaldosInventario req) {
        return confirmar(saldos(empresaId, sedeSesion, req, false), () -> saldos(empresaId, sedeSesion, req, true));
    }

    /** Una fila ya validada, lista para grabar. */
    private record Linea(FilaSaldoInventario fila, BigDecimal cantidad, BigDecimal costo, LocalDate vence,
            LocalDate fabrica, List<String> seriales) {
    }

    private Resultado saldos(Integer empresaId, Long sedeSesion, SaldosInventario req, boolean grabar) {
        Resultado r = new Resultado();
        Map<String, Long> porSku = query.productosPorSku(empresaId);
        Map<String, Long> porBarras = query.productosPorCodigoBarras(empresaId);
        List<BodegaFila> bodegas = query.bodegas(empresaId);
        Map<String, BigDecimal[]> saldos = query.inventarios(empresaId);
        BodegaFila principal = bodegas.stream()
                .filter(b -> sedeSesion != null && b.sucursalId() == sedeSesion && b.principal()).findFirst()
                .orElse(bodegas.stream().filter(b -> sedeSesion != null && b.sucursalId() == sedeSesion).findFirst()
                        .orElse(null));
        Map<Long, ProductoEntity> productos = new HashMap<>();
        Map<String, List<Linea>> grupos = new LinkedHashMap<>();
        Set<String> serialesArchivo = new HashSet<>();
        Set<String> lotesArchivo = new HashSet<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (FilaSaldoInventario f : req.getFilas()) {
            String codigo = texto(f.getProducto());
            String etiqueta = codigo != null ? codigo : "";
            Long productoId = codigo == null ? null
                    : porSku.getOrDefault(llave(codigo), porBarras.get(llave(codigo)));
            if (productoId == null) {
                error(r, f.getFila(), etiqueta, codigo == null ? "Falta el código del producto" : "No hay un producto con el código " + codigo);
                continue;
            }
            ProductoEntity p = productos.computeIfAbsent(productoId, id -> productoRepo.findById(id).orElse(null));
            if (p == null) {
                error(r, f.getFila(), etiqueta, "No hay un producto con el código " + codigo);
                continue;
            }
            etiqueta = codigo + " " + p.getNombre();
            if (!Boolean.TRUE.equals(p.getManejaInventario())
                    || !ClasificacionItem.de(p.getClasificacion()).mueveInventario()) {
                error(r, f.getFila(), etiqueta, "El producto no maneja inventario");
                continue;
            }
            BodegaFila bodega = buscarBodega(bodegas, texto(f.getBodega()), principal);
            if (bodega == null) {
                error(r, f.getFila(), etiqueta, texto(f.getBodega()) != null
                        ? "No hay una bodega '" + f.getBodega() + "'" : "Indique la bodega");
                continue;
            }
            BigDecimal[] actual = saldos.get(bodega.id() + "|" + productoId);
            if (actual != null && actual[1].signum() != 0) {
                error(r, f.getFila(), etiqueta, "Ya tiene existencias en " + bodega.nombre()
                        + ": el saldo inicial solo entra en cero. Use un ajuste o un reconteo");
                continue;
            }
            BigDecimal cantidad = f.getCantidad();
            if (cantidad == null || cantidad.signum() <= 0) {
                error(r, f.getFila(), etiqueta, "La cantidad debe ser mayor que cero");
                continue;
            }
            BigDecimal costo = f.getCosto() != null ? f.getCosto() : p.getCosto();
            if (costo == null || costo.signum() < 0) {
                error(r, f.getFila(), etiqueta, "El costo no puede ser negativo");
                continue;
            }

            // Lotes
            String lote = texto(f.getLote());
            LocalDate vence;
            LocalDate fabrica;
            try {
                vence = fecha(f.getVencimiento());
                fabrica = fecha(f.getFabricacion());
            } catch (IllegalArgumentException e) {
                error(r, f.getFila(), etiqueta, e.getMessage());
                continue;
            }
            boolean conLotes = loteStock.manejaLotes(p);
            if (!conLotes && (lote != null || vence != null)) {
                error(r, f.getFila(), etiqueta, "El producto no maneja lotes: quite el lote y el vencimiento");
                continue;
            }
            if (lote != null && !lotesArchivo.add(productoId + "|" + bodega.id() + "|" + llave(lote))) {
                error(r, f.getFila(), etiqueta, "El lote " + lote + " está repetido para el producto en esa bodega");
                continue;
            }

            // Seriales
            List<String> seriales = new ArrayList<>();
            if (texto(f.getSeriales()) != null)
                for (String s : f.getSeriales().split("[,;\\n]")) if (!s.isBlank()) seriales.add(s.trim());
            if (serialStock.manejaSerial(p)) {
                if (cantidad.stripTrailingZeros().scale() > 0) {
                    error(r, f.getFila(), etiqueta, "Un producto con serial va por unidades enteras");
                    continue;
                }
                if (seriales.size() != cantidad.intValue()) {
                    error(r, f.getFila(), etiqueta, "Son " + cantidad.intValue() + " unidades y hay " + seriales.size()
                            + " seriales: escriba uno por unidad, separados por coma");
                    continue;
                }
                String repetido = seriales.stream().filter(s -> !serialesArchivo.add(productoId + "|" + llave(s)))
                        .findFirst().orElse(null);
                if (repetido != null) {
                    error(r, f.getFila(), etiqueta, "El serial " + repetido + " está repetido en el archivo");
                    continue;
                }
            } else if (!seriales.isEmpty()) {
                error(r, f.getFila(), etiqueta, "El producto no maneja serial: quite los seriales");
                continue;
            }

            grupos.computeIfAbsent(bodega.id() + "|" + productoId, k -> new ArrayList<>())
                    .add(new Linea(f, cantidad, costo, vence, fabrica, seriales));
            BigDecimal valor = cantidad.multiply(costo);
            valorTotal = valorTotal.add(valor);
            fila(r, f.getFila(), NUEVO, etiqueta, "Entra " + cantidad.stripTrailingZeros().toPlainString() + " en "
                    + bodega.nombre() + (lote != null ? " · lote " + lote : "") + " · " + pesos(valor));
            r.setNuevos(r.getNuevos() + 1);
        }

        if (grabar && r.getErrores() == 0) {
            for (Map.Entry<String, List<Linea>> g : grupos.entrySet()) {
                String[] k = g.getKey().split("\\|");
                grabarSaldo(empresaId, Long.valueOf(k[0]), productos.get(Long.valueOf(k[1])), g.getValue(), saldos.get(g.getKey()));
            }
        }
        r.getAvisos().add("Valor del inventario: " + pesos(valorTotal) + ". No genera asiento: el valor contable va por "
                + "Contabilidad → Importar → Saldos iniciales (cuenta 1435). Revise que los dos cuadren.");
        r.setConfirmado(grabar);
        return r;
    }

    /** Un producto en una bodega: saldo, kardex, lotes, seriales y costo promedio. */
    private void grabarSaldo(Integer empresaId, Long bodegaId, ProductoEntity p, List<Linea> lineas, BigDecimal[] existente) {
        BodegaEntity bodega = bodegaRepo.findById(bodegaId).orElseThrow();
        BigDecimal total = lineas.stream().map(Linea::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valor = lineas.stream().map(l -> l.cantidad().multiply(l.costo())).reduce(BigDecimal.ZERO, BigDecimal::add);

        InventarioEntity inv = existente != null ? inventarioRepo.findById(existente[0].longValue()).orElseThrow()
                : new InventarioEntity();
        if (existente == null) {
            inv.setProducto(p);
            inv.setBodega(bodega);
            inv.setSucursal(bodega.getSucursal());
            inv.setStockMinimo(BigDecimal.ZERO);
        }
        inv.setStockActual(total);
        inv.setUpdatedAt(LocalDateTime.now());
        inv = inventarioRepo.saveAndFlush(inv);

        MovimientoInventarioEntity mov = new MovimientoInventarioEntity();
        mov.setSucursal(bodega.getSucursal());
        mov.setBodega(bodega);
        mov.setProducto(p);
        mov.setTipoMovimiento(TipoMovimientoInventario.AJUSTE_MANUAL_ENTRADA.codigo());
        mov.setCantidad(total);
        mov.setSaldoAnterior(BigDecimal.ZERO);
        mov.setSaldoNuevo(total);
        mov.setCostoHistorico(valor.divide(total, 6, RoundingMode.HALF_UP));
        mov.setReferenciaOrigen("Saldo inicial importado desde Excel");
        mov.setCreatedAt(LocalDateTime.now());
        movimientoRepo.save(mov);

        for (Linea l : lineas) {
            String lote = texto(l.fila().getLote());
            List<LoteStockService.Entrada> entradas = lote != null
                    ? List.of(new LoteStockService.Entrada(lote, l.vence(), l.fabrica(), l.cantidad()))
                    : null;
            loteStock.entradaDocumento(LoteStockService.AJUSTE_INVENTARIO, inv.getId(), p, bodega, empresaId,
                    l.cantidad(), entradas, l.costo());
            if (!l.seriales().isEmpty())
                serialStock.entradaSaldoInicial(p, bodega, empresaId, inv.getId(), l.seriales(), l.cantidad(), l.costo());
        }
        costoPromedio.aplicar(p.getId(), total, valor,
                OrigenCosto.de(OrigenCosto.SALDO_INICIAL, "Saldo inicial importado desde Excel"));
    }

    private static BodegaFila buscarBodega(List<BodegaFila> bodegas, String nombre, BodegaFila principal) {
        if (nombre == null) return principal;
        String k = llave(nombre);
        for (BodegaFila b : bodegas) if (b.codigo() != null && llave(b.codigo()).equals(k)) return b;
        List<BodegaFila> porNombre = bodegas.stream().filter(b -> llave(b.nombre()).equals(k)).toList();
        if (porNombre.size() == 1) return porNombre.get(0);
        // El mismo nombre en dos sedes: la de la sede de trabajo.
        if (principal != null)
            for (BodegaFila b : porNombre) if (b.sucursalId() == principal.sucursalId()) return b;
        return porNombre.isEmpty() ? null : porNombre.get(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Conteo físico (reconteo abierto)
    // ════════════════════════════════════════════════════════════════════════

    public Resultado validarConteo(Integer empresaId, Conteo req) {
        return conteo(empresaId, req, false);
    }

    @Transactional
    public Resultado confirmarConteo(Integer empresaId, Conteo req) {
        return confirmar(conteo(empresaId, req, false), () -> conteo(empresaId, req, true));
    }

    private Resultado conteo(Integer empresaId, Conteo req, boolean grabar) {
        Resultado r = new Resultado();
        if (req.getReconteoId() == null) throw new GlobalException(org.springframework.http.HttpStatus.BAD_REQUEST, "Falta el reconteo");
        ReconteoEntity reconteo = reconteoRepo.findByIdAndEmpresaId(req.getReconteoId(), empresaId)
                .orElseThrow(() -> new GlobalException(org.springframework.http.HttpStatus.NOT_FOUND, "Reconteo no encontrado"));
        if ("APROBADO".equals(reconteo.getEstado()) || "ANULADO".equals(reconteo.getEstado()))
            throw new GlobalException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El reconteo está " + reconteo.getEstado().toLowerCase() + ": ya no se puede cargar el conteo");

        Map<String, Long> porSku = query.productosPorSku(empresaId);
        Map<String, Long> porBarras = query.productosPorCodigoBarras(empresaId);
        Map<Long, List<ReconteoDetalleEntity>> porProducto = new HashMap<>();
        for (ReconteoDetalleEntity d : reconteo.getDetalles())
            porProducto.computeIfAbsent(d.getProducto().getId(), k -> new ArrayList<>()).add(d);
        Set<Long> vistos = new HashSet<>();

        for (FilaConteo f : req.getFilas()) {
            String codigo = texto(f.getProducto());
            String lote = texto(f.getLote());
            Long productoId = codigo == null ? null : porSku.getOrDefault(llave(codigo), porBarras.get(llave(codigo)));
            String etiqueta = (codigo != null ? codigo : "") + (lote != null ? " · lote " + lote : "");
            if (productoId == null) {
                error(r, f.getFila(), etiqueta, codigo == null ? "Falta el código del producto" : "No hay un producto con el código " + codigo);
                continue;
            }
            List<ReconteoDetalleEntity> lineas = porProducto.get(productoId);
            if (lineas == null) {
                error(r, f.getFila(), etiqueta, "El producto no está en este conteo (no tiene inventario en la bodega)");
                continue;
            }
            ReconteoDetalleEntity d;
            if (lote != null) {
                d = lineas.stream().filter(x -> x.getLote() != null && llave(x.getLote().getCodigoLote()).equals(llave(lote)))
                        .findFirst().orElse(null);
                if (d == null) {
                    error(r, f.getFila(), etiqueta, "El producto no tiene el lote " + lote + " en este conteo");
                    continue;
                }
            } else if (lineas.size() == 1) {
                d = lineas.get(0);
            } else {
                error(r, f.getFila(), etiqueta, "El producto tiene " + lineas.size() + " lotes en el conteo: indique el lote");
                continue;
            }
            if (f.getCantidad() == null || f.getCantidad().signum() < 0) {
                error(r, f.getFila(), etiqueta, "La cantidad contada no puede estar vacía ni ser negativa");
                continue;
            }
            if (!vistos.add(d.getId())) {
                error(r, f.getFila(), etiqueta, "El producto (o el lote) está repetido en el archivo");
                continue;
            }
            if (grabar) {
                UpdateReconteoDetalleDto u = new UpdateReconteoDetalleDto();
                u.setStockContado(f.getCantidad());
                reconteoService.actualizarDetalle(reconteo.getId(), d.getId(), u, empresaId);
            }
            BigDecimal sistema = d.getStockSistema() != null ? d.getStockSistema() : BigDecimal.ZERO;
            BigDecimal diferencia = f.getCantidad().subtract(sistema);
            String msg = "Sistema " + sistema.stripTrailingZeros().toPlainString() + ", contado "
                    + f.getCantidad().stripTrailingZeros().toPlainString()
                    + (diferencia.signum() == 0 ? " · cuadra" : " · diferencia " + diferencia.stripTrailingZeros().toPlainString());
            boolean yaContado = d.getStockContado() != null;
            fila(r, f.getFila(), yaContado ? ACTUALIZA : NUEVO, etiqueta, msg);
            if (yaContado) r.setActualizados(r.getActualizados() + 1);
            else r.setNuevos(r.getNuevos() + 1);
        }
        int sinContar = (int) reconteo.getDetalles().stream().filter(d -> d.getStockContado() == null && !vistos.contains(d.getId())).count();
        if (sinContar > 0) r.getAvisos().add(sinContar + " producto(s) del conteo no vienen en el archivo: quedan sin contar.");
        r.getAvisos().add("Cargar el conteo no ajusta el inventario: el ajuste se hace al aprobar el reconteo.");
        r.setConfirmado(grabar);
        return r;
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private static LocalDate fecha(String v) {
        String t = texto(v);
        if (t == null) return null;
        try {
            return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Fecha no válida: " + t + " (use AAAA-MM-DD)");
        }
    }

    private static String pesos(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }
}

package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.DocumentoSerialEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SerialProductoEntity;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleSerialEntity;
import com.cloud_technological.aura_pos.repositories.inventario.DocumentoSerialJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.SerialProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.venta_detalle_serial.VentaDetalleSerialJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * El único que cambia el estado de los seriales. Corre dentro de la transacción
 * del documento que mueve el inventario, para que se cumpla por bodega:
 *
 * <pre>seriales DISPONIBLES = inventario.stock_actual</pre>
 *
 * Un producto con serial se mueve en unidades enteras: cada unidad es un serial.
 */
@Service
@RequiredArgsConstructor
public class SerialStockService {

    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String VENDIDO = "VENDIDO";
    public static final String EN_GARANTIA = "EN_GARANTIA";
    public static final String DEVUELTO_PROVEEDOR = "DEVUELTO_PROVEEDOR";
    public static final String MERMA = "MERMA";
    public static final String OBSEQUIADO = "OBSEQUIADO";
    public static final String CONSUMO_INTERNO = "CONSUMO_INTERNO";

    // Orígenes de documento_serial
    public static final String ORIGEN_COMPRA = "COMPRA";
    public static final String ORIGEN_NOTA_CREDITO_COMPRA = "NOTA_CREDITO_COMPRA";
    public static final String ORIGEN_MERMA = "MERMA";
    public static final String ORIGEN_OBSEQUIO = "OBSEQUIO";
    public static final String ORIGEN_CONSUMO_INTERNO = "CONSUMO_INTERNO";
    public static final String ORIGEN_TRASLADO = "TRASLADO";
    public static final String ORIGEN_DEVOLUCION = "DEVOLUCION";

    private final SerialProductoJPARepository serialRepository;
    private final DocumentoSerialJPARepository documentoSerialRepository;
    private final VentaDetalleSerialJPARepository ventaDetalleSerialRepository;

    public boolean manejaSerial(ProductoEntity producto) {
        return producto != null && Boolean.TRUE.equals(producto.getManejaSerial());
    }

    // ── Entrada por compra ────────────────────────────────────────────────

    /** La compra crea un serial DISPONIBLE por unidad, con su costo y la línea de origen. */
    public List<SerialProductoEntity> entradaCompra(ProductoEntity producto, BodegaEntity bodega,
            Integer empresaId, Long compraDetalleId, List<String> seriales, BigDecimal cantidadBase,
            BigDecimal costoUnitario) {
        List<String> lista = seriales != null ? seriales : List.of();
        if (!manejaSerial(producto)) {
            if (!lista.isEmpty())
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "'" + producto.getNombre() + "' no maneja serial: quita los seriales de la línea");
            return List.of();
        }
        int unidades = unidadesEnteras(producto, cantidadBase);
        List<String> limpios = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (String s : lista) {
            String serial = s != null ? s.trim() : "";
            if (serial.isEmpty()) continue;
            if (serial.length() > 100)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El serial " + serial + " es demasiado largo");
            if (!vistos.add(serial.toUpperCase()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El serial " + serial + " está repetido en la línea de " + producto.getNombre());
            limpios.add(serial);
        }
        if (limpios.size() != unidades)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    producto.getNombre() + " tiene " + unidades + " unidades y llegaron " + limpios.size()
                            + " seriales: escribe uno por unidad");

        List<SerialProductoEntity> creados = new ArrayList<>();
        for (String serial : limpios) {
            if (serialRepository.existsByProductoIdAndSerialIgnoreCase(producto.getId(), serial))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El serial " + serial + " de " + producto.getNombre() + " ya está registrado");
            SerialProductoEntity e = new SerialProductoEntity();
            e.setProducto(producto);
            e.setBodega(bodega);
            e.setSucursal(bodega.getSucursal());
            e.setEmpresaId(empresaId);
            e.setSerial(serial);
            e.setEstado(DISPONIBLE);
            e.setCosto(costoUnitario);
            e.setFechaIngreso(LocalDateTime.now());
            e.setCompraDetalleId(compraDetalleId);
            e = serialRepository.save(e);
            rastro(ORIGEN_COMPRA, compraDetalleId, e, null, null);
            creados.add(e);
        }
        return creados;
    }

    /**
     * Deshace la entrada de una compra (anular o editar): los seriales se borran,
     * pero solo si siguen DISPONIBLES y nunca salieron por nada.
     */
    public void revertirEntradaCompra(Long compraDetalleId, String accion) {
        for (DocumentoSerialEntity fila : documentoSerialRepository
                .findByOrigenAndDetalleIdOrderByIdAsc(ORIGEN_COMPRA, compraDetalleId)) {
            SerialProductoEntity s = fila.getSerial();
            boolean conMovimientos = !DISPONIBLE.equals(s.getEstado())
                    || documentoSerialRepository.countBySerialId(s.getId()) > 1
                    || ventaDetalleSerialRepository.existsBySerialProductoId(s.getId());
            if (conMovimientos)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede " + accion + ": el serial " + s.getSerial()
                                + (DISPONIBLE.equals(s.getEstado())
                                        ? " ya tiene historial (se vendió o movió antes) y no se puede borrar."
                                        : " ya salió (" + s.getEstado() + ").")
                                + " Registra una nota crédito.");
            documentoSerialRepository.delete(fila);
            serialRepository.delete(s);
        }
    }

    // ── Salidas ───────────────────────────────────────────────────────────

    /**
     * Saca los seriales elegidos: tienen que ser tantos como unidades, del
     * producto, de la bodega y DISPONIBLES.
     */
    public List<SerialProductoEntity> salida(String origen, Long detalleId, ProductoEntity producto,
            BodegaEntity bodega, Integer empresaId, BigDecimal cantidadBase, List<Long> serialIds,
            String estadoNuevo) {
        if (!manejaSerial(producto)) return List.of();
        List<SerialProductoEntity> seriales = elegidos(producto, bodega, empresaId, cantidadBase, serialIds);
        for (SerialProductoEntity s : seriales) {
            rastro(origen, detalleId, s, s.getEstado(), s.getBodega());
            s.setEstado(estadoNuevo);
            s.setDocumentoSalidaTipo(origen);
            s.setDocumentoSalidaId(detalleId);
            serialRepository.save(s);
        }
        return seriales;
    }

    /** Anula una salida: cada serial vuelve a como estaba, si nadie lo movió después. */
    public void revertirSalida(String origen, Long detalleId, String accion) {
        for (DocumentoSerialEntity fila : documentoSerialRepository.findByOrigenAndDetalleIdOrderByIdAsc(origen, detalleId)) {
            SerialProductoEntity s = fila.getSerial();
            if (!origen.equals(s.getDocumentoSalidaTipo()) || !detalleId.equals(s.getDocumentoSalidaId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede " + accion + ": el serial " + s.getSerial() + " ya tuvo otro movimiento ("
                                + s.getEstado() + ")");
            s.setEstado(fila.getEstadoAnterior() != null ? fila.getEstadoAnterior() : DISPONIBLE);
            s.setDocumentoSalidaTipo(null);
            s.setDocumentoSalidaId(null);
            serialRepository.save(s);
        }
    }

    // ── Venta ─────────────────────────────────────────────────────────────

    /** La venta marca VENDIDO, deja la garantía al cliente y usa venta_detalle_serial. */
    public List<SerialProductoEntity> venta(VentaDetalleEntity detalle, ProductoEntity producto,
            BodegaEntity bodega, Integer empresaId, BigDecimal cantidadBase, List<Long> serialIds) {
        if (!manejaSerial(producto)) return List.of();
        List<SerialProductoEntity> seriales = elegidos(producto, bodega, empresaId, cantidadBase, serialIds);
        LocalDate garantia = producto.getMesesGarantia() != null && producto.getMesesGarantia() > 0
                ? LocalDate.now().plusMonths(producto.getMesesGarantia()) : null;
        for (SerialProductoEntity s : seriales) {
            s.setEstado(VENDIDO);
            s.setDocumentoSalidaTipo("VENTA");
            s.setDocumentoSalidaId(detalle.getId());
            s.setGarantiaClienteHasta(garantia);
            serialRepository.save(s);
            VentaDetalleSerialEntity vds = new VentaDetalleSerialEntity();
            vds.setVentaDetalle(detalle);
            vds.setSerialProducto(s);
            ventaDetalleSerialRepository.save(vds);
        }
        return seriales;
    }

    /** Anular la venta: solo los seriales de esa línea que sigan VENDIDOS vuelven a DISPONIBLE. */
    public void anularVenta(Long ventaDetalleId) {
        for (VentaDetalleSerialEntity vds : ventaDetalleSerialRepository.findByVentaDetalleId(ventaDetalleId)) {
            SerialProductoEntity s = vds.getSerialProducto();
            if (!VENDIDO.equals(s.getEstado())) continue;
            s.setEstado(DISPONIBLE);
            s.setDocumentoSalidaTipo(null);
            s.setDocumentoSalidaId(null);
            s.setGarantiaClienteHasta(null);
            serialRepository.save(s);
        }
    }

    // ── Devolución de venta ───────────────────────────────────────────────

    /**
     * Los seriales que devuelve el cliente: si la mercancía vuelve al inventario
     * quedan DISPONIBLES; si no (defectuoso), EN_GARANTIA. Sin elegir, y si se
     * devuelve toda la línea, salen todos los que siguen VENDIDOS.
     */
    public List<SerialProductoEntity> devolucion(Long devolucionDetalleId, VentaDetalleEntity ventaDetalle,
            ProductoEntity producto, BigDecimal cantidadBase, List<Long> serialIds, boolean reintegra,
            Integer empresaId) {
        if (!manejaSerial(producto)) return List.of();
        int unidades = unidadesEnteras(producto, cantidadBase);
        List<SerialProductoEntity> vendidos = ventaDetalleSerialRepository.findByVentaDetalleId(ventaDetalle.getId())
                .stream().map(VentaDetalleSerialEntity::getSerialProducto)
                .filter(s -> VENDIDO.equals(s.getEstado()))
                .toList();
        if (vendidos.isEmpty()) return List.of();   // venta anterior a F4: no hay seriales que mover

        List<SerialProductoEntity> devueltos;
        if (serialIds == null || serialIds.isEmpty()) {
            if (vendidos.size() != unidades)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Indica cuáles seriales de " + producto.getNombre() + " devuelve el cliente ("
                                + unidades + " de " + vendidos.size() + ")");
            devueltos = vendidos;
        } else {
            if (serialIds.size() != unidades)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Elige " + unidades + " seriales de " + producto.getNombre() + " (llegaron " + serialIds.size() + ")");
            devueltos = new ArrayList<>();
            for (Long id : serialIds) {
                SerialProductoEntity s = vendidos.stream().filter(v -> v.getId().equals(id)).findFirst()
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                                "Ese serial no salió en esta venta o ya fue devuelto"));
                devueltos.add(s);
            }
        }
        for (SerialProductoEntity s : devueltos) {
            rastro(ORIGEN_DEVOLUCION, devolucionDetalleId, s, s.getEstado(),
                    s.getBodega());
            s.setEstado(reintegra ? DISPONIBLE : EN_GARANTIA);
            s.setDocumentoSalidaTipo(ORIGEN_DEVOLUCION);
            s.setDocumentoSalidaId(devolucionDetalleId);
            serialRepository.save(s);
        }
        return devueltos;
    }

    // ── Traslado ──────────────────────────────────────────────────────────

    /** Los seriales cambian de bodega y siguen DISPONIBLES. */
    public List<SerialProductoEntity> traslado(Long detalleId, ProductoEntity producto, BodegaEntity origen,
            BodegaEntity destino, Integer empresaId, BigDecimal cantidadBase, List<Long> serialIds) {
        if (!manejaSerial(producto)) return List.of();
        List<SerialProductoEntity> seriales = elegidos(producto, origen, empresaId, cantidadBase, serialIds);
        for (SerialProductoEntity s : seriales) {
            rastro(ORIGEN_TRASLADO, detalleId, s, s.getEstado(), origen);
            s.setBodega(destino);
            s.setSucursal(destino.getSucursal());
            serialRepository.save(s);
        }
        return seriales;
    }

    /** Anular el traslado: vuelven al origen, si siguen DISPONIBLES en el destino. */
    public void revertirTraslado(Long detalleId, BodegaEntity origen, BodegaEntity destino) {
        for (DocumentoSerialEntity fila : documentoSerialRepository
                .findByOrigenAndDetalleIdOrderByIdAsc(ORIGEN_TRASLADO, detalleId)) {
            SerialProductoEntity s = fila.getSerial();
            if (!DISPONIBLE.equals(s.getEstado()) || s.getBodega() == null
                    || !s.getBodega().getId().equals(destino.getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "No se puede anular el traslado: el serial " + s.getSerial()
                                + " ya se movió en la bodega destino (" + s.getEstado() + ")");
            s.setBodega(origen);
            s.setSucursal(origen.getSucursal());
            serialRepository.save(s);
        }
    }

    // ── Privados ──────────────────────────────────────────────────────────

    private List<SerialProductoEntity> elegidos(ProductoEntity producto, BodegaEntity bodega,
            Integer empresaId, BigDecimal cantidadBase, List<Long> serialIds) {
        int unidades = unidadesEnteras(producto, cantidadBase);
        List<Long> ids = serialIds != null ? serialIds : List.of();
        if (new HashSet<>(ids).size() != ids.size())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Hay un serial repetido en " + producto.getNombre());
        if (ids.size() != unidades)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    producto.getNombre() + " maneja serial: elige " + unidades
                            + (unidades == 1 ? " serial" : " seriales") + " (llegaron " + ids.size() + ")");
        List<SerialProductoEntity> seriales = new ArrayList<>();
        for (Long id : ids) {
            SerialProductoEntity s = serialRepository.findByIdAndSucursalEmpresaId(id, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Serial no encontrado: " + id));
            if (!s.getProducto().getId().equals(producto.getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El serial " + s.getSerial() + " no es de " + producto.getNombre());
            if (s.getBodega() == null || !s.getBodega().getId().equals(bodega.getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El serial " + s.getSerial() + " no está en esta bodega");
            if (!DISPONIBLE.equals(s.getEstado()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El serial " + s.getSerial() + " no está disponible (" + s.getEstado() + ")");
            seriales.add(s);
        }
        return seriales;
    }

    private int unidadesEnteras(ProductoEntity producto, BigDecimal cantidadBase) {
        BigDecimal c = cantidadBase != null ? cantidadBase.abs() : BigDecimal.ZERO;
        if (c.stripTrailingZeros().scale() > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    producto.getNombre() + " maneja serial: la cantidad tiene que ser entera (cada unidad es un serial)");
        return c.intValueExact();
    }

    private void rastro(String origen, Long detalleId, SerialProductoEntity serial, String estadoAnterior,
            BodegaEntity bodegaAnterior) {
        DocumentoSerialEntity fila = new DocumentoSerialEntity();
        fila.setOrigen(origen);
        fila.setDetalleId(detalleId);
        fila.setSerial(serial);
        fila.setEstadoAnterior(estadoAnterior);
        fila.setBodegaAnteriorId(bodegaAnterior != null ? bodegaAnterior.getId() : null);
        fila.setSucursalAnteriorId(bodegaAnterior != null && bodegaAnterior.getSucursal() != null
                ? bodegaAnterior.getSucursal().getId() : null);
        documentoSerialRepository.save(fila);
    }
}

package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.InventarioConsumoComponenteEntity;
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.MovimientoInventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.BodegaEntity;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteJPARepository;
import com.cloud_technological.aura_pos.repositories.movimiento_inventario.MovimientoInventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Salida de inventario de un producto con receta fuera de la venta (merma,
 * obsequio). El padre no tiene stock propio: lo que sale son sus componentes,
 * con la misma regla que VentaServiceImpl — un solo nivel y solo los
 * componentes que manejan inventario.
 *
 * Lo consumido queda en inventario_consumo_componente: anular devuelve eso y no
 * lo que diga la receta ese día, y el asiento acredita el inventario de cada
 * componente.
 *
 * No abre transacción propia: corre dentro de la del documento que lo llama.
 */
@Service
@RequiredArgsConstructor
public class ConsumoComposicionService {

    /** Bloquea el saldo (bodega, producto) antes de moverlo: ver InventarioStockService. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.InventarioStockService inventarioStock;

    public static final String ORIGEN_MERMA = "MERMA";
    public static final String ORIGEN_OBSEQUIO = "OBSEQUIO";
    public static final String ORIGEN_CONSUMO_INTERNO = "CONSUMO_INTERNO";

    /** Mismo scale con el que la receta guarda el consumo por unidad. */
    private static final int SCALE_CANTIDAD = 6;
    private static final int SCALE_DINERO = 2;

    private final ProductoComposicionJPARepository composicionRepository;
    private final InventarioJPARepository inventarioRepository;
    private final MovimientoInventarioJPARepository movimientoRepository;
    private final InventarioConsumoComponenteJPARepository consumoRepository;
    private final LoteStockService loteStock;

    /**
     * Un componente que sale por cierta cantidad del padre.
     *
     * @param stockDisponible stock del componente en la bodega; null si no
     *                        tiene fila de inventario ahí
     */
    public record Consumo(ProductoEntity componente, BigDecimal cantidad, BigDecimal costoUnitario,
            BigDecimal stockDisponible) {

        public BigDecimal costoTotal() {
            return cantidad.multiply(costoUnitario).setScale(SCALE_DINERO, RoundingMode.HALF_UP);
        }

        public boolean suficiente() {
            return stockDisponible != null
                    && (Boolean.TRUE.equals(componente.getPermitirStockNegativo())
                            || stockDisponible.compareTo(cantidad) >= 0);
        }
    }

    public boolean tieneComposicion(Long productoId) {
        return composicionRepository.existsByProductoPadreId(productoId);
    }

    /** Componentes con inventario que salen por {@code cantidad} unidades del padre. */
    public List<Consumo> explotar(Long productoPadreId, BigDecimal cantidad, Long bodegaId) {
        List<Consumo> consumos = new ArrayList<>();
        for (ProductoComposicionEntity linea
                : composicionRepository.findByProductoPadreIdOrderByOrdenAscIdAsc(productoPadreId)) {
            ProductoEntity hijo = linea.getProductoHijo();
            // Igual que en venta: lo que no maneja inventario no se descuenta.
            if (!Boolean.TRUE.equals(hijo.getManejaInventario())) {
                continue;
            }

            BigDecimal porUnidad = linea.getCantidad() != null ? linea.getCantidad() : BigDecimal.ZERO;
            BigDecimal stock = inventarioStock
                    .bloquear(bodegaId, hijo.getId())
                    .map(InventarioEntity::getStockActual)
                    .orElse(null);

            consumos.add(new Consumo(
                    hijo,
                    cantidad.multiply(porUnidad).setScale(SCALE_CANTIDAD, RoundingMode.HALF_UP),
                    hijo.getCosto() != null ? hijo.getCosto() : BigDecimal.ZERO,
                    stock));
        }
        return consumos;
    }

    public void validarStock(ProductoEntity padre, List<Consumo> consumos) {
        if (consumos.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La receta de '" + padre.getNombre() + "' no tiene componentes que manejen inventario: "
                    + "no hay nada que descontar");
        }

        for (Consumo consumo : consumos) {
            ProductoEntity hijo = consumo.componente();
            if (consumo.stockDisponible() == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El componente '" + hijo.getNombre() + "' de '" + padre.getNombre()
                        + "' no tiene inventario en esta bodega");
            }
            if (!consumo.suficiente()) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente del componente '" + hijo.getNombre() + "' (receta de '"
                        + padre.getNombre() + "'). Disponible: " + consumo.stockDisponible()
                        + " | Requerido: " + consumo.cantidad());
            }
        }
    }

    /**
     * Descuenta los componentes, deja el kardex y el registro de lo consumido.
     *
     * @return costo de la línea: la suma de lo que valen hoy sus componentes
     */
    public BigDecimal consumir(String origen, Long detalleId, Integer empresaId, BodegaEntity bodega,
            ProductoEntity padre, List<Consumo> consumos, String tipoMovimiento, String referencia) {
        BigDecimal costo = BigDecimal.ZERO;

        for (Consumo consumo : consumos) {
            ProductoEntity hijo = consumo.componente();
            InventarioEntity inventario = buscarInventario(bodega, hijo);

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.subtract(consumo.cantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            InventarioConsumoComponenteEntity registro = new InventarioConsumoComponenteEntity();
            registro.setEmpresaId(empresaId);
            registro.setOrigen(origen);
            registro.setDetalleId(detalleId);
            registro.setProductoPadreId(padre.getId());
            registro.setProductoHijo(hijo);
            registro.setCantidad(consumo.cantidad());
            registro.setCostoUnitario(consumo.costoUnitario());
            registro.setCreatedAt(LocalDateTime.now());
            consumoRepository.save(registro);

            // El componente con lotes sale del que vence primero. Solo la merma
            // saca vencidos; el resto sigue la regla de la empresa.
            boolean permitirVencidos = ORIGEN_MERMA.equals(origen) || !loteStock.bloqueaVencidos(empresaId);
            List<LoteStockService.Asignacion> lotes = loteStock.salidaDocumento(LoteStockService.COMPONENTE,
                    registro.getId(), hijo, bodega, empresaId, consumo.cantidad(), null, permitirVencidos,
                    Boolean.TRUE.equals(hijo.getPermitirStockNegativo()));
            String refComponente = referencia + " [componente de " + padre.getNombre() + "]";
            loteStock.kardex(lotes, consumo.cantidad().negate(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, hijo, lote, cant, ant, nuevo,
                            consumo.costoUnitario(), tipoMovimiento, refComponente));

            costo = costo.add(consumo.costoTotal());
        }
        return costo;
    }

    /**
     * Reingresa lo que consumió una línea al anular el documento.
     *
     * @return false si la línea no salió por receta (el llamador devuelve el
     *         producto como siempre)
     */
    public boolean revertir(String origen, Long detalleId, BodegaEntity bodega, ProductoEntity padre,
            String tipoMovimiento, String referencia) {
        List<InventarioConsumoComponenteEntity> consumos =
                consumoRepository.findByOrigenAndDetalleIdOrderByIdAsc(origen, detalleId);
        if (consumos.isEmpty()) {
            return false;
        }

        for (InventarioConsumoComponenteEntity consumo : consumos) {
            ProductoEntity hijo = consumo.getProductoHijo();
            InventarioEntity inventario = buscarInventario(bodega, hijo);

            BigDecimal saldoAnterior = inventario.getStockActual();
            BigDecimal saldoNuevo = saldoAnterior.add(consumo.getCantidad());
            inventario.setStockActual(saldoNuevo);
            inventario.setUpdatedAt(LocalDateTime.now());
            inventarioRepository.save(inventario);

            List<LoteStockService.Asignacion> lotes = loteStock.revertirDocumento(LoteStockService.COMPONENTE,
                    consumo.getId(), true, "anular el documento");
            String refComponente = referencia + " [componente de " + padre.getNombre() + "]";
            loteStock.kardex(lotes, consumo.getCantidad(), saldoAnterior,
                    (lote, cant, ant, nuevo) -> registrarMovimiento(bodega, hijo, lote, cant, ant, nuevo,
                            consumo.getCostoUnitario(), tipoMovimiento, refComponente));
        }
        return true;
    }

    public List<InventarioConsumoComponenteEntity> consumosDe(String origen, Long detalleId) {
        return consumoRepository.findByOrigenAndDetalleIdOrderByIdAsc(origen, detalleId);
    }

    private InventarioEntity buscarInventario(BodegaEntity bodega, ProductoEntity producto) {
        return inventarioStock
                .bloquear(bodega.getId(), producto.getId())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "El componente '" + producto.getNombre() + "' no tiene inventario en la bodega "
                        + bodega.getNombre()));
    }

    private void registrarMovimiento(BodegaEntity bodega, ProductoEntity producto,
            com.cloud_technological.aura_pos.entity.LoteEntity lote, BigDecimal cantidad,
            BigDecimal saldoAnterior, BigDecimal saldoNuevo, BigDecimal costo, String tipo, String referencia) {
        MovimientoInventarioEntity movimiento = new MovimientoInventarioEntity();
        movimiento.setBodega(bodega);
        movimiento.setSucursal(bodega.getSucursal());
        movimiento.setProducto(producto);
        movimiento.setLote(lote);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(saldoNuevo);
        movimiento.setCostoHistorico(costo);
        movimiento.setReferenciaOrigen(referencia);
        movimiento.setCreatedAt(LocalDateTime.now());
        movimientoRepository.save(movimiento);
    }
}

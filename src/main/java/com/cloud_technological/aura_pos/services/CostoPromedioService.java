package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.inventario.CostoPromedioQueryRepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;

/**
 * Costo promedio ponderado del producto ({@code producto.costo}).
 *
 * <p>Antes cada compra sobrescribía el costo con su precio: si había 10 a $100
 * y llegaban 10 a $200, las 20 pasaban a costar $200 y el costo de ventas, la
 * utilidad y el inventario valorizado salían inflados (o al revés si bajaba).
 * Ahora la entrada se mezcla con lo que había: 20 a $150.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Es por producto y empresa, sobre el stock de todas las bodegas: un
 *       traslado no cambia el costo.</li>
 *   <li>Solo lo mueven los documentos que traen un costo propio: compra
 *       (neto de descuento, con su parte de los fletes), su nota crédito, y las
 *       reversiones de ambos al anular o editar. Las salidas (venta, merma…)
 *       salen al promedio y no lo cambian.</li>
 *   <li>Si no había existencias (o eran negativas), el costo es el de la
 *       entrada. Si después del movimiento no queda existencia, se conserva el
 *       último promedio.</li>
 * </ul>
 *
 * <p>Se llama DESPUÉS de mover el inventario del documento: lee el stock ya
 * actualizado y deduce el que había restando la cantidad del movimiento.
 */
@Service
public class CostoPromedioService {

    private static final int ESCALA = 6;

    private final CostoPromedioQueryRepository queryRepo;
    private final ProductoJPARepository productoRepo;

    public CostoPromedioService(CostoPromedioQueryRepository queryRepo, ProductoJPARepository productoRepo) {
        this.queryRepo = queryRepo;
        this.productoRepo = productoRepo;
    }

    /**
     * Aplica al promedio un movimiento ya reflejado en el inventario.
     *
     * @param productoId producto
     * @param cantidad   unidades base del movimiento, con signo (+ entra, − sale)
     * @param valor      valor total del movimiento, con el mismo signo
     */
    public void aplicar(Long productoId, BigDecimal cantidad, BigDecimal valor) {
        if (productoId == null || cantidad == null || cantidad.signum() == 0) return;

        // El inventario del documento puede estar aún en el contexto de JPA:
        // se escribe antes de sumar el stock por SQL.
        productoRepo.flush();
        BigDecimal costoActual = queryRepo.bloquearYLeerCosto(productoId);
        if (costoActual == null) return;
        BigDecimal stockDespues = queryRepo.stockTotal(productoId);

        BigDecimal nuevo = calcular(stockDespues, cantidad, valor != null ? valor : BigDecimal.ZERO, costoActual);
        if (nuevo.compareTo(costoActual) == 0) return;

        ProductoEntity producto = productoRepo.findById(productoId).orElse(null);
        if (producto == null) return;
        producto.setCosto(nuevo);
        productoRepo.save(producto);
    }

    /**
     * Fórmula pura del promedio móvil.
     *
     * @param stockDespues existencia total ya con el movimiento aplicado
     * @param cantidad     cantidad del movimiento (con signo)
     * @param valor        valor del movimiento (con signo)
     * @param costoActual  promedio vigente antes del movimiento
     */
    public static BigDecimal calcular(BigDecimal stockDespues, BigDecimal cantidad, BigDecimal valor,
            BigDecimal costoActual) {
        BigDecimal costo = costoActual != null ? costoActual : BigDecimal.ZERO;
        BigDecimal stockAntes = stockDespues.subtract(cantidad);

        // Sin existencias previas que promediar: manda el costo de la entrada.
        if (stockAntes.signum() <= 0) {
            if (cantidad.signum() > 0) {
                return valor.divide(cantidad, ESCALA, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            }
            return costo;
        }
        // No queda nada que valorar: se conserva el último promedio.
        if (stockDespues.signum() <= 0) return costo;

        BigDecimal valorDespues = stockAntes.multiply(costo).add(valor);
        if (valorDespues.signum() <= 0) return costo;
        return valorDespues.divide(stockDespues, ESCALA, RoundingMode.HALF_UP);
    }
}

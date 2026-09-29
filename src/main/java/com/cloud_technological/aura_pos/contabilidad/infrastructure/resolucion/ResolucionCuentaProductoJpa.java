package com.cloud_technological.aura_pos.contabilidad.infrastructure.resolucion;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentaProducto;
import com.cloud_technological.aura_pos.contabilidad.application.resolucion.ResolucionCuentas;
import com.cloud_technological.aura_pos.entity.CategoriaContableProductoEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CategoriaContableProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Cadena de resolución por producto (E4):
 * 1. override del producto (excepción puntual)
 * 2. categoría contable del producto (regla normal)
 * 3. concepto de la empresa (INGRESOS_VENTAS/COSTO_VENTAS/INVENTARIO) —
 *    idéntico al comportamiento previo, cero fricción sin configurar.
 */
@Component
@RequiredArgsConstructor
public class ResolucionCuentaProductoJpa implements ResolucionCuentaProducto {

    private final ProductoJPARepository productoRepo;
    private final CategoriaContableProductoJPARepository categoriaRepo;
    private final ResolucionCuentas cuentas;

    @Override
    public CuentasProducto resolver(Long productoId, Integer empresaId) {
        ProductoEntity producto = productoId != null
                ? productoRepo.findByIdAndEmpresaId(productoId, empresaId).orElse(null)
                : null;
        CategoriaContableProductoEntity categoria = null;
        if (producto != null && producto.getCategoriaContableId() != null) {
            categoria = categoriaRepo
                    .findByIdAndEmpresaId(producto.getCategoriaContableId(), empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                    .orElse(null);
        }

        Long ingreso = primeroNoNulo(
                producto != null ? producto.getCuentaIngresoId() : null,
                categoria != null ? categoria.getCuentaIngresoId() : null);
        if (ingreso == null) {
            ingreso = cuentas.resolver(empresaId, ConceptoContable.INGRESOS_VENTAS);
        }

        Long costo = primeroNoNulo(
                producto != null ? producto.getCuentaCostoId() : null,
                categoria != null ? categoria.getCuentaCostoId() : null);
        if (costo == null) {
            costo = cuentas.resolver(empresaId, ConceptoContable.COSTO_VENTAS);
        }

        Long inventario = primeroNoNulo(
                producto != null ? producto.getCuentaInventarioId() : null,
                categoria != null ? categoria.getCuentaInventarioId() : null);
        if (inventario == null) {
            inventario = cuentas.resolver(empresaId, ConceptoContable.INVENTARIO);
        }

        Long devolucion = categoria != null && categoria.getCuentaDevolucionId() != null
                ? categoria.getCuentaDevolucionId() : ingreso;

        boolean esServicio = categoria != null && "SERVICIO".equals(categoria.getTipo());

        return new CuentasProducto(ingreso, costo, inventario, devolucion, esServicio);
    }

    private Long primeroNoNulo(Long a, Long b) {
        return a != null ? a : b;
    }
}

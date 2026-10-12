# Plan: línea "Contabilidad" que sí factura

**Fecha:** 2026-10-07 · **Estado:** implementado 2026-10-09 (local, sin commit) · Continúa `docs/PLAN_PERFIL_EMPRESA.md`

## Por qué

Una empresa que contrata Aura "solo para contabilidad" igual vende, compra y paga. La diferencia está en
**dónde hace la factura**. Hoy la plantilla de la línea `CONTABILIDAD` (`services/empresa/LineaUso.java`) no trae
facturación: esa empresa no podría facturar ni sus servicios.

## Qué hace un contador (referencia)

El contador no vende: registra lo que la empresa hizo y con eso saca impuestos y estados financieros.

1. **Al empezar (una vez):** plan de cuentas, terceros, saldos iniciales (importador Excel).
2. **Durante el mes:**

   | Qué pasó en la empresa | Qué registra | Pantalla en Aura |
   |---|---|---|
   | Vendió (producto o servicio) | Factura de venta: ingreso, IVA, CxC | Ventas → Facturas |
   | Le pagaron | Recibo de caja | Tesorería → Recaudos |
   | Compró o le facturaron un gasto | Compra/gasto: costo, IVA descontable, retenciones, CxP | Compras / Gastos |
   | Le compró a un no obligado a facturar | Documento soporte | Compras → Documentos soporte |
   | Pagó | Comprobante de egreso | Tesorería → Egresos |
   | Pagó nómina | Liquidación | Nómina |
   | Ajustes | Nota contable | Contabilidad → Notas |

3. **Cierre de mes:** conciliación bancaria, depreciaciones, diferidos y provisiones, revisar borradores, cerrar período.
4. **Impuestos:** retención (350, mensual), IVA (300, bimestral o cuatrimestral), ICA, renta y exógena (anuales).
5. **Reportes:** balance de prueba, libros, estados financieros; cierre anual.

## Dos tipos de cliente "solo contabilidad"

- **A. Factura en Aura** (el más común: la factura electrónica es obligatoria). Aura contabiliza cada venta,
  compra y pago solo; el contador revisa, ajusta y cierra. Es lo que más vende.
- **B. Factura en otro sistema** (facturador DIAN, Siigo…). El contador trae las ventas con el importador de
  documentos (`contabilidad.importar-datos`) o con notas contables.

## Brecha

La plantilla actual de `CONTABILIDAD` trae contabilidad, tesorería, cartera, cuentas, documento soporte, comprobantes
de caja y dos reportes. Le falta:

- `ventas.facturas` y `ventas.notas-credito/debito`.
- `catalogo.productos`: cada línea de factura exige `producto_id` (`factura_venta_detalle.producto_id NOT NULL`);
  un servicio es un producto con `tipo_producto = SERVICIO`.
- `compras.compras`.

## Propuesta

Que `CONTABILIDAD` sirva para una empresa de servicios u oficina que factura, compra y lleva sus libros (casos A y B),
**sin** POS, inventario, bodegas, lotes, seriales ni listas de precios. `COMERCIAL` queda para quien además maneja inventario.

### Tareas

1. **Plantilla** (`LineaUso.CONTABILIDAD`): agregar `ventas.facturas`, `ventas.notas-credito/debito`,
   `ventas.cotizaciones` (opcional), `catalogo.productos`, `catalogo.categorias`, `compras.compras`,
   `reportes.facturacion-electronica`.
2. **Mínimos:** dejar los actuales (plan de cuentas, notas). No exigir facturas: el cliente B no las usa.
3. **Formulario de producto sin inventario:** verificar que crear un ítem tipo SERVICIO no pida bodega, stock,
   presentaciones ni lista de precios cuando la empresa no tiene Inventario/Precios activos.
4. **Factura de venta sin inventario:** verificar que emitir una FV con solo servicios no toque kardex ni pida bodega.
5. **Puesta en marcha** (`TableroInicioService`): para `CONTABILIDAD` agregar "Servicios/productos creados" y, si
   tiene facturación electrónica, "Rango de Factus", igual que hoy en POS/Comercial.
6. **Prueba unitaria:** que la plantilla de `CONTABILIDAD` incluya facturas y productos y no incluya
   `inventario.*` ni `principal.punto-de-venta`.
7. **Empresas ya creadas con la línea:** al editarlas y guardar con "completar módulos" se suman las pantallas
   nuevas (no se apaga nada). No hace falta migración.
8. **Manual `/ayuda`:** explicar en "Inicio" que Contabilidad incluye facturar servicios.

## Pendiente de la sesión anterior

- Se corrigió el 409 al crear empresa (`empresa.configuracion` se guarda como texto JSON). Falta **reintentar la
  creación** de la empresa `900452123` y seguir las 7 pruebas de `docs/PLAN_PERFIL_EMPRESA.md`.
- Nada de esto tiene commit todavía (back y front).

## Cómo quedó (2026-10-09)

- **Plantilla** (`LineaUso.CONTABILIDAD`): suma `compras.compras`, `ventas.facturas`, `ventas.notas-credito/debito`,
  `ventas.cotizaciones`, `catalogo.productos`, `catalogo.categorias`, `catalogo.unidades` y
  `reportes.facturacion-electronica`. Sin `principal.*`, `inventario.*`, `precios.*` ni marcas/presentaciones.
- **Mínimos**: sin cambio (plan de cuentas y notas contables).
- **Producto sin inventario**: verificado en código. La clasificación `SERVICIO` apaga inventario, lotes y seriales
  (`ProductoServiceImpl.aplicarClasificacion`); el formulario no llama a inventario ni a listas de precios, y las
  lecturas de marcas/unidades/categorías están en `PermisoRutas.LECTURA_LIBRE`.
- **Factura sin inventario**: verificado en código. `VentaServiceImpl` solo toca stock y kardex si
  `maneja_inventario`; la bodega se resuelve con la principal de la sede, que toda sede crea al nacer
  (también la de los gestionados).
- **Puesta en marcha**: el grupo de productos aparece siempre que la empresa tenga la línea Contabilidad. Sin POS ni
  Comercial se llama "Facturación" (código `FACTURACION`) y pide "Servicios o productos creados" y, si tiene
  facturación electrónica, el rango de Factus.
- **Pruebas**: `LineaUsoTest` (3) + `ConfiguracionEmpresaServiceTest` (9) en verde.
- **Manual**: Inicio → "Un tablero por línea de uso" explica que Contabilidad factura servicios.
- **Empresas existentes**: editarlas y guardar con "completar módulos" suma las pantallas nuevas. Sin migración.
- No probado en navegador.

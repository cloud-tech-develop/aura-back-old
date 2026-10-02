# Plan de pruebas — Fases 1 a 4 (plan World Office)

Fecha: 2026-09-30 · Para probar en **local** (base `aura-pos` @ 127.0.0.1). Nada de esto está en producción.

Cada caso dice qué hacer, qué debe pasar y dónde mirarlo. Marca ✅ o ❌ y anota lo raro en la columna de notas. Donde dice "asiento", míralo en Contabilidad › Asientos (buscar por el número del documento) o con **Ver asiento** en la compra.

---

## 0. Preparación (una vez)

| # | Paso | Listo |
|---|---|---|
| 0.1 | Backup de la base local: `pg_dump -h 127.0.0.1 -U postgres aura-pos > aura-pos-antes-f1f4.sql` | |
| 0.2 | Migraciones en el proyecto Laravel (`aura-pos-migracion-old`, `.env` apuntando a **127.0.0.1**): `php artisan migrate`. Deben correr **000184 a 000187** (costo 6 decimales, catálogo unificado, activos completos, traslado/fusión). | |
| 0.3 | Menú: correr `docs/sql/menu_submodulos_f2_f4.sql` (Categorías Contables, Parametrización Contable, Balance de Prueba, Herramientas del Contador, Sugerido de Compra). | |
| 0.4 | Backend: detener el que esté corriendo y compilar limpio `mvnw clean compile` (si VS Code dejó clases a medias, Spring dice que falta un bean). Arrancar. | |
| 0.5 | Frontend: `ng serve` y entrar con un usuario **ADMIN** y otro **SUPER_ADMIN**. | |
| 0.6 | Tener abierto un mes contable (Contabilidad › Períodos) y conocer uno cerrado para las pruebas de bloqueo. | |

Datos que conviene crear antes: un proveedor, un cliente, un producto mercancía con presentación "Paca ×24" y otro con lotes.

---

## Fase 1 · Integridad

### 1A. Seguridad de roles
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.1 | Como ADMIN, editar un usuario y ponerle rol SUPER_ADMIN | Lo rechaza: solo un SUPER_ADMIN asigna SUPER_ADMIN | |
| 1.2 | Como SUPER_ADMIN, intentar asignar PLATFORM_ADMIN | Lo rechaza | |
| 1.3 | Editar un empleado con cargo escrito "PLATFORM_ADMIN" | Su usuario no cambia de rol | |
| 1.4 | Asignar a un usuario una sucursal de otra empresa (por API) | "Sucursal no encontrada" | |

### 1B. Costo promedio
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.5 | Producto sin stock: comprar 10 a $100 | Costo del producto = 100 | |
| 1.6 | Comprar 10 más a $200 | Costo = 150 | |
| 1.7 | Anular la compra del 1.6 | Costo vuelve a 100 | |
| 1.8 | Compra con descuento de línea y flete | El costo sube con el flete y baja con el descuento | |

### 1C. Stock y consecutivos con bloqueo
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.9 | Producto con 1 unidad, sin stock negativo. Dos pestañas del POS venden esa unidad al mismo tiempo | Una vende, la otra dice que no hay stock | |
| 1.10 | Dos ventas guardadas casi al tiempo | Consecutivos distintos, sin huecos raros | |
| 1.11 | Inventario › Stock: cambiar el stock a mano sin motivo | Pide motivo | |
| 1.12 | Con motivo | Kardex con AJUSTE_MANUAL_ENTRADA/SALIDA y el motivo | |

### 1D. Período cerrado y reproceso
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.13 | Compra, gasto, recibo y abono con fecha de un mes cerrado | Los rechaza con "El período contable … está cerrado" | |
| 1.14 | Editar una compra (cambiar cantidad) | Asiento viejo reversado + asiento nuevo con los valores nuevos | |
| 1.15 | Contabilidad › Revisión › pestaña "Sin asiento" | Lista documentos sin asiento; "Reprocesar" los contabiliza | |

### 1E. Anulaciones completas
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.16 | Anular una compra de contado pagada por banco | Vuelve la plata al banco, CxP anulada, asiento reversado | |
| 1.17 | Anular una compra a crédito con un abono | La niega: anule el abono primero | |
| 1.18 | Anular una venta pagada por transferencia | Revierte el banco | |
| 1.19 | Anular una venta con devolución vigente | La niega | |
| 1.20 | Eliminar un gasto | Anulación completa (caja/banco/CxP) | |
| 1.21 | Devolución de una venta con descuento de línea y general | Reembolso = valor pagado proporcional, no el precio lleno | |

### 1F. Factura electrónica (sandbox Factus)
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 1.22 | Facturar venta con IVA 19 %, descuento de línea, pago con tarjeta, cliente persona jurídica | Factus acepta; en el PDF salen IVA, descuento, forma y medio de pago correctos | |
| 1.23 | Simular timeout (desconectar red al enviar) | La venta queda DESCONOCIDO, no se reenvía sola | |

---

## Fase 2 · Catálogo unificado

### 2A. Clasificación del producto
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 2.1 | Nuevo producto → "¿Qué es este ítem?" = **Activo fijo** (ej. "Computador portátil") | Inventario se apaga y bloquea; "Visible en POS" apagado; en Contabilidad la cuenta se llama "Cuenta del activo (15)" | |
| 2.2 | Crear también: **Gasto** ("Papelería"), **Diferido** ("Póliza anual"), **Servicio** ("Instalación") | Se guardan; el listado muestra la clase en "Uso / clase" | |
| 2.3 | Filtro "Todo el catálogo" del listado por Activo fijo | Solo salen los activos | |
| 2.4 | POS | No aparecen el activo, el gasto ni el diferido; el servicio sí | |
| 2.5 | Mermas / obsequios: buscar el activo | No aparece (solo mercancía) | |
| 2.6 | Producto mercancía **con stock** → cambiar a Gasto | "El producto tiene existencias: no puede dejar de ser mercancía…" | |
| 2.7 | Contabilidad › Categorías Contables → nueva tipo **Activo fijo** "Equipo de cómputo": cuenta 1528, dep. 1592, gasto 5160, vida 36 | Se guarda; "Copiar" crea otra igual | |
| 2.8 | Asignar esa categoría al computador | En su pestaña Contabilidad muestra 1528, 1592, 5160, 36 meses | |

### 2B. Compra mixta (la prueba central)
Una sola compra a crédito con: 10 und de mercancía a $2.000, **3** computadores a $1.000.000, papelería $50.000, póliza $1.200.000, flete $30.000.

| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 2.9 | Al agregar las líneas | Cada línea no-mercancía muestra la etiqueta azul de lo que hará | |
| 2.10 | **Ver asiento** antes de guardar | Débitos: 1435 mercancía, 1528 computadores, 5195 papelería, 1705 póliza, IVA descontable; crédito 2205 proveedor. Flete repartido proporcional. Cuadra | |
| 2.11 | Guardar | Stock sube **solo** en la mercancía; kardex solo de la mercancía | |
| 2.12 | Activos Fijos | **3 fichas** AF-…, cada una ≈ $1.000.000 + su parte del flete; la suma = débito a 1528 del asiento | |
| 2.13 | Base / API: diferido creado por la póliza (12 meses si la categoría no dice otra cosa) | Existe VIGENTE | |
| 2.14 | `POST /api/contabilidad/devengo/diferidos/amortizar` (Swagger) | Genera la cuota del mes: DB gasto · CR 1705 por 1/12 | |
| 2.15 | Editar la compra (cambiar a 2 computadores) antes de depreciar | Quedan 2 fichas (las 3 viejas eliminadas) | |
| 2.16 | Depreciar un mes y luego intentar anular la compra | "No se puede anular la compra: un activo fijo que creó esta compra ya se depreció…" | |
| 2.17 | Nota crédito de la compra con la línea del computador | "… es activo fijo: no se acredita con nota crédito…" | |
| 2.18 | Compra solo de mercancía (sin activos) | Igual que antes: inventario, costo promedio, asiento a 1435 | |

### 2C. Presentaciones, stock y reorden
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 2.19 | Compra: producto Paca ×24, 4 pacas **+ 2 sueltas** a $48.000 la paca | Cantidad base 98; subtotal = 4×48.000 + 2×2.000 = $196.000 | |
| 2.20 | Inventario › Stock de ese producto | Debajo del número: "4 Pacas + 2 und" | |
| 2.21 | Editar ese stock: mínimo 30, reorden 50, máximo 200 | Guarda; reorden 20 < mínimo 30 lo rechaza; máximo 40 < reorden lo rechaza | |
| 2.22 | Bajar el stock a 40 (ajuste con motivo) → Compras › Sugerido de Compra | Sale con "Pedir 160", agrupado por el último proveedor; CSV descarga | |
| 2.23 | Servicio "Mantenimiento" con presentación "Año ×12" | Se puede vender por mes o por año | |

---

## Fase 3 · Activos fijos

| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 3.1 | Ficha de un computador: placa, serial, responsable (tercero), póliza | Guarda y se ve en la pestaña Datos | |
| 3.2 | "Calcular Depreciación" del mes de la compra | Un solo asiento DP con una línea de gasto (con centro de costo) y una de acumulada por activo | |
| 3.3 | Cuota de un activo de $1.000.000 a 36 meses | $27.777,78 cada mes, **fija** (antes bajaba cada mes) | |
| 3.4 | Ficha › Depreciación | Historial con el mes y la proyección hasta el final | |
| 3.5 | Depreciar el mismo mes otra vez | No duplica | |
| 3.6 | Depreciar un mes anterior al "Inicio de la depreciación" | Ese activo no se deprecia | |
| 3.7 | Depreciar dos meses y reversar el primero | "Hay meses posteriores depreciados: reverse primero el más reciente" | |
| 3.8 | Reversar el último | Contraasiento con la misma fecha; la acumulada baja | |
| 3.9 | Activo por **unidades de producción** (100.000 horas): en Calcular Depreciación escribir 1.500 horas | Cuota = costo × 1.500 / 100.000 | |
| 3.10 | Ficha › Adiciones: $300.000, +12 meses, pagado desde caja | Asiento DB 15xx · CR caja; costo y vida suben; la proyección cambia | |
| 3.11 | Ficha › Mantenimientos: preventivo con próximo | Queda en el historial, sin asiento | |
| 3.12 | **Dar de baja** un activo a mitad de vida | Asiento: DB dep. acumulada + DB 5310 (lo pendiente) · CR costo | |
| 3.13 | **Vender** otro por más de su valor en libros, con IVA 19 %, cobrado en banco | DB banco (precio + IVA) · DB acumulada · CR costo · CR IVA · CR 4245 utilidad | |
| 3.14 | Vender por menos del valor en libros | La diferencia va a DB 5310 | |
| 3.15 | Ficha del vendido › "Anular retiro" | Contraasiento; el activo vuelve a vigente | |
| 3.16 | Activos › Informe: agrupar por centro de costo y por responsable; elegir el mes | Totales cuadran con el listado; CSV | |
| 3.17 | Editar el costo de una ficha que vino de una compra | "El costo de este activo viene de la compra #…" | |

---

## Fase 4 · Contabilidad para el contador

### 4A. Parametrización
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 4.1 | Contabilidad › Parametrización › Conceptos: cambiar "Gasto general" a otra 5xxx | Guarda; en Historial queda antes/ahora | |
| 4.2 | El dropdown de un concepto de inventario | Solo ofrece cuentas 14xx | |
| 4.3 | Formas de pago: asignar cuenta a "Crédito" y **Copiar a…** otras dos | Las dos quedan con la misma cuenta | |
| 4.4 | Impuestos: cambiar la cuenta del IVA descontable 19 % | Una compra nueva con IVA usa esa cuenta (Ver asiento) | |
| 4.5 | Modo → Revisión; registrar una venta | El asiento queda en BORRADOR (Revisión de Comprobantes); volver a Automático | |

### 4B. Vista previa
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 4.6 | En una compra con retención en la fuente y pago de contado, **Ver asiento** y luego guardar | El asiento guardado es idéntico al de la vista previa | |
| 4.7 | Ver asiento con un producto cuya categoría no tiene cuenta y la empresa tampoco | Muestra el error de cuenta no configurada, sin guardar nada | |

### 4C. Saldos iniciales
(En una empresa de prueba **sin** apertura, o borrando la apertura con "Rehacer" si no hay otros asientos.)

| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 4.8 | "Traer saldos desde" Inventario, Activos fijos, Cartera y Proveedores | Aparecen las líneas con su cuenta, tercero y origen; la depreciación acumulada sale al crédito | |
| 4.9 | El valor de Inventario | = stock × costo promedio de Inventario › reporte | |
| 4.10 | Tocar de nuevo una fuente cargada | Quita sus líneas | |
| 4.11 | Guardar sin cuadrar | Pregunta si la diferencia es patrimonio; "Revisar saldos" no guarda | |
| 4.12 | Confirmar | Asiento de apertura con la diferencia en la cuenta de ajuste | |

### 4D. Balance de prueba
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 4.13 | Mes actual, nivel Subcuenta | Débitos = créditos ("el libro cuadra"); clases en negrilla | |
| 4.14 | Saldo final de 1105 / 1110 | Igual al del Balance General y al libro mayor | |
| 4.15 | Comparar con mes anterior | Columnas Comparado, Variación, %; el comparado = saldo final del mes anterior | |
| 4.16 | Filtrar por un tercero | Solo movimientos de ese tercero | |
| 4.17 | Exportar CSV | Abre en Excel con tildes bien | |

### 4E. Herramientas del contador
| # | Qué hacer | Qué debe pasar | Nota |
|---|---|---|---|
| 4.18 | Registrar un gasto a 5195 que era 5135; Traslado 5195 → 5135 en el mes, "Ver qué se mueve" | Cuenta los movimientos y el valor | |
| 4.19 | Trasladar con motivo | El mayor de 5195 baja y el de 5135 sube; queda en la bitácora | |
| 4.20 | Traslado con un rango que toca un mes cerrado | Avisa los meses y no deja trasladar | |
| 4.21 | Crear un cliente duplicado, venderle algo; Fusión duplicado → original | "Ver qué se mueve" lista ventas, cartera, asientos…; al fusionar, todo pasa al original y el duplicado queda inactivo | |
| 4.22 | Fusionar dos terceros que son empleados | "Los dos terceros tienen ficha de empleado…" | |

---

## Pendientes conocidos (no son fallas de las pruebas)
- Impuestos múltiples por producto (bolsa, ultraprocesados): fuera de estas fases por decisión.
- La vista previa del asiento está en compras; gasto y otros documentos quedan para después.
- La amortización de diferidos corre el día 1 a las 3 a. m.; para probarla a demanda se usa el endpoint del 2.14.
- 5 pruebas de `VentaGeneradorTest` ya fallaban antes de estas fases (esperan un texto de descripción viejo).

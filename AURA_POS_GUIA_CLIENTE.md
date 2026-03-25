# AURA POS — Guía de Usuario
**Cloud Technological**

---

## TABLA DE CONTENIDO

1. [¿Qué es AURA POS?](#1-qué-es-aura-pos)
2. [¿Cómo ingresar al sistema?](#2-cómo-ingresar-al-sistema)
3. [Módulo: Empresa y Sucursales](#3-módulo-empresa-y-sucursales)
4. [Módulo: Usuarios](#4-módulo-usuarios)
5. [Módulo: Productos](#5-módulo-productos)
6. [Módulo: Inventario](#6-módulo-inventario)
7. [Módulo: Caja y Turnos](#7-módulo-caja-y-turnos)
8. [Módulo: Ventas](#8-módulo-ventas)
9. [Módulo: Compras](#9-módulo-compras)
10. [Módulo: Cotizaciones](#10-módulo-cotizaciones)
11. [Módulo: Clientes y Proveedores](#11-módulo-clientes-y-proveedores)
12. [Módulo: Cuentas por Cobrar y Pagar](#12-módulo-cuentas-por-cobrar-y-pagar)
13. [Módulo: Comisiones](#13-módulo-comisiones)
14. [Módulo: Factura Electrónica DIAN](#14-módulo-factura-electrónica-dian)
15. [Módulo: Precios y Descuentos](#15-módulo-precios-y-descuentos)
16. [Módulo: Reportes](#16-módulo-reportes)
17. [¿Qué hacer cuando algo no funciona?](#17-qué-hacer-cuando-algo-no-funciona)

---

## 1. ¿Qué es AURA POS?

AURA POS es un programa para manejar su negocio desde el computador o tablet. Con él puede:

- **Vender** productos y registrar los pagos.
- **Controlar** cuántos productos tiene en bodega.
- **Manejar** lo que le deben sus clientes y lo que usted le debe a sus proveedores.
- **Generar** facturas electrónicas válidas ante la DIAN.
- **Ver reportes** de cómo va su negocio.

Todo queda guardado automáticamente. No necesita llevar cuadernos ni hojas de cálculo por separado.

---

## 2. ¿Cómo ingresar al sistema?

Al abrir el programa verá una pantalla de inicio de sesión.

**Pasos para ingresar:**
1. Escriba su **correo electrónico**.
2. Escriba su **contraseña**.
3. Haga clic en **Ingresar**.

La sesión dura **3 días**. Pasado ese tiempo el sistema le pedirá que ingrese de nuevo.

---

### ¿Olvidó su contraseña?

1. En la pantalla de inicio haga clic en **"Olvidé mi contraseña"**.
2. Escriba su correo electrónico.
3. Revise su correo — le llegará un mensaje con un enlace.
4. Haga clic en ese enlace y escriba su nueva contraseña.

---

### Errores al ingresar

| Lo que ve en pantalla | Qué pasó | Qué hacer |
|-----------------------|----------|-----------|
| "Credenciales incorrectas" | El correo o la contraseña están mal escritos | Verifique que no tenga mayúsculas o espacios de más |
| "Su sesión ha expirado" | Lleva más de 3 días sin ingresar | Vuelva a escribir su correo y contraseña |
| "Sin acceso a esta sección" | Su usuario no tiene permiso para esa parte | Comuníquese con el administrador del negocio |
| El correo de recuperación no llega | Puede estar en la carpeta de spam | Revise la carpeta "No deseados" o "Spam" de su correo |

---

## 3. Módulo: Empresa y Sucursales

### ¿Para qué sirve?

Aquí se guarda la información de su negocio: nombre, NIT, dirección, teléfono y logo. Esta información aparece en los documentos y facturas que genera el sistema.

Si su negocio tiene **varias sedes o puntos de venta**, cada una se crea como una **sucursal**. Cada sucursal maneja su propio inventario y su propia caja.

### ¿Qué puede hacer aquí?

- Ver y editar los datos de la empresa.
- Crear, editar o desactivar sucursales.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| Las facturas salen sin logo | No se ha subido el logo de la empresa | Suba la imagen del logo en la configuración de la empresa |
| No puede generar facturas electrónicas | Faltan datos de la DIAN en la configuración | Comuníquese con el administrador para completar la configuración |

---

## 4. Módulo: Usuarios

### ¿Para qué sirve?

Aquí el administrador crea las cuentas de acceso para cada empleado. Cada persona tiene su propio usuario con su correo y contraseña.

### Tipos de usuario (roles)

| Rol | ¿Quién es? | ¿Qué puede hacer? |
|-----|------------|-------------------|
| **Administrador** | El dueño o gerente | Acceso completo al sistema |
| **Super Administrador** | Jefe de punto de venta | Acceso completo + cerrar turnos de otros cajeros |
| **Cajero** | El vendedor en caja | Solo puede vender, abrir su turno y consultar productos |

### ¿Qué puede hacer aquí?

- Crear un nuevo empleado con su nombre, correo y rol.
- Cambiar los datos de un empleado.
- Desactivar el acceso de un empleado que ya no trabaja en el negocio.
- Asignar a qué sucursal pertenece cada empleado.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "Ya existe un usuario con ese correo" | Ese correo ya fue registrado | Use otro correo electrónico |
| El empleado no puede ver cierta sucursal | No está asignado a esa sucursal | Asígnelo a la sucursal desde su perfil de usuario |
| El cajero no puede realizar alguna acción | No tiene el permiso necesario | El administrador debe cambiar su rol o realizar la acción por él |

---

## 5. Módulo: Productos

### ¿Para qué sirve?

Aquí se crea y organiza todo lo que el negocio vende. Cada producto queda registrado con su precio, costo y los demás datos necesarios.

### ¿Qué información tiene un producto?

- **Nombre y referencia** del producto.
- **Código de barras** (si tiene lector de barras).
- **Categoría y marca** para organizar el catálogo.
- **Unidad de medida** (unidad, kilo, litro, caja, etc.).
- **Precio de venta** y **costo de compra**.
- **Imagen** del producto.
- **Control de inventario** activado o desactivado.

### Funciones especiales de productos

- **Presentaciones:** Un mismo producto en diferentes tamaños o variantes (ej. gaseosa de 350ml y de 1.5L).
- **Productos compuestos:** Un producto que se arma con otros (ej. un combo que incluye varios artículos).
- **Lotes:** Para productos con fecha de vencimiento (ej. alimentos, medicamentos).
- **Seriales:** Para productos que necesitan número serial individual (ej. electrodomésticos).

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "El código de barras ya existe" | Otro producto tiene ese mismo código | Revise si el producto ya está creado o use otro código |
| El producto no aparece al vender | Está desactivado o sin stock | Actívelo y verifique que tiene inventario |
| "Faltan campos obligatorios" | Le falta llenar algún dato | Complete todos los campos marcados con * |

---

## 6. Módulo: Inventario

### ¿Para qué sirve?

Controla cuántas unidades hay de cada producto en cada sucursal. El inventario se actualiza solo cada vez que se hace una venta, una compra, un traslado o un ajuste.

### Operaciones de inventario

#### Movimientos (Kardex)
Es el historial completo de todo lo que ha entrado y salido de un producto. Útil para saber por qué cambió la cantidad de un artículo.

#### Merma
Cuando un producto se pierde, se daña o se vence, se registra como merma. Esto descuenta automáticamente la cantidad del inventario.

**Pasos:**
1. Vaya a Mermas y cree un nuevo registro.
2. Seleccione el producto y la cantidad.
3. Elija el motivo (daño, vencimiento, robo, etc.).
4. Confirme y el inventario se actualiza solo.

#### Reconteo (conteo físico)
Cuando quiere verificar que lo que dice el sistema coincide con lo que hay físicamente en la bodega.

**Pasos:**
1. Cree un nuevo reconteo.
2. Ingrese la cantidad física que encontró de cada producto.
3. El sistema muestra las diferencias.
4. Confirme para ajustar el inventario.

#### Traslado entre sucursales
Mueve mercancía de una sucursal a otra.

**Pasos:**
1. Cree un nuevo traslado.
2. Indique la sucursal de origen y la de destino.
3. Agregue los productos y cantidades a trasladar.
4. Confirme — el inventario se descuenta en origen y se suma en destino.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "Stock insuficiente" al vender | No hay unidades disponibles | Registre una compra o verifique si hay stock en otra sucursal |
| El inventario no coincide con la realidad | Hay ventas o compras sin registrar | Haga un reconteo y ajuste |
| No puede trasladar productos | No tiene permiso | Solo el administrador puede hacer traslados |

---

## 7. Módulo: Caja y Turnos

### ¿Para qué sirve?

Controla el dinero en efectivo de la caja durante cada jornada de trabajo. Cada cajero trabaja dentro de un **turno**: abre la caja al inicio del día y la cierra al final.

### ¿Cómo funciona un turno?

#### Paso 1 — Abrir turno
Antes de empezar a vender, el cajero debe abrir su turno:
1. Vaya a **Turnos** y seleccione **Abrir turno**.
2. Cuente el dinero inicial que hay en la caja.
3. Ingrese ese monto y confirme.

#### Paso 2 — Vender durante el turno
Con el turno abierto ya puede registrar ventas normalmente.

#### Paso 3 — Movimientos de caja
Si durante el turno entra o sale dinero por razones distintas a las ventas (ej. un gasto menor pagado con caja, o un préstamo a la caja), regístrelo como movimiento de caja:
- **Entrada:** Dinero que entra a la caja.
- **Salida:** Dinero que sale de la caja.

#### Paso 4 — Cerrar turno
Al final del turno:
1. Cuente físicamente todo el dinero que hay en la caja.
2. Vaya a **Turnos** y seleccione **Cerrar turno**.
3. Ingrese el monto que contó físicamente.
4. El sistema le muestra si hay diferencia (más o menos dinero del esperado).
5. Confirme el cierre.

> **Importante:** Dependiendo de la configuración del negocio, solo el administrador puede cerrar turnos.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "Ya tiene un turno activo" al abrir | Olvidó cerrar el turno anterior | Primero cierre el turno que tiene abierto |
| No puede cerrar el turno | Su usuario no tiene ese permiso | Llame al administrador para que lo cierre |
| Hay diferencia en el arqueo | El conteo físico no cuadra con las ventas | Revise el resumen del turno para identificar el movimiento que falta |
| No puede vender | No tiene un turno abierto | Abra el turno primero |

---

## 8. Módulo: Ventas

### ¿Para qué sirve?

Aquí se registra cada venta que hace el negocio. El sistema descuenta el inventario automáticamente y puede generar el recibo o la factura.

### ¿Cómo registrar una venta?

1. Vaya a **Nueva Venta**.
2. Busque el producto por nombre o escaneando el código de barras.
3. Indique la cantidad.
4. Si el cliente tiene precio especial, seleccione el cliente primero.
5. Elija el **método de pago** (efectivo, transferencia, tarjeta, etc.). Puede combinar varios métodos en una misma venta.
6. Confirme la venta.

El recibo se puede imprimir o enviar al cliente.

### Venta a crédito (pago parcial)
Si el cliente no paga todo en el momento:
1. Marque la opción de **pago parcial**.
2. Ingrese lo que pagó el cliente.
3. El resto queda registrado automáticamente en **Cuentas por Cobrar**.

### Anular una venta
Si necesita cancelar una venta ya registrada:
1. Busque la venta en el historial.
2. Seleccione **Anular**.
3. El inventario se devuelve automáticamente.

> Solo el administrador puede anular ventas.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| No puede registrar ventas | No tiene un turno de caja abierto | Abra el turno primero |
| "Sin stock suficiente" | El producto no tiene unidades disponibles | Registre una entrada de mercancía |
| No puede anular la venta | Su usuario no tiene ese permiso | Solicítele al administrador que la anule |
| La venta no aparece en el historial | Puede estar en otra sucursal | Verifique que está consultando la sucursal correcta |

---

## 9. Módulo: Compras

### ¿Para qué sirve?

Registra cada vez que el negocio recibe mercancía de un proveedor. Al confirmar una compra, el inventario aumenta automáticamente.

### ¿Cómo registrar una compra?

1. Vaya a **Nueva Compra**.
2. Seleccione el **proveedor**.
3. Agregue los productos comprados con su cantidad y precio de compra.
4. Seleccione cómo pagó (contado o crédito).
5. Confirme.

Si la compra fue a **crédito**, el sistema crea automáticamente una cuenta por pagar con el proveedor.

### Anular una compra
Si necesita cancelar una compra registrada por error, el inventario se descuenta automáticamente al anularla.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| El proveedor no aparece | No está creado en el sistema | Créelo primero en el módulo de Clientes y Proveedores |
| El producto no aparece | Está desactivado | Actívelo en el catálogo de productos |
| No puede anular la compra | Tiene pagos registrados | Hable con el administrador |

---

## 10. Módulo: Cotizaciones

### ¿Para qué sirve?

Permite crear un **presupuesto** para un cliente antes de confirmar la venta. El cliente puede revisar los precios y decidir si acepta.

### ¿Cómo crear una cotización?

1. Vaya a **Nueva Cotización**.
2. Agregue los productos y cantidades.
3. Confirme.
4. Puede descargarla en PDF para enviársela al cliente.

### Convertir cotización en venta
Cuando el cliente acepta:
1. Abra la cotización.
2. Haga clic en **Convertir a Venta**.
3. Seleccione el método de pago y confirme.

La cotización se convierte en venta y el inventario se descuenta automáticamente.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| No puede convertir a venta | No tiene turno de caja abierto | Abra el turno primero |
| La cotización ya fue convertida | Solo se puede convertir una vez | Si necesita otra venta, cree una nueva cotización |

---

## 11. Módulo: Clientes y Proveedores

### ¿Para qué sirve?

Aquí se guardan los datos de las personas o empresas con quienes el negocio tiene relación comercial:
- **Clientes:** A quienes les vende.
- **Proveedores:** De quienes compra.

### ¿Qué información se guarda?

- Nombre o razón social.
- NIT o cédula.
- Teléfono y correo.
- Dirección.
- Ciudad.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "Ya existe un tercero con ese NIT" | El cliente o proveedor ya está registrado | Búsquelo antes de crear uno nuevo |
| No puede eliminar un cliente | Tiene ventas o cuentas asociadas | Solo se puede desactivar, no eliminar |

---

## 12. Módulo: Cuentas por Cobrar y Pagar

### Cuentas por Cobrar — Lo que le deben a usted

Se crean automáticamente cuando una venta queda pendiente de pago total.

**¿Qué puede hacer?**
- Ver cuánto debe cada cliente.
- Registrar abonos cuando el cliente paga una parte.
- Ver el historial de todos los pagos que ha hecho un cliente.
- Descargar un estado de cuenta en PDF para enviárselo al cliente.
- Ver qué cuentas están vencidas.

**¿Cómo registrar un pago de un cliente?**
1. Busque la cuenta del cliente.
2. Haga clic en **Registrar abono**.
3. Ingrese el monto que pagó y el método de pago.
4. Confirme. El saldo pendiente se actualiza automáticamente.

---

### Cuentas por Pagar — Lo que usted debe

Se crean automáticamente cuando una compra queda pendiente de pago.

Funciona igual que las cuentas por cobrar pero en sentido contrario: usted registra los pagos que hace a sus proveedores.

---

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| La cuenta no aparece | La venta o compra fue de contado | Solo las operaciones a crédito generan cuentas pendientes |
| El saldo no cambió después de abonar | El abono no se confirmó | Verifique en el historial de abonos si quedó guardado |
| No puede eliminar un abono | No tiene el permiso necesario | Solicítelo al administrador |

---

## 13. Módulo: Comisiones

### ¿Para qué sirve?

Calcula automáticamente lo que le corresponde ganar a cada vendedor según las ventas que realizó.

### ¿Cómo funciona?

1. **El administrador configura** qué porcentaje de comisión gana cada vendedor.
2. **Cada vez que se registra una venta**, el sistema calcula la comisión del vendedor automáticamente.
3. **Al final del período**, el administrador crea una **liquidación** que agrupa todas las comisiones pendientes de ese vendedor.
4. Se marca como pagada cuando se le entrega el dinero al vendedor.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| El vendedor no tiene comisiones registradas | No está configurado en el sistema de comisiones | El administrador debe activarlo en la configuración |
| Una venta no generó comisión | La regla de comisión no aplica para ese producto | Revise la configuración con el administrador |
| No puede crear la liquidación | No hay comisiones pendientes de ese vendedor | Verifique que hay ventas sin liquidar |

---

## 14. Módulo: Factura Electrónica DIAN

### ¿Para qué sirve?

Genera facturas electrónicas válidas ante la DIAN directamente desde el sistema, sin necesidad de usar otro programa.

### ¿Cómo generar una factura electrónica?

1. Registre la venta normalmente.
2. Abra la venta desde el historial.
3. Haga clic en **Generar Factura Electrónica**.
4. El sistema se conecta con la DIAN y en unos segundos la factura queda generada.
5. Descargue el PDF de la factura.

### Estados de una factura

| Estado | Significado |
|--------|-------------|
| **Pendiente** | Aún no se ha generado la factura |
| **Emitida** | La DIAN aceptó la factura — es válida |
| **Rechazada** | La DIAN no aceptó la factura — hay un error en los datos |

### ¿Qué hacer si la factura falla?

**Si dice "Servicio no disponible":**
El sistema de la DIAN o el servicio de facturación está temporalmente caído. Espere 2 o 3 minutos y vuelva a intentarlo desde la misma venta.

**Si la factura queda en estado "Rechazada":**
La DIAN encontró un error en los datos. Los motivos más comunes son:
- El NIT del cliente está mal escrito.
- Falta información en la configuración de la empresa.
- El número de resolución de facturación está vencido.

En este caso, contacte al administrador para revisar y corregir los datos antes de reintentar.

**Si el botón de reintento no funciona:**
Espere unos minutos. El sistema tiene una protección automática que pausa los intentos de facturación cuando detecta un problema continuo. Pasado ese tiempo, el botón vuelve a funcionar.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| "Servicio no disponible" | El servicio de facturación está caído | Espere 2-3 minutos y reintente |
| Factura rechazada | Datos incorrectos | Revise NIT del cliente y datos de la empresa |
| No aparece el botón de factura | La venta fue anulada | No se puede facturar una venta anulada |
| El PDF no descarga | La factura aún no ha sido generada | Genere la factura primero |

---

## 15. Módulo: Precios y Descuentos

### ¿Para qué sirve?

Permite manejar distintos precios para distintos clientes o situaciones, sin tener que cambiar el precio del producto cada vez.

### Listas de precios

Puede crear diferentes listas. Por ejemplo:
- **Precio público:** El precio normal del mostrador.
- **Precio mayorista:** Un precio más bajo para clientes que compran en volumen.

Se asigna una lista a cada cliente. Cuando ese cliente compre, el sistema aplica su precio automáticamente.

### Descuentos por cliente

Puede negociar un descuento fijo con un cliente específico. Cada vez que ese cliente compre, el descuento se aplica solo.

### Precios por volumen

Si un cliente compra más de cierta cantidad, el precio baja automáticamente. Por ejemplo: "si compra más de 10 unidades, el precio es X".

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| El precio no cambia al seleccionar el cliente | La lista de precios no está asignada a ese cliente | Asígnele la lista correcta desde su perfil |
| El descuento no se aplica | La regla está desactivada | Verifique que la regla de descuento está activa |

---

## 16. Módulo: Reportes

### ¿Para qué sirve?

Le permite ver cómo va el negocio y descargar documentos para análisis o archivo.

### Panel principal (Dashboard)

Al entrar al sistema verá un resumen con:
- **Ventas del día.**
- **Ventas de la semana** en una gráfica.
- **Cómo pagaron los clientes** (cuánto en efectivo, cuánto en transferencia, etc.).

### Reportes disponibles para descargar

| Reporte | ¿Para qué sirve? |
|---------|-----------------|
| **Reporte de Ventas** | Ver todas las ventas de un período con su detalle |
| **Reporte de Inventario** | Ver el stock actual de todos los productos |
| **Estado de cuenta del cliente** | Ver el resumen de lo que debe un cliente |
| **Estado de cuenta del proveedor** | Ver el resumen de lo que le debe a un proveedor |
| **Recibo de venta** | Comprobante de una venta específica |
| **Recibo de abono** | Comprobante de un pago recibido o realizado |
| **Resumen de turno** | Detalle completo de un turno de caja al cierre |

Todos los reportes se pueden descargar en **PDF** o **Excel**.

### Errores frecuentes

| Lo que ve | Qué pasó | Qué hacer |
|-----------|----------|-----------|
| El reporte sale vacío | No hay datos en ese rango de fechas | Cambie el período de consulta |
| No puede descargar el archivo | Problema temporal del servidor | Espere unos segundos e intente de nuevo |
| Los datos no cuadran | Hay ventas o compras sin registrar | Revise si falta algún movimiento |

---

## 17. ¿Qué hacer cuando algo no funciona?

### Antes de llamar al soporte, pruebe esto:

1. **Recargue la página** — Muchos errores pequeños se resuelven solos al recargar.
2. **Cierre sesión y vuelva a entrar** — Resuelve problemas de sesión.
3. **Espere 2-3 minutos y vuelva a intentar** — Algunos servicios (como la facturación DIAN) pueden estar momentáneamente lentos.
4. **Verifique su conexión a internet** — Sin internet el sistema no funciona.

---

### Mensajes de error más comunes

| Lo que aparece en pantalla | Qué significa | Qué hacer |
|---------------------------|---------------|-----------|
| "Su sesión ha expirado" | Lleva mucho tiempo sin usar el sistema | Vuelva a ingresar con su correo y contraseña |
| "Sin acceso" o "No autorizado" | Su usuario no tiene permiso para esa acción | Contacte al administrador del negocio |
| "No encontrado" | El registro que busca fue eliminado o no existe | Verifique el nombre o número del registro |
| "Ya existe un registro con esos datos" | Está intentando crear algo que ya existe | Busque si ya existe antes de crear uno nuevo |
| "Campos obligatorios incompletos" | Le falta llenar algún campo del formulario | Complete todos los campos marcados |
| "Servicio no disponible" | Un servicio externo (como la DIAN) está caído | Espere unos minutos y reintente |
| "Error inesperado" | Ocurrió un problema interno | Tome nota del mensaje y contacte al soporte |

---

### Cómo reportar un problema al soporte

Si el error persiste después de intentar las soluciones anteriores, comuníquese con **Cloud Technological** y tenga lista la siguiente información:

1. **¿Qué estaba haciendo** cuando ocurrió el error? (ej. "estaba registrando una venta")
2. **¿Qué mensaje** apareció en pantalla? (si puede, tome una foto)
3. **¿A qué hora** ocurrió?
4. **¿Con qué usuario** estaba trabajando?
5. **¿En qué sucursal** estaba?

Con esa información el equipo técnico podrá ayudarle mucho más rápido.

---

*AURA POS — Cloud Technological*

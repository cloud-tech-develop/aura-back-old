# AGENTS.MD - AURA POS Backend Architecture & Roadmap

# ============================================================
# 1. ARQUITECTURA DEL SISTEMA
# ============================================================

Este proyecto utiliza **Spring Boot 3** con arquitectura modular y patrón **CQRS Ligero**.

## Estructura de Capas

1. Controller
   - Recibe HTTP request
   - Valida DTOs (@Valid)
   - Retorna ApiResponse
   - NO contiene lógica de negocio
   - empresaId siempre se extrae del token via SecurityUtils

2. Service (Interface + Impl)
   - Contiene lógica de negocio
   - Maneja transacciones (@Transactional)
   - Orquesta repositorios
   - Usa MapStruct, NO hace set/get manual masivo

3. Mapper (MapStruct)
   - Convierte DTO <-> Entity
   - Relaciones (empresa, sucursal, etc.) se ignoran en el mapper
   - Se asignan manualmente en el Service

4. Repository (Separación estricta)

   a) ...JPARepository
      - Extiende JpaRepository
      - Métodos simples: findByIdAndEmpresaId, existsBy...
      - SIN métodos con nombres largos tipo findByIdAndEmpresaIdAndDeletedAtIsNull
      - Validaciones complejas van al QueryRepository

   b) ...QueryRepository
      - @Repository
      - Usa NamedParameterJdbcTemplate
      - Listados paginados, reportes, validaciones complejas
      - Métodos de existencia: existeNombre(), existeNombreExcluyendo()
      - Retorna DTOs directos con BeanPropertyRowMapper

--------------------------------------------------------------

# ============================================================
# 2. SEGURIDAD (SPRING SECURITY 6)
# ============================================================

- Autenticación Stateless con JWT
- Password con BCrypt
- Custom Claims en el token:

{
  "empresaId": 1,
  "sucursalId": 3,
  "usuarioId": 1,
  "rol": "ADMIN"
}

Componentes:
- SecurityConfig
- JwtTokenProvider
- JwtAuthenticationFilter
- CustomUserDetailsService
- SecurityUtils → extrae claims del token en cada request

--------------------------------------------------------------

# ============================================================
# 3. MODELO DE DATOS COMPLETO
# ============================================================

El sistema soporta:

✔ Multi-empresa
✔ Multi-sucursal
✔ Multi-usuario
✔ POS
✔ Inventario avanzado
✔ Lotes (FEFO)
✔ Seriales
✔ Kardex inmutable (9 tipos de movimiento)
✔ Motor de descuentos automático
✔ Facturación electrónica (estructura lista)
✔ Auditoría de sesiones

--------------------------------------------------------------

# 3.1 ESTRUCTURA EMPRESARIAL

TABLAS:
- empresa
- sucursal
- tercero
- usuario
- usuario_sucursal
- historial_sesion

Características clave:
• UNIQUE(empresa_id, numero_documento) en tercero
• Usuario puede pertenecer a múltiples sucursales
• Una sucursal default por usuario
• Auditoría completa de sesiones

--------------------------------------------------------------

# 3.2 CATÁLOGO DE PRODUCTOS

TABLAS:
- categoria (jerárquica con padre_id)
- marca
- unidad_medida (sin empresa_id, es global)
- producto
- producto_presentacion
- producto_composicion

Características avanzadas del producto:
- tipo_producto: ESTANDAR | SERVICIO | KIT | PESABLE
- maneja_inventario
- maneja_lotes → si true, compras crean/actualizan lotes automáticamente
- maneja_serial
- atributos JSONB dinámicos

--------------------------------------------------------------

# 3.3 PRECIOS Y MOTOR DE DESCUENTOS

TABLAS:
- lista_precios
- producto_precio
- regla_descuento (JSONB para dias_semana)

Motor de descuentos:
- Por fecha, día de semana, hora
- Por producto o categoría (no ambos)
- Tipo: PORCENTAJE | MONTO
- buscarReglasAplicables() en QueryRepository para motor de ventas

--------------------------------------------------------------

# 3.4 INVENTARIO (KARDEX PROFESIONAL)

TABLAS:
- inventario (stock actual por sucursal/producto)
- lote (FEFO - First Expired First Out)
- serial_producto (estado: DISPONIBLE | VENDIDO | GARANTIA)
- motivo_merma
- merma + merma_detalle
- movimiento_inventario (INMUTABLE)

Reglas críticas:
1. movimiento_inventario NUNCA se edita ni elimina
2. Stock SOLO se mueve via movimientos registrados
3. Cada operación registra saldo_anterior y saldo_nuevo
4. Si producto.maneja_lotes = true → compra crea/actualiza lote
5. Stock nunca queda negativo → validación en anulaciones

Tipos de movimiento (9 en total):
- COMPRA / ANULACION_COMPRA
- VENTA / ANULACION_VENTA
- MERMA / ANULACION_MERMA
- TRASLADO_SALIDA / TRASLADO_ENTRADA / ANULACION_TRASLADO

--------------------------------------------------------------

# 3.5 COMPRAS

TABLAS:
- compra (cabecera)
- compra_detalle

Flujo:
1. Crear cabecera
2. Por cada detalle:
   a. Crear compra_detalle
   b. resolverLote() → crea o actualiza lote si maneja_lotes = true
   c. resolverInventario() → crea o actualiza stock en inventario
   d. registrarMovimiento() → Kardex tipo COMPRA
3. Actualizar totales en cabecera

Estados: RECIBIDA | ANULADA

--------------------------------------------------------------

# 3.6 TERCEROS

TABLAS:
- tercero

Características:
- Un tercero puede ser cliente, proveedor y empleado simultáneamente
- nombre_completo = COALESCE(razon_social, nombres + apellidos)
- listarClientes() y listarProveedores() para selectores en POS y compras
- Soft delete con deleted_at

--------------------------------------------------------------

# 3.7 VENTAS (POS)

TABLAS:
- caja
- turno_caja
- venta
- venta_detalle
- venta_detalle_serial
- venta_pago

Flujo:
1. Validar turno abierto
2. Validar stock por producto
3. Crear cabecera con consecutivo automático por sucursal
4. Por cada detalle:
   - Calcular impuestos (iva_porcentaje del producto)
   - Descontar stock + lote si aplica
   - Marcar seriales como VENDIDO
   - Kardex tipo VENTA
5. Validar pago >= total
6. Guardar métodos de pago
7. Actualizar totales

Estados: COMPLETADA | ANULADA
Métodos de pago: EFECTIVO | TARJETA | NEQUI | (extensible)

--------------------------------------------------------------

# 3.8 MERMAS

TABLAS:
- motivo_merma
- merma + merma_detalle

Flujo:
1. Validar stock suficiente
2. Restar inventario + lote si aplica
3. Kardex tipo MERMA
4. Anulación revierte con ANULACION_MERMA

Estados: APROBADA | ANULADA

--------------------------------------------------------------

# 3.9 TRASLADOS

TABLAS:
- traslado
- traslado_detalle

Flujo:
1. Validar stock en sucursal origen
2. Restar origen → Kardex TRASLADO_SALIDA
3. Sumar destino → Kardex TRASLADO_ENTRADA
4. Si maneja lotes: restar lote origen, crear/actualizar lote destino
5. Anulación revierte ambas sucursales con ANULACION_TRASLADO

Estados: COMPLETADO | ANULADO

--------------------------------------------------------------

# ============================================================
# 4. PATRONES Y CONVENCIONES
# ============================================================

## DTOs
- XxxDto → detalle completo (formulario/obtenerPorId)
- XxxTableDto → tabla paginada (incluye totalRows)
- CreateXxxDto → creación
- UpdateXxxDto → actualización

## Paginación
- Siempre via POST /page con PageableDto
- COUNT(*) OVER() AS total_rows en queries
- BeanPropertyRowMapper para mapeo automático
- PageImpl como respuesta

## Soft Delete
- Entidades con deleted_at: categoria, marca, producto, tercero
- Entidades con activo flag: unidad_medida, lista_precios, lote, regla_descuento, caja
- Hard delete: serial_producto, producto_composicion, motivo_merma

## Seguridad
- empresaId siempre del token: securityUtils.getEmpresaId()
- sucursalId del token: securityUtils.getSucursalId()
- usuarioId del token: securityUtils.getUsuarioId()
- NUNCA recibir empresaId por header o body

## Validaciones
- Duplicados → QueryRepository (existeNombre, existeDocumento)
- Existencia por id → JPARepository (findByIdAndEmpresaId)
- Lógica de negocio → Service

--------------------------------------------------------------

# ============================================================
# 5. ROADMAP DEL PROYECTO
# ============================================================

## ✅ FASE 1 - CORE Y SEGURIDAD (COMPLETADO)
- Spring Boot 3 + PostgreSQL + JWT
- Login + Register transaccional multi-sede
- SecurityUtils con claims del token

## ✅ FASE 2 - CATÁLOGO (COMPLETADO)
- Categorías (jerárquico)
- Marcas
- Unidades de Medida (global)
- Productos
- Presentaciones
- Composición (Kits y Recetas)
- Listas de Precios
- Precios por Producto

## ✅ FASE 3 - MOTOR DE DESCUENTOS (COMPLETADO)
- Reglas de Descuento (por fecha, hora, día, categoría, producto)

## ✅ FASE 4 - INVENTARIO (COMPLETADO)
- Inventario
- Lotes
- Seriales

## ✅ FASE 5 - OPERACIONES (COMPLETADO)
- Terceros (clientes y proveedores)
- Compras + Kardex automático
- Caja + Turnos de Caja
- Ventas / POS
- Mermas
- Traslados entre sucursales

## ✅ FASE 6 - CONSULTAS (COMPLETADO)
- Kardex Viewer (9 tipos de movimiento, filtros avanzados)
- Dashboard (ventas hoy/mes, stock bajo, lotes por vencer, top productos)

--------------------------------------------------------------

# ============================================================
# 6. POR CONSTRUIR - PRIORIDAD ALTA
# ============================================================

## 6.1 REPORTES (Prioridad Alta)

### Reporte de Ventas
- Ventas por período (día, semana, mes, rango)
- Ventas por vendedor/usuario
- Ventas por sucursal
- Ventas por método de pago
- Devoluciones y anulaciones
- Ticket promedio por período

Endpoints sugeridos:
- POST /api/reportes/ventas/periodo
- POST /api/reportes/ventas/por-usuario
- POST /api/reportes/ventas/por-sucursal

### Reporte de Inventario
- Stock valorizado (cantidad × costo unitario)
- Rotación de inventario (kardex del período)
- Productos sin movimiento en X días
- Stock por sucursal comparativo
- Historial de precios de costo

Endpoints sugeridos:
- GET /api/reportes/inventario/valorizado
- POST /api/reportes/inventario/rotacion
- GET /api/reportes/inventario/sin-movimiento

### Reporte de Compras
- Compras por proveedor y período
- Costo promedio por producto
- Evolución de precios de compra

Endpoints sugeridos:
- POST /api/reportes/compras/por-proveedor
- POST /api/reportes/compras/periodo

### Reporte de Mermas
- Mermas por motivo y período
- Costo total de mermas
- Productos con más mermas

Endpoints sugeridos:
- POST /api/reportes/mermas/periodo

--------------------------------------------------------------

## 6.2 FACTURACIÓN ELECTRÓNICA DIAN (Prioridad Alta para Colombia)

### Estructura necesaria (tabla venta ya tiene campos):
- cufe VARCHAR
- qr_data TEXT
- estado_dian VARCHAR (PENDIENTE | ENVIADA | ACEPTADA | RECHAZADA)

### Módulos a construir:

#### Configuración DIAN por empresa
```sql
CREATE TABLE configuracion_dian (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    ambiente VARCHAR(10), -- PRUEBAS | PRODUCCION
    nit_empresa VARCHAR(20),
    software_id VARCHAR(100),
    software_pin VARCHAR(100),
    set_pruebas VARCHAR(100),
    certificado_path TEXT,
    certificado_password VARCHAR(100),
    resolucion_numero VARCHAR(50),
    resolucion_fecha DATE,
    resolucion_prefijo VARCHAR(10),
    resolucion_desde BIGINT,
    resolucion_hasta BIGINT,
    resolucion_vigencia DATE,
    activo BOOLEAN DEFAULT TRUE
);
```

#### Flujo DIAN:
1. Venta se crea con estado_dian = 'PENDIENTE'
2. Job asíncrono (@Scheduled) toma ventas PENDIENTE
3. Genera XML UBL 2.1
4. Firma con certificado digital (X.509)
5. Envía a web service DIAN
6. Actualiza CUFE + estado_dian
7. Genera QR con datos de la factura
8. Guarda PDF de la factura

#### Dependencias necesarias:
```xml
<!-- Firma digital -->
<dependency>
    <groupId>org.apache.santuario</groupId>
    <artifactId>xmlsec</artifactId>
</dependency>
<!-- Generación PDF -->
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
</dependency>
<!-- QR -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
</dependency>
```

--------------------------------------------------------------

# ============================================================
# 7. POR CONSTRUIR - MÓDULOS PARA COMPETIR CON SIGO POS
# ============================================================

## 7.1 CUENTAS POR COBRAR (CxC) ← CRÍTICO

Sigo POS tiene cartera completa. Sin esto perdemos clientes que dan crédito.

```sql
CREATE TABLE credito_cliente (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    cliente_id INT REFERENCES tercero(id),
    venta_id INT REFERENCES venta(id),
    monto_total DECIMAL(14,2),
    monto_pagado DECIMAL(14,2) DEFAULT 0,
    saldo DECIMAL(14,2),
    fecha_vencimiento DATE,
    estado VARCHAR(20) -- VIGENTE | VENCIDO | PAGADO
);

CREATE TABLE pago_credito (
    id SERIAL PRIMARY KEY,
    credito_id INT REFERENCES credito_cliente(id),
    usuario_id INT REFERENCES usuario(id),
    monto DECIMAL(14,2),
    metodo_pago VARCHAR(30),
    referencia VARCHAR(100),
    fecha TIMESTAMP DEFAULT NOW(),
    observacion TEXT
);
```

Funcionalidad:
- Estado de cuenta por cliente
- Ventas a crédito con cupo máximo
- Registro de abonos parciales
- Alertas de cartera vencida
- Reporte de cartera por edades

--------------------------------------------------------------

## 7.2 CUENTAS POR PAGAR (CxP)

```sql
CREATE TABLE credito_proveedor (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    proveedor_id INT REFERENCES tercero(id),
    compra_id INT REFERENCES compra(id),
    monto_total DECIMAL(14,2),
    monto_pagado DECIMAL(14,2) DEFAULT 0,
    saldo DECIMAL(14,2),
    fecha_vencimiento DATE,
    estado VARCHAR(20)
);

CREATE TABLE pago_proveedor (
    id SERIAL PRIMARY KEY,
    credito_proveedor_id INT REFERENCES credito_proveedor(id),
    usuario_id INT REFERENCES usuario(id),
    monto DECIMAL(14,2),
    metodo_pago VARCHAR(30),
    referencia VARCHAR(100),
    fecha TIMESTAMP DEFAULT NOW()
);
```

--------------------------------------------------------------

## 7.3 DEVOLUCIONES DE VENTA ← CRÍTICO

Sin esto el cajero no puede devolver dinero ni producto formalmente.

```sql
CREATE TABLE devolucion_venta (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    venta_id INT REFERENCES venta(id),
    usuario_id INT REFERENCES usuario(id),
    motivo TEXT,
    tipo VARCHAR(20), -- DINERO | CAMBIO_PRODUCTO
    estado VARCHAR(20), -- APROBADA | ANULADA
    fecha TIMESTAMP DEFAULT NOW()
);

CREATE TABLE devolucion_venta_detalle (
    id SERIAL PRIMARY KEY,
    devolucion_id INT REFERENCES devolucion_venta(id),
    producto_id INT REFERENCES producto(id),
    cantidad DECIMAL(14,4),
    precio_unitario DECIMAL(14,2)
);
```

Flujo:
- Seleccionar venta original
- Elegir productos a devolver
- Devolver stock al inventario
- Registrar movimiento DEVOLUCION_VENTA en Kardex
- Generar nota crédito (DIAN)

--------------------------------------------------------------

## 7.4 MÓDULO DE EMPLEADOS Y COMISIONES

```sql
CREATE TABLE empleado (
    id SERIAL PRIMARY KEY,
    tercero_id INT REFERENCES tercero(id),
    empresa_id INT REFERENCES empresa(id),
    cargo VARCHAR(100),
    salario_base DECIMAL(14,2),
    tipo_comision VARCHAR(20), -- PORCENTAJE | MONTO_FIJO | NINGUNA
    valor_comision DECIMAL(14,2),
    fecha_ingreso DATE,
    activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE comision_vendedor (
    id SERIAL PRIMARY KEY,
    empleado_id INT REFERENCES empleado(id),
    venta_id INT REFERENCES venta(id),
    base_calculo DECIMAL(14,2),
    porcentaje DECIMAL(5,2),
    monto_comision DECIMAL(14,2),
    estado VARCHAR(20), -- PENDIENTE | PAGADA
    fecha TIMESTAMP DEFAULT NOW()
);
```

--------------------------------------------------------------

## 7.5 PUNTOS Y FIDELIZACIÓN

```sql
CREATE TABLE programa_puntos (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    nombre VARCHAR(100),
    pesos_por_punto DECIMAL(14,2), -- cada X pesos = 1 punto
    valor_punto DECIMAL(14,2),     -- 1 punto = X pesos de descuento
    activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE puntos_cliente (
    id SERIAL PRIMARY KEY,
    cliente_id INT REFERENCES tercero(id),
    empresa_id INT REFERENCES empresa(id),
    puntos_acumulados INT DEFAULT 0,
    puntos_canjeados INT DEFAULT 0,
    puntos_disponibles INT DEFAULT 0
);

CREATE TABLE movimiento_puntos (
    id SERIAL PRIMARY KEY,
    cliente_id INT REFERENCES tercero(id),
    venta_id INT REFERENCES venta(id),
    tipo VARCHAR(20), -- ACUMULACION | CANJE
    puntos INT,
    fecha TIMESTAMP DEFAULT NOW()
);
```

--------------------------------------------------------------

## 7.6 ÓRDENES DE PRODUCCIÓN (Para restaurantes/manufactura)

```sql
CREATE TABLE orden_produccion (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    sucursal_id INT REFERENCES sucursal(id),
    producto_id INT REFERENCES producto(id), -- producto a producir
    cantidad DECIMAL(14,4),
    estado VARCHAR(20), -- PENDIENTE | EN_PROCESO | COMPLETADA | CANCELADA
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    costo_total DECIMAL(14,2)
);

CREATE TABLE orden_produccion_insumo (
    id SERIAL PRIMARY KEY,
    orden_id INT REFERENCES orden_produccion(id),
    producto_id INT REFERENCES producto(id), -- insumo consumido
    cantidad_requerida DECIMAL(14,4),
    cantidad_usada DECIMAL(14,4),
    costo_unitario DECIMAL(14,2)
);
```

Flujo:
- Toma la composición del producto (producto_composicion)
- Valida stock de insumos
- Descuenta insumos del inventario
- Agrega el producto terminado al inventario
- Genera movimientos en Kardex

--------------------------------------------------------------

## 7.7 MULTIMONEDA

```sql
CREATE TABLE moneda (
    id SERIAL PRIMARY KEY,
    codigo VARCHAR(10), -- COP, USD, EUR
    nombre VARCHAR(50),
    simbolo VARCHAR(5),
    tasa_cambio DECIMAL(14,4), -- respecto a moneda base
    activa BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP
);
```

--------------------------------------------------------------

## 7.8 INTEGRACIÓN CON PLATAFORMAS DE DOMICILIOS

```sql
CREATE TABLE pedido_externo (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    sucursal_id INT REFERENCES sucursal(id),
    plataforma VARCHAR(30), -- RAPPI | IFOOD | UBER_EATS
    id_externo VARCHAR(100),
    venta_id INT REFERENCES venta(id),
    estado VARCHAR(20),
    comision_plataforma DECIMAL(14,2),
    created_at TIMESTAMP DEFAULT NOW()
);
```

--------------------------------------------------------------

## 7.9 NOTIFICACIONES Y ALERTAS

```sql
CREATE TABLE alerta (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    tipo VARCHAR(50),    -- STOCK_BAJO | LOTE_VENCER | CARTERA_VENCIDA
    mensaje TEXT,
    leida BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
```

Job @Scheduled diario que:
- Revisa stock bajo mínimo → genera alerta
- Revisa lotes próximos a vencer → genera alerta
- Revisa cartera vencida → genera alerta

--------------------------------------------------------------

## 7.10 APERTURA Y CIERRE CONTABLE

```sql
CREATE TABLE periodo_contable (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    anio INT,
    mes INT,
    estado VARCHAR(20), -- ABIERTO | CERRADO
    fecha_cierre TIMESTAMP,
    usuario_cierre_id INT REFERENCES usuario(id)
);
```

Impide modificar documentos de períodos cerrados.

--------------------------------------------------------------

## 7.11 BACKUP Y AUDITORÍA AVANZADA

```sql
CREATE TABLE auditoria_log (
    id SERIAL PRIMARY KEY,
    empresa_id INT REFERENCES empresa(id),
    usuario_id INT REFERENCES usuario(id),
    tabla VARCHAR(100),
    operacion VARCHAR(10), -- INSERT | UPDATE | DELETE
    registro_id BIGINT,
    datos_anteriores JSONB,
    datos_nuevos JSONB,
    ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT NOW()
);
```

--------------------------------------------------------------

# ============================================================
# 8. ANÁLISIS COMPETITIVO VS SIGO POS
# ============================================================

## ✅ LO QUE TENEMOS Y SIGO TIENE

| Módulo | AURA POS | Sigo POS |
|--------|----------|----------|
| Multi-sucursal | ✅ | ✅ |
| Inventario con Kardex | ✅ | ✅ |
| Lotes y vencimientos | ✅ | ✅ |
| Seriales | ✅ | ✅ |
| Motor de descuentos | ✅ | ✅ |
| Múltiples listas de precio | ✅ | ✅ |
| Compras | ✅ | ✅ |
| POS / Ventas | ✅ | ✅ |
| Traslados | ✅ | ✅ |
| Mermas | ✅ | ✅ |
| Dashboard | ✅ | ✅ |
| Facturación electrónica DIAN | ⏳ | ✅ |

## ✅ LO QUE TENEMOS Y SIGO NO TIENE

| Ventaja AURA POS |
|-----------------|
| Arquitectura moderna (Spring Boot 3, API REST) |
| API pública documentable con Swagger |
| Multi-empresa en una sola instancia |
| Kits y Recetas (producto_composicion) |
| Motor de descuentos por hora/día/categoría |
| FEFO automático en lotes |
| Kardex con 9 tipos de movimiento trazables |
| Preparado para integración con apps móviles |
| Código fuente propio (sin licencias) |

## ⚠️ LO QUE SIGO TIENE Y AURA POS AÚN NO

| Módulo Faltante | Prioridad |
|----------------|-----------|
| Cuentas por Cobrar (cartera clientes) | 🔴 Alta |
| Devoluciones de venta | 🔴 Alta |
| Facturación electrónica DIAN | 🔴 Alta |
| Reportes exportables (Excel/PDF) | 🟡 Media |
| Cuentas por Pagar | 🟡 Media |
| Comisiones vendedores | 🟡 Media |
| Puntos y fidelización | 🟡 Media |
| Órdenes de producción | 🟡 Media |
| Alertas automáticas | 🟡 Media |
| Integración domicilios (Rappi, iFood) | 🟢 Baja |
| Multimoneda | 🟢 Baja |
| Período contable | 🟢 Baja |
| Auditoría avanzada | 🟢 Baja |

--------------------------------------------------------------

# ============================================================
# 9. ORDEN DE CONSTRUCCIÓN RECOMENDADO (SIGUIENTE FASE)
# ============================================================

```
PRIORIDAD 1 - Para poder salir a producción:
├── Reportes básicos (ventas, inventario)
├── Devoluciones de venta
├── CxC (ventas a crédito)
└── DIAN (para facturar legalmente)

PRIORIDAD 2 - Para competir con Sigo:
├── CxP (cuentas por pagar)
├── Comisiones vendedores
├── Alertas automáticas (@Scheduled)
└── Exportación Excel/PDF de reportes

PRIORIDAD 3 - Para superar a Sigo:
├── Puntos y fidelización
├── Órdenes de producción
├── Integración domicilios
└── App móvil PWA

FRONTEND (Angular) - Puede ir en paralelo con Prioridad 2:
├── Login + dashboard
├── POS (pantalla de venta)
├── Módulos de catálogo
├── Inventario y compras
└── Reportes y kardex viewer
```

--------------------------------------------------------------

# ============================================================
# 10. GUÍA PARA CREAR UN NUEVO MÓDULO
# ============================================================

Orden estricto:
1. SQL migration (si es tabla nueva)
2. Entity → domain/models
3. DTOs → dto/modulo (Dto, TableDto, CreateDto, UpdateDto)
4. JPARepository → comandos simples
5. QueryRepository → listados + validaciones complejas
6. Mapper → MapStruct
7. Service Interface + ServiceImpl
8. Controller

--------------------------------------------------------------

# REGLAS DE ORO

1. Consultas de lectura masiva SIEMPRE van al QueryRepository con JDBC.
2. El stock NUNCA se modifica directamente, siempre via movimiento_inventario.
3. Toda operación que mueve stock debe registrar en Kardex (inmutable).
4. El empresaId SIEMPRE viene del JWT, nunca del body o header.
5. Nunca usar Hibernate para listar registros masivos.
6. Todo módulo transaccional va dentro de @Transactional.
7. Las anulaciones siempre validan que no dejen stock negativo.
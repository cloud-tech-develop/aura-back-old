# AURA POS - Documentación de APIs

## Tabla de Contenidos
1. [Introducción](#introducción)
2. [Autenticación](#autenticación)
3. [Administración de Plataforma](#administración-de-plataforma)
4. [Gestión de Usuarios](#gestión-de-usuarios)
5. [Sucursales](#sucursales)
6. [Cajas y Turnos](#cajas-y-turnos)
7. [Catálogo de Productos](#catálogo-de-productos)
8. [Terceros](#terceros)
9. [Compras](#compras)
10. [Ventas (POS)](#ventas-pos)
11. [Inventario](#inventario)
12. [Kardex](#kardex)
13. [Mermas](#mermas)
14. [Traslados](#traslados)
15. [Descuentos](#descuentos)
16. [Dashboard](#dashboard)

---

## Introducción

### Base URL
```
http://localhost:8080/api
```

### Estructura de Respuesta
Todas las respuestas siguen el mismo formato:

```json
{
  "status": 200,
  "message": "Mensaje descriptivo",
  "error": false,
  "data": { ... }
}
```

### Headers Requeridos
```
Content-Type: application/json
Authorization: Bearer {token}
```

### Códigos de Estado HTTP
| Código | Descripción |
|--------|-------------|
| 200 | OK - Solicitud exitosa |
| 201 | Created - Recurso creado |
| 400 | Bad Request - Datos inválidos |
| 401 | Unauthorized - Token inválido o ausente |
| 403 | Forbidden - Sin permisos |
| 404 | Not Found - Recurso no encontrado |
| 422 | Unprocessable Entity - Validación fallida |
| 500 | Internal Server Error - Error del servidor |

---

## Autenticación

### 1. Login
**Endpoint:** `POST /api/auth/login`

Autentica un usuario y retorna un token JWT.

**Request:**
```json
{
  "username": "admin",
  "password": "123456"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Login exitoso",
  "error": false,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tipoToken": "Bearer",
    "usuarioId": 1,
    "username": "admin",
    "nombreCompleto": "Administrador",
    "rol": "ADMIN",
    "sucursales": [
      {
        "id": 1,
        "nombre": "Sucursal Principal",
        "direccion": "Calle 123"
      }
    ]
  }
}
```

---

### 2. Register (Platform Admin)
**Endpoint:** `POST /api/auth/register`

Crea una nueva empresa y usuario administrador. Requiere rol `PLATFORM_ADMIN`.

**Request:**
```json
{
  "empresa": {
    "nombre": "Mi Empresa SAS",
    "nit": "900123456",
    "direccion": "Calle 123",
    "telefono": "3001234567",
    "email": "contacto@miempresa.com"
  },
  "usuario": {
    "username": "admin",
    "password": "123456",
    "nombres": "Juan",
    "apellidos": "Pérez",
    "numeroDocumento": "12345678",
    "tipoDocumento": "CC",
    "email": "juan@miempresa.com",
    "telefono": "3001234567"
  },
  "sucursal": {
    "nombre": "Sucursal Principal",
    "direccion": "Calle 123",
    "telefono": "3001234567",
    "codigo": "001"
  }
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Empresa y Usuario creados exitosamente",
  "error": false,
  "data": true
}
```

---

## Administración de Plataforma

### 3. Dashboard Platform
**Endpoint:** `GET /api/platform/dashboard`

Obtiene estadísticas globales de la plataforma. Requiere rol `PLATFORM_ADMIN`.

**Response (200):**
```json
{
  "status": 200,
  "message": "Dashboard obtenido",
  "error": false,
  "data": {
    "totalEmpresas": 10,
    "empresasActivas": 8,
    "empresasSuspendidas": 2,
    "totalUsuarios": 50
  }
}
```

---

### 4. Listar Empresas (Paginado)
**Endpoint:** `POST /api/platform/empresas/page`

Lista empresas con paginación. Requiere rol `PLATFORM_ADMIN`.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Empresa SAS",
        "nit": "900123456",
        "estado": "ACTIVA",
        "createdAt": "2024-01-01T00:00:00"
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 5. Obtener Empresa por ID
**Endpoint:** `GET /api/platform/empresas/{id}`

Obtiene una empresa específica. Requiere rol `PLATFORM_ADMIN`.

**Response (200):**
```json
{
  "status": 200,
  "message": "Empresa encontrada",
  "error": false,
  "data": {
    "id": 1,
    "nombre": "Empresa SAS",
    "nit": "900123456",
    "direccion": "Calle 123",
    "telefono": "3001234567",
    "email": "contacto@empresa.com",
    "estado": "ACTIVA",
    "createdAt": "2024-01-01T00:00:00"
  }
}
```

---

### 6. Crear Empresa
**Endpoint:** `POST /api/platform/empresas`

Crea una nueva empresa. Requiere rol `PLATFORM_ADMIN`.

**Request:**
```json
{
  "nombre": "Nueva Empresa SAS",
  "nit": "900999999",
  "direccion": "Carrera 45",
  "telefono": "3009999999",
  "email": "nueva@empresa.com"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Empresa creada exitosamente",
  "error": false,
  "data": {
    "id": 2,
    "nombre": "Nueva Empresa SAS",
    "nit": "900999999",
    "estado": "ACTIVA"
  }
}
```

---

### 7. Actualizar Empresa
**Endpoint:** `PUT /api/platform/empresas/{id}`

Actualiza una empresa existente. Requiere rol `PLATFORM_ADMIN`.

**Request:**
```json
{
  "nombre": "Empresa Actualizada SAS",
  "direccion": "Nueva Dirección"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Empresa actualizada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 8. Suspender Empresa
**Endpoint:** `PATCH /api/platform/empresas/{id}/suspender`

Suspende una empresa. Requiere rol `PLATFORM_ADMIN`.

**Response (200):**
```json
{
  "status": 200,
  "message": "Empresa suspendida correctamente",
  "error": false,
  "data": true
}
```

---

### 9. Activar Empresa
**Endpoint:** `PATCH /api/platform/empresas/{id}/activar`

Activa una empresa suspendida. Requiere rol `PLATFORM_ADMIN`.

**Response (200):**
```json
{
  "status": 200,
  "message": "Empresa activada correctamente",
  "error": false,
  "data": true
}
```

---

## Gestión de Usuarios

### 10. Listar Usuarios (Paginado)
**Endpoint:** `POST /api/usuarios/page`

Lista usuarios de la empresa con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "nombres": "Juan",
        "apellidos": "Pérez",
        "email": "juan@empresa.com",
        "rol": "ADMIN",
        "activo": true
      }
    ],
    "totalRows": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 11. Obtener Usuario por ID
**Endpoint:** `GET /api/usuarios/{id}`

Obtiene un usuario específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Usuario encontrado",
  "error": false,
  "data": {
    "id": 1,
    "username": "admin",
    "nombres": "Juan",
    "apellidos": "Pérez",
    "tipoDocumento": "CC",
    "numeroDocumento": "12345678",
    "email": "juan@empresa.com",
    "telefono": "3001234567",
    "rol": "ADMIN",
    "activo": true,
    "sucursales": [
      {
        "id": 1,
        "nombre": "Sucursal Principal",
        "esDefault": true
      }
    ]
  }
}
```

---

### 12. Crear Usuario
**Endpoint:** `POST /api/usuarios/create`

Crea un nuevo usuario.

**Request:**
```json
{
  "username": "juan.perez",
  "password": "123456",
  "nombres": "Juan",
  "apellidos": "Pérez",
  "tipoDocumento": "CC",
  "numeroDocumento": "12345678",
  "email": "juan@empresa.com",
  "telefono": "3001234567",
  "rol": "CAJERO",
  "sucursalIds": [1, 2],
  "sucursalDefaultId": 1
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Usuario creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 13. Actualizar Usuario
**Endpoint:** `PUT /api/usuarios/{id}`

Actualiza un usuario existente.

**Request:**
```json
{
  "nombres": "Juan Carlos",
  "apellidos": "Pérez Gómez",
  "email": "juan.carlos@empresa.com",
  "telefono": "3001234567",
  "rol": "ADMIN"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Usuario actualizado correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 14. Desactivar Usuario
**Endpoint:** `DELETE /api/usuarios/{id}`

Desactiva un usuario (soft delete).

**Response (200):**
```json
{
  "status": 200,
  "message": "Usuario desactivado correctamente",
  "error": false,
  "data": true
}
```

---

## Sucursales

### 15. Listar Sucursales (Paginado)
**Endpoint:** `POST /api/sucursales/page`

Lista sucursales con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Sucursal Principal",
        "codigo": "001",
        "direccion": "Calle 123",
        "telefono": "3001234567",
        "activo": true
      }
    ],
    "totalRows": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 16. Listar Sucursales Activas
**Endpoint:** `GET /api/sucursales/activas`

Lista todas las sucursales activas de la empresa.

**Response (200):**
```json
{
  "status": 200,
  "message": "Sucursales activas",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Sucursal Principal",
      "codigo": "001",
      "direccion": "Calle 123"
    }
  ]
}
```

---

### 17. Obtener Sucursal por ID
**Endpoint:** `GET /api/sucursales/{id}`

Obtiene una sucursal específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "Sucursal encontrada",
  "error": false,
  "data": {
    "id": 1,
    "nombre": "Sucursal Principal",
    "codigo": "001",
    "direccion": "Calle 123",
    "telefono": "3001234567",
    "activo": true
  }
}
```

---

### 18. Crear Sucursal
**Endpoint:** `POST /api/sucursales/create`

Crea una nueva sucursal.

**Request:**
```json
{
  "nombre": "Nueva Sucursal",
  "codigo": "002",
  "direccion": "Carrera 45",
  "telefono": "3009999999"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Sucursal creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 19. Actualizar Sucursal
**Endpoint:** `PUT /api/sucursales/{id}`

Actualiza una sucursal existente.

**Request:**
```json
{
  "nombre": "Sucursal Actualizada",
  "direccion": "Nueva Dirección"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Sucursal actualizada correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 20. Eliminar Sucursal
**Endpoint:** `DELETE /api/sucursales/{id}`

Desactiva una sucursal (soft delete).

**Response (200):**
```json
{
  "status": 200,
  "message": "Sucursal desactivada correctamente",
  "error": false,
  "data": true
}
```

---

## Cajas y Turnos

### 21. Listar Cajas (Paginado)
**Endpoint:** `POST /api/cajas/page`

Lista cajas con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Caja 1",
        "codigo": "CAJ001",
        "sucursalId": 1,
        "sucursalNombre": "Sucursal Principal",
        "activo": true
      }
    ],
    "totalRows": 3,
    "page": 0,
    "size": 10
  }
}
```

---

### 22. Obtener Caja por ID
**Endpoint:** `GET /api/cajas/{id}`

Obtiene una caja específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "Caja encontrada",
  "error": false,
  "data": {
    "id": 1,
    "nombre": "Caja 1",
    "codigo": "CAJ001",
    "sucursalId": 1,
    "activo": true
  }
}
```

---

### 23. Crear Caja
**Endpoint:** `POST /api/cajas/create`

Crea una nueva caja.

**Request:**
```json
{
  "nombre": "Caja 2",
  "codigo": "CAJ002",
  "sucursalId": 1
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Caja creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 24. Actualizar Caja
**Endpoint:** `PUT /api/cajas/{id}`

Actualiza una caja existente.

**Request:**
```json
{
  "nombre": "Caja Modificada",
  "sucursalId": 2
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Caja actualizada correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 25. Eliminar Caja
**Endpoint:** `DELETE /api/cajas/{id}`

Desactiva una caja.

**Response (200):**
```json
{
  "status": 200,
  "message": "Caja eliminada correctamente",
  "error": false,
  "data": true
}
```

---

### 26. Listar Turnos (Paginado)
**Endpoint:** `POST /api/turnos/page`

Lista turnos de caja con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "cajaId": 1,
        "cajaNombre": "Caja 1",
        "usuarioId": 1,
        "usuarioNombre": "Juan Pérez",
        "fechaApertura": "2024-01-15T08:00:00",
        "fechaCierre": null,
        "montoApertura": 100000.00,
        "montoCierre": null,
        "estado": "ABIERTO"
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 27. Obtener Turno por ID
**Endpoint:** `GET /api/turnos/{id}`

Obtiene un turno específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Turno encontrado",
  "error": false,
  "data": { ... }
}
```

---

### 28. Obtener Turno Activo
**Endpoint:** `GET /api/turnos/activo`

Obtiene el turno activo del usuario actual.

**Response (200):**
```json
{
  "status": 200,
  "message": "Turno activo",
  "error": false,
  "data": {
    "id": 1,
    "cajaId": 1,
    "usuarioId": 1,
    "fechaApertura": "2024-01-15T08:00:00",
    "montoApertura": 100000.00,
    "estado": "ABIERTO"
  }
}
```

---

### 29. Abrir Turno
**Endpoint:** `POST /api/turnos/abrir`

Abre un nuevo turno de caja.

**Request:**
```json
{
  "cajaId": 1,
  "montoApertura": 100000.00
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Turno abierto exitosamente",
  "error": false,
  "data": {
    "id": 1,
    "cajaId": 1,
    "usuarioId": 1,
    "fechaApertura": "2024-01-15T08:00:00",
    "montoApertura": 100000.00,
    "estado": "ABIERTO"
  }
}
```

---

### 30. Cerrar Turno
**Endpoint:** `PATCH /api/turnos/{id}/cerrar`

Cierra un turno de caja.

**Request:**
```json
{
  "montoCierre": 250000.00,
  "observacion": "Cierre de día"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Turno cerrado correctamente",
  "error": false,
  "data": {
    "id": 1,
    "montoCierre": 250000.00,
    "estado": "CERRADO",
    "totalVentas": 150000.00,
    "totalEfectivo": 100000.00,
    "totalTarjeta": 50000.00,
    "diferencia": 0.00
  }
}
```

---

### 31. Resumen de Turno
**Endpoint:** `GET /api/turnos/{id}/resumen`

Obtiene el resumen de un turno.

**Response (200):**
```json
{
  "status": 200,
  "message": "Resumen del turno",
  "error": false,
  "data": {
    "turnoId": 1,
    "ventas": 15,
    "totalVentas": 150000.00,
    "ventasEfectivo": 100000.00,
    "ventasTarjeta": 50000.00,
    "totalAnulaciones": 2,
    "valorAnulaciones": 15000.00
  }
}
```

---

## Catálogo de Productos

### 32. Listar Productos (Paginado)
**Endpoint:** `POST /api/productos/page`

Lista productos con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "codigo": "PROD001",
        "nombre": "Producto A",
        "categoriaId": 1,
        "categoriaNombre": "Categoría 1",
        "marcaId": 1,
        "marcaNombre": "Marca A",
        "precioVenta": 25000.00,
        "stock": 100,
        "activo": true
      }
    ],
    "totalRows": 50,
    "page": 0,
    "size": 10
  }
}
```

---

### 33. Obtener Producto por ID
**Endpoint:** `GET /api/productos/{id}`

Obtiene un producto específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Producto encontrado",
  "error": false,
  "data": {
    "id": 1,
    "codigo": "PROD001",
    "nombre": "Producto A",
    "descripcion": "Descripción del producto",
    "categoriaId": 1,
    "marcaId": 1,
    "unidadMedidaId": 1,
    "tipoProducto": "ESTANDAR",
    "precioVenta": 25000.00,
    "precioCosto": 15000.00,
    "ivaPorcentaje": 19.0,
    "manejaInventario": true,
    "manejaLotes": true,
    "manejaSerial": false,
    "stockMinimo": 10,
    "activo": true
  }
}
```

---

### 34. Crear Producto
**Endpoint:** `POST /api/productos/create`

Crea un nuevo producto.

**Request:**
```json
{
  "codigo": "PROD001",
  "nombre": "Producto Nuevo",
  "descripcion": "Descripción",
  "categoriaId": 1,
  "marcaId": 1,
  "unidadMedidaId": 1,
  "tipoProducto": "ESTANDAR",
  "precioVenta": 25000.00,
  "precioCosto": 15000.00,
  "ivaPorcentaje": 19.0,
  "manejaInventario": true,
  "manejaLotes": true,
  "manejaSerial": false,
  "stockMinimo": 10
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Producto creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 35. Actualizar Producto
**Endpoint:** `PUT /api/productos/{id}`

Actualiza un producto existente.

**Request:**
```json
{
  "nombre": "Producto Modificado",
  "precioVenta": 28000.00,
  "stockMinimo": 15
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Producto actualizado correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 36. Eliminar Producto
**Endpoint:** `DELETE /api/productos/{id}`

Desactiva un producto (soft delete).

**Response (200):**
```json
{
  "status": 200,
  "message": "Producto eliminado correctamente",
  "error": false,
  "data": true
}
```

---

### 37. Listar Productos (Simple)
**Endpoint:** `GET /api/productos/list`

Lista todos los productos activos (para selects).

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "codigo": "PROD001",
      "nombre": "Producto A"
    }
  ]
}
```

---

### 38. Listar Productos POS
**Endpoint:** `GET /api/productos/pos`

Lista productos para el punto de venta (con stock y precios).

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": [
    {
      "id": 1,
      "codigo": "PROD001",
      "nombre": "Producto A",
      "precioVenta": 25000.00,
      "stock": 100,
      "categoria": "Categoría 1",
      "imagen": null
    }
  ]
}
```

---

### 39. Listar Categorías (Paginado)
**Endpoint:** `POST /api/categorias/page`

Lista categorías con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Bebidas",
        "padreId": null,
        "padreNombre": null,
        "activo": true
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 40. Listar Categorías (Simple)
**Endpoint:** `GET /api/categorias/list`

Lista todas las categorías activas.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Bebidas",
      "padreId": null
    }
  ]
}
```

---

### 41. Crear Categoría
**Endpoint:** `POST /api/categorias`

Crea una nueva categoría.

**Request:**
```json
{
  "nombre": "Nueva Categoría",
  "padreId": null
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Categoría creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 42. Listar Marcas (Paginado)
**Endpoint:** `POST /api/marcas/page`

Lista marcas con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Marca A",
        "activo": true
      }
    ],
    "totalRows": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 43. Listar Marcas (Simple)
**Endpoint:** `GET /api/marcas/list`

Lista todas las marcas activas.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Marca A"
    }
  ]
}
```

---

### 44. Listar Unidades de Medida (Paginado)
**Endpoint:** `POST /api/unidades-medida/page`

Lista unidades de medida con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Unidad",
        "abreviatura": "UND",
        "activo": true
      }
    ],
    "totalRows": 8,
    "page": 0,
    "size": 10
  }
}
```

---

### 45. Listar Unidades de Medida (Simple)
**Endpoint:** `GET /api/unidades-medida/list`

Lista todas las unidades de medida activas.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Unidad",
      "abreviatura": "UND"
    }
  ]
}
```

---

### 46. Crear Unidad de Medida
**Endpoint:** `POST /api/unidades-medida`

Crea una nueva unidad de medida.

**Request:**
```json
{
  "nombre": "Kilogramo",
  "abreviatura": "KG"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Unidad de medida creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

## Terceros

### 47. Listar Terceros (Paginado)
**Endpoint:** `POST /api/terceros/page`

Lista terceros (clientes/proveedores) con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "tipoDocumento": "CC",
        "numeroDocumento": "12345678",
        "nombres": "Juan",
        "apellidos": "Pérez",
        "razonSocial": null,
        "email": "juan@email.com",
        "telefono": "3001234567",
        "direccion": "Calle 123",
        "tipo": "CLIENTE"
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 48. Obtener Tercero por ID
**Endpoint:** `GET /api/terceros/{id}`

Obtiene un tercero específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Tercero encontrado",
  "error": false,
  "data": {
    "id": 1,
    "tipoDocumento": "CC",
    "numeroDocumento": "12345678",
    "nombres": "Juan",
    "apellidos": "Pérez",
    "razonSocial": null,
    "email": "juan@email.com",
    "telefono": "3001234567",
    "direccion": "Calle 123",
    "tipo": "CLIENTE",
    "activo": true
  }
}
```

---

### 49. Listar Clientes
**Endpoint:** `GET /api/terceros/clientes`

Lista clientes con búsqueda opcional.

**Parámetros Query:**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| search | String | Búsqueda por nombre o documento |

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "numeroDocumento": "12345678",
      "nombreCompleto": "Juan Pérez"
    }
  ]
}
```

---

### 50. Listar Proveedores
**Endpoint:** `GET /api/terceros/proveedores`

Lista proveedores con búsqueda opcional.

**Parámetros Query:**
| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| search | String | Búsqueda por nombre o documento |

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "numeroDocumento": "900123456",
      "nombreCompleto": "Proveedor SAS"
    }
  ]
}
```

---

### 51. Crear Tercero
**Endpoint:** `POST /api/terceros/create`

Crea un nuevo tercero.

**Request:**
```json
{
  "tipoDocumento": "CC",
  "numeroDocumento": "12345678",
  "nombres": "Juan",
  "apellidos": "Pérez",
  "razonSocial": null,
  "email": "juan@email.com",
  "telefono": "3001234567",
  "direccion": "Calle 123",
  "tipo": "CLIENTE"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Tercero creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 52. Actualizar Tercero
**Endpoint:** `PUT /api/terceros/{id}`

Actualiza un tercero existente.

**Request:**
```json
{
  "nombres": "Juan Carlos",
  "telefono": "3009999999"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Tercero actualizado correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 53. Eliminar Tercero
**Endpoint:** `DELETE /api/terceros/{id}`

Desactiva un tercero (soft delete).

**Response (200):**
```json
{
  "status": 200,
  "message": "Tercero eliminado correctamente",
  "error": false,
  "data": true
}
```

---

## Compras

### 54. Listar Compras (Paginado)
**Endpoint:** `POST /api/compras/page`

Lista compras con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numero": "COMP-001",
        "fecha": "2024-01-15T10:00:00",
        "proveedorId": 1,
        "proveedorNombre": "Proveedor SAS",
        "usuarioId": 1,
        "usuarioNombre": "Juan Pérez",
        "subtotal": 100000.00,
        "iva": 19000.00,
        "total": 119000.00,
        "estado": "RECIBIDA"
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 55. Obtener Compra por ID
**Endpoint:** `GET /api/compras/{id}`

Obtiene una compra específica con todos sus detalles.

**Response (200):**
```json
{
  "status": 200,
  "message": "Compra encontrada",
  "error": false,
  "data": {
    "id": 1,
    "numero": "COMP-001",
    "fecha": "2024-01-15T10:00:00",
    "proveedorId": 1,
    "sucursalId": 1,
    "usuarioId": 1,
    "subtotal": 100000.00,
    "iva": 19000.00,
    "total": 119000.00,
    "estado": "RECIBIDA",
    "detalles": [
      {
        "id": 1,
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 10,
        "precioUnitario": 10000.00,
        "ivaPorcentaje": 19.0,
        "subtotal": 100000.00,
        "loteId": 1,
        "loteNumero": "LOTE001"
      }
    ]
  }
}
```

---

### 56. Crear Compra
**Endpoint:** `POST /api/compras/create`

Registra una nueva compra.

**Request:**
```json
{
  "proveedorId": 1,
  "sucursalId": 1,
  "detalles": [
    {
      "productoId": 1,
      "cantidad": 10,
      "precioUnitario": 10000.00,
      "ivaPorcentaje": 19.0,
      "lote": {
        "numero": "LOTE001",
        "fechaVencimiento": "2025-01-15"
      }
    }
  ]
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Compra registrada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 57. Anular Compra
**Endpoint:** `PATCH /api/compras/{id}/anular`

Anula una compra.

**Response (200):**
```json
{
  "status": 200,
  "message": "Compra anulada correctamente",
  "error": false,
  "data": true
}
```

---

## Ventas (POS)

### 58. Listar Ventas (Paginado)
**Endpoint:** `POST /api/ventas/page`

Lista ventas con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numero": "POS-001",
        "fecha": "2024-01-15T10:30:00",
        "clienteId": 1,
        "clienteNombre": "Juan Pérez",
        "usuarioId": 1,
        "usuarioNombre": "Cajero 1",
        "turnoId": 1,
        "subtotal": 210000.00,
        "descuento": 10000.00,
        "iva": 38000.00,
        "total": 228000.00,
        "estado": "COMPLETADA"
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 59. Obtener Venta por ID
**Endpoint:** `GET /api/ventas/{id}`

Obtiene una venta específica con todos sus detalles.

**Response (200):**
```json
{
  "status": 200,
  "message": "Venta encontrada",
  "error": false,
  "data": {
    "id": 1,
    "numero": "POS-001",
    "fecha": "2024-01-15T10:30:00",
    "clienteId": 1,
    "sucursalId": 1,
    "usuarioId": 1,
    "turnoId": 1,
    "subtotal": 210000.00,
    "descuento": 10000.00,
    "iva": 38000.00,
    "total": 228000.00,
    "estado": "COMPLETADA",
    "detalles": [
      {
        "id": 1,
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 10,
        "precioUnitario": 21000.00,
        "descuento": 1000.00,
        "ivaPorcentaje": 19.0,
        "subtotal": 210000.00
      }
    ],
    "pagos": [
      {
        "id": 1,
        "metodoPago": "EFECTIVO",
        "monto": 228000.00
      }
    ]
  }
}
```

---

### 60. Crear Venta
**Endpoint:** `POST /api/ventas/create`

Registra una nueva venta.

**Request:**
```json
{
  "clienteId": 1,
  "turnoId": 1,
  "detalles": [
    {
      "productoId": 1,
      "cantidad": 10,
      "precioUnitario": 21000.00,
      "descuento": 1000.00,
      "ivaPorcentaje": 19.0,
      "loteId": null,
      "serialId": null
    }
  ],
  "pagos": [
    {
      "metodoPago": "EFECTIVO",
      "monto": 228000.00
    }
  ]
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Venta registrada exitosamente",
  "error": false,
  "data": {
    "id": 1,
    "numero": "POS-001",
    "total": 228000.00,
    "estado": "COMPLETADA"
  }
}
```

---

### 61. Anular Venta
**Endpoint:** `PATCH /api/ventas/{id}/anular`

Anula una venta.

**Response (200):**
```json
{
  "status": 200,
  "message": "Venta anulada correctamente",
  "error": false,
  "data": true
}
```

---

## Inventario

### 62. Listar Inventario (Paginado)
**Endpoint:** `POST /api/inventario/page`

Lista inventario con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "productoId": 1,
        "productoCodigo": "PROD001",
        "productoNombre": "Producto A",
        "sucursalId": 1,
        "sucursalNombre": "Sucursal Principal",
        "cantidad": 100,
        "stockMinimo": 10,
        "stockMaximo": 500
      }
    ],
    "totalRows": 50,
    "page": 0,
    "size": 10
  }
}
```

---

### 63. Obtener Inventario por ID
**Endpoint:** `GET /api/inventario/{id}`

Obtiene un registro de inventario específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Inventario encontrado",
  "error": false,
  "data": { ... }
}
```

---

### 64. Listar Stock Bajo
**Endpoint:** `GET /api/inventario/stock-bajo`

Lista productos con stock bajo el mínimo.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "productoId": 1,
      "productoNombre": "Producto A",
      "sucursalNombre": "Sucursal Principal",
      "cantidad": 5,
      "stockMinimo": 10
    }
  ]
}
```

---

### 65. Crear Inventario
**Endpoint:** `POST /api/inventario/create`

Crea un registro de inventario.

**Request:**
```json
{
  "productoId": 1,
  "sucursalId": 1,
  "cantidad": 100,
  "stockMinimo": 10,
  "stockMaximo": 500
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Inventario creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 66. Actualizar Inventario
**Endpoint:** `PUT /api/inventario/{id}`

Actualiza un registro de inventario.

**Request:**
```json
{
  "cantidad": 150,
  "stockMinimo": 15
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Inventario actualizado correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 67. Listar Lotes (Paginado)
**Endpoint:** `POST /api/lotes/page`

Lista lotes con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numero": "LOTE001",
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 50,
        "cantidadDisponible": 45,
        "fechaVencimiento": "2025-01-15",
        "fechaRegistro": "2024-01-15"
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 68. Listar Lotes Por Vencer
**Endpoint:** `GET /api/lotes/por-vencer`

Lista lotes próximos a vencer (30 días).

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "numero": "LOTE001",
      "productoNombre": "Producto A",
      "cantidadDisponible": 45,
      "diasParaVencer": 15
    }
  ]
}
```

---

### 69. Listar Lotes Disponibles por Producto
**Endpoint:** `GET /api/lotes/disponibles/{productoId}/{sucursalId}`

Lista lotes disponibles de un producto en una sucursal.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "numero": "LOTE001",
      "cantidadDisponible": 45,
      "fechaVencimiento": "2025-01-15"
    }
  ]
}
```

---

### 70. Listar Seriales (Paginado)
**Endpoint:** `POST /api/seriales/page`

Lista seriales con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "serial": "SN123456",
        "productoId": 1,
        "productoNombre": "Producto A",
        "estado": "DISPONIBLE"
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 71. Listar Seriales Disponibles
**Endpoint:** `GET /api/seriales/disponibles/{productoId}/{sucursalId}`

Lista seriales disponibles de un producto.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "serial": "SN123456",
      "estado": "DISPONIBLE"
    }
  ]
}
```

---

## Kardex

### 72. Listar Movimientos (Paginado)
**Endpoint:** `POST /api/kardex/page`

Lista movimientos de kardex con filtros.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {
    "productoId": 1,
    "sucursalId": 1,
    "tipoMovimiento": "COMPRA",
    "fechaDesde": "2024-01-01",
    "fechaHasta": "2024-01-31"
  },
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Kardex consultado",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "fecha": "2024-01-15T10:00:00",
        "tipoMovimiento": "COMPRA",
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 10,
        "saldoAnterior": 0,
        "saldoNuevo": 10,
        "referenciaId": 1,
        "referencia": "COMP-001",
        "usuarioId": 1,
        "usuarioNombre": "Juan Pérez"
      }
    ],
    "totalRows": 50,
    "page": 0,
    "size": 10
  }
}
```

**Tipos de Movimiento:**
- COMPRA
- ANULACION_COMPRA
- VENTA
- ANULACION_VENTA
- MERMA
- ANULACION_MERMA
- TRASLADO_SALIDA
- TRASLADO_ENTRADA
- ANULACION_TRASLADO

---

### 73. Resumen Stock por Producto
**Endpoint:** `GET /api/kardex/resumen/{productoId}`

Obtiene el resumen de movimientos de un producto.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "sucursalId": 1,
      "sucursalNombre": "Sucursal Principal",
      "stockActual": 50,
      "ultimaEntrada": "2024-01-15",
      "ultimaSalida": "2024-01-20"
    }
  ]
}
```

---

## Mermas

### 74. Listar Mermas (Paginado)
**Endpoint:** `POST /api/mermas/page`

Lista mermas con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numero": "MERMA-001",
        "fecha": "2024-01-15T14:00:00",
        "motivoId": 1,
        "motivoNombre": "Vencido",
        "usuarioId": 1,
        "usuarioNombre": "Juan Pérez",
        "total": 50000.00,
        "estado": "APROBADA"
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 75. Obtener Merma por ID
**Endpoint:** `GET /api/mermas/{id}`

Obtiene una merma específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "Merma encontrada",
  "error": false,
  "data": {
    "id": 1,
    "numero": "MERMA-001",
    "fecha": "2024-01-15T14:00:00",
    "motivoId": 1,
    "sucursalId": 1,
    "usuarioId": 1,
    "total": 50000.00,
    "estado": "APROBADA",
    "detalles": [
      {
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 5,
        "precioUnitario": 10000.00,
        "loteId": 1
      }
    ]
  }
}
```

---

### 76. Crear Merma
**Endpoint:** `POST /api/mermas/create`

Registra una nueva merma.

**Request:**
```json
{
  "motivoId": 1,
  "sucursalId": 1,
  "detalles": [
    {
      "productoId": 1,
      "cantidad": 5,
      "precioUnitario": 10000.00,
      "loteId": 1
    }
  ]
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Merma registrada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 77. Anular Merma
**Endpoint:** `PATCH /api/mermas/{id}/anular`

Anula una merma.

**Response (200):**
```json
{
  "status": 200,
  "message": "Merma anulada correctamente",
  "error": false,
  "data": true
}
```

---

### 78. Listar Motivos Merma (Paginado)
**Endpoint:** `POST /api/motivos-merma/page`

Lista motivos de merma.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Vencido",
        "descripcion": "Producto vencido"
      }
    ],
    "totalRows": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 79. Crear Motivo Merma
**Endpoint:** `POST /api/motivos-merma/create`

Crea un nuevo motivo de merma.

**Request:**
```json
{
  "nombre": "Dañado",
  "descripcion": "Producto dañado"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Motivo creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

## Traslados

### 80. Listar Traslados (Paginado)
**Endpoint:** `POST /api/traslados/page`

Lista traslados con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numero": "TRASLADO-001",
        "fecha": "2024-01-15T14:00:00",
        "sucursalOrigenId": 1,
        "sucursalOrigenNombre": "Sucursal A",
        "sucursalDestinoId": 2,
        "sucursalDestinoNombre": "Sucursal B",
        "usuarioId": 1,
        "usuarioNombre": "Juan Pérez",
        "total": 50000.00,
        "estado": "COMPLETADO"
      }
    ],
    "totalRows": 10,
    "page": 0,
    "size": 10
  }
}
```

---

### 81. Obtener Traslado por ID
**Endpoint:** `GET /api/traslados/{id}`

Obtiene un traslado específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "Traslado encontrado",
  "error": false,
  "data": {
    "id": 1,
    "numero": "TRASLADO-001",
    "fecha": "2024-01-15T14:00:00",
    "sucursalOrigenId": 1,
    "sucursalDestinoId": 2,
    "usuarioId": 1,
    "total": 50000.00,
    "estado": "COMPLETADO",
    "detalles": [
      {
        "productoId": 1,
        "productoNombre": "Producto A",
        "cantidad": 10,
        "loteOrigenId": 1,
        "loteDestinoId": null
      }
    ]
  }
}
```

---

### 82. Crear Traslado
**Endpoint:** `POST /api/traslados/create`

Registra un nuevo traslado.

**Request:**
```json
{
  "sucursalOrigenId": 1,
  "sucursalDestinoId": 2,
  "detalles": [
    {
      "productoId": 1,
      "cantidad": 10,
      "loteOrigenId": 1
    }
  ]
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Traslado registrado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 83. Anular Traslado
**Endpoint:** `PATCH /api/traslados/{id}/anular`

Anula un traslado.

**Response (200):**
```json
{
  "status": 200,
  "message": "Traslado anulado correctamente",
  "error": false,
  "data": true
}
```

---

## Descuentos

### 84. Listar Reglas Descuento (Paginado)
**Endpoint:** `POST /api/descuentos/page`

Lista reglas de descuento con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Descuento Lunes",
        "tipo": "PORCENTAJE",
        "valor": 10.0,
        "fechaInicio": "2024-01-01",
        "fechaFin": "2024-12-31",
        "diaSemana": ["LUNES"],
        "horaInicio": "08:00",
        "horaFin": "12:00",
        "ambito": "CATEGORIA",
        "ambitoId": 1,
        "activo": true
      }
    ],
    "totalRows": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### 85. Obtener Regla Descuento por ID
**Endpoint:** `GET /api/descuentos/{id}`

Obtiene una regla de descuento específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "Regla encontrada",
  "error": false,
  "data": { ... }
}
```

---

### 86. Crear Regla Descuento
**Endpoint:** `POST /api/descuentos/create`

Crea una nueva regla de descuento.

**Request:**
```json
{
  "nombre": "Descuento Especial",
  "tipo": "PORCENTAJE",
  "valor": 15.0,
  "fechaInicio": "2024-01-01",
  "fechaFin": "2024-12-31",
  "diaSemana": ["LUNES", "MARTES"],
  "horaInicio": "08:00",
  "horaFin": "18:00",
  "ambito": "PRODUCTO",
  "ambitoId": 1
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Regla creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 87. Actualizar Regla Descuento
**Endpoint:** `PUT /api/descuentos/{id}`

Actualiza una regla de descuento existente.

**Request:**
```json
{
  "nombre": "Descuento Modificado",
  "valor": 20.0
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Regla actualizada correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 88. Eliminar Regla Descuento
**Endpoint:** `DELETE /api/descuentos/{id}`

Desactiva una regla de descuento.

**Response (200):**
```json
{
  "status": 200,
  "message": "Regla eliminada correctamente",
  "error": false,
  "data": true
}
```

---

## Listas de Precios

### 89. Listar Listas Precios (Paginado)
**Endpoint:** `POST /api/listas-precios/page`

Lista listas de precios con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "nombre": "Precio Base",
        "descripcion": "Lista de precios base",
        "activo": true
      }
    ],
    "totalRows": 3,
    "page": 0,
    "size": 10
  }
}
```

---

### 90. Listar Listas Precios (Simple)
**Endpoint:** `GET /api/listas-precios/list`

Lista todas las listas de precios activas.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Precio Base"
    }
  ]
}
```

---

### 91. Obtener Lista Precio por ID
**Endpoint:** `GET /api/listas-precios/{id}`

Obtiene una lista de precios específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "Lista encontrada",
  "error": false,
  "data": { ... }
}
```

---

### 92. Crear Lista Precio
**Endpoint:** `POST /api/listas-precios/create`

Crea una nueva lista de precios.

**Request:**
```json
{
  "nombre": "Precio Mayoreo",
  "descripcion": "Lista para ventas al por mayor"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Lista creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 93. Actualizar Lista Precio
**Endpoint:** `PUT /api/listas-precios/{id}`

Actualiza una lista de precios existente.

**Request:**
```json
{
  "nombre": "Precio Mayoreo Actualizado"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Lista actualizada correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 94. Eliminar Lista Precio
**Endpoint:** `DELETE /api/listas-precios/{id}`

Desactiva una lista de precios.

**Response (200):**
```json
{
  "status": 200,
  "message": "Lista eliminada correctamente",
  "error": false,
  "data": true
}
```

---

## Precios por Producto

### 95. Listar Precios Producto (Paginado)
**Endpoint:** `POST /api/productos/precios/page`

Lista precios por producto con paginación.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "productoId": 1,
        "productoNombre": "Producto A",
        "listaPrecioId": 1,
        "listaPrecioNombre": "Precio Base",
        "precio": 25000.00
      }
    ],
    "totalRows": 50,
    "page": 0,
    "size": 10
  }
}
```

---

### 96. Listar Precios por Lista
**Endpoint:** `GET /api/productos/precios/lista/{listaPrecioId}`

Lista precios de una lista específica.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "productoId": 1,
      "productoNombre": "Producto A",
      "precio": 25000.00
    }
  ]
}
```

---

### 97. Crear Precio Producto
**Endpoint:** `POST /api/productos/precios`

Crea un precio para un producto.

**Request:**
```json
{
  "productoId": 1,
  "listaPrecioId": 2,
  "precio": 22000.00
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Precio creado exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 98. Actualizar Precio Producto
**Endpoint:** `PUT /api/productos/precios/{id}`

Actualiza un precio de producto.

**Request:**
```json
{
  "precio": 23000.00
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Precio actualizado correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 99. Eliminar Precio Producto
**Endpoint:** `DELETE /api/productos/precios/{id}`

Elimina un precio de producto.

**Response (200):**
```json
{
  "status": 200,
  "message": "Precio eliminado correctamente",
  "error": false,
  "data": true
}
```

---

## Presentaciones

### 100. Listar Presentaciones (Paginado)
**Endpoint:** `POST /api/productos/presentaciones/page`

Lista presentaciones de productos.

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "productoId": 1,
        "productoNombre": "Producto A",
        "nombre": "Caja",
        "cantidad": 12,
        "codigoBarras": "7891234567890",
        "precioAdicional": 0.00
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 101. Listar Presentaciones por Producto
**Endpoint:** `GET /api/productos/presentaciones/producto/{productoId}`

Lista presentaciones de un producto específico.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "nombre": "Caja",
      "cantidad": 12,
      "codigoBarras": "7891234567890"
    }
  ]
}
```

---

## Composiciones

### 102. Listar Composiciones (Paginado)
**Endpoint:** `POST /api/productos/composicion/page`

Lista composiciones de productos (kits/recetas).

**Request:**
```json
{
  "page": 0,
  "size": 10,
  "filters": {},
  "sort": []
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Listado exitoso",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "productoPadreId": 1,
        "productoPadreNombre": "Kit A",
        "productoHijoId": 2,
        "productoHijoNombre": "Producto B",
        "cantidad": 2
      }
    ],
    "totalRows": 20,
    "page": 0,
    "size": 10
  }
}
```

---

### 103. Listar Composiciones por Padre
**Endpoint:** `GET /api/productos/composicion/padre/{productoPadreId}`

Lista componentes de un producto (kit/receta).

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "id": 1,
      "productoHijoId": 2,
      "productoHijoNombre": "Producto B",
      "cantidad": 2
    }
  ]
}
```

---

### 104. Crear Composición
**Endpoint:** `POST /api/productos/composicion/create`

Crea una composición de producto.

**Request:**
```json
{
  "productoPadreId": 1,
  "productoHijoId": 2,
  "cantidad": 2
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Composición creada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 105. Actualizar Composición
**Endpoint:** `PUT /api/productos/composicion/{id}`

Actualiza una composición existente.

**Request:**
```json
{
  "cantidad": 3
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Composición actualizada correctamente",
  "error": false,
  "data": { ... }
}
```

---

### 106. Eliminar Composición
**Endpoint:** `DELETE /api/productos/composicion/{id}`

Elimina una composición.

**Response (200):**
```json
{
  "status": 200,
  "message": "Composición eliminada correctamente",
  "error": false,
  "data": true
}
```

---

## Dashboard

### 107. Obtener Dashboard
**Endpoint:** `GET /api/dashboard`

Obtiene estadísticas generales del dashboard.

**Response (200):**
```json
{
  "status": 200,
  "message": "Dashboard cargado",
  "error": false,
  "data": {
    "ventasHoy": 1500000.00,
    "ventasMes": 45000000.00,
    "cantidadVentasHoy": 45,
    "cantidadVentasMes": 1350,
    "productosStockBajo": 8,
    "lotesPorVencer": 3,
    "topProductos": [
      {
        "productoId": 1,
        "productoNombre": "Producto A",
        "totalVendido": 500000.00,
        "cantidad": 25
      }
    ]
  }
}
```

---

### 108. Ventas por Día de la Semana
**Endpoint:** `GET /api/dashboard/ventas-semana`

Obtiene ventas agrupadas por día de la semana actual.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "dia": "LUNES",
      "total": 1500000.00,
      "cantidad": 45
    },
    {
      "dia": "MARTES",
      "total": 1800000.00,
      "cantidad": 52
    }
  ]
}
```

---

### 109. Ventas por Método de Pago
**Endpoint:** `GET /api/dashboard/ventas-metodo-pago`

Obtiene ventas agrupadas por método de pago.

**Response (200):**
```json
{
  "status": 200,
  "message": "",
  "error": false,
  "data": [
    {
      "metodoPago": "EFECTIVO",
      "total": 1000000.00,
      "cantidad": 30
    },
    {
      "metodoPago": "TARJETA",
      "total": 500000.00,
      "cantidad": 15
    },
    {
      "metodoPago": "NEQUI",
      "total": 100000.00,
      "cantidad": 5
    }
  ]
}
```

---

## Tabla Resumen de Endpoints

| Módulo | Endpoint | Método | Descripción |
|--------|----------|--------|-------------|
| Auth | `/api/auth/login` | POST | Login de usuario |
| Auth | `/api/auth/register` | POST | Registro (Platform Admin) |
| Platform | `/api/platform/dashboard` | GET | Dashboard plataforma |
| Platform | `/api/platform/empresas/page` | POST | Listar empresas |
| Platform | `/api/platform/empresas/{id}` | GET | Obtener empresa |
| Platform | `/api/platform/empresas` | POST | Crear empresa |
| Platform | `/api/platform/empresas/{id}` | PUT | Actualizar empresa |
| Platform | `/api/platform/empresas/{id}/suspender` | PATCH | Suspender empresa |
| Platform | `/api/platform/empresas/{id}/activar` | PATCH | Activar empresa |
| Usuarios | `/api/usuarios/page` | POST | Listar usuarios |
| Usuarios | `/api/usuarios/{id}` | GET | Obtener usuario |
| Usuarios | `/api/usuarios/create` | POST | Crear usuario |
| Usuarios | `/api/usuarios/{id}` | PUT | Actualizar usuario |
| Usuarios | `/api/usuarios/{id}` | DELETE | Desactivar usuario |
| Sucursales | `/api/sucursales/page` | POST | Listar sucursales |
| Sucursales | `/api/sucursales/activas` | GET | Listar activas |
| Sucursales | `/api/sucursales/{id}` | GET | Obtener sucursal |
| Sucursales | `/api/sucursales/create` | POST | Crear sucursal |
| Sucursales | `/api/sucursales/{id}` | PUT | Actualizar sucursal |
| Sucursales | `/api/sucursales/{id}` | DELETE | Eliminar sucursal |
| Cajas | `/api/cajas/page` | POST | Listar cajas |
| Cajas | `/api/cajas/{id}` | GET | Obtener caja |
| Cajas | `/api/cajas/create` | POST | Crear caja |
| Cajas | `/api/cajas/{id}` | PUT | Actualizar caja |
| Cajas | `/api/cajas/{id}` | DELETE | Eliminar caja |
| Turnos | `/api/turnos/page` | POST | Listar turnos |
| Turnos | `/api/turnos/{id}` | GET | Obtener turno |
| Turnos | `/api/turnos/activo` | GET | Turno activo |
| Turnos | `/api/turnos/abrir` | POST | Abrir turno |
| Turnos | `/api/turnos/{id}/cerrar` | PATCH | Cerrar turno |
| Turnos | `/api/turnos/{id}/resumen` | GET | Resumen turno |
| Productos | `/api/productos/page` | POST | Listar productos |
| Productos | `/api/productos/{id}` | GET | Obtener producto |
| Productos | `/api/productos/create` | POST | Crear producto |
| Productos | `/api/productos/{id}` | PUT | Actualizar producto |
| Productos | `/api/productos/{id}` | DELETE | Eliminar producto |
| Productos | `/api/productos/list` | GET | Listar simple |
| Productos | `/api/productos/pos` | GET | Listar POS |
| Categorías | `/api/categorias/page` | POST | Listar categorías |
| Categorías | `/api/categorias/{id}` | GET | Obtener categoría |
| Categorías | `/api/categorias` | POST | Crear categoría |
| Categorías | `/api/categorias/{id}` | PUT | Actualizar categoría |
| Categorías | `/api/categorias/{id}` | DELETE | Eliminar categoría |
| Categorías | `/api/categorias/list` | GET | Listar simple |
| Marcas | `/api/marcas/page` | POST | Listar marcas |
| Marcas | `/api/marcas/list` | GET | Listar simple |
| Marcas | `/api/marcas/{id}` | GET | Obtener marca |
| Marcas | `/api/marcas` | POST | Crear marca |
| Marcas | `/api/marcas/{id}` | PUT | Actualizar marca |
| Marcas | `/api/marcas/{id}` | DELETE | Eliminar marca |
| Unidades | `/api/unidades-medida/page` | POST | Listar unidades |
| Unidades | `/api/unidades-medida/list` | GET | Listar simple |
| Unidades | `/api/unidades-medida/{id}` | GET | Obtener unidad |
| Unidades | `/api/unidades-medida` | POST | Crear unidad |
| Unidades | `/api/unidades-medida/{id}` | PUT | Actualizar unidad |
| Unidades | `/api/unidades-medida/{id}` | DELETE | Eliminar unidad |
| Terceros | `/api/terceros/page` | POST | Listar terceros |
| Terceros | `/api/terceros/{id}` | GET | Obtener tercero |
| Terceros | `/api/terceros/clientes` | GET | Listar clientes |
| Terceros | `/api/terceros/proveedores` | GET | Listar proveedores |
| Terceros | `/api/terceros/create` | POST | Crear tercero |
| Terceros | `/api/terceros/{id}` | PUT | Actualizar tercero |
| Terceros | `/api/terceros/{id}` | DELETE | Eliminar tercero |
| Compras | `/api/compras/page` | POST | Listar compras |
| Compras | `/api/compras/{id}` | GET | Obtener compra |
| Compras | `/api/compras/create` | POST | Crear compra |
| Compras | `/api/compras/{id}/anular` | PATCH | Anular compra |
| Ventas | `/api/ventas/page` | POST | Listar ventas |
| Ventas | `/api/ventas/{id}` | GET | Obtener venta |
| Ventas | `/api/ventas/create` | POST | Crear venta |
| Ventas | `/api/ventas/{id}/anular` | PATCH | Anular venta |
| Inventario | `/api/inventario/page` | POST | Listar inventario |
| Inventario | `/api/inventario/{id}` | GET | Obtener inventario |
| Inventario | `/api/inventario/stock-bajo` | GET | Stock bajo |
| Inventario | `/api/inventario/create` | POST | Crear inventario |
| Inventario | `/api/inventario/{id}` | PUT | Actualizar inventario |
| Lotes | `/api/lotes/page` | POST | Listar lotes |
| Lotes | `/api/lotes/{id}` | GET | Obtener lote |
| Lotes | `/api/lotes/por-vencer` | GET | Por vencer |
| Lotes | `/api/lotes/disponibles/{productoId}/{sucursalId}` | GET | Disponibles |
| Lotes | `/api/lotes/create` | POST | Crear lote |
| Lotes | `/api/lotes/{id}` | DELETE | Eliminar lote |
| Seriales | `/api/seriales/page` | POST | Listar seriales |
| Seriales | `/api/seriales/{id}` | GET | Obtener serial |
| Seriales | `/api/seriales/disponibles/{productoId}/{sucursalId}` | GET | Disponibles |
| Seriales | `/api/seriales/create` | POST | Crear serial |
| Seriales | `/api/seriales/{id}` | DELETE | Eliminar serial |
| Kardex | `/api/kardex/page` | POST | Listar movimientos |
| Kardex | `/api/kardex/resumen/{productoId}` | GET | Resumen producto |
| Mermas | `/api/mermas/page` | POST | Listar mermas |
| Mermas | `/api/mermas/{id}` | GET | Obtener merma |
| Mermas | `/api/mermas/create` | POST | Crear merma |
| Mermas | `/api/mermas/{id}/anular` | PATCH | Anular merma |
| Motivos Merma | `/api/motivos-merma/page` | POST | Listar motivos |
| Motivos Merma | `/api/motivos-merma/{id}` | GET | Obtener motivo |
| Motivos Merma | `/api/motivos-merma/create` | POST | Crear motivo |
| Motivos Merma | `/api/motivos-merma/{id}` | PUT | Actualizar motivo |
| Motivos Merma | `/api/motivos-merma/{id}` | DELETE | Eliminar motivo |
| Traslados | `/api/traslados/page` | POST | Listar traslados |
| Traslados | `/api/traslados/{id}` | GET | Obtener traslado |
| Traslados | `/api/traslados/create` | POST | Crear traslado |
| Traslados | `/api/traslados/{id}/anular` | PATCH | Anular traslado |
| Descuentos | `/api/descuentos/page` | POST | Listar reglas |
| Descuentos | `/api/descuentos/{id}` | GET | Obtener regla |
| Descuentos | `/api/descuentos/create` | POST | Crear regla |
| Descuentos | `/api/descuentos/{id}` | PUT | Actualizar regla |
| Descuentos | `/api/descuentos/{id}` | DELETE | Eliminar regla |
| Listas Precios | `/api/listas-precios/page` | POST | Listar listas |
| Listas Precios | `/api/listas-precios/list` | GET | Listar simple |
| Listas Precios | `/api/listas-precios/{id}` | GET | Obtener lista |
| Listas Precios | `/api/listas-precios/create` | POST | Crear lista |
| Listas Precios | `/api/listas-precios/{id}` | PUT | Actualizar lista |
| Listas Precios | `/api/listas-precios/{id}` | DELETE | Eliminar lista |
| Precios Producto | `/api/productos/precios/page` | POST | Listar precios |
| Precios Producto | `/api/productos/precios/{id}` | GET | Obtener precio |
| Precios Producto | `/api/productos/precios/lista/{listaPrecioId}` | GET | Por lista |
| Precios Producto | `/api/productos/precios` | POST | Crear precio |
| Precios Producto | `/api/productos/precios/{id}` | PUT | Actualizar precio |
| Precios Producto | `/api/productos/precios/{id}` | DELETE | Eliminar precio |
| Presentaciones | `/api/productos/presentaciones/page` | POST | Listar presentaciones |
| Presentaciones | `/api/productos/presentaciones/{id}` | GET | Obtener presentación |
| Presentaciones | `/api/productos/presentaciones/producto/{productoId}` | GET | Por producto |
| Presentaciones | `/api/productos/presentaciones/create` | POST | Crear presentación |
| Presentaciones | `/api/productos/presentaciones/{id}` | PUT | Actualizar presentación |
| Presentaciones | `/api/productos/presentaciones/{id}` | DELETE | Eliminar presentación |
| Composiciones | `/api/productos/composicion/page` | POST | Listar composiciones |
| Composiciones | `/api/productos/composicion/{id}` | GET | Obtener composición |
| Composiciones | `/api/productos/composicion/padre/{productoPadreId}` | GET | Por padre |
| Composiciones | `/api/productos/composicion/create` | POST | Crear composición |
| Composiciones | `/api/productos/composicion/{id}` | PUT | Actualizar composición |
| Composiciones | `/api/productos/composicion/{id}` | DELETE | Eliminar composición |
| Dashboard | `/api/dashboard` | GET | Dashboard general |
| Dashboard | `/api/dashboard/ventas-semana` | GET | Ventas semana |
| Dashboard | `/api/dashboard/ventas-metodo-pago` | GET | Ventas método pago |

---

## Notas Adicionales

1. **Paginación**: Todos los endpoints de listado utilizan `POST /page` con el formato de `PageableDto`.

2. **Seguridad**: El `empresaId` se extrae automáticamente del token JWT. No debe enviarse en el body.

3. **Fechas**: Los formatos de fecha aceptados son `YYYY-MM-DD` y `YYYY-MM-DDTHH:MM:SS`.

4. **Soft Delete**: Los endpoints DELETE realizan soft delete (desactivación) en lugar de eliminación física.

5. **Validaciones**: Los campos obligatorios tienen validaciones con `@NotBlank`, `@NotNull`, etc. Los mensajes de error se encuentran en español.

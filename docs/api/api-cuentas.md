# API Documentation - Cuentas por Cobrar y Pagar

## Overview

This document provides the API specification for implementing the frontend of the Accounts Receivable (Cuentas por Cobrar) and Accounts Payable (Cuentas por Pagar) modules.

---

## Base URL

```
/api/cuentas-cobrar
/api/cuentas-pagar
```

---

## Common Types

### Estados de Cuenta
| Value | Description |
|-------|-------------|
| `activa` | Active account with pending balance |
| `pagada` | Fully paid account |
| `vencida` | Overdue account (past due date) |

### Métodos de Pago
| Value | Description |
|-------|-------------|
| `efectivo` | Cash |
| `transferencia` | Bank transfer |
| `consignacion` | Deposit to account |
| `cheque` | Check |

---

## Cuentas por Cobrar (Accounts Receivable)

### 1. Listar Cuentas con Paginación y Filtros

**Endpoint:** `POST /api/cuentas-cobrar/page`

**Request Body:**
```json
{
  "page": 0,
  "rows": 10,
  "search": "",
  "order_by": "fecha_emision",
  "order": "desc",
  "params": {
    "fechaDesde": "2026-01-01",
    "fechaHasta": "2026-12-31",
    "clienteId": null,
    "estado": null
  }
}
```

**Response:**
```json
{
  "status": 200,
  "message": "Cuentas por cobrar obtenidas exitosamente",
  "error": false,
  "data": {
    "content": [
      {
        "id": 1,
        "numeroCuenta": "CC-20260226-0001",
        "clienteNombre": "Juan Pérez",
        "clienteDocumento": "12345678",
        "fechaEmision": "2026-02-26T10:00:00",
        "fechaVencimiento": "2026-03-26T10:00:00",
        "totalDeuda": 100000.00,
        "totalAbonado": 25000.00,
        "saldoPendiente": 75000.00,
        "estado": "activa",
        "totalRows": 15
      }
    ],
    "totalElements": 15,
    "totalPages": 2,
    "size": 10,
    "number": 0
  }
}
```

---

### 2. Obtener Cuenta por ID

**Endpoint:** `GET /api/cuentas-cobrar/{id}`

**Response:**
```json
{
  "status": 200,
  "message": "Cuenta por cobrar obtenida exitosamente",
  "error": false,
  "data": {
    "id": 1,
    "empresaId": 1,
    "terceroId": 1,
    "clienteNombre": "Juan Pérez",
    "clienteDocumento": "12345678",
    "ventaId": 1,
    "numeroCuenta": "CC-20260226-0001",
    "fechaEmision": "2026-02-26T10:00:00",
    "fechaVencimiento": "2026-03-26T10:00:00",
    "totalDeuda": 100000.00,
    "totalAbonado": 25000.00,
    "saldoPendiente": 75000.00,
    "estado": "activa",
    "observaciones": "Compra de mercancía",
    "createdAt": "2026-02-26T10:00:00",
    "abonos": [
      {
        "id": 1,
        "cuentaCobrarId": 1,
        "monto": 25000.00,
        "metodoPago": "efectivo",
        "referencia": "REC-001",
        "fechaPago": "2026-02-26T15:30:00",
        "usuarioId": 1,
        "usuarioNombre": "admin",
        "createdAt": "2026-02-26T15:30:00"
      }
    ]
  }
}
```

---

### 3. Crear Cuenta por Cobrar

**Endpoint:** `POST /api/cuentas-cobrar`

**Request Body:**
```json
{
  "clienteId": 1,
  "ventaId": 1,
  "totalDeuda": 100000.00,
  "fechaEmision": "2026-02-26T10:00:00",
  "fechaVencimiento": "2026-03-26T10:00:00",
  "observaciones": "Compra de mercancía"
}
```

**Validation:**
- `clienteId` (required): Must be a valid client (tercero with esCliente=true)
- `totalDeuda` (required): Must be greater than 0
- `fechaEmision` (required): Emission date
- `fechaVencimiento` (optional): Due date

**Response:**
```json
{
  "status": 201,
  "message": "Cuenta por cobrar creada exitosamente",
  "error": false,
  "data": {
    "id": 1,
    "numeroCuenta": "CC-20260226-0001",
    "totalDeuda": 100000.00,
    "totalAbonado": 0.00,
    "saldoPendiente": 100000.00,
    "estado": "activa",
    "createdAt": "2026-02-26T10:00:00"
  }
}
```

---

### 4. Actualizar Cuenta por Cobrar

**Endpoint:** `PUT /api/cuentas-cobrar/{id}`

**Request Body:**
```json
{
  "fechaVencimiento": "2026-04-26T10:00:00",
  "observaciones": "Nueva observación"
}
```

**Nota:** Solo se pueden actualizar `fechaVencimiento` y `observaciones`.

**Response:**
```json
{
  "status": 200,
  "message": "Cuenta por cobrar actualizada exitosamente",
  "error": false,
  "data": { ... }
}
```

---

### 5. Registrar Abono (Entrada de Caja)

**Endpoint:** `POST /api/cuentas-cobrar/{id}/abonos`

**Request Body:**
```json
{
  "monto": 25000.00,
  "metodoPago": "efectivo",
  "referencia": "REC-001",
  "fechaPago": "2026-02-26T15:30:00"
}
```

**Validation:**
- `monto` (required): Must be greater than 0 and <= saldoPendiente
- `metodoPago` (required): Must be valid payment method

**Response:**
```json
{
  "status": 201,
  "message": "Abono registrado exitosamente",
  "error": false,
  "data": {
    "id": 1,
    "cuentaCobrarId": 1,
    "monto": 25000.00,
    "metodoPago": "efectivo",
    "referencia": "REC-001",
    "fechaPago": "2026-02-26T15:30:00",
    "usuarioId": 1,
    "usuarioNombre": "admin",
    "createdAt": "2026-02-26T15:30:00"
  }
}
```

**Error Scenarios:**
- **400:** "La cuenta ya está pagada"
- **400:** "El monto no puede ser mayor al saldo pendiente"
- **400:** "El monto debe ser mayor a 0"

---

### 6. Listar Abonos de Cuenta

**Endpoint:** `GET /api/cuentas-cobrar/{id}/abonos`

**Response:**
```json
{
  "status": 200,
  "message": "Abonos obtenidos exitosamente",
  "error": false,
  "data": [
    {
      "id": 1,
      "cuentaCobrarId": 1,
      "monto": 25000.00,
      "metodoPago": "efectivo",
      "referencia": "REC-001",
      "fechaPago": "2026-02-26T15:30:00",
      "usuarioId": 1,
      "usuarioNombre": "admin",
      "createdAt": "2026-02-26T15:30:00"
    }
  ]
}
```

---

### 7. Eliminar Abono

**Endpoint:** `DELETE /api/cuentas-cobrar/{cuentaId}/abonos/{abonoId}`

**Nota:** Solo se pueden eliminar abonos del día actual.

**Response:**
```json
{
  "status": 200,
  "message": "Abono eliminado exitosamente",
  "error": false,
  "data": null
}
```

**Error Scenarios:**
- **400:** "Solo se pueden eliminar abonos del día actual"
- **400:** "No se puede eliminar el abono de una cuenta pagada"

---

### 8. Obtener Resumen

**Endpoint:** `GET /api/cuentas-cobrar/resumen?fechaDesde=2026-01-01&fechaHasta=2026-12-31&clienteId=1&estado=activa`

**Query Parameters:**
- `fechaDesde` (optional): Start date
- `fechaHasta` (optional): End date
- `clienteId` (optional): Filter by client
- `estado` (optional): Filter by state (activa, pagada, vencida)

**Response:**
```json
{
  "status": 200,
  "message": "Resumen obtenido exitosamente",
  "error": false,
  "data": {
    "totalCuentas": 50,
    "totalDeuda": 5000000.00,
    "totalAbonado": 1500000.00,
    "saldoPendiente": 3500000.00,
    "cantidadActivas": 30,
    "cantidadPagadas": 15,
    "cantidadVencidas": 5
  }
}
```

---

### 9. Obtener Cuentas Vencidas

**Endpoint:** `GET /api/cuentas-cobrar/vencidas`

**Response:**
```json
{
  "status": 200,
  "message": "Cuentas vencidas obtenidas exitosamente",
  "error": false,
  "data": [
    {
      "id": 1,
      "numeroCuenta": "CC-20260101-0001",
      "clienteNombre": "Juan Pérez",
      "clienteDocumento": "12345678",
      "fechaEmision": "2026-01-01T10:00:00",
      "fechaVencimiento": "2026-01-31T10:00:00",
      "totalDeuda": 100000.00,
      "totalAbonado": 0.00,
      "saldoPendiente": 100000.00,
      "estado": "vencida"
    }
  ]
}
```

---

## Cuentas por Pagar (Accounts Payable)

### Endpoints (Same Structure as Cuentas por Cobrar)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/cuentas-pagar/page` | List with pagination and filters |
| GET | `/api/cuentas-pagar/{id}` | Get by ID |
| POST | `/api/cuentas-pagar` | Create |
| PUT | `/api/cuentas-pagar/{id}` | Update |
| POST | `/api/cuentas-pagar/{id}/abonos` | Register payment (salida de caja) |
| GET | `/api/cuentas-pagar/{id}/abonos` | List payments |
| DELETE | `/api/cuentas-pagar/{cuentaId}/abonos/{abonoId}` | Delete payment |
| GET | `/api/cuentas-pagar/resumen` | Get summary |
| GET | `/api/cuentas-pagar/vencidas` | Get overdue accounts |

### Diferencias en DTOs

**CreateCuentaPagarDto:**
```json
{
  "proveedorId": 1,
  "compraId": 1,
  "totalDeuda": 100000.00,
  "fechaEmision": "2026-02-26T10:00:00",
  "fechaVencimiento": "2026-03-26T10:00:00",
  "observaciones": "Compra de mercancía"
}
```

**AbonoPagarDto:**
```json
{
  "monto": 25000.00,
  "metodoPago": "transferencia",
  "referencia": "TRF-001",
  "banco": "Banco de Colombia",
  "fechaPago": "2026-02-26T15:30:00"
}
```

**Nota:** AbonoPagarDto incluye campo adicional `banco`.

---

## Frontend Implementation Checklist

### 1. Components Needed
- [ ] AccountListComponent (with pagination)
- [ ] AccountDetailComponent
- [ ] AccountFormComponent (create/update)
- [ ] PaymentFormComponent (register payment)
- [ ] PaymentListComponent
- [ ] SummaryDashboardComponent
- [ ] OverdueAccountsComponent

### 2. State Management
- [ ] Store accounts list with pagination state
- [ ] Cache single account details
- [ ] Handle loading and error states

### 3. Filters
- [ ] Date range picker (fechaDesde, fechaHasta)
- [ ] Client/Supplier dropdown
- [ ] Status filter (activa, pagada, vencida)
- [ ] Search functionality

### 4. Validations (Frontend)
- [ ] Validate monto > 0
- [ ] Validate monto <= saldoPendiente
- [ ] Validate required fields
- [ ] Format currency values (COP)
- [ ] Format dates

### 5. Business Rules
- [ ] Show warning when fechaVencimiento is near
- [ ] Highlight overdue accounts in red
- [ ] Disable "Registrar Abono" when saldoPendiente = 0
- [ ] Show payment history timeline
- [ ] Calculate and display progress percentage

### 6. UI/UX
- [ ] Color coding by status:
  - Green: pagada
  - Blue: activa
  - Red: vencida
- [ ] Progress bar for payment progress
- [ ] Quick actions (register payment, view details)
- [ ] Export to Excel/PDF capability

---

## Error Codes Reference

| Status | Code | Message |
|--------|------|---------|
| 400 | CLIENTE_NO_ENCONTRADO | Cliente no encontrado |
| 400 | PROVEEDOR_NO_ENCONTRADO | Proveedor no encontrado |
| 400 | NO_ES_CLIENTE | El tercero no es un cliente |
| 400 | NO_ES_PROVEEDOR | El tercero no es un proveedor |
 TOTAL_DEUDA_INVALIDO| 400 | | El total de deuda debe ser mayor a 0 |
| 400 | CUENTA_YA_PAGADA | La cuenta ya está pagada |
| 400 | MONTO_INVALIDO | El monto debe ser mayor a 0 |
| 400 | MONTO_MAYOR_SALDO | El monto no puede ser mayor al saldo pendiente |
| 400 | ABONO_DIA_INVALIDO | Solo se pueden eliminar abonos del día actual |
| 400 | CUENTA_PAGADA_NO_ELIMINAR | No se puede eliminar el abono de una cuenta pagada |
| 404 | CUENTA_NO_ENCONTRADA | Cuenta por cobrar/pagar no encontrada |
| 404 | ABONO_NO_ENCONTRADO | Abono no encontrado |

---

## Currency Format

All monetary values are in **COP (Colombian Pesos)**.

Format: `Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' })`

Example: `$100.000,00`

---

## Date Format

Format: `YYYY-MM-DDTHH:mm:ss` (ISO 8601)

Display format: `DD/MM/YYYY HH:mm` (Locale: es-CO)

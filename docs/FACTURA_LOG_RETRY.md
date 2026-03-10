# Factura Log & Retry System

Sistema de auditoría y reintentos para el módulo de facturación electrónica.

---

## Arquitectura General

```
[HTTP Request]
     │
     ▼
FacturaController
     │
     ▼
FacturaServiceImpl  ──── crearDesdeVenta()
     │  (transacción)
     │  1. Guarda factura
     │  2. Copia pagos
     │  3. publishEvent(FacturaLogEvent) ─── NO bloquea
     │
     ▼ COMMIT ✅
     
[Hilo separado: factura-log-X]  (después del commit)
     │
     ▼
FacturaLogServiceImpl.handleFacturaLogEvent()
     │  REQUIRES_NEW transaction
     │
     ▼
INSERT en factura_log ✅
```

---

## Tabla `factura_log`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | bigserial | PK |
| `factura_id` | int8 | FK → `factura` |
| `evento` | varchar(50) | Tipo de evento (ver catálogo) |
| `estado_anterior` | varchar(50) | Estado previo de la factura |
| `estado_nuevo` | varchar(50) | Estado resultante del evento |
| `datos` | jsonb | Snapshot de la factura en ese momento |
| `mensaje` | varchar(500) | Descripción del evento |
| `metadata` | jsonb | Payload para reintento (ver abajo) |
| `usuario_id` | int4 | Quién disparó el evento |
| `created_at` | timestamp | Cuándo ocurrió |

### Catálogo de Eventos (`evento`)

| Valor | Cuándo ocurre |
|---|---|
| `CREACION` | Se genera la factura desde una venta |
| *(futuros)* | `ENVIO_DIAN`, `ANULACION`, `RECHAZO`, etc. |

---

## Payload de Reintento (`metadata`)

Cada evento guarda en `metadata` los datos mínimos para poder **reproducir exactamente** la operación que lo originó.

### Ejemplo para `CREACION`

```json
{
  "action": "crearDesdeVenta",
  "ventaId": 17,
  "empresaId": 1,
  "usuarioId": 5
}
```

> **Regla**: El campo `action` siempre coincide con el nombre del método del servicio que debe invocarse para el reintento.

---

## Mecanismo de Reintento

### 1. Automático (Scheduler)

`FacturaRetryScheduler` corre cada **5 minutos** buscando logs en `PENDIENTE` que tengan `metadata.action`.

```
Cada 5 min:
    factura_log WHERE estado_nuevo = 'PENDIENTE'
                 AND metadata ? 'action'
    → Para cada uno: FacturaRetryService.ejecutarPayload()
```

Configurable via `application.properties`:
```properties
app.facturacion.retry.cron=0 */5 * * * *
```

### 2. Manual (Endpoint REST)

```http
POST /api/facturas/{facturaId}/reintentar
```

Lee el último log `PENDIENTE` de esa factura y ejecuta el reintento inmediatamente.

**Respuestas:**
- `200 OK` → Reintento ejecutado exitosamente
- `404 Not Found` → No hay log PENDIENTE con payload para esa factura

---

## Endpoints

```http
# Crear factura desde venta
POST /api/facturas/desde-venta?ventaId=1&empresaId=1&usuarioId=1

# Ver historial completo de logs de una factura
GET  /api/facturas/{facturaId}/logs

# Reintentar manualmente
POST /api/facturas/{facturaId}/reintentar
```

---

## Agregar un Nuevo Evento con Reintento

1. **Construir el `retryPayload`** en el servicio que dispara el evento:
   ```java
   Map<String, Object> retryPayload = new HashMap<>();
   retryPayload.put("action", "enviarADian");    // nombre del método
   retryPayload.put("facturaId", factura.getId());
   retryPayload.put("empresaId", empresaId);
   ```

2. **Publicar el evento** pasando el payload en `metadata`:
   ```java
   eventPublisher.publishEvent(new FacturaLogEvent(
       factura.getId(), "ENVIO_DIAN", "PENDIENTE", "ENVIADA",
       datos, usuarioId, "Factura enviada a DIAN", retryPayload
   ));
   ```

3. **Agregar el case** en `FacturaRetryService.ejecutarPayload()`:
   ```java
   case "enviarADian" -> {
       Long facturaId = toLong(metadata.get("facturaId"));
       dianService.enviar(facturaId);
   }
   ```

---

## Clases Clave

| Clase | Ubicación | Rol |
|---|---|---|
| `FacturaLogEntity` | `entity/` | Entidad JPA de la tabla `factura_log` |
| `FacturaLogEvent` | `event/` | Record del evento Spring |
| `FacturaLogService` | `services/` | Interface del servicio de logs |
| `FacturaLogServiceImpl` | `services/implementations/` | Guarda logs y escucha eventos |
| `FacturaRetryService` | `services/implementations/` | Lógica de reintento basada en `metadata.action` |
| `FacturaRetryScheduler` | `scheduler/` | Job automático cada 5 minutos |
| `FacturaController` | `controllers/` | Endpoints REST |
| `FacturaLogJPARepository` | `repositories/factura_log/` | Acceso a DB, incluye `findPendingWithRetryPayload()` |
| `AsyncConfig` | `config/` | Thread pool `facturaLogExecutor`, `@EnableScheduling` |

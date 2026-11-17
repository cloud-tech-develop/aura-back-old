# Plan — Módulo Servicio técnico (celulares, tablets y equipos)

Módulo opcional que se activa por empresa desde la plataforma, como cualquier otro
módulo del árbol. Para el cliente se ve como algo aparte (menú propio, rutas propias
`/servicio-tecnico/**` cargadas solo cuando entra), pero vive dentro de Aura para
reutilizar lo que ya existe:

| Necesidad | Lo que se reutiliza |
|---|---|
| Cliente | Terceros (`<app-tercero-autocomplete>`) |
| Técnico | Usuarios de la empresa (`/api/comisiones/tecnicos`) |
| Repuestos | Productos e inventario (fase 2 descuenta stock con la factura) |
| Fotos | Almacenamiento privado y cifrado de la nube (`NubeAlmacenamiento`), nunca el bucket público |
| Abonos | Recibo de caja como anticipo del cliente (fase 2) |
| Cobro final | Factura de venta, electrónica o no (fase 2) |
| Email | Resend + componente `enviar-correo` (fase 3) |
| PDF | Estilo de documentos de Aura (`pdf-estilo.ts`) (fase 3) |

## Decisiones (por defecto, cambiables)

1. **Repuestos**: cada línea puede ser un producto del inventario o un texto libre
   (repuesto comprado afuera). El stock se descuenta al facturar, no al anotarlo.
2. **Entrega**: con factura de venta por el saldo (fase 2) o sin factura (garantía,
   no reparado, sin cobro).
3. **Técnicos**: varios; la orden tiene técnico asignado y la bandeja filtra "Mis
   órdenes". Todos los que tienen el permiso ven todas.
4. **Clave/patrón de desbloqueo**: no se guarda en la v1. Si el cliente la exige,
   se agrega cifrada y visible solo para el técnico asignado.

## Estados

```
RECIBIDO → EN_DIAGNOSTICO → ESPERANDO_APROBACION → ESPERANDO_REPUESTO → EN_REPARACION → LISTO → ENTREGADO
                                    ↘ NO_REPARADO → ENTREGADO              CANCELADO (desde cualquiera no final)
```

Se permite saltar pasos hacia adelante (un arreglo simple va de RECIBIDO a EN_REPARACION)
y volver a uno anterior mientras no esté ENTREGADO o CANCELADO. Cada cambio queda en el
historial con usuario, fecha y nota. ENTREGADO y CANCELADO son finales.

## Modelo de datos (V249)

- `servicio_tecnico_config` — por empresa: prefijo (OS), siguiente número, días de
  garantía por defecto, condiciones que salen en la orden.
- `servicio_orden` — número/código, cliente (tercero), teléfono/correo de contacto,
  técnico, estado, prioridad, fechas (recibido, estimada, entregado), diagnóstico,
  trabajo realizado, observaciones, garantía, totales (repuestos, mano de obra, total,
  abonado, saldo), venta_id (fase 2).
- `servicio_orden_equipo` — la card: tipo, marca, modelo, IMEI (validado con Luhn),
  IMEI 2, serial, color, accesorios recibidos, estado físico, enciende, problemas
  reportados.
- `servicio_orden_foto` — fotos cifradas en la nube privada (clave del objeto, IV,
  versión de llave), momento (RECIBIDO / REPARACION / ENTREGA).
- `servicio_orden_item` — repuestos, mano de obra y otros: producto opcional,
  descripción, cantidad, valor unitario, total.
- `servicio_orden_historial` — cambios de estado y notas.

Abonos (fase 2): `servicio_orden_abono` ligado al recibo de caja.

## Fases

### F1 — Base (en curso)
- V249 + espejo Laravel + `docs/sql/menu_servicio_tecnico.sql` (módulo
  `servicio-tecnico`, submódulos `ordenes` y `configuracion`).
- Backend: entidades, repos, `ServicioOrdenService` (crear, editar, cambiar estado,
  items, notas, fotos, bandeja con filtros e indicadores), config.
- Frontend: bandeja con indicadores (recibidos, en reparación, listos, vencidos) y
  orden con pestañas: Equipo · Diagnóstico y servicio · Repuestos y valor · Historial.
- Permisos en `PermisoRutas` y `sidebar.config.ts`.

### F2 — Dinero
- Abonos con forma de pago → recibo de caja (anticipo del cliente) y caja del turno.
- Entregar: factura de venta por repuestos + mano de obra, cruzando los abonos;
  descuenta stock de los repuestos del inventario.
- Entregar sin factura (garantía / no reparado).

### F3 — Comunicación
- PDF de la orden (card del equipo, diagnóstico, valor, abonos, saldo, condiciones).
- "Enviar al cliente" por email con el PDF y las fotos.
- Etiqueta imprimible con número de orden para pegar al equipo.

### F4 — Opcional
- Link público de consulta del estado y aprobación del presupuesto.
- Historial por IMEI (el equipo ya vino antes).

## Estado (2026-11-17): F1–F4 hechas, local sin commit

- **V249** (tablas) y **V250** (abonos, factura de la orden, enlace público, servicio
  de facturación) corridas solo en local. Menú: `docs/sql/menu_servicio_tecnico.sql`.
- **Abono** = recibo de caja del cliente sin facturas aplicadas → anticipo (asiento AN).
  En efectivo va a una cuenta de caja o "otro día": la regla del recibo no deja anticipos
  dentro del arqueo del turno.
- **Entregar** (`POST /ordenes/{id}/entregar`): arma la factura con las líneas (valores =
  precio final, IVA incluido; la base se saca del IVA del producto), la emite a crédito
  cruzando los anticipos de los abonos y, si hay cobro, crea el recibo aplicado a esa
  factura (este sí puede entrar a la caja abierta). Todo en una transacción. Repuestos
  libres y mano de obra se facturan con el servicio de la configuración.
- **Correo**: `/api/correos/servicio-tecnico/{id}` (tipo SERVICIO_TECNICO) con el PDF y
  las fotos como adjuntos adicionales.
- **Enlace público**: `/consulta-servicio/{token}` (front) → `/api/auth/consulta-servicio/{token}`
  (permitAll, límite de intentos de /api/auth). Sin documento, teléfono ni IMEI completo.
  El cliente aprueba (→ EN_REPARACION) o rechaza (→ NO_REPARADO) el presupuesto.
- **Historial por IMEI**: aviso en la orden con las órdenes anteriores del mismo equipo.
- **Manual**: tema "Órdenes de servicio técnico" en /ayuda (solo con el permiso), con la
  guía de cómo crear los repuestos.

### Prueba completa por API (2026-11-17): 48/48
Script `prueba_servicio.py` (scratchpad de la sesión): servicio y repuesto creados por API,
cliente rápido, IMEI inválido/válido, foto cifrada (sube, se lee igual, rechaza no-imagen),
repuesto + mano de obra (230.000), estados, consulta pública sin datos personales y
aprobación (una sola vez), abonos efectivo/transferencia, anulación, topes, sugerencia de
correo, entrega facturada (factura EMITIDA por 230.000 con IVA, cartera en 0, stock 3→2,
orden cerrada), segunda orden del mismo IMEI, entrega sin factura por garantía y
aislamiento entre empresas. Asientos generados y cuadrados (ANTICIPO, ANULACION_ANTICIPO,
ANTICIPO_CRUCE, VENTA, ABONO_COBRAR). Correo real enviado a delivered@resend.dev.
Falta: probar las pantallas en el navegador (PDF, etiqueta, página pública).

### Producción
1. Migraciones Laravel 000249 y 000250.
2. `docs/sql/menu_servicio_tecnico.sql`.
3. Activar el módulo solo a la empresa del cliente desde la plataforma.
4. En la empresa: crear el servicio "Mano de obra" y elegirlo en Servicio Técnico › Configuración.

## Pruebas F1
1. Activar el módulo a la empresa desde la plataforma y dar permiso al perfil.
2. Crear orden con cliente nuevo y existente; IMEI inválido debe rechazarse.
3. Subir 3 fotos desde el celular (se comprimen) y verlas; borrar una.
4. Agregar repuesto del inventario, repuesto libre y mano de obra: el total cuadra.
5. Mover estados: saltar adelante, volver atrás, cancelar; ENTREGADO no se edita.
6. Bandeja: filtros por estado/técnico, "Mis órdenes", vencidas por fecha estimada.
7. Otra empresa no ve ni abre la orden ni sus fotos (404).

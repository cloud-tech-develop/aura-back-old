# Modo híbrido: servidor local por tienda

AURA puede correr en dos papeles con el **mismo código**:

| Papel | `app.sync.modo` | Qué hace |
|---|---|---|
| **Nube** | `NUBE` (por defecto) | El servidor de siempre. Recibe lo que hacen las tiendas y les entrega el catálogo. |
| **Nodo local** | `LOCAL` | Un backend + Postgres instalado en la tienda. El front de la tienda apunta a él. Trabaja igual con o sin internet y sincroniza cuando hay conexión. |

El nodo no es una réplica de la base: **reproduce operaciones**. Cada venta, turno o
cliente que se crea en la tienda queda en una cola (`sync_outbox`) y, cuando hay
internet, la nube la vuelve a ejecutar con sus mismos servicios. Por eso el inventario
se descuenta, la cartera se crea y **el asiento contable se genera en la nube** como si
la venta se hubiera hecho allá, con la fecha y el número que tuvo en la tienda.

```
 Tienda (LOCAL)                                    Nube (NUBE)
 ─────────────                                    ───────────
 POS ──► VentaService.crear ──► venta, inventario,
                               asiento local
                               + sync_outbox  ── push ──► ProcesadorComandos
                                                           └► VentaService.crear (replay)
                                                              inventario, cartera, asiento
          catálogo, saldos  ◄── pull ──────────────────── SyncTablas (row_to_json)
```

## Qué sincroniza hoy

**Tienda → nube** (en orden; si un comando falla, la cola se detiene ahí):

| Comando | Origen | En la nube |
|---|---|---|
| `TERCERO_CREAR` | `TerceroServiceImpl.crear` | Crea el tercero, o enlaza el existente con el mismo documento |
| `TURNO_ABRIR` / `TURNO_CERRAR` | `TurnoCajaServiceImpl` | Abre / cierra el turno con la fecha original |
| `MOVIMIENTO_CAJA` | `TurnoCajaServiceImpl.registrarMovimiento` | Ingreso/egreso; el abono a CxC se ubica por su venta |
| `VENTA_CREAR` | `VentaServiceImpl.crear` (POS) | Misma venta, mismo prefijo y consecutivo; descuenta inventario y contabiliza |
| `VENTA_ANULAR` | `VentaServiceImpl.anular` | Anula la venta (o la factura de Facturación que la originó) |
| `FACTURA_EMITIR` | `FacturaVentaService.emitir` | Crea y emite la factura ERP con la numeración del nodo |
| `FE_EMITIR` | `FacturaElectronicaHibridaService` | Envía la factura electrónica a Factus y devuelve el CUFE al nodo |

**Nube → tienda** (`SyncTablas`):

- **Catálogo**, cada `app.sync.catalogo-cada-ms` (5 min): empresa, sucursales, cajas,
  bodegas, usuarios y permisos, terceros, productos, presentaciones, precios, descuentos,
  impuestos, formas de pago, cuentas bancarias, plan de cuentas y configuración contable.
- **Existencias** (inventario, lotes, seriales): solo cuando la cola está vacía, es decir,
  cuando la nube ya tiene todo lo que pasó en la tienda.

## Reglas de diseño

1. **Ids sin choques.** La base del nodo arranca sus secuencias en 1.500.000.000
   (`SyncIds.BASE_LOCAL`, `scripts/nodo-local/preparar_nodo.sql`). Lo que baja de la nube
   conserva su id; lo creado en la tienda queda por encima. La nube traduce esos ids con
   `sync_id_map`.
2. **Numeración propia.** Cada nodo tiene un prefijo de ventas (`sync_nodo.prefijo_ventas`,
   ej. `T1`) que la nube nunca usa: `T1-1, T1-2…`. La nube excluye esas series al
   calcular el consecutivo de la sucursal. El nodo no vende hasta haber sincronizado una
   vez (necesita su prefijo y el último consecutivo conocido).
3. **Idempotencia.** Cada comando lleva un uuid; la nube guarda los procesados en
   `sync_recibido`. Si el nodo reintenta un lote que la nube alcanzó a procesar, no se
   duplica nada.
4. **Lo que ya pasó no se bloquea.** Al reproducir (`SyncContexto.enReplay()`), la nube no
   vuelve a exigir stock, cupo de crédito, permisos, turno abierto ni vencimientos: la
   mercancía ya salió. El stock puede quedar negativo en la nube y se ve en el kardex.
5. **Fecha original.** `SyncContexto.ahora()` devuelve la hora en que ocurrió en la tienda;
   el asiento cae en el período correcto aunque la sincronización llegue días después.
6. **Solo la nube habla con Factus.** En el nodo, pedir la factura electrónica deja la venta
   en `PENDIENTE_SYNC` y encola `FE_EMITIR`; con internet se sube de inmediato y vuelve el
   CUFE. Un rechazo de la DIAN no detiene la cola: se corrige y reenvía desde la nube.
7. **Tareas programadas.** En el nodo no corren reintentos de Factus, causaciones,
   diferidos ni evaluación de acuerdos de pago: las hace la nube.

## Instalación de un nodo

1. **En la nube**: aplicar la migración V226 (Laravel `2026_10_17_000226_sync_hibrido`).
2. **Registrar el nodo** (usuario ADMIN de la empresa):
   ```http
   POST /api/sync/nodos
   { "codigo": "T1", "nombre": "Tienda centro", "sucursalId": 3, "prefijoVentas": "T1" }
   ```
   La respuesta trae el **token**: se muestra una sola vez.
3. **En el equipo de la tienda**: instalar Postgres y crear la base del nodo con el
   esquema de la nube (mismas migraciones, o `pg_dump --schema-only`).
4. Correr **una vez** `scripts/nodo-local/preparar_nodo.sql` en la base del nodo.
5. Configurar el backend con `scripts/nodo-local/nodo.env.example`
   (`APP_SYNC_MODO=LOCAL`, URL de la nube y token) y arrancarlo **con internet** la
   primera vez: baja el catálogo y los usuarios pueden iniciar sesión.
6. Apuntar el front de la tienda al backend local.

## Operación

| Endpoint | Dónde | Para |
|---|---|---|
| `GET /api/sync/local/estado` | nodo | Indicador en línea / pendientes / errores |
| `GET /api/sync/local/cola` | nodo | Ver la cola (ADMIN) |
| `POST /api/sync/local/sincronizar` | nodo | Sincronizar ya |
| `POST /api/sync/local/cola/{id}/descartar` | nodo | Sacar un comando en error que no se resolverá (ADMIN) |
| `GET /api/sync/nodos`, `PATCH /api/sync/nodos/{id}/activo` | nube | Administrar nodos (ADMIN) |

## Límites conocidos (siguientes fases)

- **El catálogo se edita en la nube.** Lo que se cambie de productos, precios o terceros en
  el nodo se sobrescribe en la siguiente bajada. Lo único que el nodo crea y sube es el
  tercero nuevo (se fusiona con la copia de la nube al bajar).
- **Borrados**: un registro borrado físicamente en la nube sigue en el nodo; el catálogo
  usa borrado lógico (`activo`, `deleted_at`), que sí baja.
- **Cupo de crédito**: el nodo valida con la cartera que conoce (la creada en la tienda);
  la cartera de la nube todavía no baja.
- **No sincronizan aún**: compras, devoluciones, traslados, ajustes de inventario, recibos
  de caja, cotizaciones, pedidos, nómina y documentos contables manuales. Se hacen en la
  nube. Cada uno se agrega como un comando nuevo (`ManejadorComando`) siguiendo el mismo
  patrón.
- **Contabilidad del nodo**: el nodo genera sus asientos para sus propios reportes, pero los
  oficiales son los de la nube.

---

# Análisis y decisiones (17-oct-2026)

## Estado actual del código

| Qué | Estado |
|---|---|
| Implementación backend (paquete `sync/`, `SyncController`, cambios en servicios) | Hecha, **sin commit**, en `main` local |
| Compilación | Compiló a mitad de la implementación; los últimos cambios en `ReplicadorLocal`, `SincronizadorLocal`, `SeriesVenta` y las pruebas `SeriesVentaTest` / `TraductorIdsTest` **no se han compilado** |
| Prueba de punta a punta (nube ↔ tienda) | **No hecha**: el empaquetado se detuvo por falta de memoria en el equipo |
| Bases de prueba | `aura_sync_nube` y `aura_sync_nodo` creadas con datos de prueba (`cajero@prueba.co` / `Aura2026!`, nodo `T1`, token `token-prueba-nodo-T1`). `aura_pos` no se tocó |
| Migración Laravel | `2026_10_17_000226_sync_hibrido.php` creada; el repo Laravel llega a la 197 y el espejo SQL va en V225 (desfase previo a este trabajo) |
| Frontend | Sin cambios |

### Impacto de estos cambios en la nube (si se despliegan)

Con `app.sync.modo=NUBE` (valor por defecto) el comportamiento es el mismo de hoy:

- Los servicios solo encolan operaciones cuando corren como nodo local.
- Las tareas programadas solo se apagan en el nodo.
- El consecutivo de ventas excluye las series de los nodos; **sin nodos registrados no cambia nada**. Si la tabla `sync_nodo` no existe, la consulta lo detecta antes y sigue igual.
- Las tablas `sync_*` se usan por JDBC, no hay entidades JPA: `ddl-auto=validate` no se afecta.
- Riesgo real: lo que no se ha probado de punta a punta. **Recomendación: no descartar, pero no llevar a `main` ni a producción hasta compilar y hacer la prueba completa.** Moverlo a una rama `feature/modo-hibrido`.

## Cómo explicarlo al cliente

**En una frase:** la tienda sigue vendiendo aunque se caiga el internet, y cuando vuelve, todo se envía solo a la nube.

- **Qué se instala:** un computador por tienda que hace de servidor local (AURA + base de datos). Las cajas trabajan contra él. La nube sigue siendo la central (todas las sedes, contabilidad, reportes).
- **Con internet:** todo normal; cada venta sube en segundos, la factura electrónica sale de inmediato con CUFE y QR, y los cambios de la nube (productos, precios, clientes, usuarios) bajan cada pocos minutos.
- **Sin internet:** se abre y cierra caja, se vende, se crean clientes, se anula, se imprime y se descuenta inventario. Todo queda en una cola de pendientes que no se pierde aunque se apague el equipo. La factura electrónica queda "en cola" y el CUFE llega al volver la conexión.
- **Cuando vuelve el internet:** la cola se envía sola y en orden. Las ventas llegan con su fecha y número originales, el inventario se descuenta en la central, la contabilidad se genera con la fecha real y las facturas electrónicas se envían a la DIAN.
- **Números de factura:** cada tienda tiene su serie (`T1-1`, `T1-2`…), distinta a la de la nube; nunca se repiten.

**Reglas para el cliente:**
1. Productos, precios, usuarios y configuración se administran desde la nube.
2. La primera vez el servidor de la tienda necesita internet para descargar los datos; sin eso no vende.
3. Sin internet, la tienda no ve las otras sedes (el cupo de crédito se valida solo con lo vendido en esa tienda).
4. Si la DIAN rechaza una factura, la tienda no se bloquea; se corrige desde la nube.

| Funciona sin internet | Por ahora requiere internet |
|---|---|
| Ventas POS, facturas, anulaciones | Compras y recepción de mercancía |
| Apertura/cierre de caja, ingresos y egresos | Devoluciones, traslados, ajustes de inventario |
| Crear clientes | Recibos de caja, cotizaciones, pedidos |
| Factura electrónica (queda en cola) | Nómina y comprobantes contables manuales |

**Pendiente legal (validar con el contador):** reglas DIAN de facturación en contingencia, en particular el plazo de transmisión (se entiende que son 48 horas después de superado el problema) y los requisitos del comprobante impreso sin CUFE.

## Instalación: qué va en cada equipo

- **PCs de los cajeros: nada.** Usan el navegador apuntando a la dirección del servidor de la tienda en la red local (ej. `http://192.168.1.10`), con un acceso directo en el escritorio. Abre aunque no haya internet.
- **Servidor de la tienda (una vez):** PostgreSQL, backend AURA en modo `LOCAL`, el frontend servido desde ese mismo equipo y el archivo de configuración (URL de la nube + token). Todo como servicio de Windows. Si la tienda tiene una sola caja, esa caja puede ser el servidor.
- **Requisitos del servidor:** encendido durante la jornada, red por cable, IP fija, idealmente UPS.

**Puesta en marcha de una tienda:**
1. Un administrador registra la tienda en la nube (`POST /api/sync/nodos`, prefijo `T1`) y obtiene el token.
2. El técnico instala el paquete en el servidor de la tienda y pega el token.
3. Primera sincronización con internet (catálogo, usuarios, existencias).
4. Acceso directo a la dirección local en cada caja.

**Actualizaciones:** cada versión nueva hay que llevarla también a cada servidor de tienda. La nube y las tiendas no deben quedar con versiones muy distintas, porque los comandos de la cola tienen que entenderse en ambos lados.

## Empaquetado: opciones y esfuerzo

| Nivel | Qué incluye | Esfuerzo aprox. |
|---|---|---|
| 1. Script de instalación (técnico) | Instala PostgreSQL, crea la base, corre `preparar_nodo.sql`, copia backend + frontend, servicio de Windows, pide token | 2–3 días |
| 2. Instalador `.exe` (Inno Setup) | Lo mismo con asistente y Java incluido | ~1 semana |
| 3. Actualización automática | Descarga e instala versiones nuevas desde la nube | 1–2 semanas más |

Recomendación: nivel 1 para las primeras tiendas; nivel 2 cuando el proceso esté probado; nivel 3 antes de tener muchas tiendas.

## Indicador de conexión (por hacer)

Barra superior, solo visible cuando AURA corre en un servidor de tienda:

- 🟢 **En línea**: todo sincronizado.
- 🔵 **Sincronizando… (N pendientes)**.
- 🟠 **Sin internet: trabajando local (N pendientes)**.
- 🔴 **Error de sincronización**: clic para ver el detalle y reintentar.

Panel al hacer clic: última sincronización, pendientes, último error y botón **Sincronizar ahora**.

- Backend: `/api/sync/local/estado` ya da `modo`, `enLinea`, `pendientes`, `conError`, `ultimoEnvio`, `ultimaBajada` y `ultimoError`. **Falta** el campo "sincronizando ahora" (una bandera en `SincronizadorLocal`).
- Frontend: consulta periódica del estado + componente en la barra. ~1 día.

## ¿Kafka o RabbitMQ? Decisión: no

1. Lo implementado (cola en PostgreSQL guardada en la misma transacción que la venta + envío HTTP por lotes) es el patrón recomendado para conexión intermitente. Con un broker igual habría que tener esa cola para no perder mensajes sin red.
2. Sería otro servicio que instalar, mantener y actualizar en cada tienda (Kafka es pesado; RabbitMQ es más liviano, pero es una pieza más que puede fallar).
3. El volumen de una tienda (cientos o miles de operaciones al día) no lo justifica.
4. Lo difícil (orden, no duplicar, traducir ids de la tienda a la nube) ya está resuelto y ningún broker lo hace.

Podría tener sentido más adelante **dentro de la nube** (notificaciones, reportes en tiempo real), no entre la tienda y la nube.

## Pendientes del frontend

1. Versión del frontend para tienda: hoy `src/environments/environment.prod.ts` tiene fija la URL de la nube (`https://back-v0.cloudtecnology.cloud/api/`). Crear un entorno con `apiUrl: '/api/'` para que una misma compilación sirva en todas las tiendas.
2. Servir el frontend desde el servidor de la tienda (backend o nginx).
3. Indicador de conexión (ver arriba).
4. Las notificaciones en tiempo real (`socket.cloudtecnology.cloud`) no llegan sin internet; no afectan la venta.

## Roadmap propuesto

1. Mover los cambios a la rama `feature/modo-hibrido`.
2. Compilar, correr las pruebas unitarias y hacer la prueba completa nube ↔ tienda (requiere memoria libre).
3. Indicador de conexión (backend + frontend) y entorno de frontend para tienda.
4. Script de instalación (nivel 1) y copias de seguridad automáticas de la base de la tienda.
5. Validar la contingencia DIAN con el contador.
6. Siguientes módulos sin internet: devoluciones, recibos de caja, ajustes y traslados, compras.
7. Instalador `.exe` y actualizador automático.

# Modo híbrido: la nube es la principal, la tienda la contingencia

> **Decisión (18-oct-2026).** AURA es "en la nube": los usuarios entran por
> `https://www.aura-pos.tech` y trabajan contra la nube. El servidor local de la tienda
> es la **contingencia**: cuando se cae internet, la tienda sigue vendiendo en él, en el
> mismo turno que tenía abierto en la nube, y al volver la conexión todo sube solo.

AURA puede correr en dos papeles con el **mismo código**:

| Papel | `app.sync.modo` | Qué hace |
|---|---|---|
| **Nube** | `NUBE` (por defecto) | El servidor de siempre. Recibe lo que hacen las tiendas y les entrega catálogo, existencias y turnos. |
| **Nodo local** | `LOCAL` | Backend + Postgres instalados en la tienda. Trabaja igual con o sin internet y sincroniza cuando hay conexión. |

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
   catálogo, saldos, turnos ◄── pull ─────────── SyncTablas (row_to_json) + GuardaEmpresa
```

## Cómo se usa en la tienda (modelo de contingencia)

1. **Con internet**: todos entran a `aura-pos.tech` y trabajan en la nube, como siempre.
   Mientras tanto el servidor de la tienda baja, cada 15 s, los turnos abiertos de la
   nube y, cada 5 min, el catálogo y las existencias.
2. **Se cae internet**: el front de `aura-pos.tech` (que abre desde la caché del
   navegador) avisa "Sin internet: siga en el servidor de la tienda" y lleva al cajero a
   `http://<IP del servidor>`. Allí está la última información sincronizada y **el mismo
   turno** que tenía abierto en la nube: sigue vendiendo sin abrir otro. *(El aviso del
   front está por hacer, ver Pendientes.)*
3. **Vuelve internet**: la tienda sube la cola en orden; las ventas de contingencia
   quedan en el turno de la nube y el cierre lo recalcula la nube con todas las ventas
   del día. El cajero regresa a `aura-pos.tech`.

**Regla para el cliente:** en una misma caja, todos los equipos trabajan a la vez en el
mismo sistema (todos en la nube, o todos en la tienda durante el corte). Mezclar —unos en
la nube y otros en la tienda al mismo tiempo— parte el turno y el efectivo en dos.

## Qué sincroniza

**Tienda → nube** (en orden; si un comando falla, la cola se detiene ahí):

| Comando | Origen | En la nube |
|---|---|---|
| `TERCERO_CREAR` | `TerceroServiceImpl.crear` | Crea el tercero, o enlaza el existente con el mismo documento |
| `TURNO_ABRIR` / `TURNO_CERRAR` | `TurnoCajaServiceImpl` | Abre / cierra con la fecha original. El cierre se recalcula en la nube; si la nube ya lo cerró, se acepta sin error |
| `MOVIMIENTO_CAJA` | `TurnoCajaServiceImpl.registrarMovimiento` | Ingreso/egreso; el abono a CxC se ubica por su venta |
| `VENTA_CREAR` | `VentaServiceImpl.crear` (POS) | Misma venta, mismo prefijo y consecutivo; descuenta inventario (kardex con la hora original) y contabiliza |
| `VENTA_ANULAR` | `VentaServiceImpl.anular` | Anula la venta (o la factura de Facturación que la originó) |
| `FACTURA_EMITIR` | `FacturaVentaService.emitir` | Crea y emite la factura ERP con la numeración del nodo |
| `FE_EMITIR` | `FacturaElectronicaHibridaService` | Envía la factura electrónica a Factus y devuelve el CUFE al nodo |

**Nube → tienda** (`SyncTablas`, tres grupos):

| Grupo | Cuándo | Qué |
|---|---|---|
| `CATALOGO` | cada `app.sync.catalogo-cada-ms` (5 min) | empresa, sucursales, cajas, bodegas, usuarios y permisos, terceros, productos, presentaciones, precios, descuentos, impuestos, formas de pago, cuentas bancarias, plan de cuentas, configuración contable |
| `SALDO` | con la bajada del catálogo, **solo con la cola vacía** | inventario, lotes, seriales |
| `TURNOS` | **cada ciclo (15 s) con la cola vacía** (`/api/sync/pull?soloTurnos=true`) y con los saldos | turnos de caja abiertos y del último día de la empresa, salvo los que abrió un nodo (esos ya los tiene quien los abrió) |

## Reglas de diseño

1. **Ids sin choques.** La base del nodo arranca sus secuencias en 1.500.000.000
   (`SyncIds.BASE_LOCAL`, `preparar_nodo.sql`). Lo que baja de la nube conserva su id; lo
   creado en la tienda queda por encima. La nube traduce esos ids con `sync_id_map`; un id
   de la nube (por ejemplo, el turno de la nube en contingencia) pasa sin traducir.
2. **Numeración propia.** Cada nodo tiene un prefijo de ventas (`sync_nodo.prefijo_ventas`,
   ej. `T1`) que la nube nunca usa: `T1-1, T1-2…`. La nube excluye esas series al
   calcular el consecutivo de la sucursal. El nodo no vende hasta haber sincronizado una vez.
3. **Idempotencia.** Cada comando lleva un uuid; la nube guarda los procesados en
   `sync_recibido`. Reenviar un lote no duplica nada (probado).
4. **Lo que ya pasó no se bloquea.** Al reproducir (`SyncContexto.enReplay()`), la nube no
   vuelve a exigir stock, cupo, permisos, turno abierto ni vencimientos.
5. **Fecha original.** `SyncContexto.ahora()` devuelve la hora de la tienda: venta, turno,
   kardex y asiento caen en el período correcto aunque la sincronización llegue días después.
6. **Solo la nube habla con Factus.** En el nodo, la factura electrónica queda
   `PENDIENTE_SYNC` y encola `FE_EMITIR`; con internet vuelve el CUFE de inmediato.
7. **Tareas programadas.** En el nodo no corren reintentos de Factus, causaciones,
   diferidos ni acuerdos de pago: las hace la nube.
8. **Aislamiento por empresa (estricto).** La empresa la define el **token del nodo**, nunca
   lo que envía la tienda: al bajar, cada tabla tiene un filtro SQL fijo; al subir, cada
   comando se procesa con la empresa del nodo y se rechaza un usuario de otra empresa.
   Segunda barrera, `GuardaEmpresa`: antes de enviar, la nube revisa fila por fila que
   `empresa_id` sea el del nodo (o vacío en plantillas globales); si aparece otra empresa,
   **cancela toda la bajada** y lo registra. La base del nodo tiene una sola empresa
   (auditado: 0 filas ajenas, incluidas las tablas hijas sin `empresa_id`).
9. **Turno compartido.** Los turnos de la nube bajan con su id. En contingencia, el POS de
   la tienda encuentra el turno abierto (`obtenerTurnoActivo`) y vende en él; las ventas
   suben pegadas a ese turno. Los turnos solo se aplican con la cola vacía, así un cierre
   hecho en la tienda y aún sin subir nunca se pisa con el turno que la nube aún tiene abierto.
10. **Réplica por foto completa.** Con las llaves apagadas (superusuario), cada tabla con
    llave `id` se reemplaza completa: un nodo nuevo, con ids sembrados por las migraciones
    distintos a los de la nube, no choca con los índices únicos.

## Instalación de una tienda

**Antes, en la nube:** la migración V227 (Laravel `2026_07_03_000227_sync_hibrido`) y
registrar la tienda (usuario ADMIN de la empresa):

```http
POST /api/sync/nodos
{ "codigo": "T1", "nombre": "Tienda centro", "sucursalId": 3, "prefijoVentas": "T1" }
```

La respuesta trae el **token** (se muestra una sola vez).

**En el servidor de la tienda: `AuraTienda-Setup-<versión>.exe`** (Inno Setup, ~234 MB).
El asistente pide la URL de la nube y el token, muestra el avance en una consola y al
final la dirección para las cajas y si el catálogo quedó sincronizado. Sin asistente:
`AuraTienda-Setup.exe /VERYSILENT /NUBE=<url> /TOKEN=<token>`. Volver a correrlo actualiza
backend y front y conserva base, llaves y token.

Lo que deja en `C:\AuraTienda`:

| Servicio / pieza | Detalle |
|---|---|
| `AuraPostgres` | PostgreSQL 17 (sin pgAdmin), solo `localhost`, puerto **5433** |
| `AuraBackend` | Java 17 + `aura.jar` en modo `LOCAL`, solo `127.0.0.1`, puerto **9091** |
| `AuraWeb` | nginx en el puerto **80**: sirve el front (`--configuration tienda`, `apiUrl: '/api/'`) y pasa `/api` al backend |
| Llaves | JWT, AES y clave de Postgres propias, generadas al azar en `config\tienda.json` (solo administradores) |
| Firewall | Puerto 80 abierto en redes privadas/dominio (avisa si la red está como Pública) |
| Respaldo | Tarea diaria 22:00, `pg_dump` en `respaldos\`, 14 días |
| Logs | `logs\instalacion.log`, `logs\AuraBackend.*.log`, `logs\resumen.txt` |

Desinstalar desde "Agregar o quitar programas": quita servicios, firewall y tarea; la base
y los respaldos **se conservan** (puede haber ventas sin subir).

**Armar el instalador** (equipo de desarrollo):

```powershell
.\scripts\nodo-local\empaquetar.ps1 -Instalador
```

Descarga una vez PostgreSQL, Java, nginx y WinSW (`dist\cache-empaquetar`), saca el esquema
de una base **recién migrada con Laravel** (`-BaseReferencia`, por defecto `aura_sync_nodo`),
compila backend (`mvn clean package`) y front, y compila el `.exe` en `dist\`. Requiere Inno
Setup 6 (`winget install --id JRSoftware.InnoSetup -e --scope user`).

Archivos: `scripts/nodo-local/` → `empaquetar.ps1`, `instalar.ps1`, `desinstalar.ps1`,
`respaldar.ps1`, `preparar_nodo.sql`, `LEEME-TIENDA.txt`, `instalador/aura-tienda.iss`.

**Requisitos del servidor:** Windows 10/11 64 bits, 8 GB RAM, red por cable, IP fija,
encendido en la jornada, ideal con UPS. **En las cajas no se instala nada.**

## Migraciones (Laravel) para una base nueva

Una base vacía ya se instala completa con `php artisan migrate` y el backend arranca con
`ddl-auto=validate`. Para lograrlo (17-oct-2026):

- 29 migraciones `2024_*` ahora agregan columnas solo si no existen.
- Nuevas: `pedido_vendedor` (V27) y precios dinámicos (V14), que no tenían espejo;
  `cuenta_config` y obligaciones financieras, y `tercero.es_banco` (las creó Hibernate).
- `2026_10_17_000001_alinear_esquema_base_nueva`: columnas faltantes, 44 tipos
  integer/bigint, `venta.fecha_emision` con zona, `dv` varchar y 165 nulos. Todo
  condicional: en las bases en uso no hace nada.

Para verificar una base nueva: comparar `information_schema.columns` contra `aura-pos`.

## Operación

| Endpoint | Dónde | Para |
|---|---|---|
| `GET /api/sync/local/estado` | nodo | En línea / pendientes / errores |
| `GET /api/sync/local/cola` | nodo | Ver la cola (ADMIN) |
| `POST /api/sync/local/sincronizar` | nodo | Sincronizar ya |
| `POST /api/sync/local/cola/{id}/descartar` | nodo | Sacar un comando en error que no se resolverá (ADMIN) |
| `GET /api/sync/pull?saldos=&soloTurnos=` | nube | Bajada del nodo (token) |
| `GET /api/sync/nodos`, `PATCH /api/sync/nodos/{id}/activo` | nube | Administrar nodos (ADMIN) |

## Límites conocidos

- **El catálogo se edita en la nube.** Lo que se cambie en el nodo se sobrescribe en la
  siguiente bajada; lo único que el nodo crea y sube es el tercero nuevo.
- **Cupo de crédito**: el nodo valida con la cartera que conoce; la de la nube no baja.
- **No sincronizan aún**: compras, devoluciones, traslados, ajustes, recibos de caja,
  cotizaciones, pedidos, nómina y documentos contables manuales (se hacen en la nube).
- **Cierre en contingencia**: el total que ve el cajero en la tienda solo incluye lo vendido
  sin internet; el definitivo lo recalcula la nube al sincronizar.
- **Contabilidad del nodo**: solo para sus reportes; los oficiales son los de la nube.
- **Actualizar una tienda** con migraciones nuevas: el instalador conserva la base pero aún
  no le aplica migraciones (hoy se hace a mano).

---

# Estado y bitácora

## Estado (18-oct-2026)

| Qué | Estado |
|---|---|
| Rama | `feature/modo-hibrido` (backend). Front: `environment.tienda.ts` + configuración `tienda` en `angular.json` (repo aura-frontend, sin commit) |
| Pruebas unitarias | `SeriesVentaTest`, `TraductorIdsTest`, `GuardaEmpresaTest`: 12/12 |
| Entorno de prueba | `docker-compose.hibrido.yml`: nube `:9001` (`aura_sync_nube`, datos de `aura-pos`), tienda T1 `:9002` (`aura_sync_nodo`, base creada desde cero). FERRETOTAL (empresa 4, sucursal 5) |
| Nodos de prueba | `T1` (Docker, token `token-prueba-nodo-T1`), `T2` (instalado con el `.exe` en el equipo de desarrollo, token `token-prueba-nodo-T2`) |

## Pruebas hechas

- **17-oct, nube ↔ tienda T1:** venta con internet; sin internet 2 ventas, anulación,
  cliente y cierre de turno; al volver, 7 comandos en orden y sin error, ventas con su
  número y hora, inventario y 4 asientos cuadrados; **reenvío de toda la cola sin duplicar**.
- **17-oct, instalador:** T2 instalado con el `.exe`, catálogo sincronizado y venta `T2-1`
  en la nube.
- **18-oct, aislamiento:** base de T1 sin una sola fila de otra empresa; `GuardaEmpresa` no
  frena una bajada legítima.
- **18-oct, turnos:** T1 bajó los 4 turnos abiertos nacidos en la nube; los abiertos por un
  nodo no se duplican.

## Corregido en el camino

- `ReplicadorLocal`: un nodo nuevo no podía bajar `submodulos` (ids distintos a los de la nube).
- Kardex de una venta reproducida con la hora de la sincronización → `SyncContexto.ahora()`.
- Empaquetado: el IDE dejaba clases rotas en `target/classes` → `mvn clean package`.
- Instalador: PostgreSQL sin pgAdmin (de 315 a 45 MB, se descomprime en 1,4 s con el
  `tar.exe` de Windows —el de Git falla con rutas `D:`—), backend en 9091 (el 9001 choca),
  consola con el avance, solo IPs de adaptadores físicos.

## Pendientes

1. **Prueba de contingencia con el turno de la nube**: abrir turno en la nube (usuario
   `jota@gmail.com`, turno 59), vender, apagar la nube, seguir en la tienda en el mismo
   turno, prender y revisar ventas y cierre recalculado en la nube.
2. **Aviso de contingencia en `aura-pos.tech`**:
   - backend: el nodo informa a la nube sus direcciones locales; endpoint para el front;
   - front nube: revisar conexión con la nube cada 10–15 s (no basta `navigator.onLine`),
     aviso "Sin internet → siga en el servidor de la tienda" con botón; la dirección se
     guarda en el navegador mientras hay internet;
   - front tienda: "Volvió internet, todo sincronizado → volver a aura-pos.tech" con la cola
     vacía; aviso de cierre parcial en contingencia.
3. **Indicador de conexión** en la tienda (`/api/sync/local/estado`; falta la bandera
   "sincronizando ahora").
4. **DIAN (con el contador)**: la serie de la tienda (`T1`) necesita su propio rango de
   numeración autorizado; plazo de transmisión en contingencia y comprobante sin CUFE.
5. Revisar la diferencia en 0 del cierre de turno (no es del modo híbrido).
6. Migraciones al actualizar una tienda; actualizador automático.
7. Siguientes operaciones sin internet: devoluciones, recibos de caja, ajustes, traslados, compras.

## Decisiones

- **Kafka/RabbitMQ: no.** La cola en PostgreSQL (misma transacción que la venta) + HTTP por
  lotes es el patrón para conexión intermitente; un broker sería otra pieza por tienda y no
  resuelve orden, idempotencia ni traducción de ids.
- **El front de la nube no puede usar el servidor local como API** (https → http y acceso a
  la red local los bloquea el navegador; un nombre DNS no resuelve sin internet). Por eso el
  paso a la tienda es una **navegación** a su dirección, no llamadas desde `aura-pos.tech`.
- **App de escritorio en las cajas: no por ahora** (iría contra "AURA en la nube").

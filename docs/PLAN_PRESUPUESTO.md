# Plan: presupuesto por cuenta contable

Fecha: 2026-09-20 · Estado: **diseñado, no implementado** · Eje elegido: **cuenta del PUC**

La idea del negocio: una empresa se crea diciendo si maneja presupuesto. Si lo maneja, se
carga el presupuesto del año y **la contabilidad trabaja contra ese presupuesto**: cada
gasto ejecutado descuenta del rubro, y el sistema avisa (o frena) cuando la cuenta se queda
sin plata.

---

## 1. Qué hay hoy

| Pieza | Qué es | Sirve para el presupuesto |
|---|---|---|
| `plan_cuenta` | PUC por empresa: `codigo`, `nombre`, `tipo`, `naturaleza`, `nivel`, `padre_id`, `auxiliar` | **Sí.** Es el eje del presupuesto. |
| `asiento_detalle` | `cuenta_id`, `debito`, `credito`, `tercero_id`, `centro_costo_id`, `proyecto_id`, `frente_id` | **Sí.** De aquí sale la ejecución real, sin parametrizar nada. |
| `periodo_contable` + `PeriodoContableResolver` | Período por fecha del documento, varios meses abiertos, apertura automática (V171) | **Sí.** El presupuesto se corta por el mismo período que la contabilidad. |
| `centro_costo` | Tiene campos de presupuesto | Parcial. Es otro eje; queda como **dimensión opcional** de la línea (ver §7). |
| `proyecto_presupuesto` (V116) | Presupuesto de obra/proyecto | **No.** Es otra cosa: control de obra, no presupuesto contable de la empresa. Convive. |
| `orden_compra` | Órdenes aprobadas no facturadas | **Sí**, para el *comprometido* (§5). |
| `POST /api/platform/empresas` | Alta de empresa desde el super admin | Aquí entra el interruptor `maneja_presupuesto`. |

**No existe** ninguna tabla de presupuesto a nivel empresa. Se construye desde cero.

---

## 2. Modelo de datos (migración V172+, idempotente y espejada en Laravel)

### 2.1 Interruptor en la empresa

```sql
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS maneja_presupuesto BOOLEAN NOT NULL DEFAULT FALSE;
-- Qué hace el sistema cuando un documento excede el rubro.
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS presupuesto_politica VARCHAR(15) NOT NULL DEFAULT 'ALERTA';
--   INFORMATIVO = solo reporta   ALERTA = avisa y deja pasar   BLOQUEO = no deja guardar
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS presupuesto_tolerancia NUMERIC(5,2) NOT NULL DEFAULT 0;
--   % por encima del rubro que se perdona antes de alertar/bloquear
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS presupuesto_acumula_saldo BOOLEAN NOT NULL DEFAULT TRUE;
--   TRUE = lo no ejecutado de enero queda disponible en febrero (acumulado anual)
--   FALSE = cada mes arranca con su cupo y lo que sobró se pierde
```

> **Ojo con `ADD COLUMN IF NOT EXISTS`**: no detecta un tipo distinto. Si la columna ya
> existe con otro tipo, `ddl-auto=validate` tumba el arranque y el error queda al fondo de
> la traza. Verificar el tipo real antes de correr.

### 2.2 Cabecera: `presupuesto`

Un presupuesto por empresa y año. Se versiona por estado, no por copias.

| Columna | Tipo | Nota |
|---|---|---|
| `id` | BIGSERIAL | |
| `empresa_id` | INTEGER NOT NULL | FK `empresa` |
| `anio` | INTEGER NOT NULL | |
| `nombre` | VARCHAR(120) | "Presupuesto 2027", "2027 revisado" |
| `estado` | VARCHAR(15) NOT NULL | `BORRADOR` / `APROBADO` / `CERRADO` |
| `aprobado_por` / `aprobado_at` | BIGINT / TIMESTAMP | quién lo firmó |
| `created_at` / `updated_at` | TIMESTAMP | |

`UNIQUE (empresa_id, anio)` sobre los que no están `CERRADO`: **un solo presupuesto vigente
por año**. Solo el `APROBADO` controla; el `BORRADOR` se edita sin afectar nada.

### 2.3 Detalle: `presupuesto_linea`

**Una fila por cuenta y mes.** Es más filas que 12 columnas, pero permite reportar,
reajustar un mes suelto y auditar sin reescribir la fila entera.

| Columna | Tipo | Nota |
|---|---|---|
| `id` | BIGSERIAL | |
| `presupuesto_id` | BIGINT NOT NULL | FK, `ON DELETE CASCADE` |
| `cuenta_id` | BIGINT NOT NULL | FK `plan_cuenta`. **Solo cuentas `auxiliar = true`**: presupuestar un mayor y además sus hijas haría doble conteo. |
| `mes` | SMALLINT NOT NULL | 1..12 (`CHECK mes BETWEEN 1 AND 12`) |
| `monto` | NUMERIC(18,2) NOT NULL DEFAULT 0 | siempre positivo, en la naturaleza de la cuenta |
| `centro_costo_id` | BIGINT NULL | dimensión opcional, ver §7 |
| `nota` | VARCHAR(300) | por qué ese número |

`UNIQUE (presupuesto_id, cuenta_id, mes, COALESCE(centro_costo_id, 0))`.

### 2.4 Traza: `presupuesto_movimiento`

Un presupuesto aprobado **no se edita en silencio**. Todo cambio posterior es un traslado o
una adición, y queda registrado — es justo lo que pide un contador o una junta.

| Columna | Tipo | Nota |
|---|---|---|
| `id` | BIGSERIAL | |
| `presupuesto_id` | BIGINT NOT NULL | |
| `tipo` | VARCHAR(15) NOT NULL | `ADICION` / `REDUCCION` / `TRASLADO` |
| `cuenta_origen_id` / `cuenta_destino_id` | BIGINT | en `TRASLADO` van las dos; en adición solo destino |
| `mes` | SMALLINT NOT NULL | |
| `monto` | NUMERIC(18,2) NOT NULL | |
| `motivo` | VARCHAR(300) NOT NULL | obligatorio |
| `usuario_id` / `created_at` | BIGINT / TIMESTAMP | |

El cupo vigente de una cuenta-mes = `presupuesto_linea.monto` + suma de sus movimientos.
`presupuesto_linea` nunca se toca después de aprobado.

---

## 3. De dónde sale la ejecución

**No se registra en ningún lado: se lee del mayor.** Ese es el punto del eje PUC — no hay
que parametrizar ni mantener nada, y lo ejecutado siempre amarra con el balance.

```sql
-- Ejecutado de una cuenta en un mes (cuentas de gasto/costo, naturaleza DÉBITO)
SELECT COALESCE(SUM(ad.debito - ad.credito), 0)
FROM asiento_detalle ad
JOIN asiento_contable ac ON ac.id = ad.asiento_id
WHERE ac.empresa_id = :empresaId
  AND ad.cuenta_id  = :cuentaId
  AND ac.estado IN ('APROBADO', 'CONTABILIZADO')   -- el borrador no consume presupuesto
  AND ac.fecha BETWEEN :desde AND :hasta
```

Reglas:

- **La fecha manda**, igual que en los períodos (V171): una factura de enero registrada en
  febrero consume presupuesto de **enero**.
- El **borrador no consume**. Consume al aprobarse. Si la empresa está en modo revisión
  (`modo_contabilizacion`), el presupuesto se mueve cuando el contador aprueba, no cuando el
  cajero factura.
- La anulación (asiento reverso) **devuelve** el cupo sola, porque es un crédito sobre la
  misma cuenta y la resta lo recoge. Sin código extra.
- Para cuentas de naturaleza crédito (ingresos, si se presupuesta venta) se invierte:
  `SUM(credito - debito)`.

### Vista materializada opcional

Si el mayor crece, una vista `v_ejecucion_presupuesto (empresa_id, anio, mes, cuenta_id,
ejecutado)` refrescada por el cierre de mes evita recorrer `asiento_detalle` en cada
validación. **No arrancar con esto**: primero medir con el índice
`(empresa_id, cuenta_id, fecha)`.

---

## 4. El control: dónde se valida

Un solo punto: `PresupuestoGuard`, llamado desde el mismo sitio donde hoy se resuelve el
período contable (`PeriodoContableResolver`), justo antes de persistir el asiento.

```
asiento por aprobar
   └─ por cada línea de una cuenta presupuestada:
        cupo      = linea.monto + movimientos      (del mes, o del año si acumula saldo)
        ejecutado = mayor del período
        nuevo     = débito de esta línea
        si ejecutado + nuevo > cupo * (1 + tolerancia/100):
             INFORMATIVO → sigue, se registra el desborde
             ALERTA      → sigue, devuelve advertencia al front (toast ámbar + traza)
             BLOQUEO     → GlobalException 409 con el detalle del rubro
```

Qué debe decir el mensaje de bloqueo, porque de eso depende que no sea odiado:

> «La cuenta 5135 Servicios — Aseo tiene $1.200.000 presupuestados para marzo, lleva
> ejecutados $1.150.000 y este documento suma $300.000. Faltan $250.000. Pida una adición
> presupuestal o traslade cupo desde otra cuenta.»

**Excepciones que hay que respetar desde el día uno**, o el bloqueo vuelve el sistema
inusable:

- Los asientos de **cierre** (cancelación de resultados, V93) no validan presupuesto.
- Los asientos de **corrección/reverso** no validan (siempre liberan cupo).
- Una cuenta **sin línea presupuestal** no se controla: se reporta como "sin presupuesto",
  nunca se bloquea. Presupuestar es opt-in cuenta por cuenta.
- Un rol autorizador (reusar `empresa.rol_autoriza_retroactivo` o uno nuevo
  `rol_autoriza_presupuesto`) puede **forzar** con motivo obligatorio, y queda en la traza.

---

## 5. Comprometido: lo que ya está pedido y todavía no llegó

Sin esto el control es de mentiras: la plata se acaba cuando se firma la orden de compra,
no cuando llega la factura.

```
disponible = cupo − ejecutado − comprometido
comprometido = Σ órdenes de compra APROBADAS, no facturadas ni anuladas,
               por la cuenta de gasto de cada línea
```

Al facturar la orden, el comprometido baja y el ejecutado sube: no se cuenta dos veces.
Es una consulta sobre `orden_compra` + su detalle, sin tabla nueva.

---

## 6. Pantallas

| Pantalla | Qué hace |
|---|---|
| **Alta de empresa** (super admin) | Interruptor "Maneja presupuesto" + política (informativo/alerta/bloqueo), tolerancia y si acumula saldo. Botón de opción, nunca switch. |
| **Presupuesto → Definición** | Grilla cuenta × 12 meses, editable en línea. Buscador de cuenta (solo auxiliares), total por cuenta y por mes al pie. Botones: cargar el año anterior × factor, importar desde Excel, distribuir un anual entre 12 meses. Estado BORRADOR → APROBAR. |
| **Presupuesto → Ejecución** | El reporte que se mira todos los meses: cuenta, presupuestado, ejecutado, comprometido, disponible, % y semáforo. Filtros por mes/trimestre/año y centro de costo. Drill-down: clic en la cuenta → los asientos que la movieron. Export a Excel (POI, como el de facturación electrónica). |
| **Presupuesto → Movimientos** | Adiciones, reducciones y traslados con motivo. Es el historial que se lleva a la junta. |
| **Dashboard / campana** | Alerta cuando una cuenta pasa del 90 % del cupo del mes, por el mismo canal de notificaciones de cartera y lotes. |

Convenciones a respetar: ficha dentro de **una sola `.card`** con `card-head` y secciones
divididas; preguntas sí/no con botones de opción, **nunca `p-inputSwitch`** (usar
`p-toggleswitch`); overlays con `appendTo="body"` se corren porque el scroll vive en
`.aura-content`; y el menú filtra por `normalize(label)` contra un Set global, así que los
labels de los submódulos nuevos deben ser únicos y hay que correr el SQL del menú.

---

## 7. La dimensión centro de costo

`presupuesto_linea.centro_costo_id` nace **nullable** y el MVP la deja siempre en `NULL`:
se presupuesta la cuenta para toda la empresa. Cuando se quiera el control por área, se
llena la columna y el guard agrega `AND ad.centro_costo_id = :ccId` a la consulta de
ejecución. Nada más cambia. Así se entrega valor en la fase 1 sin cerrarse la puerta a la
matriz cuenta × centro de costo, que es lo que hacen los grandes.

Lo mismo aplica a `proyecto_id` / `frente_id`, que ya viajan en `asiento_detalle`.

---

## 8. Fases

| Fase | Alcance | Deja servible |
|---|---|---|
| **P0** | Migración (§2) + `maneja_presupuesto` en el alta de empresa + entidades/DTOs | Nada visible; cimiento. |
| **P1** | CRUD de presupuesto y líneas + pantalla de definición + aprobar | Se puede cargar el presupuesto del año. |
| **P2** | Consulta de ejecución (§3) + pantalla y export de ejecución presupuestal | **Aquí ya sirve:** el contador ve real vs presupuesto sin que nada se bloquee. |
| **P3** | `PresupuestoGuard` con las tres políticas y sus excepciones (§4) | El presupuesto empieza a controlar. |
| **P4** | Adiciones, reducciones y traslados con traza (§2.4) | El presupuesto se puede gobernar en el año. |
| **P5** | Comprometido desde órdenes de compra (§5) | Control real, no contable-tardío. |
| **P6** | Alertas al 90 % en la campana + tarjeta en el dashboard | Nadie tiene que acordarse de entrar a mirar. |
| **P7** | Comparativo multi-año, presupuesto por centro de costo (§7), vista materializada si hace falta | Escala. |

**Entregar P0–P2 primero y vivir con eso un mes.** Encender el bloqueo (P3) sobre un
presupuesto mal cargado es la forma más rápida de que el cliente odie el módulo: primero se
mira, después se controla.

---

## 9. Riesgos

1. **Bloquear con presupuesto incompleto.** Mitigado: cuenta sin línea = sin control, y la
   política arranca en `ALERTA`.
2. **Doble conteo por presupuestar cuentas mayores.** Mitigado: solo cuentas `auxiliar`.
3. **Ejecución que no amarra con el balance.** Mitigado: se lee del mismo `asiento_detalle`
   y con el mismo criterio de fecha que los períodos; no hay una segunda fuente de verdad.
4. **Empresa que no lleva contabilidad juiciosa.** Si no aprueban asientos, la ejecución se
   ve en cero. El módulo exige la contabilidad al día — hay que decirlo al vender.
5. **Año sin presupuesto aprobado.** El guard no hace nada: sin presupuesto vigente, todo
   pasa. Nunca frenar la operación por una configuración faltante.

---

## 10. Decisiones ya tomadas

- **Eje: cuenta del PUC.** No rubros propios (evita una capa de mapeo que se desactualiza) y
  no centro de costo solo (dice cuánto queda, no en qué se gastó).
- **Ejecución derivada del mayor**, nunca un contador propio que se desincronice.
- **Solo el presupuesto `APROBADO` controla.**
- **`maneja_presupuesto` se decide al crear la empresa**, y se puede encender después: no es
  irreversible.

# Plan: perfil de empresa (líneas de uso)

**Fecha:** 2026-10-06 · **Estado:** P1–P7 implementadas (back + front, local, sin commit); falta probar contra la base

## Problema

Aura asume que toda empresa es un punto de venta:

- El login y la raíz llevan siempre a `/dashboard`, que es 100 % POS (ventas, stock, vencimientos, más vendidos).
  Una empresa de solo contabilidad o solo nómina lo ve en ceros.
- Si se le quita el submódulo Dashboard, `permiso.guard.ts` muestra "Sin permiso — Dashboard" en cada login antes de redirigir.
- Al crear la empresa (`EmpresaPlataformaServiceImpl.crear`) no se carga el PUC ni la configuración contable:
  depende del botón "Cargar PUC" (`POST /contabilidad/plan-cuentas/seed`) y nadie se lo indica al usuario.
- En el panel, el árbol de módulos arranca con **todo** marcado.

## Idea

La empresa **declara** qué líneas usa (no se deduce de los submódulos, igual que el origen de fondos).
Eso vive en `empresa.configuracion` (jsonb que ya existe y hoy nadie usa).

Las líneas **no dan permisos**: los permisos siguen saliendo de submódulos + perfiles (PLAN_PERMISOS).
Las líneas deciden cuatro cosas:

1. **Plantilla de módulos** al crear la empresa (qué viene marcado en el árbol).
2. **Arranque**: qué se siembra (PUC, configuración contable, parámetros de nómina…).
3. **Inicio**: qué tablero ve al entrar.
4. **Lista de puesta en marcha**: qué le falta configurar para operar.

## Líneas

| Código | Para quién | Submódulos de plantilla | Arranque | Tablero |
|---|---|---|---|---|
| `POS` | Tienda, restaurante, mostrador | Principal, Catálogo, Precios, Inventario, Caja, Ventas, Cartera | PUC + config contable + formas de pago | Tablero POS (el actual) |
| `COMERCIAL` | ERP sin mostrador: factura, compra, inventario | Catálogo, Inventario, Compras, Ventas (FV, cotización, pedido), Cartera, Tesorería | PUC + config contable | Tablero comercial |
| `CONTABILIDAD` | El contador opera directo | Contabilidad, Tesorería, Cartera, Terceros, Reportes contables | PUC + config contable + impuestos + exógena | Tablero contable |
| `NOMINA` | Solo nómina / RRHH | Recursos Humanos, Terceros | Conceptos y parámetros de nómina del año | Tablero de nómina |

Se combinan: `["CONTABILIDAD","NOMINA"]` es una firma que lleva contabilidad y nómina.
La plantilla es solo un punto de partida: el administrador de plataforma puede marcar o desmarcar en el árbol.

## Forma del JSON

```json
{
  "version": 1,
  "lineas": ["CONTABILIDAD", "NOMINA"],
  "inicio": null,
  "arranque": {
    "CONTABLE": { "resultado": "OK", "ejecutado": "2026-10-06T10:00:00-05:00", "detalle": "Plan de cuentas cargado (…)" }
  }
}
```

- `inicio`: tablero preferido; `null` = el de la primera línea en orden POS → COMERCIAL → CONTABILIDAD → NOMINA.
- `arranque`: va por **paso**, no por línea (todas las líneas piden el paso CONTABLE; nómina usa los conceptos
  globales y no siembra nada propio). Lo escribe solo el back; permite reintentar.

## Reglas para que sea robusto

1. **Tipado, no `Object`.** `EmpresaConfiguracion` (record/DTO con `version`) y un servicio único
   `EmpresaConfiguracionService` que lee, valida y escribe. Nadie toca el jsonb a mano.
2. **Sin configuración = comportamiento de hoy.** Si `configuracion` es null o no trae `lineas`,
   se lee como `["POS"]`. Las empresas actuales no cambian en nada → no hace falta migrar datos.
3. **Versión.** `version` permite cambiar la forma después sin romper empresas viejas (se lee con defaults).
4. **Arranque idempotente.** Cada siembra ya es idempotente (`seedPUC`, `seedDefaults`); el arranque solo
   las orquesta por línea y registra el resultado. Si falla, la empresa se crea igual y queda
   `resultado: ERROR` con botón "Reintentar" en el panel. Agregar una línea después dispara su arranque.
5. **Validación cruzada.** Una línea sin sus submódulos mínimos (ej. `CONTABILIDAD` sin Contabilidad)
   se rechaza al guardar, con mensaje claro.
6. **El back manda.** El login y `/empresa/configuracion` devuelven las líneas; el front solo las lee.
   El inicio también lo decide el back (`inicio` resuelto), así no se repite la regla en dos lados.
7. **Inicio sin error.** Si la empresa no tiene el tablero resuelto o el perfil no lo puede ver,
   se va a la primera pantalla permitida **sin** mostrar "Sin permiso".
8. **Bitácora.** Cambiar líneas queda en la bitácora de plataforma (quién, cuándo, antes/después).

## Tableros

Un solo componente `/inicio` que pinta el tablero de la línea resuelta; si hay varias líneas, pestañas.
El tablero POS actual se mueve tal cual.

**Contable** (endpoint nuevo `GET /api/tablero/contable`):
- Disponible: caja y bancos (saldo de 11xx)
- CxC y CxP: total y vencido
- Resultado del mes y del año (ingresos − costos − gastos)
- Período: abierto/cerrado, meses abiertos
- Pendientes: comprobantes en borrador, cuentas bancarias sin conciliar el mes anterior,
  IVA/retención del período por declarar

**Nómina** (`GET /api/tablero/nomina`): empleados activos, próximo período y su estado,
novedades sin liquidar, PILA del mes, contratos por vencer.

**Comercial**: ventas y compras del mes (FV, no POS), cotizaciones/pedidos abiertos, cartera vencida, stock bajo.

## Lista de puesta en marcha

Tarjeta en el tablero hasta que esté completa (se calcula, no se guarda):

- **CONTABILIDAD:** PUC cargado · configuración contable sin cuentas vacías · cuentas bancarias con cuenta contable ·
  saldos iniciales importados · período abierto
- **NOMINA:** parámetros del año (SMLV, auxilio) · empleados con contrato · EPS/AFP/caja asignadas · aportante PILA
- **COMERCIAL / POS:** resolución de facturación · bodega · productos · formas de pago

## Fases

| Fase | Qué | Repo |
|---|---|---|
| P1 | `EmpresaConfiguracion` + servicio + defaults + endpoint GET/PUT (plataforma) + líneas en el login | back |
| P2 | Arranque por línea al crear empresa y al agregar línea; reintento; bitácora | back |
| P3 | Panel: elegir líneas al crear/editar empresa → marca la plantilla del árbol; estado del arranque | front |
| P4 | Inicio sin error + `/inicio` que resuelve tablero (POS actual dentro) | front |
| P5 | Tablero contable (endpoint + vista) | back + front |
| P6 | Tablero de nómina | back + front |
| P7 | Tablero comercial + lista de puesta en marcha | back + front |

P1–P5 dejan lista la venta "solo contabilidad". Sin migración Flyway en P1–P4 (la columna ya existe).

## Implementado (P1–P2)

| Pieza | Dónde |
|---|---|
| Líneas, plantilla, mínimos | `services/empresa/LineaUso.java` |
| Pasos de arranque | `services/empresa/PasoArranque.java` |
| Forma del JSON (conserva claves ajenas) | `services/empresa/ConfiguracionEmpresaJson.java` |
| Leer, cambiar, validar, plantilla | `services/empresa/ConfiguracionEmpresaService.java` |
| Arranque después del commit, en transacción propia | `services/empresa/ArranqueEmpresaService.java` |
| Bitácora en la empresa afectada | `BitacoraService.registrarEnEmpresa` |

**Endpoints**

- `GET  /api/platform/lineas-uso`: catálogo con los ids de submódulo de cada plantilla y de los mínimos.
- `GET  /api/platform/empresas/{id}/configuracion`
- `PUT  /api/platform/empresas/{id}/configuracion`: `{ lineas, inicio, completarModulos }`.
- `POST /api/platform/empresas/{id}/arranque`: reintenta el arranque (idempotente).
- `GET  /api/empresa/configuracion`: la de la empresa de la sesión.
- `POST /api/platform/empresas` acepta `lineas`; si llegan sin `submodulos`, activa la plantilla.
- Login y cambio de sede devuelven `lineas` e `inicio`.

**Cambio de comportamiento:** toda empresa nueva (panel o autoregistro) nace con el PUC y la configuración
contable cargados, aunque no declare líneas.

**Pruebas:** `ConfiguracionEmpresaServiceTest` (9 casos). Falta probar contra la base: crear una empresa con
`lineas: ["CONTABILIDAD"]` y revisar que quede el PUC y `arranque.CONTABLE = OK`.

## Implementado (P3–P7)

**Back**
- `GET /api/tablero/financiero`: disponible (cuentas 11 en el mayor), CxC y CxP con lo vencido.
- `GET /api/tablero/comercial`: FV emitidas y compras del mes, cotizaciones `PENDIENTE`, pedidos `CREADA/DESPACHADA`, stock bajo y el financiero.
- `GET /api/tablero/puesta-en-marcha`: chequeos por línea, calculados cada vez (`TableroInicioService`).
  - Contabilidad (todas): PUC, parametrización sin cuentas vacías, cuentas bancarias con cuenta contable; solo línea
    CONTABILIDAD: saldos iniciales (asiento `APERTURA`).
  - Nómina: `nomina_config`, contratos activos, afiliaciones EPS/AFP/CCF vigentes, aportante PILA.
  - POS/Comercial: productos; rango de Factus si tiene FE.
- `/api/tablero` exige `principal.dashboard` (PermisoRutas).

**Front** (aura-frontend)
- Panel → empresa: tarjetas "¿Para qué usará Aura?"; al crear marcan la plantilla en el árbol; al editar, tablero de
  inicio y estado del arranque con "Cargar ahora".
- `/dashboard` ahora es `features/inicio/InicioComponent`: puesta en marcha + pestañas por línea (POS = dashboard de
  siempre, Comercial = `TableroComercialComponent`, Contabilidad = resumen financiero + Centro de Contabilidad,
  Nómina = Centro de RRHH). Una pestaña solo sale si el perfil ve ese módulo; la última elegida se recuerda.
- `permiso.guard`: sin permiso al inicio redirige sin el aviso "Sin permiso".
- Manual `/ayuda`: tema "Inicio (Dashboard)" actualizado.

## Checklist de prueba

1. Panel → crear empresa con solo **Contabilidad**: el árbol marca solo contabilidad/tesorería/cartera/base.
2. Esa empresa: `GET /api/platform/empresas/{id}/configuracion` → `CONTABLE = OK`; tiene plan de cuentas.
3. Entrar con su admin: Inicio sin POS; resumen financiero + centro contable; puesta en marcha pide bancos y saldos.
4. Editar la empresa y agregar **Nómina**: se activan los módulos de RRHH; aparece la pestaña Nómina.
5. Empresa vieja (sin líneas): entra igual que antes al dashboard POS.
6. Usuario sin permiso de Inicio: entra a su primera pantalla sin aviso de error.
7. Crear empresa con una línea y desmarcar "Plan de cuentas" en el árbol: debe rechazar con mensaje claro.

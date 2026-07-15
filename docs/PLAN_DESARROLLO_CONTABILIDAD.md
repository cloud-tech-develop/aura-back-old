# Plan de desarrollo — Contabilidad completa Aura Nube

> Documento de EJECUCIÓN. El diseño técnico detallado vive en
> `docs/DISENO_CONTABILIDAD_AVANZADA.md`; este archivo es la lista de trabajo ordenada,
> con tareas y criterios de aceptación por etapa. Marcar `[x]` al completar.
>
> Modelo de producto (decidido): **opinado por defecto, parametrizable acotado** —
> el sistema contabiliza bien sin configurar nada; el contador remapea DESTINOS
> (cuentas), nunca la LÓGICA del asiento. Con E0–E11 se cubren los 10 pasos del ciclo
> contable académico, los ciclos de Romney & Steinbart y el conjunto de EEFF de NIIF
> para pymes (Sección 3).

**Convenciones de todas las etapas (definition of done):**
- Compila + arranca. Asientos SIEMPRE cuadrados (`AsientoBalanceValidator`).
- Todo generador nuevo: `tipoOrigen`+`origenId`, idempotente, evento AFTER_COMMIT
  (fallo → ErrorLog, nunca tumba la operación), reversa por contraasiento.
- Conceptos nuevos se agregan a `ConceptoContable` con código default + seed idempotente.
- Migraciones Flyway numeradas desde **V85** (última actual: V84).
- Front en `D:\Proyectos Camilo\aura-post\aura-frontend`: forms PLANOS estándar;
  el label del ítem de menú debe normalizar al `submoduloCodigo` de BD (gotcha conocido).
- Validado en runtime con caso real antes de marcar la etapa.

---

## E0 — Validación runtime del ciclo completo (prioridad #1) — tamaño M

Cerrar la Fase A: probar punta a punta lo YA construido. Base: `docs/VALIDACION_CONTABLE_FASE_A.md`.

- [ ] Venta contado / crédito / mixta multi-pago → asiento VE cuadra, split Caja-Banco-Clientes, par COGS
- [ ] Compra contado / crédito, con y sin retenciones (2365/2367/2368)
- [ ] Devolución con y sin reintegro de inventario / afectación de cartera
- [ ] Abono a cartera (RC) y a proveedores (EG), efectivo y banco
- [ ] Gasto con IVA y retenciones; merma
- [ ] Nómina: aprobar (NO), pagar, pago de prestación; verificar 5105xx/23xx/25xx
- [ ] Obligación: desembolso a cuenta bancaria real + pago de cuota (capital/interés)
- [ ] Movimiento de caja y tesorería
- [ ] Saldos iniciales (`AperturaContableServiceImpl`) → balance de apertura cuadrado
- [ ] Cierre de período → asiento CE a 3605; reabrir no duplica; reportes excluyen CIERRE
- [ ] Reversas: anular venta/compra/gasto/merma/nómina/devolución → contraasiento RV, idempotente
- [ ] Matriz caso→asiento esperado→asiento real documentada en el doc de validación

**Aceptación:** los 12 flujos generan asiento correcto sin intervención; los errores
encontrados quedan corregidos o registrados como issue.

## E1 — Guardarraíles de parametrización + UI de configuración contable — tamaño S/M

La puerta de entrada del contador. NO exponer la UI sin los guardarraíles.

Backend:
- [ ] `ConfiguracionContableService.actualizar`: validar que la cuenta exista, esté activa,
      sea de movimiento (último nivel) y su código empiece por la clase permitida del
      concepto (mapa concepto→prefijos: INGRESOS_VENTAS→"4", CLIENTES→"13", IVA→"24",
      NOMINA_*→"51/23/25", etc.)
- [ ] Misma validación en cuenta bancaria→cuenta contable (solo 11xx) y en futuros mapeos
- [ ] Tabla `contabilidad_config_log` (V85): empresa, concepto, cuenta anterior, cuenta nueva,
      usuario, timestamp — se escribe en cada cambio de `CuentaConfig`
- [ ] Endpoint GET del log para auditoría

Frontend:
- [ ] Pantalla "Configuración contable" (módulo Contabilidad): tabla concepto→cuenta con
      dropdown de cuentas filtrado por clase permitida + descripción del concepto
- [ ] Solo visible para rol admin/contador

**Aceptación:** mapear INGRESOS_VENTAS a una cuenta clase 1 es rechazado con mensaje claro;
todo cambio queda en el log; un contador puede remapear sin tocar BD.

## E2 — C1 Tesorería completa (formas de pago, compra contable, sobregiro) — tamaño M

- [ ] V86 `forma_pago_contable` (empresaId, codigo, nombre, cuentaContableId,
      requiereCuentaBancaria, activo) + entity/repo/service/controller
      `/api/contabilidad/formas-pago` + seed (EFECTIVO→1105, TRANSFERENCIA/TARJETA→1110)
- [ ] `resolverCuentaPago` prioridad: cuentaBancaria.cuentaContableId → formaPago(codigo)
      → fallback CAJA/BANCOS (guardarraíl clase 11)
- [ ] `CompraEntity` += `centroCostoId` + `cuentaContableId`; débito de compra =
      cuentaContableId ?? INVENTARIO; centro de costo propagado a las líneas
- [ ] Sobregiro: `CuentaBancariaEntity` += permiteSobregiro + cupoSobregiro; validación de
      saldo respeta cupo; reclasificación 1110→21xx en cierre de período + reversa al abrir
- [ ] Front: pantalla Formas de pago; form de compra += centro de costo y cuenta destino;
      form cuenta bancaria += sobregiro

**Aceptación:** pago con Nequi mapeado a 111005 va a esa cuenta; compra de papelería
debita 5195 con centro de costo; banco con sobregiro queda en 21xx al cerrar.

## E3 — C4 Estados de comprobante + modo revisión + diferencias de caja — tamaño S/M

- [ ] Config empresa `modoContabilizacion = AUTOMATICO | REVISION` (default AUTOMATICO)
- [ ] En REVISION los asientos automáticos nacen `BORRADOR`; endpoints
      `POST /asientos/{id}/contabilizar` + masivo por rango de fechas/origen
- [ ] BORRADOR excluido de libro mayor/balances; incluido en reporte "pendientes"
- [ ] Reporte "comprobantes descuadrados" (red de seguridad, debería estar vacío)
- [ ] Tabla `contabilidad_posting_log` (asientoId, origen, estado, error, usuario, ts)
- [ ] Cierre de caja: faltante → DB GASTO_DIFERENCIA_CAJA(519530)·CR CAJA; sobrante →
      DB CAJA·CR INGRESO_SOBRANTE_CAJA(4295); conceptos nuevos + evento en cierre de caja
- [ ] Front: bandeja "Comprobantes pendientes" con aprobar individual/masivo; toggle de
      modo en configuración

**Aceptación:** empresa en REVISION vende → asiento BORRADOR → contador aprueba y solo
entonces suma al mayor; cierre de caja con diferencia genera su asiento; período cerrado
bloquea contabilizar borradores.

## E4 — C2 Categorías contables de producto — tamaño M/L

- [ ] V87 `categoria_contable_producto` (nombre, tipo BIEN|SERVICIO|INSUMO|ACTIVO_FIJO,
      cuentas ingreso/inventario/costo/devolución, impuestoId) + producto +=
      categoriaContableId + 3 overrides de cuenta
- [ ] Seed categoría "General" (4135/1435/6135) por empresa; productos sin categoría caen ahí
- [ ] `ResolucionCuentaProductoService`: producto → categoría → `resolverCuenta` (concepto)
- [ ] Motor: `generarDesdeVenta` agrupa ingresos por cuenta resuelta (Map cuenta→subtotal)
      y COGS por cuenta costo/inventario; SERVICIO sin par COGS
- [ ] `generarDesdeCompra`: débito por categoría (1435/1455/gasto/15xx)
- [ ] `generarDesdeDevolucion`: proporcional por línea con cuenta de devolución
- [ ] Front: CRUD categorías contables + dropdown en form producto (default General)

**Aceptación:** venta mixta (2 categorías + 1 servicio) → 3 créditos de ingreso distintos,
pares COGS solo de bienes, cuadrado; empresa sin configurar contabiliza idéntico a hoy.

## E5 — C3 Impuestos parametrizables — tamaño M

- [ ] V88 `impuesto` (tipo IVA|INC|EXCLUIDO|EXENTO, %, cuentaGeneradoId, cuentaDescontableId,
      vigencias) + producto.impuestoId + seed (IVA 19/5, INC 8, Excluido, Exento)
- [ ] Seed PUC: subcuentas **240801 IVA generado / 240802 IVA descontable**; conceptos
      IVA_GENERADO/IVA_DESCONTABLE cambian default; UPDATE de cuenta_config para
      empresas existentes (migración de datos)
- [ ] Migración: porcentajeIva del producto → impuestoId equivalente por empresa
- [ ] Motor: asiento agrupa impuestos por cuenta del impuesto (venta y compra)
- [ ] `TarifaRetencionEntity` += cuentaContableId (fallback: conceptos 2365/2367/2368)
- [ ] Front: CRUD impuestos + dropdown en producto (reemplaza % suelto)

**Aceptación:** venta con producto IVA 19 + producto INC 8 → dos líneas de impuesto a
cuentas distintas; reporte IVA generado vs descontable ya no se mezcla en 2408.

## E6 — C8 Ajustes de devengo (anticipos, diferidos, causaciones, deterioro) — tamaño L

- [ ] **Anticipo de cliente**: recibo marcado "anticipo" → DB Caja/Banco·CR 2805
      (concepto ANTICIPOS_CLIENTES); pantalla de cruce anticipo→factura
      (DB 2805·CR 1305); saldo de anticipos por tercero
- [ ] **Anticipo a proveedor**: espejo (1330, ANTICIPOS_PROVEEDORES) en compras
- [ ] **Gasto diferido**: GastoEntity += esDiferido + mesesDiferido → DB 1705 al pagar;
      job mensual amortiza DB gasto·CR 1705 (patrón del job de depreciación)
- [ ] V89 `causacion_programada` (líneas débito/crédito, periodicidad, día, activa) +
      job mensual que genera asiento en BORRADOR (usa E3)
- [ ] **Deterioro de cartera**: parametrización % por tramo de edad; job/pantalla propone
      asiento DB 5199·CR 1399 en BORRADOR — el contador siempre aprueba
- [ ] Obsolescencia de inventario desde reconteo: DB gasto·CR 1499 (provisión)
- [ ] Front: flag anticipo en recibos/pagos, pantalla cruces, CRUD causaciones,
      pantalla deterioro por edades

**Aceptación:** anticipo recibido no infla ingresos (queda en 2805) y al facturar se
cruza; arriendo pagado anticipado se amortiza mes a mes; propuesta de deterioro
respeta edades y nace en borrador.

## E7 — C5 Dimensiones proyecto/frente — tamaño M

- [x] V92: `asiento_detalle` += proyectoId + frenteId
- [x] Nómina: repartir gasto 5105xx por proyecto/frente según horas de asistencia
      (datos ya existen en novedades PROYECTO_FRENTE)
- [x] Compras/gastos: proyectoId opcional en form; ventas: centro de costo desde caja/sucursal
- [x] Reportes: filtros opcionales centroCosto/proyecto/frente en estado de resultados y
      libro mayor (`WHERE :x IS NULL OR ...` en `AsientoContableQueryRepository`;
      expuestos como query params en `/estado-resultados` y `/libro-mayor`)
- [ ] Front: filtros de dimensión en reportes

**Aceptación:** estado de resultados filtrado por obra muestra la rentabilidad del
proyecto incluyendo su nómina real por horas.

## E8 — C9 Cierre anual fiscal — tamaño M  ⚠️ listo ANTES del primer diciembre de un cliente

- [x] Conceptos: GASTO_IMPUESTO_RENTA(5405), IMPUESTO_RENTA_POR_PAGAR(2404),
      RESERVA_LEGAL(330505), DIVIDENDOS_POR_PAGAR(2360) + seed cuentas
      (V93 + seedPUC: 54/5405, 2404, 2360, 33/3305/330505)
- [x] Wizard "cierre de ejercicio": paso 1 provisión renta (sistema sugiere
      utilidad×tarifa, el contador DIGITA el valor — renta fiscal ≠ contable) →
      DB 5405·CR 2404; paso 2 cierre a 3605 (ya existe)
      (GET/POST `/api/contabilidad/cierre-anual/provision-renta`)
- [x] Apertura de año: traslado 3605→3705 (POST `/cierre-anual/traslado`;
      utilidad DB 3605·CR 3705, pérdida al revés)
- [x] Pantalla "distribución de utilidades" (post-asamblea): reserva legal 10% (tope 50%
      del capital), dividendos DB 3705·CR 2360, pago de dividendos
      (`/cierre-anual/distribucion` + `/cierre-anual/dividendos/pagos`;
      generadores CIERRE_ANUAL/DISTRIBUCION_UTILIDAD/DIVIDENDO_PAGO en el registry,
      siempre CONTABILIZADOS)
- [x] Front: wizard cierre de ejercicio + pantalla distribución con pagos
      (`aura-frontend/features/contabilidad/cierre-anual`, ruta /contabilidad/cierre-anual)

**Aceptación:** cierre de diciembre deja renta provisionada, 3605 neto; en enero 3605=0 y
3705 acumula; distribución genera reserva y dividendos por pagar correctamente.

## E9 — C6 Conciliación bancaria — tamaño M/L

- [x] V94 `extracto_bancario` + `extracto_linea` (ver diseño §8; el diseño decía V91,
      esa numeración ya la tomó devengo) + cuentas 530515/530595/421005 para empresas
      existentes y en seedPUC
- [x] Import CSV genérico (fecha, descripción, valor; separadores `;`/`,`/tab, fechas
      dd/MM/yyyy, montos $1.234.567,89) y/o líneas JSON ya estructuradas
- [x] Matching sugerido (valor exacto, fecha ±3 días) contra movimientos de la cuenta
      contable del banco; confirmación manual línea a línea (conciliar/desconciliar)
- [x] Ajustes desde la pantalla: GASTOS_BANCARIOS(530515), GMF(530595), INTERES
      (cargo→5305 / abono→421005) — generador AJUSTE_BANCARIO (prefijo AB), siempre
      CONTABILIZADO, en el registry
- [x] Cierre del extracto exige: cero pendientes, ajustes con asiento y saldo inicial
      + líneas conciliadas/ajustadas = saldo final (endpoint /resumen expone además
      partidas en tránsito del libro)
- [x] Guardarraíl inverso: `cerrarPeriodo` bloquea si el mes tiene extractos
      bancarios ABIERTOS (concíliense o elimínense — DELETE /extractos/{id}, solo
      ABIERTO y sin líneas de ajuste contabilizadas)
- [x] Front: pantalla conciliación dos columnas con import CSV, sugerencias,
      ajustes y cierre (`features/contabilidad/conciliacion`, /contabilidad/conciliacion)

API: `/api/contabilidad/conciliacion/extractos` (+ `/lineas`, `/sugerencias`,
`/movimientos-libro`, `/lineas/{id}/conciliar|desconciliar|ajuste`, `/resumen`,
`/cerrar`). Servicio en `contabilidad/infrastructure/conciliacion/`.

**Aceptación:** extracto real de un banco se concilia completo; comisiones y 4x1000
quedan contabilizados desde la misma pantalla.

## E10 — C10 Estados financieros NIIF completos — tamaño M

- [x] Estado de cambios en el patrimonio (clase 3 por cuenta: inicial/aumentos/
      disminuciones/final) desde `asiento_detalle` — incluye asientos de CIERRE
      (son los que llevan el resultado a 3605)
- [x] Estado de flujos de efectivo método indirecto (utilidad ± depreciación 5160 y
      deterioro 5199 ± Δ capital de trabajo 13/14/17·22–28 + inversión 12/15/16/18/19
      + financiación 21/29/clase 3, entre dos cortes; excluye asientos CIERRE y las
      contra-cuentas 1399/1499/1592 para no duplicar los add-backs) — devuelve
      `cuadra` validando flujo neto = Δ saldo clase 11
- [ ] (Opcional) Notas: plantilla exportable con cifras inyectadas
- [x] Front: dos reportes en tabs (`features/contabilidad/eeff`, /contabilidad/eeff)

API: GET `/api/contabilidad/eeff/cambios-patrimonio?desde&hasta` y
`/api/contabilidad/eeff/flujo-efectivo?desde&hasta`. Servicio en
`contabilidad/infrastructure/reportes/EstadosFinancierosService`.

**Aceptación:** con E0–E8 hechos, los 5 componentes de NIIF pymes Sección 3 salen del
sistema y cruzan entre sí (EFE cuadra con Δ disponible del balance).

## E11 — C7 Información exógena DIAN — tamaño L (venderla como cierre de año)

- [x] V95 tablas exógena (formato, concepto, mapeo_cuenta por prefijo/rango, lote
      versionado, línea, error) — el diseño decía V92, esa numeración ya la tomaron
      las dimensiones
- [x] Seed formatos 1001/1005/1006/1007/1008/1009/2276 con conceptos + mapeos default
      sobre el PUC seed (migración para empresas existentes + `seedDefaults` en seedPUC);
      el mapeo más específico gana (5105→5001 le gana a 51→5016)
- [x] Validador previo: terceros incompletos (documento/DV/municipio/dirección),
      cuentas con movimiento sin mapeo (de las clases del formato), comprobantes en
      borrador, períodos abiertos y movimientos sin tercero en cuentas mapeadas
- [x] Generación de lote: agrupa asiento_detalle por tercero×concepto (MOVIMIENTO_DB/CR
      del año, SALDO_DB/CR a dic 31, excluyendo asientos CIERRE); cuantías menores por
      umbral configurable (default $100.000) al NIT 222222222; export Excel (POI)
      columnas prevalidador DIAN
- [x] Aprobación bloquea el lote y exige cero hallazgos; regenerar un BORRADOR lo
      reutiliza, regenerar sobre APROBADO crea la versión siguiente
- [x] Front: wizard exógena (validar → generar → revisar → aprobar → Excel) +
      tab de mapeos (`features/contabilidad/exogena`, /contabilidad/exogena)

API: `/api/contabilidad/exogena` (formatos, mapeos CRUD + seed, validar, lotes
generar/listar/lineas/errores/aprobar/export). Servicio y calculadora pura en
`contabilidad/infrastructure/exogena/`.

**Aceptación:** formatos 1001/1007/1008/1009 de una empresa real pasan el prevalidador
DIAN sin ediciones manuales del Excel.

---

## Resumen y dependencias

| # | Etapa | Fase diseño | Tamaño | Depende de |
|---|---|---|---|---|
| E0 | Validación runtime | Fase A | M | — |
| E1 | Guardarraíles + UI config | nuevo | S/M | E0 |
| E2 | Tesorería completa | C1 | M | E1 |
| E3 | Estados + modo revisión + dif. caja | C4 | S/M | E0 |
| E4 | Categorías producto | C2 | M/L | E2 |
| E5 | Impuestos | C3 | M | E4 |
| E6 | Devengo | C8 | L | E3 (borradores), E2 |
| E7 | Dimensiones proyecto/frente | C5 | M | E2 |
| E8 | Cierre anual fiscal | C9 | M | E0 (⚠️ antes de diciembre) |
| E9 | Conciliación bancaria | C6 | M/L | E2 |
| E10 | EEFF NIIF | C10 | M | E6, E8 |
| E11 | Exógena | C7 | L | E5, E6, E3 |

Fuera de alcance declarado: impuesto diferido, multimoneda, manufactura, consolidación
(ver diseño §14).

**Hitos comerciales:** tras E3 → "el contador revisa antes de contabilizar" (demo a
contadores). Tras E5 → paridad funcional POS-contable con Siigo en operación diaria.
Tras E8 → primer cierre de año completo dentro del sistema. Tras E11 → exógena sin Excel.

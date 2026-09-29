# Auditoría ERP AURA · 02 — Roadmap (consolidado)

> Fases agrupadas por **dependencia**, no por tamaño. Cada fase global integra las fases de bloque
> (A0–A6, B0–B7, C0–C9) definidas en `parts/`. Ver brechas en [01-gap-analysis.md](01-gap-analysis.md)
> y tareas en [03-backlog.md](03-backlog.md).

## Reglas de despliegue (aplican a todas las fases)

1. **Diagnóstico antes de corregir**: cada fase arranca con consultas de solo lectura en prod para medir el daño existente.
2. **Migraciones**: numeración desde **V184**; cada una con espejo **idempotente** en el proyecto Laravel; primero local
   (`aura-pos`), nunca directo sobre `aura-db`.
3. **Flags por empresa** para cambios de comportamiento (costo promedio, cancelación de resultados, política de negativos).
4. **Reproceso contable** (C0) disponible antes de cualquier fase que cambie cómo se contabiliza.
5. **Pruebas**: cada P0 entra con prueba de regresión; las de concurrencia son obligatorias en F1.
6. **Manual `/ayuda`** y SQL de menú actualizados en la misma entrega cuando cambian pantallas o mensajes.

## Mapa de fases

```
F0 Estabilización ──► F1 Contención P0 ──► F2 Integridad transaccional ──► F3 Costeo y kardex valorizado
                                   │                     │                              │
                                   │                     └──────► F4 Cadena documental y notas ◄┘
                                   │                                        │
                                   └──► F5 Autorización fina y auditoría    │
                                                     │                      ▼
                                                     └────► F6 Motor contable único y parametrización
                                                                            │
                               ┌────────────────────────┬───────────────────┼────────────────────┐
                               ▼                        ▼                   ▼                    ▼
                     F7 Fiscal electrónico   F8 Cierres, reportes   F9 Maestros y       F10 Productividad,
                     (requiere norma vigente)   contables, bancos,     políticas            reportes operativos
                                                activos                comerciales           y BI
                                                                            │
                                                                            ▼
                                                                   F11 Especializaciones (P3)
```

---

## F0 — Estabilización (prerrequisito)

**Objetivo:** partir de un esquema y un código conocidos.
- Correr/verificar en local las migraciones pendientes (V156–V183) y sus SQL de menú; decidir cuáles suben a prod.
- Commit de la rama actual (`fix/camilo-caja-pagos`, V183 bodega principal).
- Inventario de índices únicos reales en prod para tablas pre-V14 (`venta`, consecutivos).
- Diagnósticos SQL de solo lectura listados en 01 §8.
**Salida:** lista de migraciones aplicadas por ambiente; informe de datos afectados por cada P0.

## F1 — Contención P0 (hotfixes, sin cambio de modelo)

**Objetivo:** cerrar las vías activas de daño. Tareas independientes entre sí salvo lo indicado.

| Frente | Tareas | Gaps |
|---|---|---|
| Seguridad (A0) | TASK-A-001 roles lista blanca · A-002 sucursal por empresa · A-003 red de autorización por URL + rotación JWT · A-004 unidades globales | X-01..X-04 |
| Ventas/Compras (B0) | TASK-B-001 no anular con CUFE · B-003 anulación de venta completa (dep. B-001) · B-002 anulación de compra completa · B-005 reembolso con descuentos · B-012 gasto coherente · B-017 IDOR | X-11..X-14, X-18 |
| FE (B0) | TASK-B-007 payload fiel · B-008 paso 1: quitar `@Retry`, reservar estado antes de enviar, `reference_code` con sucursal | X-17 |
| Contabilidad (C0 parte 1) | TASK-C-001 guard de período en documentos operativos · C-002 reproceso atómico de compra editada | X-09 |

**Depende de:** F0. **Salida:** matriz curl por rol en verde; tests de regresión de anulación/devolución/FE;
scripts de corrección de datos aprobados y ejecutados.

## F2 — Integridad transaccional

**Objetivo:** que ningún número, saldo ni asiento pueda duplicarse o perderse.
- TASK-A-007 / B-009 numerador transaccional único (ventas, documentos, comprobantes contables; separar series OB/CE).
- TASK-A-005 / B-010 servicio único de movimiento de stock con bloqueo; TASK-A-006 stock solo por documentos.
- TASK-C-003 idempotencia en BD `(empresa, tipo_origen, origen_id)`, reproceso y detector de documentos sin asiento.
- TASK-B-015 idempotencia de creación + factura de proveedor duplicada; TASK-B-016 locks en abonos.
- TASK-B-024 estados tipados; TASK-B-025 anulación lógica.
- TASK-A-009 reconteo (parte stock); TASK-A-012 base de pruebas de inventario y autorización.
**Depende de:** F1. **Salida:** UNIQUE activos; pruebas concurrentes verdes; 0 documentos sin asiento; `kardexVsStock` limpio.

## F3 — Costeo y kardex valorizado

**Objetivo:** costo del mayor = costo del kardex.
- TASK-A-011 kardex estructurado (documento, usuario, costo, valor, signo único).
- TASK-A-008 + TASK-C-004 costo promedio ponderado (flag por empresa) + control 1435 vs kardex; fletes al costo.
- TASK-A-017 reversos al costo original; TASK-C-005 `DevolucionGenerador` espejo de la venta.
- TASK-A-009 reconteo con costo real y asiento.
**Depende de:** F2. **Salida:** inventario valorizado = saldo 1435 en ciclo de prueba.

## F4 — Cadena documental y notas

**Objetivo:** trazabilidad cotización→pedido→factura→NC/ND→recibo y OC→recepción→factura→NC/ND→egreso, sin doble afectación.
- TASK-B-020 grafo documental con cantidades aplicadas + control de cruces.
- TASK-B-011 recepción real de OC y legalización sin doble ingreso.
- TASK-B-004 devolución inmutable (el cambio es venta nueva).
- TASK-B-006 + TASK-C-006 nota de venta unificada ligada a venta, CxC, inventario y NC/ND electrónica.
- TASK-B-021 NC por valor y ND de compra; TASK-B-022 anticipos completos; TASK-B-019 pedido con abonos/plan separe.
- TASK-B-014 edición de compra segura; TASK-B-018 origen de fondos en reembolsos.
**Depende de:** F2, F3. **Salida:** matriz de efectos documentales (baseline §5) sin ⚠; subledger CxC/CxP = mayor.

## F5 — Autorización fina y auditoría (paralela a F3/F4 tras F1)

- TASK-A-010 RBAC por acción con overrides por usuario; TASK-C-016 catálogo de acciones contables.
- TASK-A-015 scope de sucursal/bodega y cambio de sede.
- TASK-A-016 / B-023 límites de descuento y precio + precio/impuesto validados en backend.
- TASK-A-013 bitácora de auditoría de negocio.
- TASK-A-030 menú por código de permiso (elimina gotcha label↔submódulo); TASK-A-034 bloqueo de cuenta/refresh token.
**Depende de:** F1. **Salida:** rol personalizado funcional; toda acción sensible auditada.

## F6 — Motor contable único y parametrización

- TASK-C-008 estrangular `ContabilidadAutoServiceImpl` (13 flujos legacy) y unificar el generador de ventas.
- TASK-C-010 fechas contables del documento y reloj de la empresa (UTC).
- TASK-C-009 pantalla de parametrización contable (+ asistente de categorías).
- TASK-C-011 retenciones por tarifa y 350 por concepto; TASK-B-027 retenciones de compra parametrizadas.
- TASK-C-015 validador único de cuentas; TASK-C-019 formas de pago con grupo/tercero; TASK-C-020 vista previa.
**Depende de:** F2 (C0), F5 (permisos). **Salida:** legacy eliminado; el contador parametriza sin SQL.

## F7 — Fiscal electrónico (**requiere verificación de normativa DIAN vigente**)

- TASK-B-008 completo: outbox FE/NC/DS, estados y reconciliación con Factus.
- TASK-B-026 cola de pendientes de FE; TASK-B-013 bloqueo de origen con DS aceptado + nota de ajuste.
- TASK-A-014 resoluciones/prefijos por sucursal, documento y usuario.
- TASK-A-023 / B-035 impuestos múltiples por producto y `venta_impuesto` por tarifa.
- TASK-B-028 recepción XML/RADIAN; habilitación DS ante la DIAN.
**Depende de:** F4, F6. **Salida:** reconciliación Factus sin huérfanos; dos sedes con resoluciones propias.

## F8 — Cierres, reportes contables, bancos y activos

- TASK-C-007 vista común de movimientos + modalidad de cancelación de resultados (IVA 300 y BP limpios).
- TASK-C-013 saldos iniciales con cruces; TASK-C-014 balance de prueba completo; TASK-C-024 comparativos/ER por CC.
- Fases 2–5 de `PLAN_CIERRE_CONTABLE.md`.
- TASK-C-012 activos: depreciación correcta, baja y venta; TASK-C-029 informes de activos.
- TASK-C-017 conciliación unificada con partidas arrastradas; TASK-C-026 obligaciones; TASK-C-027 saldo bancario desde el mayor.
- TASK-C-028 base/tarifa en partida → TASK-C-032 ICA y certificados → TASK-C-033 exógena.
**Depende de:** F6. **Salida:** BP, 300/350 y EEFF coherentes; conciliación reportable.

## F9 — Maestros y políticas comerciales

- Terceros: TASK-A-018 fuente canónica, A-019 direcciones/contactos, A-020 + C-022 fusión, A-021 condiciones, A-022 vista 360.
- Catálogo/inventario: A-024 reorden y sugerido de OC, A-025 negativos tri-estado, A-026 entradas por motivo,
  A-027 producción por lote, A-031 unicidad/borrado seguro, A-033 historial de precio.
- Caja: TASK-B-029 arqueo; A-029 cifrar credenciales Factus.
**Depende de:** F3, F5.

## F10 — Productividad, reportes operativos y BI

- TASK-C-023 reportes de compras + cola asíncrona con correo/campana (cubre B-031).
- TASK-A-032 informes de inventario; TASK-A-028 importación de productos/saldos; TASK-B-032 import/export de documentos.
- TASK-B-033 smart create; TASK-B-034 email/WhatsApp; TASK-B-036 retirar flujo `factura` legacy.
- TASK-C-021 reclasificación de cuentas; TASK-C-025 presupuesto; TASK-C-018 sucursal en partida; TASK-C-030 IVA mayor valor; TASK-C-031 asiento de nómina; TASK-C-037 dashboard financiero.

## F11 — Especializaciones (bajo demanda)

Variantes talla/color, AIU, usuario multiempresa, creación por NIT, traslado en tránsito, favoritos POS,
replicar documentos, comisiones por recaudo, moneda extranjera/exportación, ingresos para terceros, campos XML,
conciliación automática por banco, ficha de activo extendida, copiar parametrización, triggers de BD.

---

## Siguiente bloque ejecutable

**F0 + F1 frente Seguridad (TASK-A-001 → A-004).** Sin cambios de esquema; A-001 cierra una escalada activa.
En paralelo, diagnósticos de solo lectura para B0 y C0.

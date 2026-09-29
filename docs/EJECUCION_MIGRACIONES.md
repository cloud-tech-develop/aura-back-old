# Orden de ejecución — migraciones del plan de nómina

Compañero de `PLAN_MIGRACION_NOMINA.md`. El plan dice **qué** y **por qué**; esto dice **en qué orden** y **qué código va antes de cada SQL**.

**Regla que gobierna todo:** una migración que agrega columnas es segura sin tocar código (`ddl-auto=validate` no se queja de columnas extra en la BD). Una migración que agrega **restricciones** (NOT NULL, CHECK, FK obligatoria) **exige que el código ya sepa llenarlas**.

---

## Estado actual

| Fase | Diagnóstico | SQL escrito | SQL ejecutado | Código Java | Tests |
|---|---|---|---|---|---|
| **−1** baseline | ✅ | ⚠️ parcial (falta V13) | ❌ | — | ❌ |
| **1** tercero | ✅ | ✅ | ❌ | ❌ | ❌ |
| **2** contrato | ✅ | ❌ | ❌ | ❌ | ❌ |
| **3** conceptos | ✅ | ❌ | ❌ | ❌ | ❌ |
| **0** motor | ✅ | ❌ | ❌ | ❌ | ❌ |
| 4.5 retefuente → 10 | ✅ | ❌ | ❌ | ❌ | ❌ |

**Escribir el SQL es ~20% de cada fase.** El resto es entidades, servicios, DTOs y tests.

### Sobre el número "Fase 0"

La Fase 0 (corregir IBC, topes, Ley 1607) **se ejecuta cuarta, no segunda**. El nombre es histórico: en la v1 del plan iba primero, cuando se creía que había clientes liquidando. Al confirmarse que nómina no la usa nadie todavía, el orden se invirtió — modelo primero, motor una sola vez encima. Ver sección 4 del plan.

---

## Fase −1 — Baseline (BLOQUEA TODO)

| Paso | Qué | Quién |
|---|---|---|
| 1 | `pg_dump --schema-only` de producción | **Equipo** (requiere acceso) |
| 2 | Recortar lo que crean V14+ → `V13__baseline.sql` (55 tablas) | Pendiente |
| 3 | `V96__drift_ddl_auto.sql` | ✅ escrito |
| 4 | CI: Postgres vacío → Flyway → `validate` → app arranca | Pendiente |
| 5 | Confirmar que `ddl-auto=update` está muerto en todos los ambientes | Pendiente |

**`V96` es segura de correr ya** — en producción las columnas ya existen, `IF NOT EXISTS` la vuelve no-op. Pero **no arregla la instalación desde cero sin `V13`**.

Mientras `V13` no exista: no hay BD desde cero → no hay tests de integración → **la Fase 0 no se puede verificar**.

---

## Fase 1 — Tercero

### Bloque A — seguro sin tocar código

```
V96__drift_ddl_auto.sql
V97__tercero_campos_natural_juridica.sql
V98__tercero_rol.sql
V99__empleado_tercero_fk.sql
V101__cuenta_bancaria_banco_fk.sql
```

Todas aditivas. `V98`, `V99` y `V101` traen backfill automático **solo de los casos inequívocos**.

### Bloque B — reconciliación manual

Ver los bloques `PASOS MANUALES` al final de `V99` y `V101`. Resumen de lo que debe quedar en cero:

```sql
-- 1. Empleados sin tercero
SELECT COUNT(*) FROM empleados WHERE tercero_id IS NULL;

-- 2. Documentos duplicados en tercero (causa de los NULL de arriba)
SELECT empresa_id, tipo_documento, numero_documento, COUNT(*)
  FROM tercero WHERE deleted_at IS NULL
 GROUP BY 1,2,3 HAVING COUNT(*) > 1;

-- 3. Cuentas bancarias tipo BANCO sin tercero
SELECT COUNT(*) FROM cuenta_bancaria WHERE tipo='BANCO' AND tercero_id IS NULL;

-- 4. Terceros naturales sin nombre desagregado  ← el trabajo real
SELECT COUNT(*) FROM tercero
 WHERE tipo_persona='NATURAL' AND deleted_at IS NULL
   AND (nombre1 IS NULL OR apellido1 IS NULL);
```

**(4) es la única reconciliación grande de esta fase.** Nómina está vacía, pero hay clientes y proveedores de POS cargados. Partir `nombres`/`apellidos` por heurística falla con apellidos compuestos ("DE LA ROSA"), nombres de una palabra, o razón social metida en el campo de nombres. `tercero` alimenta a la DIAN: un apellido mal partido es un documento electrónico mal emitido.

### Bloque C — código

1. `TerceroEntity`: campos de V97 (`nombre1/2`, `apellido1/2`, `fechaNacimiento`, `sexo`, representante legal, autorretenedores, bancarios).
2. Entidad `TerceroRol` + doble escritura (booleanos **y** tabla).
   **No saltarse la doble escritura**: mientras el código viejo escriba solo booleanos, `tercero_rol` se desincroniza en silencio.
3. `TerceroQueryRepository`: colapsar `listarClientes/listarProveedores/listarBancos` en `listarPorRol(rol, search, empresaId)`.
4. `EmpleadoEntity`: agregar la relación a tercero.
   ```java
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "tercero_id", nullable = false)
   private TerceroEntity tercero;
   ```
5. `EmpleadoServiceImpl.crear()`: exigir y setear `tercero`.
6. `NominaServiceImpl`: hoy hace `getEmpleado().getBanco()` en las líneas ~133 y ~814. Migrar a `tercero`.
7. `CreateCuentaBancariaDto.terceroId`: hoy "Opcional". Obligatorio cuando `tipo = 'BANCO'`.

### Bloque D — cierre (solo con A + B + C listos)

```
V100__empleado_tercero_not_null.sql
```

⛔ **Rompe la app si corre antes del paso C.4.** `EmpleadoEntity` no tiene `terceroId` (verificado): el INSERT no manda la columna y falla por NOT NULL. La migración trae un guardarraíl que aborta si quedan huérfanos, **pero no puede detectar que falta el cambio de entidad**.

Recordar: una migración fallida deja Flyway en estado `failed` y bloquea el arranque.

---

## Fases siguientes

`2 (contrato)` → `3 (conceptos + nomina_detalle)` → `0 (motor + tests)` → `4.5 (retefuente)` → `5 (nómina electrónica)` → `5.5 → 6 (PILA)` → `8, 9, 10` → `4 (proyectos)` → `7 (async)`.

SQL pendiente de escribir. Numeración desde `V102`.

**Mínimo para el primer cliente:** `−1 → 1 → 2 → 3 → 0 → 4.5 → 5`. Sin retefuente y nómina electrónica no se puede liquidar legalmente en Colombia.

---

## Checklist por migración

Antes de correr cualquiera:

- [ ] ¿Solo agrega columnas/tablas? → seguro
- [ ] ¿Agrega NOT NULL / CHECK / FK obligatoria? → **¿el código ya llena ese campo?**
- [ ] ¿Trae backfill? → ¿qué pasa con los casos ambiguos? ¿quedan NULL o se inventan datos?
- [ ] ¿Es reversible? Si no, ¿hay backup?
- [ ] ¿Se probó en una BD desde cero? (bloqueado hasta que exista `V13`)

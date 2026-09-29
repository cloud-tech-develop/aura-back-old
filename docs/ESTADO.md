# Estado del trabajo — módulo de nómina

Última actualización: 2026-07-16

Documento de una sola pregunta: **¿qué está hecho y qué falta?**
Para el *qué* y el *por qué*, ver `PLAN_MIGRACION_NOMINA.md`.
Para el *orden de ejecución*, ver `EJECUCION_MIGRACIONES.md`.

---

## Resumen en una línea

**Todo el SQL del plan está escrito** (22 migraciones, V96–V117). El **código de la fase 1** está hecho.
**Nada se ha ejecutado. No hay un solo test.**

---

## 1. Lo que EXISTE en disco

### Migraciones — 22 archivos, ninguno ejecutado

| Archivo | Fase | ¿Seguro hoy? |
|---|---|---|
| `V96__drift_ddl_auto.sql` | −1 | ✅ no-op en prod |
| `V97__tercero_campos_natural_juridica.sql` | 1.a | ✅ |
| `V98__tercero_rol.sql` | 1.c | ✅ |
| `V99__empleado_tercero_fk.sql` | 1.b | ✅ |
| `V100__empleado_tercero_not_null.sql` | 1.b cierre | ⛔ **BLOQUEADA — requiere código** |
| `V101__cuenta_bancaria_banco_fk.sql` | 1.d | ✅ |
| `V102__contrato_laboral.sql` | 2 | ✅ |
| `V103__nomina_contrato_fk.sql` | 2 | ✅ (el cierre queda comentado adentro) |
| `V104__concepto_nomina.sql` | 3.a | ✅ |
| `V105__nomina_detalle.sql` | 3.b | ✅ |
| `V106__novedad_constituye_ibc.sql` | 0 | ✅ |
| `V107__nomina_config_topes_exoneraciones.sql` | 0 | ✅ |
| `V108__retefuente.sql` | 4.5 | ✅ |
| `V109__nomina_electronica.sql` | 5 | ✅ |
| `V110__afiliaciones_seguridad_social.sql` | 5.5 | ✅ |
| `V111__pila.sql` | 6 | ✅ |
| `V112__prestaciones_completas.sql` | 8 | ✅ |
| `V113__embargos.sql` | 9 | ✅ |
| `V114__certificados.sql` | 10 | ✅ |
| `V115__nomina_detalle_dimensiones.sql` | 4.a | ✅ |
| `V116__proyecto_presupuesto.sql` | 4.c/4.d | ✅ |
| `V117__proceso_nomina.sql` | 7 | ✅ |

**Todas son aditivas salvo `V100`.** Orden = numérico. `V100` va aparte, al final, con el código desplegado.

### Datos semilla que hay que REVISAR con un contador

Tres migraciones traen valores de referencia que **verifiqué contra la norma pero no contra un contador**:

| Migración | Qué semilla | Riesgo |
|---|---|---|
| `V107` | Rangos del fondo de solidaridad pensional 2026 | Descuento mal calculado |
| `V108` | UVT 2026 = 49.799 y tabla del art. 383 ET | **Retefuente mal → problema con la DIAN** |
| `V104` | Tarifas de ley (4%, 8.5%, 12%, 8.33%...) y `vigente_desde = 2026-01-01` | Ajustar al año real de entrada |

**No liquidar con estos valores sin validarlos.**

### Código Java (compila — `BUILD SUCCESS`)

**Nuevos:**
- `entity/TerceroRolEntity.java`
- `repositories/terceros/TerceroRolJPARepository.java`
- `services/implementations/TerceroRolService.java` — la doble escritura

**Modificados:**
- `entity/TerceroEntity.java` — 17 campos nuevos; legacy `@Deprecated`
- `entity/EmpleadoEntity.java` — relación `tercero` + helpers `*Resuelto()`
- `repositories/terceros/TerceroQueryRepository.java` — `listarPorRol()`
- `services/implementations/TerceroServiceImpl.java` — sincroniza roles; los 3 selectores delegan
- `services/implementations/NominaServiceImpl.java` — lee banco/nombre/doc vía helpers
- `dto/terceros/CreateTerceroDto.java` — campos nuevos

### Documentos y scripts
- `docs/PLAN_MIGRACION_NOMINA.md` — el plan (v8)
- `docs/EJECUCION_MIGRACIONES.md` — orden y precondiciones
- `docs/segmentacion_cartera.sql` — segmentación de cartera
- `docs/ESTADO.md` — este archivo
- `scripts/audit_baseline.py` — auditor entidades↔migraciones

---

## 2. Estado por fase

| Fase | Diseño | SQL | SQL corrido | Java | Tests | Real |
|---|---|---|---|---|---|---|
| **−1** baseline | ✅ | ⚠️ falta V13 | ❌ | — | ❌ | **~40%** |
| **1** tercero | ✅ | ✅ | ❌ | ✅ | ❌ | **~70%** |
| **2** contrato | ✅ | ✅ | ❌ | ✅ | ❌ | **~70%** |
| **3** conceptos | ✅ | ✅ | ❌ | ✅ | ❌ | **~70%** |
| **0** motor | ✅ | ✅ | ❌ | ✅ | ❌ | **~65%** |
| **4.5** retefuente | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **5** nómina electrónica | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **5.5** afiliaciones | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **6** PILA | ✅ | ✅ | ❌ | ❌ | ❌ | **~15%** |
| **8** prestaciones | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **9** embargos | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **10** certificados | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **4** proyectos | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |
| **7** async | ✅ | ✅ | ❌ | ❌ | ❌ | **~20%** |

**Ninguna fase pasa del 70%, y el techo es el mismo para todas: cero tests.**

Las fases 1, 2, 3 y 0 tienen SQL + código y compilan (`clean compile`). Pero
**compilar no es funcionar**: el motor de cálculo se reescribió entero y nadie ha
verificado que los números salgan bien. Ese 30% que falta no es cosmético.

PILA se queda en ~15% aunque tenga su SQL: las tablas son la parte fácil. El generador
—derivar las banderas de novedad, calcular 4 IBC y 4 conteos de días distintos, armar el
plano de ~120 columnas— es el 85% restante y es el subsistema más grande del módulo
después del motor.

### Código escrito (23 migraciones + 17 clases nuevas)

**Fase 1** — `TerceroRolEntity`, `TerceroRolJPARepository`, `TerceroRolService` (doble
escritura); `TerceroEntity` +17 campos; `EmpleadoEntity.tercero` + helpers `*Resuelto()`;
`listarPorRol()`; `EmpleadoServiceImpl.resolverTercero()`; DTOs y mapper.

**Fase 2** — `ContratoLaboralEntity`, `ContratoSalarioHistorialEntity`,
`ContratoRenovacionEntity`, `ContratoCentroCostoEntity`, sus repos,
`ContratoLaboralService` (con `cambiarSalario()` preservando histórico);
`NominaEntity.contrato`; `liquidarContrato()`; `liquidarPeriodoCompleto()` iterando
contratos; cierre en V118.

**Fase 3** — `ConceptoNominaEntity` (con enum `Base` acotado, sin `eval()`),
`NominaDetalleEntity`, repos, `ConceptoNominaService` (vigencia + precedencia + solapes).

**Fase 0** — `BasesLiquidacion` (las tres bases separadas), `MotorLiquidacion`,
`LineaLiquidacion`, `Traza`, `FondoSolidaridadRangoEntity` + repo; `NominaConfigEntity`
+5 campos; el cálculo cableado **eliminado** de `NominaServiceImpl`.

**Escribir SQL es ~20% de una fase. Escribir el código es ~50%. Los tests son el 30% restante y están en cero.**

---

## 3. Los tres bloqueos reales

### 🔴 1. No existe `V13__baseline.sql` — bloquea TODO

55 de 142 tablas no las crea ninguna migración (`tercero`, `venta`, `producto`, `empresa`, `usuario`, `compra`, `factura`...). Son las pre-V14, creadas por el viejo `ddl-auto=update`.

**Consecuencia:** una BD desde cero no arranca. Sin eso no hay Testcontainers, no hay CI limpio, **no hay dónde correr los tests de la Fase 0**.

**Requiere `pg_dump --schema-only` de producción.** No se puede hacer sin ese acceso.

### 🔴 2. Cero tests

No hay un solo test del motor de nómina. El plan dice que la Fase 0 **empieza** por los tests, y sigue en pie: sin ellos, corregir el IBC es cambiar números a ciegas.

Bloqueado por (1).

### 🟡 3. `V100` necesita código desplegado

`EmpleadoServiceImpl.crear()` todavía no exige ni setea `tercero`. Si `V100` corre antes, el INSERT falla por NOT NULL y **una migración fallida deja Flyway en `failed` y bloquea el arranque**.

---

## 4. Lo que falta, concreto

### Fase 1 (para cerrarla)
- [ ] `EmpleadoServiceImpl.crear()` — exigir y setear `tercero` ← bloquea V100
- [ ] `TerceroMapper` — mapear los campos nuevos
- [ ] `UpdateTerceroDto`, `TerceroDto`, `TerceroTableDto` — campos nuevos
- [ ] Endpoint genérico `listarPorRol` en `TerceroController` (opcional)
- [ ] Tests
- [ ] Correr V96–V99, V101
- [ ] Reconciliación manual (ver `EJECUCION_MIGRACIONES.md`)
- [ ] Correr V100

### Fase 2
- [ ] `ContratoLaboralEntity`, `ContratoSalarioHistorialEntity`, `ContratoRenovacionEntity`, `ContratoCentroCostoEntity`
- [ ] Repositorios + servicio
- [ ] `NominaEntity.contrato`
- [ ] `NominaServiceImpl.liquidar()` — por contrato, no por empleado
- [ ] `liquidarPeriodoCompleto()` — iterar contratos activos
- [ ] Cierre de V103 (NOT NULL + cambiar unicidad) ← **sin esto el multi-vínculo no funciona**
- [ ] Tests

### Fase 3
- [ ] `ConceptoNominaEntity`, `NominaDetalleEntity`
- [ ] Repositorios + resolución de vigencia
- [ ] El motor itera conceptos en vez de tener el cálculo cableado
- [ ] Escribir `nomina_detalle` con `traza`
- [ ] Invariante: `SUM(detalle) == nomina.total_*` ← test obligatorio
- [ ] Deprecar los porcentajes de `nomina_config`

### Fase 0 (el motor)
- [ ] **Tests primero** — casos a mano validados con un contador
- [ ] Extraer el motor de `NominaServiceImpl` (854 líneas, mezcla cálculo con orquestación y pago)
- [ ] Separar `baseIbc` de `totalDevengado` ← **el bug del auxilio de transporte**
- [ ] Tope de IBC (25 SMMLV)
- [ ] Fondo de solidaridad pensional
- [ ] Exoneración Ley 1607
- [ ] Salario integral (70/30)
- [ ] Constructor injection en `NominaServiceImpl`

### Fases 4.5 → 10
SQL sin escribir. Numeración desde `V106`.

---

## 5. Pendientes de negocio

| # | Pregunta | Bloquea |
|---|---|---|
| 1 | ¿Multi-vínculo simultáneo real? | Alcance de Fase 2 |
| 6 | ¿Qué operador de PILA? ¿API o plano? | Dimensionar Fase 6 |
| 7 | `responsabilidad_fiscal`: ¿múltiples códigos a Factus ya funciona? | Fase 1 |
| 8 | ¿Tarifa ARL por contrato o por centro de trabajo? | Fase 5.5 |
| 9 | ¿Quién mantiene el catálogo de EPS/AFP/CCF/ARL? | Fase 5.5 |
| 11 | **¿Cuándo entra el primer cliente?** | Todo el cronograma |
| 12 | ¿El primer cliente tolera PILA manual? | Si Fase 6 entra al mínimo |
| 13 | ¿Cuántos terceros hay en producción? | Tamaño de la reconciliación |

**Resueltos:** Factus emite nómina electrónica (#4) · PILA es necesaria (#5) · no hay `BONO` cargados (#2) · nada liquidado que recalcular (#3) · nómina sin clientes aún (#6.b)

---

## 6. Mínimo para el primer cliente

```
−1 → 1 → 2 → 3 → 0 → 4.5 (retefuente) → 5 (nómina electrónica)
```

Sin retefuente y nómina electrónica **no se puede liquidar legalmente en Colombia**.

PILA (6) puede ir después del arranque **solo si** el primer cliente tolera liquidarla manual un mes o dos.
Prestaciones (8) también, **salvo** que el primer cliente entre en junio/diciembre (prima) o tenga retiros.

---

## 7. Lo siguiente que yo haría

1. **Conseguir el `pg_dump` de producción** → `V13__baseline.sql`. Desbloquea todo.
2. **Correr V96–V105** (sin V100). Son aditivas.
3. **Cerrar Fase 1 en código** → correr V100.
4. **CI con BD desde cero.** Sin eso, el baseline se vuelve a romper.
5. **Fase 3 → Fase 0 con tests.**

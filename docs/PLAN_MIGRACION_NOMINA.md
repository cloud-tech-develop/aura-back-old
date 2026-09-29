# Plan de migración — Módulo de Nómina

Estado del documento: propuesta — v3
Fecha: 2026-07-16

Cambios v2: se agregó la Fase 5.5 (afiliaciones a seguridad social), que faltaba y sin la cual PILA no es construible; se detallaron los campos de `tercero` en la Fase 1; se corrigió el grafo de dependencias; se renumeraron las migraciones.

Cambios v3: EPS/AFP/CCF/ARL pasan a modelarse como **terceros con rol** (no como catálogo aislado) — son entidades a las que se les paga y deben existir en tesorería, contabilidad y exógena. El catálogo de códigos oficiales se mantiene, pero **global** y solo como fuente de códigos. Se agregó el refactor de roles a tabla (1.c) y la corrección de `banco` de VARCHAR a FK (D10).

Cambios v4: se agregó la **Fase −1 (baseline dump)**, que bloquea la Fase 0. Se corrigió la Fase 1.d: `cuenta_bancaria.tercero_id` ya existe y ya es el banco. Se agregó la **sección 8 (fuera de alcance)** para dejar explícito que este plan **no** produce una nómina colombiana completa — falta retefuente, liquidación definitiva, cesantías reales, embargos y certificados.

Cambios v5: la Fase 4 se reenfoca de "centros de costo" a **costeo por proyecto** — cierra el salto nómina→proyecto/frente (hoy roto: la V92 dimensionó `compra` y `gasto` pero no `nomina`), agrega presupuesto por proyecto con PAC mensual, y compromiso vs. ejecutado sobre `orden_compra`.

Cambios v6 — **dos correcciones de fondo**:

1. **`nomina_detalle` es núcleo, no capa de proyectos.** La v5 lo metió dentro de la Fase 4 junto con lo de proyectos. Error de empaquetado: el desglose por concepto con su traza lo necesita **toda** empresa (desprendible del empleado, y la DIAN exige cada devengado desglosado). Se mueve a la Fase 3. Lo opcional son las columnas `proyecto_id`/`frente_id`, no la tabla.

2. **La Fase 4 baja de prioridad.** La v5 la subió a segunda posición argumentando que proyectos era el diferenciador. **Principio de producto que manda sobre eso: la nómina debe servirle a todas las empresas** — incluidas las que no manejan proyectos ni frentes, con empleados que son trabajadores normales. El núcleo genérico (0→1→2→3→5) atiende al 100% de la cartera; la capa de proyectos atiende a un subconjunto. Primero el núcleo.

3. La consulta de segmentación (`segmentacion_cartera.sql`) se corrió y arrojó 9 empresas, 8 sin actividad. Ver sección 9.

Cambios v7:

1. **⚠️ La consulta de segmentación se corrió contra la BD LOCAL, no producción.** La sección 9 y el orden de la v6 quedan **SIN VALIDAR**. Ver el aviso al inicio de la sección 9. **Bloqueante para fijar el orden.**
2. **Factus confirmado**: emite nómina electrónica. Pendiente #4 resuelto. La Fase 5 se destraba.
3. **PILA confirmada como necesaria y NO cancelable**: hay clientes que pagan toda su seguridad social a través del sistema. Pendiente #5 resuelto. La Fase 6 entra al alcance firme.
4. **Se incorporan las brechas que estaban en "fuera de alcance"** como fases reales: retefuente (4.5), prestaciones y liquidación definitiva (8), embargos (9), certificados y desprendible (10). La sección 8 se reduce a lo que de verdad queda fuera.
Alcance: `nomina`, `empleados`, `nomina_novedad`, `nomina_config`, `liquidacion_prestacion` y la integración de nómina electrónica.

Este plan surge de comparar el módulo actual contra un ERP de nómina colombiano en producción (sector público/universitario, ~317 migraciones y 63 modelos solo en nómina). La comparación no busca replicar ese sistema —su arquitectura es peor que la nuestra en varios frentes— sino cerrar las brechas de **modelo de datos** y **cumplimiento normativo** que hoy son baratas de corregir y caras después.

---

## 1. Resumen ejecutivo

Tres frentes, en orden de urgencia:

| # | Frente | Urgencia | Motivo |
|---|---|---|---|
| A | Bugs de cumplimiento (IBC, topes, Ley 1607) | **Alta** | Estamos liquidando montos incorrectos hoy |
| B | Modelo de datos (empleado→tercero, contrato, conceptos) | **Alta** | Barato ahora, migración dolorosa con clientes en producción |
| C | Cumplimiento normativo faltante (nómina electrónica, PILA, retefuente) | Media-alta | Bloquea vender el módulo como nómina formal en Colombia |

El frente A no depende de nada y debería empezar ya. El frente B debe ir **antes** de sumar clientes de nómina. El frente C es el que abre el mercado.

**Dos advertencias de dimensionamiento:**

**PILA no es un módulo, es la punta de una cadena.** Depende de las cinco fases previas: identificación desagregada (1), historial salarial (2), conceptos tipificados (3), detalle por concepto (4) y afiliaciones (5.5). Hoy no existe ni siquiera modelo de afiliaciones — `empleado_arl` no dice a qué ARL está afiliado el empleado. Cualquier estimación que trate PILA como trabajo aislado está mal hecha.

**Nómina electrónica y PILA no tienen la misma urgencia.** La nómina electrónica es obligatoria ante la DIAN y no tiene alternativa manual razonable: sin ella el módulo no se vende. PILA sí tiene alternativa — muchas PyME la digitan en el portal del operador o la hace su contador. Antes de comprometer la Fase 6, confirmar que los clientes la necesitan de nosotros.

---

## 2. Diagnóstico

### 2.1 Lo que ya está bien (no tocar)

- Stack vigente: Spring Boot 3.5.10 / Java 17.
- Integridad declarada en BD: `REFERENCES`, `CHECK`, `UNIQUE (empleado_id, periodo_id)`.
- Motor de cálculo en código tipado con `BigDecimal` y `RoundingMode` explícito — **no** replicar el enfoque del ERP de referencia, que guarda PHP en base64 en una columna y lo ejecuta con `eval()`.
- Estados legibles por enum-string con `CHECK` en vez de enteros mágicos.
- Flyway lineal y versionado.
- Control de asistencia (`turno_trabajo`, `asistencia_marcaje`, `asistencia_dia`, `periodo_asistencia`, frentes de obra) con gating de liquidación — **el ERP de referencia no tiene esto**. Es una ventaja competitiva real; conservarla y construir encima.
- Modo `SIMPLIFICADO` / `COMPLETO`: buena decisión de producto para PyME. Mantener.
- Patrón Factus existente: `FactusTokenService` + `FactusService` con `@CircuitBreaker` + `@Retry` de Resilience4j y fallback. Es un buen patrón; la nómina electrónica debe seguirlo.

### 2.2 Bugs de cumplimiento detectados

Ubicación: `src/main/java/com/cloud_technological/aura_pos/services/implementations/NominaServiceImpl.java`

**B1 — El auxilio de transporte está entrando al IBC.** (línea ~515)

```java
BigDecimal totalDevengado = salarioProporcional.add(auxilio).add(novedadesDevengadas);
// ...
BigDecimal deduccionSalud = porcentaje(totalDevengado, config.getPctSaludEmpleado());
BigDecimal deduccionPension = porcentaje(totalDevengado, config.getPctPensionEmpleado());
```

El auxilio de transporte **no es base de seguridad social**. Se está descontando salud y pensión sobre él, y calculando los aportes del empleador sobre la misma base inflada. Efecto: al empleado se le descuenta de más y al empleador se le cobran aportes de más, en cada nómina.

Nota: el criterio correcto ya existe en el mismo archivo — las provisiones usan `baseConAuxilio` para prima/cesantías y `salarioProporcional` para vacaciones. Solo no se aplicó al IBC.

**B2 — Todas las novedades no-deducción entran al IBC por igual.**

`novedadesDevengadas` suma indiscriminadamente `HORA_EXTRA_*`, `BONO`, `COMISION`, `INCAPACIDAD`, `LICENCIA_REMUNERADA` y `OTRO_DEVENGO`. Pero las horas extra y comisiones **sí** son base de seguridad social, mientras que un bono no salarial pactado como tal **no** lo es, y las incapacidades tienen tratamiento propio. Falta una marca por novedad.

**B3 — Faltan topes y exoneraciones legales.**

- Tope de IBC: 25 SMMLV.
- Fondo de solidaridad pensional: aporte adicional escalonado sobre 4 SMMLV.
- **Exoneración Ley 1607**: empresas con empleados que devengan menos de 10 SMMLV no pagan salud empleador, SENA ni ICBF. Este es el más costoso: le estamos cobrando al cliente aportes que por ley no debe pagar.
- Salario integral (70/30): no contemplado.

**B4 — Provisión de intereses de cesantías: verificar.** (línea ~560)

```java
nomina.setProvisionIntCesantias(
    nomina.getProvisionCesantias()
        .multiply(PCT_INT_CESANTIAS).divide(CIEN, 2, RoundingMode.HALF_UP)
        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
);
```

Doble redondeo a 2 decimales en cadena, sobre un valor que ya es mensual. Revisar contra un caso conocido; puede haber deriva por centavos acumulada.

### 2.3 Deuda estructural del modelo

**D1 — `empleados` está desconectado de `tercero`.** Duplicamos `nombres`, `apellidos`, `tipo_documento`, `numero_documento`. Ya existe `tercero` con campos fiscales (V52: `tipo_persona`, `regimen`, `codigo_ciiu`, `responsabilidad_fiscal`, `email_fe`). El día que un empleado sea también proveedor, tenemos la misma persona dos veces sin forma de saberlo. La nómina electrónica va a exigir datos fiscales del trabajador que hoy viven en `tercero`, no en `empleados`.

**D2 — Un empleado = un contrato.** `uq_nomina_empleado_periodo` y los campos de contrato embebidos en `empleados` (`fecha_ingreso`, `fecha_retiro`, `salario_base`, `tipo_contrato`) impiden:
- Que una persona tenga dos vínculos simultáneos.
- Historial de contratos y renovaciones.
- Historial salarial (hoy un aumento sobrescribe `salario_base` y se pierde el dato anterior — con eso no se puede liquidar un retroactivo ni auditar).

El ERP de referencia modela `nom_vinculaciones` como entidad separada de la persona precisamente por esto, y le cuelga `nom_historial_salarios` y `nom_vinculaciones_renovaciones`.

**D3 — Conceptos hardcodeados.**

```java
private static final BigDecimal PCT_PRIMA = new BigDecimal("8.33");
private static final BigDecimal PCT_CESANTIAS = new BigDecimal("8.33");
```

Cambio de ley, devengado propio de un cliente, o bonificación distinta por empresa = código nuevo + release + despliegue. El requisito que el ERP resuelve con su catálogo `nom_conceptos` es legítimo; su implementación (`eval()` de base64) es lo que **no** hay que copiar.

**D4 — Sin centros de costo en nómina.** Existen `centros_costos` (V49) y `asiento_dimensiones` (V92), pero `nomina` no los referencia. No podemos responder "cuánto costó la mano de obra de este frente/proyecto" — y ya tenemos `proyecto_frentes` (V77) y `asistencia_frente` (V78), así que la pregunta va a llegar.

**D5 — Sin trazabilidad del cálculo.** Guardamos el resultado, no el desglose. Cuando un empleado reclame su liquidación no tenemos con qué responder. El ERP guarda una columna `traza` por cada línea de liquidación con el paso a paso.

**D6 — Liquidación síncrona.** `liquidarPeriodoCompleto()` corre dentro del request HTTP. Con 30 empleados va bien; con 500 y provisiones, timeout.

**D8 — No existe modelo de afiliaciones a seguridad social.** No hay EPS, AFP ni caja de compensación en ninguna migración. `empleado_arl` guarda `nivel_riesgo` y `porcentaje` pero **no guarda a qué ARL está afiliado el empleado** — es un porcentaje suelto sin entidad detrás. Tampoco existe `tipo_cotizante` en el proyecto.

Esto es bloqueante para PILA, que es en esencia un reporte de cuánto le corresponde a cada entidad de seguridad social por cada trabajador. Sin saber a qué EPS, fondo y caja está afiliado cada quien, no hay nada que reportar. Ver Fase 5.5.

**D9 — `tercero` incompleto para persona natural y jurídica.** Detalle en la Fase 1. El más grave: `nombres` y `apellidos` sin desagregar, cuando DIAN y UGPP exigen los cuatro componentes por separado.

**D10 — El rol `es_banco` existe pero no está cableado.** `TerceroQueryRepository.listarBancos()` filtra `es_banco = true` y alimenta el "selector de banco (nómina/tesorería)". Pero al persistir:

```sql
CREATE TABLE cuenta_bancaria (
    ...
    banco  VARCHAR(200),   -- ← texto libre, no FK a tercero
);
```

Y lo mismo en `empleados.banco VARCHAR(100)`. El selector muestra terceros; lo que se guarda es el **nombre como string**. El rol no apunta a nada.

Esto importa más allá de lo cosmético: es exactamente el error que se repetiría con las EPS. Si la afiliación se guarda como texto, "SURA", "Sura EPS" y "EPS SURA" son el mismo NIT y el operador de PILA rechaza el archivo. Corregir `banco` → FK a `tercero` en la misma pasada de la Fase 1. Ver Fase 5.5.

**D7 — Field injection.** `NominaServiceImpl` usa `@Autowired` sobre campos con nombres de clase totalmente cualificados inline. Dificulta el test unitario. `FactusService` ya usa constructor injection — alinear.

### 2.4 Brecha normativa

| | ERP referencia | Aura hoy |
|---|---|---|
| PILA / planilla UGPP | Formato completo con novedades | No existe |
| Nómina electrónica DIAN | Sí, con ajustes y logs | No existe |
| Retención en la fuente | Procedimientos 1 y 2 | No existe |
| Cesantías e intereses | Liquidación real | Solo provisión mensual |
| Embargos | Con prioridad, juzgado, saldo | Novedad plana `EMBARGO` |
| Fondo solidaridad pensional | Sí | No |
| Salario integral | Sí | No |

La nómina electrónica es **obligatoria** ante la DIAN. Sin ella el módulo no es vendible como nómina formal.

---

## 3. Fases

Numeración Flyway: las migraciones existentes van hasta **V95**. Este plan arranca en **V96**.

### Fase −1 — Baseline del esquema (BLOQUEANTE)

**Esto no es de nómina, pero bloquea la Fase 0 y todo lo demás.**

`CuentaBancariaEntity` declara `tercero_id` y `cuenta_contable_id`, pero **ninguna migración los crea**. La V36 crea la tabla sin ellos; la V87 solo agrega `permite_sobregiro` y `cupo_sobregiro`. Existen en las BD actuales porque los creó el viejo `ddl-auto=update` y quedaron **por debajo del baseline**:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=84
```

El ADR-005 tomó la decisión correcta (Flyway como única fuente de esquema, Hibernate solo valida), y el propio comentario en `application.properties` reconoce el hueco:

> *"instalación desde cero requiere el baseline dump (pendiente, ver ADR-005)"*

**Consecuencia: una BD desde cero no arranca.** Flyway correría V14→V95, no crearía `tercero_id` ni `cuenta_contable_id`, y `ddl-auto=validate` fallaría al no encontrar columnas que la entidad declara. Solo funciona en bases que ya existían cuando `update` estaba activo.

**Por qué bloquea la Fase 0:** la Fase 0 empieza por escribir tests del motor de liquidación. Los tests necesitan levantar un esquema limpio — Testcontainers, un CI nuevo, o el onboarding de un dev nuevo. Sin baseline reconstruible, no hay dónde correrlos.

#### Resultado de la auditoría (ejecutada — `scripts/audit_baseline.py`)

| | |
|---|---|
| Entidades con `@Table` | **142** |
| Tablas creadas por migraciones (V14–V95) | **88** |
| **Tablas que ninguna migración crea** | **55** |
| Columnas huérfanas verificadas | **7** en 2 tablas |

**A. Las 55 tablas ausentes son el núcleo del sistema:** `tercero`, `venta`, `venta_detalle`, `venta_pago`, `producto`, `producto_precio`, `producto_presentacion`, `empresa`, `usuario`, `sucursal`, `compra`, `compra_detalle`, `factura`, `cuentas_cobrar`, `cuentas_pagar`, `abonos_cobrar`, `abonos_pagar`, `caja`, `turno_caja`, `movimiento_caja`, `inventario`, `movimiento_inventario`, `lote`, `categoria`, `marca`, `cotizacion`, `merma`, `traslado`, `municipios`, `modulos`, `cuenta_config`, etc.

Esto **no es un bug nuevo**: es lo que el ADR-005 ya reconoce (las migraciones V1–V13 nunca existieron; el baseline dump está pendiente). La auditoría solo lo dimensiona: **son 55 tablas, no unas pocas**.

**B. Las 7 columnas huérfanas son el hallazgo preocupante:**

| Tabla | Columnas sin migración | Tabla creada en |
|---|---|---|
| `devolucion` | `fecha_devolucion`, `total_agregado`, `iva_agregado`, `costo_agregado`, `neto_diferencia` | V43 |
| `cuenta_bancaria` | `tercero_id`, `cuenta_contable_id` | V36 |

Estas tablas **sí** las crean migraciones. Las columnas se agregaron después con `ddl-auto=update`, sin migración. Conclusión: **`update` siguió corriendo después de que ya se escribían migraciones.** No es solo "falta el baseline" — hay drift *encima* de tablas ya migradas.

*(Descartado como falso positivo: `empleados.arl_id`. Es `@OneToOne(mappedBy="empleado")`, lado inverso, no genera columna.)*

#### Límite de la auditoría

Es análisis estático: detecta **columnas ausentes** y nada más. **No** ve diferencias de tipo, nullability, índices, constraints, ni columnas en BD que ya no estén en las entidades. Para eso hace falta comparar contra el esquema real de producción (`pg_dump --schema-only` + diff).

**Dado que apareció drift real, asumir que hay más del que se ve.**

#### El arreglo

**1. `V13__baseline.sql`** — las 55 tablas.
   - Generar con `pg_dump --schema-only` de producción, recortando lo que crean V14+.
   - Sobre BD existente **no corre** (queda bajo `baseline-version=84`).
   - Sobre BD nueva corre primero, y V14→V95 van encima.

**2. `V96__drift_ddl_auto.sql`** — las 7 columnas, con `ADD COLUMN IF NOT EXISTS`:
   - En producción ya existen → no hace nada.
   - En BD nueva las agrega después de que V43/V36 creen sus tablas.
   - Idempotente, seguro en ambos.

**3. Verificar en CI** — la única prueba que vale: Postgres vacío → Flyway → `ddl-auto=validate` → app arranca. Mientras eso no esté en el build, el problema vuelve.

**4. Confirmar que `ddl-auto=update` está muerto en todos los ambientes.** El drift de la V43 en adelante sugiere que alguno lo tuvo activo después del ADR-005.

**Renumeración:** este plan usaba V96+ para la Fase 0. Con `V96__drift_ddl_auto.sql` ocupado, **todo el plan se corre en +1** (Fase 0 arranca en V97). Ajustar al implementar.

**Urgencia:** mientras más migraciones se agreguen encima, más divergen el esquema real y el reconstruible. Este plan agrega ~20 migraciones más.

---

### Fase 0 — Corrección de cálculo (sin cambio de modelo)

**Objetivo:** liquidar los montos correctos. No toca el esquema salvo una columna.

**Precondición innegociable:** antes de tocar `calcular()`, escribir tests que fijen el comportamiento **correcto** esperado, con casos calculados a mano y validados con un contador. Hoy hay 18 archivos de test en todo el proyecto; el motor de nómina necesita los suyos.

- `V96__novedad_constituye_ibc.sql`
  ```sql
  ALTER TABLE nomina_novedad
      ADD COLUMN constituye_ibc BOOLEAN NOT NULL DEFAULT TRUE;
  -- Backfill por tipo: los no salariales quedan en FALSE
  UPDATE nomina_novedad SET constituye_ibc = FALSE
   WHERE tipo IN ('BONO', 'INCAPACIDAD', 'LICENCIA_REMUNERADA',
                  'PRESTAMO', 'EMBARGO', 'OTRO_DESCUENTO');
  ```
  > El backfill de `BONO` a FALSE es una decisión de negocio: asume bono no salarial. Confirmar con contabilidad antes de aplicar; si hay bonos salariales cargados, hay que discriminarlos.

- `V97__nomina_config_topes_exoneraciones.sql` — parametrizar por empresa: `aplica_exoneracion_1607`, `tope_ibc_smmlv` (default 25), `aplica_fondo_solidaridad`. No hardcodear.

- Refactor de `calcular()`:
  - Separar `baseIbc` de `totalDevengado`. `baseIbc = salarioProporcional + novedades con constituye_ibc = TRUE`. **Sin auxilio de transporte.**
  - Aplicar tope de IBC (25 SMMLV) antes de calcular aportes.
  - Fondo de solidaridad pensional escalonado sobre 4 SMMLV.
  - Exoneración Ley 1607: si aplica y el devengado < 10 SMMLV, `aporteSalud`, `aporteSena` y `aporteIcbf` en cero.
  - Extraer el motor a una clase propia (`MotorLiquidacion` o similar) — hoy vive dentro de `NominaServiceImpl` junto a la orquestación, el pago y la auditoría.

- Migrar `NominaServiceImpl` a constructor injection.

**Riesgo:** cambia montos ya liquidados. Definir con negocio si se recalculan períodos cerrados o solo aplica hacia adelante. **Ninguna corrección debe alterar retroactivamente una nómina en estado `PAGADO`.**

**Entregable:** suite de tests del motor + montos correctos.

---

### Fase 1 — `empleado` → `tercero`, y completar `tercero`

**Objetivo:** una sola identidad de persona en el sistema, con los campos que DIAN y UGPP exigen.

**Punto de partida favorable:** `tercero` ya tiene `es_cliente`, `es_proveedor`, `es_empleado`, `es_banco` como banderas de rol. Ese diseño es correcto y mejor que el del ERP de referencia — y `es_empleado` ya existe, o sea que esta fase está medio anticipada. Solo falta ejecutarla.

#### 1.a Campos faltantes en `tercero`

- `V98__tercero_campos_natural_juridica.sql`
  ```sql
  ALTER TABLE tercero
    -- Identificación desagregada (DIAN + UGPP la exigen así)
    ADD COLUMN nombre1                       VARCHAR(40),
    ADD COLUMN nombre2                       VARCHAR(40),
    ADD COLUMN apellido1                     VARCHAR(40),
    ADD COLUMN apellido2                     VARCHAR(40),
    -- Persona natural
    ADD COLUMN fecha_nacimiento              DATE,
    ADD COLUMN sexo                          VARCHAR(10),   -- M | F | OTRO
    ADD COLUMN fecha_expedicion_documento    DATE,
    ADD COLUMN municipio_expedicion_id       BIGINT,
    -- Persona jurídica
    ADD COLUMN nombre_comercial              VARCHAR(150),
    ADD COLUMN representante_legal_nombre    VARCHAR(150),
    ADD COLUMN representante_legal_documento VARCHAR(30),
    -- Fiscal
    ADD COLUMN es_autoretenedor_ica          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN es_autoretenedor_fuente       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN declarante                    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Bancario (hoy vive en `empleados`; al mover la identidad queda huérfano)
    ADD COLUMN banco                         VARCHAR(100),
    ADD COLUMN tipo_cuenta                   VARCHAR(20),
    ADD COLUMN numero_cuenta                 VARCHAR(50);

  CREATE TABLE tercero_contacto (
      id                    BIGSERIAL    PRIMARY KEY,
      tercero_id            BIGINT       NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
      nombre                VARCHAR(150) NOT NULL,
      cargo                 VARCHAR(100),
      email                 VARCHAR(150),
      telefono_fijo         VARCHAR(40),
      telefono_celular      VARCHAR(40),
      direccion_facturacion VARCHAR(200),
      direccion_envio       VARCHAR(200),
      notas                 VARCHAR(500),
      activo                BOOLEAN      NOT NULL DEFAULT TRUE
  );
  CREATE INDEX idx_tercero_contacto_tercero ON tercero_contacto(tercero_id);
  ```

**Justificación de los que no son obvios:**

- **`nombre1/2` + `apellido1/2`.** El más importante. La DIAN (nómina electrónica) y la UGPP (registro tipo 02 de PILA) exigen los cuatro componentes **por separado**. No es cosmético. El ERP de referencia tiene un `dividirNombresCompletos()` en su servicio de PILA — es exactamente el parche que toca escribir si no se parten los campos, y partir "JUAN CARLOS DE LA ROSA GOMEZ" por heurística falla.
- **`es_autoretenedor_ica` / `es_autoretenedor_fuente`.** Hoy hay un solo `auto_retenedor`. Son autorretenciones distintas: se puede ser de renta y no de ICA. El ERP de referencia también empezó con uno solo y los separó en 2019 — ya se estrellaron con esto. Afecta a `RetencionesSugeridasDto`.
- **Bancario.** `empleados` tiene `banco`, `numero_cuenta`, `tipo_cuenta`. Al mover la identidad a `tercero` quedan colgando en una tabla que ya no es la identidad.
- **`tercero_contacto` como tabla, no JSON.** Un proveedor con contacto de compras, de cartera y de despachos es normal; hoy hay un solo `telefono`/`email` escalar. El ERP tiene JSON *y* tabla a la vez — ese es el antipatrón de datos duplicados que ya le señalamos en `nom_vinculaciones`. Hacer solo la tabla.

**Pendiente de verificación:** `responsabilidad_fiscal` es hoy un `String`. La DIAN espera una **lista** de códigos (O-13, O-15, O-23, O-47, R-99-PN) y un tercero puede tener varias. Revisar cómo se está enviando a Factus en facturación: si ya funciona, quizá está resuelto; si no, es un bug latente y hay que migrarlo a lista.

**Backfill delicado:** poblar `nombre1/2` y `apellido1/2` desde `nombres`/`apellidos` requiere reporte + revisión humana para los ambiguos. Hacerlo en la **misma pasada** que la reconciliación de 1.b — es el mismo tipo de problema.

**No copiar del ERP:** todo su bloque de salud pública (`encuesta_sisben_id`, `puntaje_sisben`, `administrador_sivigila`, `perfil_asistencial_id`, `tipo_afiliacion_id`), lo académico, `hobbies_id`, `huella` binaria, libreta militar, y `metadatos` JSON como cajón de sastre. Su `com_terceros` de ~90 columnas absorbió cinco dominios porque nadie quiso crear tablas satélite. Es una advertencia, no un modelo.

**Nota estructural:** nuestro `tercero` tiene `empresa_id` (multi-tenant); el del ERP no (instalación por organización). Consecuencia: el mismo NIT existe N veces, una por empresa cliente. Es correcto para SaaS — no cambiarlo, solo tenerlo consciente.

#### 1.b Reconciliación `empleados` ↔ `tercero`

- `V99__empleado_tercero_fk.sql`
  ```sql
  ALTER TABLE empleados ADD COLUMN tercero_id BIGINT REFERENCES tercero(id);
  CREATE INDEX idx_empleados_tercero ON empleados(tercero_id);
  ```

- **Script de reconciliación** (no automático): emparejar `empleados` con `tercero` por `(empresa_id, tipo_documento, numero_documento)`. Los que no emparejen, crear `tercero` con `es_empleado = TRUE`. Los ambiguos, resolver a mano y dejar reporte. Este paso necesita revisión humana — **no** hacerlo en la migración.

- `V100__empleado_tercero_not_null.sql` — solo **después** de reconciliar en todos los ambientes:
  ```sql
  ALTER TABLE empleados ALTER COLUMN tercero_id SET NOT NULL;
  ALTER TABLE empleados ADD CONSTRAINT uq_empleado_tercero UNIQUE (empresa_id, tercero_id);
  ```

- Deprecar (no borrar aún) `nombres`, `apellidos`, `tipo_documento`, `numero_documento`, `banco`, `numero_cuenta`, `tipo_cuenta` en `empleados`. Leerlos desde `tercero`. Borrarlos en una migración posterior, cuando ningún código los toque.

**Por qué antes de nómina electrónica:** la DIAN exige datos fiscales y de identificación del trabajador que ya viven (o deben vivir) en `tercero`. Sin esto habría que duplicarlos otra vez en `empleados`.

#### 1.c Roles de tercero: de booleanos a tabla

**Motivación:** hoy hay 4 booleanos de rol (`es_cliente`, `es_proveedor`, `es_empleado`, `es_banco`). La Fase 5.5 necesita 4 más (EPS, AFP, CCF, ARL) → 8 booleanos. Ese es el punto donde el patrón deja de escalar: cada rol nuevo es una migración, una columna en 4 DTOs (`CreateTerceroDto`, `UpdateTerceroDto`, `TerceroDto`, `TerceroTableDto`) y un método de query casi idéntico a los anteriores.

- `V100b__tercero_rol.sql`
  ```sql
  CREATE TABLE tercero_rol (
      tercero_id BIGINT      NOT NULL REFERENCES tercero(id) ON DELETE CASCADE,
      rol        VARCHAR(20) NOT NULL,
          -- CLIENTE | PROVEEDOR | EMPLEADO | BANCO | EPS | AFP | CCF | ARL
      PRIMARY KEY (tercero_id, rol),
      CONSTRAINT chk_tercero_rol CHECK (rol IN
          ('CLIENTE','PROVEEDOR','EMPLEADO','BANCO','EPS','AFP','CCF','ARL'))
  );
  ```

- Backfill desde los 4 booleanos existentes. Deprecarlos (no borrarlos hasta que ningún código los lea).
- Colapsar `listarClientes()`, `listarProveedores()`, `listarBancos()` en un `listarPorRol(rol, search, empresaId)`.

**Costo real:** hay que migrar 4 booleanos y tocar 4 DTOs. **Si se prefiere agregar `es_eps`/`es_arl`/`es_afp`/`es_ccf` y seguir con booleanos, es defendible** — pero entonces hacerlo con conciencia de que el costo del refactor sube. Hacerlo ahora con 4 roles es más barato que en la Fase 5.5 con 8.

#### 1.d Cablear el rol `es_banco` (corrige D10)

**Corrección v4 — es menos trabajo del estimado:** `cuenta_bancaria.tercero_id` **ya existe** y **ya es el banco**. El DTO no deja dudas:

```java
/** El banco como tercero (persona jurídica). Opcional. */
private Long terceroId;
```

`titular` es un campo aparte, así que no hay ambigüedad de semántica: `tercero_id` = entidad financiera, `titular` = a nombre de quién está la cuenta. La FK que la v3 proponía crear ya está.

Lo que falta:

- Hacer `tercero_id` **obligatorio cuando `tipo = 'BANCO'`** (hoy es "Opcional" y el selector `listarBancos()` no se está aprovechando al persistir).
- Poblarlo donde esté nulo: emparejar contra `banco` (VARCHAR) con **revisión humana** — es texto libre, va a haber variantes del mismo banco.
- Deprecar el VARCHAR `banco`; leer el nombre desde el tercero.
- **`empleados.banco` sí es trabajo completo**: es VARCHAR sin FK. Convertirlo a FK → `tercero` (en 1.a se movió a `tercero`; ahora cablearlo).

Esta corrección no es opcional si se va a hacer PILA: es el mismo bug que rompería las afiliaciones, y arreglarlo aquí valida el patrón sobre un caso simple antes de replicarlo.

**No afecta contabilidad.** Verificado: el motor de asientos resuelve el lado del banco con `cuenta_bancaria.cuenta_contable_id` → `plan_cuenta`, y la contrapartida con `tesoreria_movimiento.contrapartida_cuenta_id` (ver comentario de la V66). `banco` (VARCHAR) solo se lee para display: `CuentaPdfService`, `FacturaQueryRepository` y copias entre DTOs. Ningún lector decide un débito o un crédito.

---

### Fase 2 — El contrato como entidad

**Objetivo:** soportar multi-vínculo, historial salarial y renovaciones.

- `V101__contrato_laboral.sql`
  ```sql
  CREATE TABLE contrato_laboral (
      id                  BIGSERIAL     PRIMARY KEY,
      empresa_id          INT           NOT NULL REFERENCES empresa(id),
      empleado_id         BIGINT        NOT NULL REFERENCES empleados(id),
      tipo_contrato       VARCHAR(30)   NOT NULL,
      cargo               VARCHAR(100),
      fecha_inicio        DATE          NOT NULL,
      fecha_fin           DATE,
      salario_base        NUMERIC(15,2) NOT NULL,
      es_salario_integral BOOLEAN       NOT NULL DEFAULT FALSE,
      es_principal        BOOLEAN       NOT NULL DEFAULT TRUE,
      periodicidad        VARCHAR(20),
      estado              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
                          -- ACTIVO | SUSPENDIDO | TERMINADO
      created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
      updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
      CONSTRAINT chk_contrato_tipo   CHECK (tipo_contrato IN ('INDEFINIDO','FIJO','OBRA_LABOR','PRESTACION_SERVICIOS','APRENDIZAJE')),
      CONSTRAINT chk_contrato_estado CHECK (estado IN ('ACTIVO','SUSPENDIDO','TERMINADO'))
  );

  CREATE TABLE contrato_salario_historial (
      id            BIGSERIAL     PRIMARY KEY,
      contrato_id   BIGINT        NOT NULL REFERENCES contrato_laboral(id) ON DELETE CASCADE,
      salario       NUMERIC(15,2) NOT NULL,
      fecha_desde   DATE          NOT NULL,
      fecha_hasta   DATE,
      motivo        VARCHAR(200),
      created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
  );
  ```

- Backfill: un `contrato_laboral` por cada `empleados` existente, con sus campos actuales. Una fila en `contrato_salario_historial` con `fecha_desde = fecha_ingreso`.

- `V102__nomina_contrato_fk.sql`: `nomina.contrato_id` → reemplaza la unicidad:
  ```sql
  ALTER TABLE nomina ADD COLUMN contrato_id BIGINT REFERENCES contrato_laboral(id);
  -- backfill desde el contrato único de cada empleado
  ALTER TABLE nomina DROP CONSTRAINT uq_nomina_empleado_periodo;
  ALTER TABLE nomina ADD CONSTRAINT uq_nomina_contrato_periodo UNIQUE (contrato_id, periodo_id);
  ```

- Deprecar los campos de contrato en `empleados`. `empleados` queda como el vínculo persona-empresa; `contrato_laboral` es el vínculo laboral.

**Nota de diseño:** si el multi-vínculo simultáneo no es un caso real de nuestros clientes, esta fase puede reducirse a extraer solo el historial salarial. Decidir con negocio antes de ejecutar. El costo de equivocarse a favor de la flexibilidad es bajo; al revés es alto.

**El historial salarial no es opcional.** Aunque se descarte el multi-vínculo, `contrato_salario_historial` debe existir: hoy un aumento sobrescribe `salario_base` y el dato anterior se pierde. Sin él no se puede liquidar un retroactivo, no se puede auditar, y **PILA no puede calcular la bandera `vsp`** (variación permanente de salario), que se deriva de detectar un cambio salarial en el período. Ver Fase 6.

---

### Fase 3 — Catálogo de conceptos

**Objetivo:** parametrizar sin recompilar. **Sin ejecutar código arbitrario.**

- `V103__concepto_nomina.sql`
  ```sql
  CREATE TABLE concepto_nomina (
      id                BIGSERIAL    PRIMARY KEY,
      empresa_id        INT          REFERENCES empresa(id),  -- NULL = concepto global del sistema
      codigo            VARCHAR(30)  NOT NULL,
      nombre            VARCHAR(150) NOT NULL,
      clase             VARCHAR(20)  NOT NULL,  -- DEVENGADO | DEDUCCION | APORTE_EMPLEADOR | PROVISION
      constituye_ibc    BOOLEAN      NOT NULL DEFAULT TRUE,
      base              VARCHAR(30)  NOT NULL,  -- SALARIO | SALARIO_MAS_AUXILIO | IBC | DEVENGADO_TOTAL | FIJO
      porcentaje        NUMERIC(7,4),
      valor_fijo        NUMERIC(15,2),
      vigente_desde     DATE         NOT NULL,
      vigente_hasta     DATE,
      activo            BOOLEAN      NOT NULL DEFAULT TRUE,
      CONSTRAINT uq_concepto UNIQUE (empresa_id, codigo, vigente_desde)
  );
  ```

  El campo `base` es un **enum acotado**, no una expresión. Cubre los casos reales sin abrir la puerta a inyección de código. Si algún día se necesita más expresividad, evaluar SpEL restringido o un DSL propio — **nunca** `eval()` de código almacenado.

  La vigencia por fechas (`vigente_desde`/`vigente_hasta`) es lo que permite cambiar tarifas por ley sin perder la capacidad de reliquidar períodos anteriores con las tarifas de su momento.

- Migrar `PCT_PRIMA`, `PCT_CESANTIAS`, `PCT_INT_CESANTIAS`, `PCT_VACACIONES` y los porcentajes de `nomina_config` a filas de `concepto_nomina` con `empresa_id IS NULL` y su vigencia real.

- El motor pasa a iterar conceptos vigentes en vez de tener el cálculo cableado.

#### 3.b Detalle por concepto — NÚCLEO

**Movido desde la Fase 4 (v6).** Esto no es capa de proyectos: lo necesita **toda** empresa. Es el desglose de la liquidación concepto por concepto, con la traza del cálculo. Sin él:
- No hay desprendible de pago que mostrarle al empleado.
- No hay cómo responder un reclamo ("el sistema dice 1.234.567" no es una respuesta).
- **No hay nómina electrónica**: la DIAN exige cada devengado y deducción en su etiqueta específica, no un total. Sin esto, armar el payload de la Fase 5 es un `if` gigante.

- `V104__nomina_detalle.sql`
  ```sql
  CREATE TABLE nomina_detalle (
      id                  BIGSERIAL     PRIMARY KEY,
      nomina_id           BIGINT        NOT NULL REFERENCES nomina(id) ON DELETE CASCADE,
      concepto_id         BIGINT        NOT NULL REFERENCES concepto_nomina(id),
      cantidad            NUMERIC(10,2),
      base                NUMERIC(15,2) NOT NULL DEFAULT 0,
      porcentaje          NUMERIC(7,4),
      valor               NUMERIC(15,2) NOT NULL DEFAULT 0,
      valor_empleado      NUMERIC(15,2) NOT NULL DEFAULT 0,
      valor_empleador     NUMERIC(15,2) NOT NULL DEFAULT 0,
      traza               JSONB
  );
  CREATE INDEX idx_nomina_detalle_nomina ON nomina_detalle(nomina_id);
  ```

  `traza` en JSONB guarda el desglose paso a paso. Es la diferencia entre un número y una explicación.

- Los campos agregados de `nomina` (`total_devengado`, `deduccion_salud`, …) se mantienen como denormalización de lectura, calculados desde `nomina_detalle`.

**Las dimensiones de proyecto se agregan después, en la Fase 4, como columnas nullable.** Una empresa sin proyectos usa esta tabla exactamente igual, con una fila por concepto y nada más.

---

### Fase 4 — Costeo por proyecto (CAPA OPCIONAL)

> **Principio de producto (v6): la nómina debe funcionar completa sin esta fase.**
>
> Muchas empresas no manejan proyectos ni frentes: tienen empleados que son trabajadores normales y punto. Para ellas, esta fase **no existe** — las columnas quedan nulas, la cascada de distribución cae al último paso, y el resultado es idéntico a no tenerla.
>
> Esta es una **capa aditiva sobre una nómina que ya funciona**, no un requisito de ella. Si esta fase nunca se hace, la nómina sigue siendo correcta, liquida bien, y cumple con la DIAN.
>
> Sigue el mismo patrón que ya se usó dos veces en el código y funcionó: `nomina_config.modo_nomina` (SIMPLIFICADO|COMPLETO), `nomina_config.modo_liquidacion` (SIN_ASISTENCIA|CON_ASISTENCIA_OBLIGATORIA|MIXTA), `proyecto.requiere_control_asistencia`. Capacidad opcional, activada por empresa, invisible para quien no la usa. **No inventar un patrón nuevo aquí.**

**Prioridad (corregida en v6):** la v5 subió esta fase a segunda posición argumentando que proyectos era el diferenciador. Se baja. El núcleo genérico (0→1→2→3→5) atiende al 100% de la cartera; esta capa atiende a un subconjunto. Sigue siendo valiosa —cierra un costeo hoy roto y es genérica entre verticales de proyectos— pero **después** del núcleo.

**Qué arregla:** la V92 dice *"habilita rentabilidad por obra"* y dimensionó `compra` y `gasto` con `proyecto_id`/`frente_id`, **pero no `nomina`**. La mano de obra —normalmente el costo mayor— no llega al proyecto, y la rentabilidad por obra sale incompleta y siempre optimista.

#### El problema: la cadena se corta en nómina

```
proyecto → proyecto_frente → proyecto_frente_trabajador   (quién está asignado)
                ↓
        asistencia_frente_detalle                          (horas reales por frente/día)
                ↓
              nomina                                       (cuánto se le pagó)
                ↓
             ✗ NADA                                        ← se corta aquí
                ↓
      asiento_detalle.proyecto_id / frente_id              (rentabilidad por obra)
```

La V92 dice *"habilita rentabilidad por obra"* y agrega `proyecto_id` + `frente_id` a `asiento_detalle`, `compra` y `gasto`. **Pero no a `nomina`.** `NominaEntity` no tiene proyecto, ni frente, ni centro de costo.

Consecuencia: en cualquier empresa de proyectos, la mano de obra —normalmente el costo mayor— **no llega al proyecto**. Compras y gastos sí; nómina no. La rentabilidad por obra sale incompleta y siempre optimista.

Lo notable es que el dato difícil **ya está capturado**: `asistencia_frente_detalle` tiene `horas_ordinarias`, `horas_extra_diurnas`, `horas_extra_nocturnas`, `horas_dominicales` y `horas_festivas` por empleado, por frente, por día. Y `asistencia_frente.estado` ya contempla `ENVIADO_NOMINA` — la intención de alimentar nómina estaba desde el diseño; solo falta el enlace.

#### 4.a Dimensionar la nómina (aditivo)

`nomina_detalle` ya existe desde la Fase 3.b. Aquí solo se le agregan las dimensiones, **todas nullable**:

- `V105__nomina_detalle_dimensiones.sql`
  ```sql
  ALTER TABLE nomina_detalle
      ADD COLUMN proyecto_id        BIGINT REFERENCES proyecto(id),
      ADD COLUMN frente_id          BIGINT REFERENCES proyecto_frente(id),
      ADD COLUMN centro_costo_id    BIGINT REFERENCES centro_costo(id),
      ADD COLUMN porcentaje_distrib NUMERIC(5,2) NOT NULL DEFAULT 100;

  CREATE INDEX idx_nomina_detalle_proyecto
      ON nomina_detalle(proyecto_id) WHERE proyecto_id IS NOT NULL;
  ```

  El `DEFAULT 100` no es cosmético: una empresa sin proyectos genera una fila por concepto con las tres dimensiones nulas y `porcentaje_distrib = 100`. Exactamente lo que la Fase 3.b ya producía. **La fase es invisible para quien no la usa.**

  **Dimensiones como columnas explícitas, no tabla genérica.** Sigue el precedente de la V92: *"Columnas explícitas (no tabla genérica de dimensiones — sería sobre-ingeniería a este tamaño)"*. Mantener esa decisión.

**Distribución: una fila por (concepto × dimensión).** Si un empleado trabajó 10 días en el frente A y 10 en el B, cada concepto genera dos filas al 50%. `porcentaje_distrib` deja auditable el reparto.

**El driver es la asistencia real, no un porcentaje fijo.** El reparto sale de `asistencia_frente_detalle` sumando horas por frente en el período:

```
% frente A = horas del empleado en frente A / horas totales del empleado en el período
```

Esto es mejor que el enfoque del ERP de referencia (`com_centro_costo_nom_vinculacion` con un `porcentaje` fijo pactado): ellos reparten por un estimado configurado; aquí se reparte por lo que **de verdad pasó**. Es la ventaja de tener asistencia por frente, que el ERP no tiene.

**Cascada de resolución** (en este orden):
1. Si el empleado tiene asistencia por frente en el período → distribuir por horas.
2. Si no, pero el contrato tiene `contrato_centro_costo` → distribuir por esos porcentajes.
3. Si no → una sola fila, dimensiones nulas, `porcentaje_distrib = 100`.

**El paso 3 es el caso mayoritario, no el caso borde.** Es el de cualquier empresa con empleados normales sin proyectos. Debe ser el camino por defecto y estar cubierto por tests como ciudadano de primera: una nómina sin proyectos tiene que producir el mismo resultado con o sin esta fase aplicada.

**Casos borde a definir con negocio:**
- **Conceptos no imputables a obra**: ¿la prima y las cesantías se reparten entre frentes igual que el salario, o van a un centro de costo administrativo? Contablemente lo primero es lo correcto (son costo laboral del proyecto), pero confirmarlo.
- **Ausentismos**: un empleado incapacitado no tiene horas en ningún frente ese día. ¿Su costo se reparte por la asistencia del resto del período, o va a administrativo? Sin regla explícita, la cascada lo manda al paso 2 o 3.
- **Redondeo**: repartir un valor entre 3 frentes deja centavos. Definir a cuál van (típicamente al mayor, o al primero) y que la suma de las filas **cuadre exactamente** con el total del concepto. Validar en tests.

- Al aprobar la nómina, el asiento contable propaga `proyecto_id`/`frente_id`/`centro_costo_id` a `asiento_detalle` (V92 ya tiene las columnas). Ahí es donde la cadena queda cerrada.

- Marcar `asistencia_frente.estado = 'ENVIADO_NOMINA'` cuando su período se liquide — el estado ya existe y hoy no lo pone nadie.

- `V105__contrato_centro_costo.sql` — distribución del costo laboral:
  ```sql
  CREATE TABLE contrato_centro_costo (
      id              BIGSERIAL    PRIMARY KEY,
      contrato_id     BIGINT       NOT NULL REFERENCES contrato_laboral(id) ON DELETE CASCADE,
      centro_costo_id BIGINT       NOT NULL REFERENCES centro_costo(id),
      porcentaje      NUMERIC(5,2) NOT NULL
  );
  ```
  Validar en aplicación que los porcentajes por contrato sumen 100.

- Los campos agregados de `nomina` (`total_devengado`, `deduccion_salud`, ...) se mantienen como denormalización de lectura, calculados desde `nomina_detalle`.

#### 4.c Presupuesto por proyecto

**Problema:** `proyecto` tiene `codigo`, `nombre`, `cliente_id`, fechas, `centro_costo_id`, `ubicacion`... y **ningún campo de valor**. No hay presupuesto, ni valor contratado. Se puede acumular lo gastado (vía `asiento_detalle.proyecto_id`) pero no hay contra qué compararlo. `centro_costo.presupuesto_asignado` (V49) existe, pero es por centro de costo, sin desagregación y sin relación con el proyecto.

"¿Voy sobre o bajo presupuesto?" la preguntan una constructora, una agencia y un taller por igual. Es genérico.

- `V106__proyecto_presupuesto.sql`
  ```sql
  CREATE TABLE proyecto_presupuesto (
      id            BIGSERIAL     PRIMARY KEY,
      empresa_id    INT           NOT NULL REFERENCES empresa(id),
      proyecto_id   BIGINT        NOT NULL REFERENCES proyecto(id),
      frente_id     BIGINT        REFERENCES proyecto_frente(id),
      capitulo      VARCHAR(100),
      codigo        VARCHAR(30)   NOT NULL,
      descripcion   VARCHAR(300)  NOT NULL,
      tipo_costo    VARCHAR(20)   NOT NULL,   -- MANO_OBRA | MATERIAL | EQUIPO | SUBCONTRATO | INDIRECTO | OTRO
      cuenta_contable_id BIGINT,              -- para casar presupuesto vs ejecutado por PUC
      valor         NUMERIC(15,2) NOT NULL DEFAULT 0,
      -- Programación mensual (PAC): cuánto se planea ejecutar cada mes
      pac01 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac02 NUMERIC(15,2) NOT NULL DEFAULT 0,
      pac03 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac04 NUMERIC(15,2) NOT NULL DEFAULT 0,
      pac05 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac06 NUMERIC(15,2) NOT NULL DEFAULT 0,
      pac07 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac08 NUMERIC(15,2) NOT NULL DEFAULT 0,
      pac09 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac10 NUMERIC(15,2) NOT NULL DEFAULT 0,
      pac11 NUMERIC(15,2) NOT NULL DEFAULT 0,  pac12 NUMERIC(15,2) NOT NULL DEFAULT 0,
      version       INT           NOT NULL DEFAULT 1,
      activo        BOOLEAN       NOT NULL DEFAULT TRUE,
      created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
      CONSTRAINT chk_pp_tipo CHECK (tipo_costo IN
          ('MANO_OBRA','MATERIAL','EQUIPO','SUBCONTRATO','INDIRECTO','OTRO')),
      CONSTRAINT uq_pp UNIQUE (proyecto_id, codigo, version)
  );
  CREATE INDEX idx_pp_proyecto ON proyecto_presupuesto(proyecto_id);
  ```

**El PAC es la mejor idea del ERP de referencia y es directamente aplicable.** Sus `pre_detalles_presupuestales` traen doce columnas `pac01`…`pac12` con la programación mensual. Para una empresa de proyectos eso es el **flujo de caja de la obra**: no solo cuánto vas a gastar, sino cuándo. Es lo que permite anticipar el bache de caja del mes 4 antes de llegar al mes 4.

**`version`** permite reprogramar sin perder el presupuesto original — comparar contra la línea base es la mitad del valor. `tipo_costo` alinea con `nomina_detalle` (MANO_OBRA) y con `compra`/`gasto`.

**Ejecutado:** no crear tabla de saldos todavía. Se resuelve con una consulta sobre `asiento_detalle` filtrando por `proyecto_id`, agrupada por cuenta/tipo. El ERP materializa saldos (`pre_saldos_*`) porque maneja volúmenes de sector público; a este tamaño es optimización prematura. Si la consulta se vuelve lenta, ahí se materializa.

#### 4.d Compromiso vs. ejecutado

**Otro hueco de la V92:** le puso `proyecto_id`/`frente_id` a `compra` y a `gasto`, pero **no a `orden_compra`**. Entonces una orden emitida no se puede atribuir a un proyecto.

Sin esto, la obra se ve en verde hasta que llegan las facturas todas juntas. El concepto del ERP —distinguir *comprometido* de *ejecutado*— resuelve justamente eso, y aquí sale casi gratis porque `orden_compra` ya tiene los estados y el `total`.

- `V107__orden_compra_proyecto.sql`
  ```sql
  ALTER TABLE orden_compra
      ADD COLUMN IF NOT EXISTS proyecto_id BIGINT,
      ADD COLUMN IF NOT EXISTS frente_id   BIGINT,
      ADD COLUMN IF NOT EXISTS presupuesto_item_id BIGINT REFERENCES proyecto_presupuesto(id);
  CREATE INDEX IF NOT EXISTS idx_orden_compra_proyecto
      ON orden_compra(proyecto_id) WHERE proyecto_id IS NOT NULL;
  ```

**Mapeo de estados** (los de `orden_compra` ya existen):

| Estado orden | Significa |
|---|---|
| `BORRADOR` | Nada. No afecta |
| `ENVIADA`, `CONFIRMADA` | **Comprometido** — reserva presupuesto |
| `RECIBIDA_PARCIAL` | Comprometido el saldo; ejecutado lo recibido |
| `CERRADA` | **Ejecutado** (ya hay `compra`) — libera el compromiso |
| `ANULADA` | Libera el compromiso |

La vista de control queda: **Presupuesto − Comprometido − Ejecutado = Disponible**.

**Lo que NO se toma del ERP:** la cadena formal CDP → RP → obligación → pago, `pre_cpcs`, `pre_fuentes_financiamientos`, vigencias y cierres presupuestales. Eso es obligación legal del sector público colombiano. **Tomar el concepto de compromiso, no el aparato jurídico.** Bloquear una compra por falta de presupuesto debe ser una **advertencia configurable**, no un impedimento legal.

#### Qué NO entra aquí

Específico de construcción — esperar a que el segmento lo justifique:

- **APU** (análisis de precios unitarios). El corazón de un presupuesto de obra. El ERP tampoco lo tiene.
- **Avance de obra / % ejecución física.** Sin esto, presupuesto vs. gasto miente: gastaste el 60% pero ¿construiste el 60% o el 30%? Ninguno de los dos lo modela.
- **Actas de obra / cortes.** Cómo se le factura al cliente por avance.
- **Subcontratos por frente** con su propio corte.

Con cartera mixta (constructoras + otras), esta capa genérica rinde en toda la base y **es prerrequisito** de lo anterior. Si las constructoras resultan el mejor segmento, los cimientos ya están.

**Cobertura barata:** si los clientes ya presupuestan en Excel, un importador a `proyecto_presupuesto` vale más que exigirles cambiar su forma de trabajar.

---

### Fase 4.5 — Retención en la fuente

**Estaba listada como brecha desde la v1 y nunca se le escribió fase. Es la omisión más grave del plan.**

Obligatoria por ley. Y **debe ir antes de la Fase 5**: el XML de la DIAN lleva la retención como deducción. Enviar nóminas electrónicas sin retefuente calculada es enviar documentos incompletos.

- `V111__retefuente.sql`
  ```sql
  -- Tabla de rangos UVT. Cambia cada año: por eso es tabla, no constantes.
  CREATE TABLE retefuente_rango (
      id            BIGSERIAL     PRIMARY KEY,
      agno          INT           NOT NULL,
      uvt_desde     NUMERIC(12,2) NOT NULL,
      uvt_hasta     NUMERIC(12,2),          -- NULL = sin tope (último rango)
      tarifa        NUMERIC(5,2)  NOT NULL, -- %
      uvt_resta     NUMERIC(12,2) NOT NULL DEFAULT 0,
      uvt_suma      NUMERIC(12,2) NOT NULL DEFAULT 0,
      CONSTRAINT uq_rf_rango UNIQUE (agno, uvt_desde)
  );

  -- Valor del UVT por año. Lo fija la DIAN.
  CREATE TABLE uvt_valor (
      agno   INT           PRIMARY KEY,
      valor  NUMERIC(15,2) NOT NULL
  );

  -- Procedimiento 2: porcentaje fijo, se recalcula cada semestre
  CREATE TABLE retefuente_porcentaje_fijo (
      id            BIGSERIAL     PRIMARY KEY,
      contrato_id   BIGINT        NOT NULL REFERENCES contrato_laboral(id),
      semestre      VARCHAR(7)    NOT NULL,   -- '2026-1' | '2026-2'
      porcentaje    NUMERIC(5,2)  NOT NULL,
      base_calculo  NUMERIC(15,2) NOT NULL,
      calculado_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
      CONSTRAINT uq_rf_fijo UNIQUE (contrato_id, semestre)
  );

  -- Procedimiento por contrato + deducciones del empleado
  ALTER TABLE contrato_laboral
      ADD COLUMN procedimiento_retefuente VARCHAR(3) NOT NULL DEFAULT '1',
      CONSTRAINT chk_proc_rf CHECK (procedimiento_retefuente IN ('1','2'));

  -- Deducciones que depuran la base (dependientes, intereses vivienda,
  -- medicina prepagada, AFC, aportes voluntarios)
  CREATE TABLE empleado_deduccion_renta (
      id           BIGSERIAL     PRIMARY KEY,
      empresa_id   INT           NOT NULL REFERENCES empresa(id),
      contrato_id  BIGINT        NOT NULL REFERENCES contrato_laboral(id),
      tipo         VARCHAR(30)   NOT NULL,
          -- DEPENDIENTES | INTERESES_VIVIENDA | MEDICINA_PREPAGADA | AFC | AFP_VOLUNTARIO
      valor        NUMERIC(15,2) NOT NULL DEFAULT 0,
      vigente_desde DATE         NOT NULL,
      vigente_hasta DATE,
      CONSTRAINT chk_edr_tipo CHECK (tipo IN
          ('DEPENDIENTES','INTERESES_VIVIENDA','MEDICINA_PREPAGADA','AFC','AFP_VOLUNTARIO'))
  );
  ```

**Ya existe `nom_vinculaciones.porcentaje_fijo_retencion` en el ERP de referencia y `tercero.aplica_art_383`** — señal de que ambos conceptos se necesitan. Aquí `contrato_laboral.porcentaje_fijo_retencion` ya se contempló en la Fase 2; se conecta con `retefuente_porcentaje_fijo`.

**La depuración de la base es lo que tiene chicha, no la tabla de rangos.** El orden legal: devengado → menos ingresos no constitutivos (aportes obligatorios salud/pensión) → menos deducciones (`empleado_deduccion_renta`) → menos rentas exentas → **menos el 25% exento (con tope de 790 UVT anuales)** → base gravable en UVT → aplicar rango.

**Procedimiento 1** se calcula cada mes sobre el ingreso del mes. **Procedimiento 2** calcula un porcentaje fijo en junio y diciembre (promediando los 12 meses anteriores) y lo aplica todo el semestre. Son dos rutas distintas, no una con un flag.

**Depende de la Fase 3** (conceptos con vigencia): sin ella, cada cambio de UVT o de tarifa es un despliegue. Con ella, es una fila.

**Los conceptos de retefuente van al catálogo** (`concepto_nomina` con `clase = 'DEDUCCION'`), no hardcodeados. El ERP los identifica con `retefuente_procedimiento1` y `retefuente_procedimiento2` en su maestro de conceptos.

**Tests obligatorios:** casos calculados a mano y validados con un contador, incluyendo el borde de cada rango UVT y el tope del 25% exento. Retefuente mal calculada es un problema con la DIAN, no un bug cosmético.

---

### Fase 5 — Nómina electrónica con Factus

**Verificación previa (bloqueante):** confirmar en la documentación oficial de Factus que su API soporta **documento soporte de pago de nómina electrónica**, no solo factura de venta. La integración actual (`FactusService.generarFactura`) es de facturación; nómina electrónica es un documento DIAN distinto, con su propio esquema. Si Factus no lo cubre, hay que evaluar otro proveedor (o emisión directa) y esta fase cambia de forma. **No dar por hecho el soporte.**

Asumiendo que sí lo soporta:

- `V108__nomina_electronica.sql`
  ```sql
  CREATE TABLE nomina_electronica (
      id                  BIGSERIAL    PRIMARY KEY,
      empresa_id          INT          NOT NULL REFERENCES empresa(id),
      nomina_id           BIGINT       NOT NULL REFERENCES nomina(id),
      agno                INT          NOT NULL,
      mes                 INT          NOT NULL,
      consecutivo         BIGINT       NOT NULL,
      es_ajuste           BOOLEAN      NOT NULL DEFAULT FALSE,
      nomina_ajustada_id  BIGINT       REFERENCES nomina_electronica(id),
      estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
                          -- PENDIENTE | ENVIADO | ACEPTADO | RECHAZADO | ANULADO
      cune                VARCHAR(120),
      payload_json        JSONB,
      xml                 TEXT,
      created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
      updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
      CONSTRAINT chk_ne_estado CHECK (estado IN ('PENDIENTE','ENVIADO','ACEPTADO','RECHAZADO','ANULADO')),
      CONSTRAINT uq_ne_consecutivo UNIQUE (empresa_id, agno, consecutivo)
  );

  CREATE TABLE nomina_electronica_log (
      id                    BIGSERIAL   PRIMARY KEY,
      nomina_electronica_id BIGINT      NOT NULL REFERENCES nomina_electronica(id) ON DELETE CASCADE,
      codigo_respuesta      VARCHAR(20),
      mensaje_respuesta     TEXT,
      created_at            TIMESTAMP   NOT NULL DEFAULT NOW()
  );
  ```

- `FactusNominaService` siguiendo **exactamente** el patrón de `FactusService`:
  - Constructor injection, `@CircuitBreaker(name = "factus-nomina")`, `@Retry(name = "factus-nomina")`, método fallback.
  - Reutilizar `FactusTokenService` (ya está separado justamente para que el circuit breaker del token no se mezcle con el del documento — mantener esa separación).
  - Nuevas instancias en `application.properties` para `factus-nomina`, con umbrales propios.

- **Envío asíncrono, nunca en el request.** Una nómina aprobada encola el envío. `estado = PENDIENTE` → job → `ENVIADO` → respuesta → `ACEPTADO`/`RECHAZADO`. Cada intento deja fila en `nomina_electronica_log`.

- **Idempotencia:** el consecutivo se reserva antes de enviar y `uq_ne_consecutivo` lo protege. Un reintento nunca debe generar un documento nuevo ante la DIAN. Este es el punto donde más duele equivocarse.

- Flujo de ajustes (nota de ajuste de nómina) vía `es_ajuste` + `nomina_ajustada_id`.

- Disparo: al pasar `nomina` a `APROBADO`, igual que hoy se genera el asiento contable tras el commit.

**Dependencias:** requiere Fase 1 (datos fiscales del trabajador desde `tercero`) y se beneficia mucho de Fase 3+4 (la DIAN exige cada devengado y deducción **desglosado por tipo**, no un total — sin `nomina_detalle` con conceptos tipificados, armar el payload es un `if` gigante).

---

### Fase 5.5 — Afiliaciones a seguridad social

**Esta fase faltaba en la v1 del plan. Sin ella PILA no es construible.**

Hoy no existe modelo de afiliaciones: no hay EPS, AFP ni caja de compensación en ninguna migración, y `empleado_arl` guarda un `porcentaje` sin decir **a qué ARL** está afiliado el empleado. PILA es, en esencia, un reporte de cuánto le corresponde a cada entidad de seguridad social por cada trabajador — sin saber a qué entidad está afiliado cada quien, no hay nada que reportar.

#### Decisión de diseño: son terceros con rol, no un catálogo aparte

La v2 de este plan modelaba EPS/AFP/CCF/ARL como un catálogo aislado. **Estaba incompleto:** a esas entidades **se les paga**. Son acreedores. Hay que girarles desde tesorería, el asiento contable necesita su NIT, y exógena las reporta. Un catálogo que no sea `tercero` obliga a duplicarlas como tercero de todas formas el día del pago de aportes.

El patrón ya existe y funciona: `es_banco` con `listarBancos()` filtrando por rol para el "selector de banco (nómina/tesorería)". Extenderlo es coherente con el diseño actual.

**Pero hay una tensión real:** `tercero` es multi-tenant (`empresa_id`) y los códigos de PILA son **nacionales**. Si cada empresa cliente crea su tercero "EPS SURA" y digita el código a mano, divergen (`EPS002`, `EPS-002`, vacío) y el archivo se rechaza por empresa sin diagnóstico claro.

Por eso son **las dos cosas, con responsabilidades separadas**:

```
entidad_seguridad_social   ← GLOBAL (sin empresa_id). Solo códigos. Solo lectura para el cliente.
        ▲
        │ entidad_seguridad_social_id
        │
    tercero                ← por empresa, como hoy. Con rol EPS/AFP/CCF/ARL.
        ▲
        │ tercero_id
        │
contrato_afiliacion        ← apunta a tercero, no a entidad
```

`contrato_afiliacion` apunta a **`tercero`** (así pagos, asientos y exógena funcionan). El `codigo_oficial` se obtiene navegando a `entidad_seguridad_social`. El cliente **elige de una lista**, no digita el código. Cuando una EPS se fusiona o se liquida —pasa seguido— se actualiza el catálogo central una vez, no empresa por empresa.

- `V109__afiliaciones_seguridad_social.sql`
  ```sql
  -- Catálogo NACIONAL de códigos. Sin empresa_id: lo mantenemos nosotros.
  CREATE TABLE entidad_seguridad_social (
      id             BIGSERIAL    PRIMARY KEY,
      tipo           VARCHAR(10)  NOT NULL,   -- EPS | AFP | CCF | ARL
      codigo_oficial VARCHAR(20)  NOT NULL,   -- el que exige el operador de PILA
      nit            VARCHAR(30)  NOT NULL,
      nombre         VARCHAR(150) NOT NULL,
      activo         BOOLEAN      NOT NULL DEFAULT TRUE,
      CONSTRAINT chk_ess_tipo   CHECK (tipo IN ('EPS','AFP','CCF','ARL')),
      CONSTRAINT uq_ess_codigo  UNIQUE (tipo, codigo_oficial)
  );

  -- Enlace del tercero local (por empresa) al catálogo nacional
  ALTER TABLE tercero
      ADD COLUMN entidad_seguridad_social_id BIGINT REFERENCES entidad_seguridad_social(id);

  -- Afiliación vigente por contrato. Apunta a TERCERO. Con fechas, para derivar traslados.
  CREATE TABLE contrato_afiliacion (
      id          BIGSERIAL   PRIMARY KEY,
      contrato_id BIGINT      NOT NULL REFERENCES contrato_laboral(id) ON DELETE CASCADE,
      tercero_id  BIGINT      NOT NULL REFERENCES tercero(id),
      tipo        VARCHAR(10) NOT NULL,   -- EPS | AFP | CCF | ARL
      fecha_desde DATE        NOT NULL,
      fecha_hasta DATE,
      CONSTRAINT chk_afil_tipo CHECK (tipo IN ('EPS','AFP','CCF','ARL'))
  );
  CREATE INDEX idx_contrato_afiliacion ON contrato_afiliacion(contrato_id, tipo);

  -- Tipo de cotizante UGPP + centro de trabajo (tarifa ARL por sede)
  ALTER TABLE contrato_laboral
      ADD COLUMN tipo_cotizante    VARCHAR(5),
      ADD COLUMN subtipo_cotizante VARCHAR(5),
      ADD COLUMN centro_trabajo_id BIGINT;
  ```

- **Validación en aplicación:** un `tercero` referenciado desde `contrato_afiliacion` debe tener el rol correspondiente (`tipo = 'EPS'` → rol EPS) y su `entidad_seguridad_social_id` poblado. Sin lo segundo, PILA no puede resolver el código y el archivo sale incompleto. Validar al guardar la afiliación, no al generar la planilla.

- Migrar `empleado_arl` a `contrato_afiliacion` con `tipo = 'ARL'`. **Ojo:** `empleado_arl` no dice a qué ARL — solo tiene `nivel_riesgo` y `porcentaje`. La migración **no puede inferir la entidad**; hay que capturarla. Los `nivel_riesgo`/`porcentaje` se conservan en el contrato o en el centro de trabajo según se resuelva el pendiente #8.

- Poblar `entidad_seguridad_social` con los códigos oficiales vigentes. **No es un seed de una sola vez** — requiere mantenimiento.

**Las fechas en `contrato_afiliacion` no son decorativas:** las banderas de traslado de PILA (`tde`, `tae`, `tdp`, `tap`) se **derivan** de detectar un cambio de entidad dentro del período. Sin historial de afiliación, no se pueden calcular.

**Prerrequisito:** la Fase 1.d (cablear `banco` de VARCHAR a FK) valida este mismo patrón sobre un caso más simple. Si `cuenta_bancaria.banco` sigue siendo texto libre, el mismo error se replica aquí y PILA se rechaza.

---

### Fase 6 — PILA

La planilla de seguridad social. El otro proceso mensual obligatorio, y el subsistema más grande del módulo después del motor de liquidación.

**Depende de las cinco fases anteriores.** No es la última por orden arbitrario: consume la salida de todas. Ver el grafo en la sección 4.

- `V110__pila.sql`: `pila_encabezado` (datos del aportante), `pila_planilla` (registro tipo 01), `pila_cotizante` (registro tipo 02).

- `pila_cotizante` es el trabajo grueso: **~120 columnas** en el formato oficial.

**Lo que hay que construir, y de dónde sale cada cosa:**

| Bloque | Origen | Estado |
|---|---|---|
| Identificación del cotizante (doc + 4 componentes de nombre) | `tercero` | Fase 1 |
| `cod_eps`, `cod_afp`, `cod_ccf`, `cod_arl` + traslados | `contrato_afiliacion` → `tercero` → `entidad_seguridad_social` | Fase 5.5 |
| `tipo_cotizante`, `subtipo_cotizante` | `contrato_laboral` | Fase 5.5 |
| `ibc_pension`, `ibc_salud`, `ibc_arl`, `ibc_ccf` | Motor | Fase 0 + **nuevo** |
| `dias_afp`, `dias_eps`, `dias_arl`, `dias_ccf` | Motor + novedades | **Nuevo** |
| Banderas `ing`, `ret`, `tde`, `tae`, `tdp`, `tap`, `vsp`, `sln`, `ige`, `lma`, `vac_lr`, `avp`, `vct`, `irl` + pares de fechas | Derivadas | **Nuevo** |
| `aporte_fondo_solidaridad`, `aporte_fondo_subsistencia` | Motor | Fase 0 |
| `exonerado_ley_1607` | `nomina_config` | Fase 0 |
| `centro_trabajo` | `contrato_laboral` | Fase 5.5 |
| `no_autorizacion_ige`, `valor_ige`, `no_autorizacion_lma`, `valor_lma` | Novedades | **Nuevo** |
| Aportante (encabezado): clase, naturaleza, tipo, operador, rep. legal | `empresa` + `tercero` | Fase 1 + **nuevo** |

**Tres cosas que hay que entender antes de estimar:**

**Los IBC son distintos entre subsistemas.** No es una base multiplicada por cuatro tarifas. Un empleado en licencia no remunerada cotiza salud pero no pensión; uno con incapacidad tiene IBC de ARL distinto al de EPS. El motor hoy produce **una** base (y en la v1 del código, inflada con el auxilio — ver Fase 0). Hay que producir cuatro.

**Los días también son distintos.** `dias_eps` ≠ `dias_afp` ≠ `dias_arl` según las novedades del mes. Hoy hay un `dias_trabajados INT DEFAULT 30`.

**Las banderas de novedad son el corazón de PILA y son derivadas, no capturadas.** `ing` sale de comparar la fecha de inicio del contrato contra el período; `ret` de la fecha de retiro; `vsp` de detectar un cambio salarial (**requiere `contrato_salario_historial`, Fase 2**); `sln`, `ige`, `lma`, `vac_lr` de las novedades con sus pares de fechas; `tde`/`tae`/`tdp`/`tap` de cambios de afiliación (**requiere `contrato_afiliacion`, Fase 5.5**). Se calculan cruzando contrato + historial + afiliaciones + novedades contra el período.

- Tabla de equivalencias y validación de cruces: mapear catálogos internos a códigos del operador.
- Generación del archivo plano + descarga. Asíncrono (Fase 7).

**Realidad de esfuerzo:** en el ERP de referencia, el servicio de PILA tiene ~3.900 líneas (solo `consultarNovedades()` ocupa ~500), tres tablas, más equivalencias y validación de cruces — y eso **reutilizando** un motor de conceptos, un modelo de vinculaciones y un histórico salarial que ya existían. No es una fase de dos semanas.

**Dos vías para reducir el alcance, a investigar antes de escribir código:**

1. **Operador con API en vez de archivo plano.** Aportes en Línea, SOI y otros tienen integración. Generar el plano de ~120 columnas y lidiar con sus validaciones es donde se va el tiempo.
2. **¿Los clientes lo necesitan de nosotros?** Muchas PyME liquidan PILA digitando en el portal del operador, o lo hace su contador. Si ese es el caso de nuestra base, PILA puede no ser lo siguiente. **La nómina electrónica sí es obligatoria y no tiene alternativa manual razonable; PILA sí la tiene.** Esta es una decisión de producto, no técnica.

---

### Fase 7 — Asincronía y escala

- Mover `liquidarPeriodoCompleto()` y los envíos DIAN/PILA a jobs con estado y progreso consultable.
- Tabla `proceso_nomina` (estado, progreso, mensaje, usuario, timestamps) para que el frontend haga polling — equivalente a `nom_procesos_logs` + `ComProgressEvent` del ERP.
- Reversa de procesos: registrar quién procesó y quién reversó.

---

## 4. Orden y dependencias

```
Fase −1 (baseline dump)  ──► BLOQUEA TODO: sin BD desde cero no hay tests
   │
   ▼
Fase 0  (IBC correcto, topes, 1607, tests)   ─┐
Fase 1  (tercero completo + empleado→tercero) ─┤
Fase 2  (contrato + historial salarial)       ─┤──► Fase 5.5 ──► Fase 6
Fase 3  (conceptos tipificados)               ─┤   (afiliaciones)  (PILA)
Fase 4  (nomina_detalle + centros de costo)   ─┘

Fase 5  (nómina electrónica Factus)  ◄── requiere 1; se apoya fuerte en 3+4
Fase 7  (async)                      ◄── transversal, adelantable en cualquier momento
```

**PILA depende de las cinco fases anteriores.** No es la última por orden arbitrario: consume la salida de todas. Cualquier estimación que la trate como un módulo aislado está mal hecha.

**Nómina electrónica depende de la Fase 1** (datos fiscales y de identificación del trabajador) y **se apoya fuerte en 3+4**: la DIAN exige cada devengado y deducción desglosado por tipo, no un total. Sin `nomina_detalle` con conceptos tipificados, armar el payload es un `if` gigante e inmantenible.

**Paralelizable:** Fases 0 y 1 tocan cosas distintas y pueden ir a la vez. Fase 7 se adelanta si el volumen aprieta.

**Punto de no retorno:** las fases 1 y 2 deben completarse **antes** de meter clientes de nómina en producción. Después, cada cliente multiplica el costo del backfill — y los dos backfills delicados (nombres desagregados y reconciliación empleado↔tercero) requieren revisión humana registro por registro en los casos ambiguos.

**Orden (v8) — FIJADO.** Nómina sin clientes en producción todavía (ver sección 9), clientes por entrar de ambos perfiles.

```
−1 (baseline)                       ← bloquea todo: sin BD desde cero no hay tests
  → 1 + 2 + 3 (modelo)              ← la ventana barata; se cierra con el primer cliente
  → 0 (motor + tests)               ← una sola vez, sobre el modelo bueno
  → 4.5 (retefuente)                ← obligatoria; antes que 5
  → 5 (nómina electrónica)          ← Factus confirmado
  → 5.5 (afiliaciones) → 6 (PILA)   ← confirmada necesaria, no cancelable
  → 8 (prestaciones y liq. definitiva)
  → 9 (embargos)  → 10 (certificados)
  → 4 (capa proyectos)              ← clientes constructores confirmados
  → 7 (async)                       ← transversal, adelantable
```

**MÍNIMO PARA EL PRIMER CLIENTE:** `−1 → 1 → 2 → 3 → 0 → 4.5 → 5`. Sin retefuente y nómina electrónica no se puede liquidar legalmente en Colombia. PILA (6) puede ir después del arranque si el primer cliente tolera liquidarla manual un mes o dos; **prestaciones (8) también, salvo que el primer cliente entre en junio o diciembre** (prima) o tenga retiros.

**Dónde va la Fase 0:** después del modelo. Los bugs de IBC no están haciendo daño porque nadie liquida, y arreglarlos antes de la Fase 3 obliga a tocar el motor dos veces. **Esto se invierte el día que entre el primer cliente:** a partir de ahí, cualquier bug de cálculo es daño en curso y va primero.

Razonamiento del resto:

- **−1 primero, siempre.** Sin BD reconstruible no hay tests, y sin tests no se toca el motor.
- **El modelo antes que el motor.** Esto invierte el orden de las v1–v5, y la razón está en la sección 9: **no hay clientes en producción**. Las fases 1–3 nunca van a ser más baratas que ahora, y arreglar el motor primero significa tocarlo dos veces (una en la Fase 0, otra en la Fase 3 cuando llegue el catálogo de conceptos). Hacer el modelo primero y el motor una sola vez, bien.
- **Fase 0 después del modelo**, no antes. Los bugs de IBC son graves pero **no están haciendo daño**: nadie está liquidando en producción. La urgencia era "estamos liquidando mal cada mes" y esa premisa resultó falsa.
- **Retefuente antes de la Fase 5.** El XML de la DIAN lleva la retención como deducción. Sin ella se envían documentos incompletos. Hoy no tiene fase — ver sección 8.
- **Fase 5** es obligatoria por ley y aplica al 100% de la cartera.
- **Fase 4 baja.** La nómina debe servirle a todas las empresas, incluidas las que no manejan proyectos. Esta capa atiende a un subconjunto; el núcleo atiende a todos. Sigue siendo valiosa y arregla algo roto, pero después.
- **Fase 6 (PILA) al final, y es cancelable.** La más cara, depende de las cinco anteriores, y la digita el contador en el portal del operador. Confirmar el pendiente #5 antes de comprometerla.

---

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Fase 0 cambia montos ya liquidados | Definir con negocio: no tocar `PAGADO`; decidir explícitamente si se recalcula lo cerrado |
| Reconciliación empleado↔tercero con datos sucios | Script con reporte + revisión humana; **no** automatizar el emparejamiento ambiguo |
| Factus podría no soportar nómina electrónica | **Verificar antes de comprometer la Fase 5**; tener plan B de proveedor |
| Doble envío a la DIAN en reintentos | Consecutivo reservado + `uq_ne_consecutivo` + idempotencia en el job |
| Backfill de `constituye_ibc` mal asumido en `BONO` | Confirmar con contabilidad antes de aplicar V96 |
| PILA subestimada en esfuerzo | Referencia: ~3.900 líneas en el ERP comparado, **reutilizando** infraestructura que aquí no existe. Depende de 5 fases previas |
| Motor sin tests al refactorizar | Fase 0 **empieza** por los tests, no termina con ellos |
| Backfill de `nombre1/2`+`apellido1/2` por heurística | Reporte + revisión humana. Partir "DE LA ROSA" o nombres compuestos automáticamente **falla**. Hacerlo junto con la reconciliación de 1.b |
| Catálogo `entidad_seguridad_social` desactualizado | EPS se fusionan y liquidan. Necesita mantenimiento, no es seed de una sola vez. Global y centralizado justamente para poder actualizarlo una vez |
| Se construye PILA sin afiliaciones | Bloqueado por diseño: Fase 6 no arranca sin Fase 5.5 |
| Afiliación guardada como texto libre (repetir el bug de `banco`) | `contrato_afiliacion.tercero_id` es FK, nunca VARCHAR. Fase 1.d valida el patrón antes de replicarlo |
| Códigos de PILA divergentes entre empresas | El código vive en el catálogo global, no en el tercero por empresa. El cliente elige de lista, no digita |
| `empleado_arl` no dice a qué ARL | La migración **no puede inferirlo**: hay que capturar la entidad. Planear el dato faltante, no asumir backfill automático |

---

## 6. Lo que NO vamos a copiar del ERP de referencia

Explícito, para que no se cuele en una discusión futura:

- **`eval()` de fórmulas en base64 almacenadas en BD.** Es ejecución de código arbitrario para cualquiera que edite un concepto, no se puede testear, no se versiona en git y hace imposible entender un cálculo leyendo el repo. Copiamos el *requisito* (conceptos parametrizables por cliente y por ley), no la implementación. Ver Fase 3.
- **Banderas `tinyInteger` 1=Sí / 2=No.** Nuestros `BOOLEAN` y enums con `CHECK` son mejores.
- **FK como enteros sueltos sin `REFERENCES`.** Nuestra integridad la declara la BD.
- **Servicios de 3.971 líneas** con la orquestación, el cálculo, la validación y la persistencia mezclados.
- **Duplicar datos en JSON y en tabla pivote a la vez** (su `nom_vinculaciones.centros_costo` JSON conviviendo con `com_centro_costo_nom_vinculacion`, sin que quede claro cuál está vivo).

---

### Fase 8 — Prestaciones sociales y liquidación definitiva

**Hoy:** `liquidacion_prestacion` (V76) declara en su CHECK los tipos `PRIMA, VACACIONES, CESANTIAS, INTERESES_CESANTIAS, LIQUIDACION_DEFINITIVA, INDEMNIZACION`, pero en la práctica solo se usan PRIMA y VACACIONES. Y `nomina` solo **provisiona** cesantías mensualmente; nunca las liquida ni las consigna.

- `V112__prestaciones_completas.sql`
  ```sql
  ALTER TABLE liquidacion_prestacion
      ADD COLUMN contrato_id        BIGINT REFERENCES contrato_laboral(id),
      ADD COLUMN fondo_cesantias_id BIGINT REFERENCES tercero(id),  -- rol AFP/fondo
      ADD COLUMN base_prestacional  NUMERIC(15,2) NOT NULL DEFAULT 0,
      ADD COLUMN dias_liquidados    INT           NOT NULL DEFAULT 0,
      ADD COLUMN causa_retiro       VARCHAR(40),
      ADD COLUMN nomina_id          BIGINT REFERENCES nomina(id);

  -- Detalle por concepto de la liquidación definitiva
  CREATE TABLE liquidacion_prestacion_detalle (
      id                     BIGSERIAL     PRIMARY KEY,
      liquidacion_id         BIGINT        NOT NULL REFERENCES liquidacion_prestacion(id) ON DELETE CASCADE,
      concepto_id            BIGINT        NOT NULL REFERENCES concepto_nomina(id),
      base                   NUMERIC(15,2) NOT NULL DEFAULT 0,
      dias                   INT           NOT NULL DEFAULT 0,
      valor                  NUMERIC(15,2) NOT NULL DEFAULT 0,
      traza                  JSONB
  );
  ```

**Alcance:**
- **Cesantías**: liquidación anual + consignación al fondo (por eso `fondo_cesantias_id`), e intereses del 12% pagaderos en enero.
- **Liquidación definitiva**: al terminar el contrato — cesantías proporcionales, intereses, prima proporcional, vacaciones proporcionales, y retefuente sobre indemnización si aplica.
- **Indemnización** por despido sin justa causa: el cálculo depende del tipo de contrato y del tiempo servido. `causa_retiro` la determina.
- **La base prestacional no es el salario**: incluye auxilio de transporte, horas extra, comisiones y todo lo salarial del período de referencia. Es distinta de la base de seguridad social (Fase 0) y de la base de retefuente (Fase 4.5). **Tres bases distintas — no confundirlas.**

**Depende de:** Fase 2 (historial salarial: sin él no se puede promediar el salario del último año), Fase 3 (conceptos), Fase 4.5 (retefuente sobre indemnización).

---

### Fase 9 — Embargos y descuentos judiciales

**Hoy:** una novedad plana de tipo `EMBARGO` con `valor_unitario`. No hay expediente, ni juzgado, ni prelación, ni saldo.

- `V113__embargos.sql`
  ```sql
  CREATE TABLE embargo (
      id               BIGSERIAL     PRIMARY KEY,
      empresa_id       INT           NOT NULL REFERENCES empresa(id),
      contrato_id      BIGINT        NOT NULL REFERENCES contrato_laboral(id),
      expediente       VARCHAR(60)   NOT NULL,
      tipo             VARCHAR(30)   NOT NULL,
          -- ALIMENTOS | COOPERATIVA | JUDICIAL_ORDINARIO | FISCAL
      prioridad        INT           NOT NULL DEFAULT 1,
      juzgado_id       BIGINT        REFERENCES tercero(id),
      demandante_id    BIGINT        REFERENCES tercero(id),
      valor_total      NUMERIC(15,2),
      porcentaje       NUMERIC(5,2),
      saldo            NUMERIC(15,2) NOT NULL DEFAULT 0,
      fecha_inicio     DATE          NOT NULL,
      fecha_fin        DATE,
      estado           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
      CONSTRAINT chk_embargo_tipo   CHECK (tipo IN ('ALIMENTOS','COOPERATIVA','JUDICIAL_ORDINARIO','FISCAL')),
      CONSTRAINT chk_embargo_estado CHECK (estado IN ('ACTIVO','SUSPENDIDO','TERMINADO'))
  );

  CREATE TABLE embargo_descuento (
      id          BIGSERIAL     PRIMARY KEY,
      embargo_id  BIGINT        NOT NULL REFERENCES embargo(id),
      nomina_id   BIGINT        NOT NULL REFERENCES nomina(id),
      valor       NUMERIC(15,2) NOT NULL,
      saldo_antes NUMERIC(15,2) NOT NULL,
      saldo_despues NUMERIC(15,2) NOT NULL
  );
  ```

**La prelación es lo difícil, no la tabla.** Reglas legales: los embargos por alimentos van primero y pueden llegar al 50% del salario; los demás se limitan a la quinta parte de lo que exceda el SMMLV. Con varios embargos concurrentes hay que aplicarlos **en orden de prioridad hasta agotar el límite legal**, y los que no caben se difieren al mes siguiente. `embargo_descuento` deja el rastro y `saldo` se va consumiendo.

`juzgado_id` y `demandante_id` apuntan a `tercero` — mismo patrón que EPS/ARL (Fase 5.5). Al juzgado se le gira.

---

### Fase 10 — Certificados y desprendible

Depende enteramente de la Fase 3.b (`nomina_detalle` con `traza`): sin el desglose no hay nada que imprimir.

- **Desprendible de pago**: el detalle por concepto de una nómina. Ya existe `CuentaPdfService` como precedente de generación de PDF.
- **Certificado de ingresos y retenciones**: obligación anual ante la DIAN (formato 220). Agrega ingresos y retenciones del año por empleado.
- **Certificado laboral**: cargo, salario, fechas. Trivial una vez existe `contrato_laboral`.

- `V114__historico_certificados.sql` — registrar qué se emitió, a quién y cuándo (el ERP tiene `nom_historicos_certificados`). Un certificado emitido es un documento con valor probatorio: guardar el **snapshot**, no regenerarlo (mismo criterio de la sección sobre FK vs. literal).

**Salario integral (70/30):** no es fase propia. Es un modo de cálculo en el motor — `contrato_laboral.es_salario_integral` (Fase 2) hace que el IBC sea el 70% y que no se provisionen prestaciones. Va dentro de la Fase 0 o de la 3, según cuándo se toque el motor.

---

## 9. Estado de la cartera — por qué el orden cambió en v6

`docs/segmentacion_cartera.sql` se corrió para resolver el pendiente de priorización de la Fase 4. Resultado:

| Segmento | Clientes | % | Ingreso mensual | Empleados | Proyectos | Días asistencia |
|---|---|---|---|---|---|---|
| SIN_ACTIVIDAD_90D | 8 | 88.9% | $50.000 | 0 | 0 | 0 |
| PROYECTOS_CON_ASISTENCIA | 1 | 11.1% | $0 | 3 | 1 | 5 |

*(Corrida contra BD local — conservada como referencia del formato de salida, no como dato de producción.)*

### El matiz que importa: nómina vacía ≠ todo vacío

Que nadie use nómina **no** significa que la base esté vacía. Los clientes de POS/facturación existen, así que `tercero` y `cuenta_bancaria` **sí tienen datos de producción**. Esto parte el plan en dos:

| Cambio | Costo real |
|---|---|
| `empleado` → `tercero` (1.b) | **Gratis** — no hay empleados |
| Contrato como entidad (2) | **Gratis** — no hay nóminas |
| Catálogo de conceptos (3) | **Gratis** |
| `nomina_detalle` (3.b) | **Gratis** |
| Corregir IBC, topes, 1607 (0) | **Gratis** — nada liquidado que recalcular |
| Dimensiones de proyecto (4.a) | **Gratis** |
| **Partir `nombres`/`apellidos` (1.a)** | **NO gratis** — hay clientes y proveedores cargados. Reconciliación con revisión humana **se mantiene** |
| **`cuenta_bancaria.banco` → FK (1.d)** | **NO gratis** — hay cuentas reales con banco en texto libre |
| **Roles a tabla (1.c)** | **NO gratis** — hay terceros con roles asignados |

**Todo lo de nómina es gratis. Lo de `tercero` no.** Las únicas reconciliaciones que sobreviven son las de la Fase 1 sobre datos de POS.

> # Estado real (v8) — confirmado por el equipo
>
> **La nómina no la usa nadie todavía.** Está desarrollada pero sin clientes en producción. Van a entrar.
>
> **Clientes comprometidos, dos perfiles confirmados:**
> - Constructoras: usan proyectos, frentes y asistencia por frente.
> - Empresas normales: sin proyectos ni frentes, empleados y ya.
>
> Esto confirma dos decisiones del plan:
> 1. **El núcleo de nómina debe funcionar solo**, sin la capa de proyectos (ambos perfiles existen y hay que atender a los dos).
> 2. **La Fase 4 no es especulativa** — hay clientes reales que la necesitan. Pero va después del núcleo.
>
> La corrida de `segmentacion_cartera.sql` fue contra la BD local y no refleja producción; **se mantiene como herramienta** para cuando haya cartera de nómina que medir, pero sus números no se usan aquí.

### La ventana está abierta, y se cierra con el primer cliente

El plan advertía: *"las fases 1 y 2 deben completarse antes de meter clientes de nómina en producción; después, cada cliente multiplica el costo del backfill"*.

**No se ha cruzado.** Y los clientes están por entrar. **Este es el momento**, y no va a volver.

Se caen dos pendientes:
- **#2 (¿los `BONO` son salariales?)**: no hay ninguno cargado. El `CHECK` y el default se definen desde cero, bien.
- **#3 (¿recalcular períodos liquidados?)**: no hay ninguno liquidado.

Los bugs de IBC **no han hecho daño** — nadie liquidó. Pero hay que arreglarlos **antes del primer cliente**, no después. La urgencia no es reparar; es no estrenar con ellos.

**Por eso el modelo va antes que el motor:** arreglar el motor ahora y volver a tocarlo en la Fase 3 (catálogo de conceptos) es hacer el trabajo dos veces. Con la ventana abierta, conviene dejar el modelo bien y escribir el motor una sola vez encima.

### Lo que esto define sobre la Fase 4

**Deja de ser especulativa.** Hay clientes constructores comprometidos que usan proyectos, frentes y asistencia. La fase se va a necesitar.

Pero **el orden no cambia**: también hay clientes sin proyectos, y el principio manda — la nómina debe servirle a todos. Núcleo primero (atiende al 100%), capa de proyectos después (atiende a un subconjunto). La diferencia es que ahora sabemos que esa capa **sí se va a construir**, no que "quizá".

---

## 8. Fuera de alcance

**v7: las brechas que antes estaban aquí se convirtieron en fases reales** — retefuente (4.5), prestaciones y liquidación definitiva (8), embargos (9), certificados y desprendible (10), salario integral (dentro de 0/3). Con eso, el plan **sí** apunta a una nómina colombiana completa.

Lo que queda genuinamente fuera:

| Falta | Por qué queda fuera |
|---|---|
| **Dotación** (Ley 11/1984) | Depende del perfil de clientes. Barato de agregar si alguien lo pide: es un concepto más del catálogo (Fase 3) |
| **Retención por ingresos de trabajadores independientes** | Otro régimen. Solo si se liquidan contratos de prestación de servicios |
| **Aportes voluntarios AFC/AFP** más allá de su efecto en retefuente | El descuento sí (Fase 4.5); la gestión del producto financiero no |
| **Sindicatos, aportes a masa** | Del dominio del ERP de referencia (sector público). Solo si aparece un cliente |

### Lo que explícitamente no aplica

- **Módulo de Contratación** (en el sentido del ERP de referencia: `ctr_contratos`, `ctr_modalidades`, `ctr_plantillas_clausulas`, estudios previos, requisitos habilitantes). Eso es contratación estatal/procurement, no laboral. No aplica a un POS/ERP de PyME. **No confundir con `contrato_laboral` de la Fase 2**, que sí es el vínculo laboral del empleado.
- Multi-vinculación docente, cátedra, escalafón: dominio universitario del ERP de referencia.

---

## 7. Pendientes de decisión

Requieren respuesta de negocio antes de ejecutar:

1. ¿Algún cliente real necesita multi-vínculo simultáneo, o la Fase 2 se reduce a historial salarial? (el historial es obligatorio en cualquier caso). **Preguntar a los clientes que están por entrar — es el momento.**
2. ~~¿Los `BONO` cargados son salariales?~~ **RESUELTO (v8):** no hay ninguno cargado. Se define desde cero.
3. ~~¿Se recalculan los períodos ya liquidados?~~ **RESUELTO (v8):** no hay ninguno liquidado.
4. ~~¿Factus soporta nómina electrónica?~~ **RESUELTO (v7): sí, emite todo.** Fase 5 destrabada. Queda verificar el contrato específico del endpoint al implementar.
5. ~~¿Los clientes necesitan PILA?~~ **RESUELTO (v7): sí.** Hay clientes que pagan toda su seguridad social a través del sistema. **La Fase 6 es alcance firme, no cancelable.** Con eso, la Fase 5.5 (afiliaciones) también deja de ser opcional.
6. Si se hace PILA: ¿qué operador usan? ¿Tiene API o toca archivo plano? (dimensiona la Fase 6 — sigue abierto)
6.b ~~Correr `segmentacion_cartera.sql` contra producción~~ **RESUELTO (v8):** la nómina no la usa nadie todavía. Clientes por entrar de dos perfiles (constructoras con proyectos/frentes, y empresas sin proyectos). Orden fijado.
11. **¿Cuándo entra el primer cliente de nómina?** Define cuánto margen hay para el modelo antes de que la ventana se cierre, y si prestaciones (Fase 8) debe adelantarse (prima en junio/diciembre).
12. **¿El primer cliente tolera liquidar PILA manual un mes o dos?** Si sí, la Fase 6 puede ir después del arranque. Si no, entra al mínimo viable y hay que adelantar 5.5 + 6.
13. **¿Cuántos terceros hay en producción?** Dimensiona la única reconciliación que sobrevive (partir `nombres`/`apellidos` en `tercero`, Fase 1.a).
7. `responsabilidad_fiscal` como `String`: ¿ya se resolvió el envío de múltiples códigos a Factus en facturación, o hay que migrarlo a lista?
8. ¿La tarifa de ARL se resuelve por contrato o por centro de trabajo? (condiciona dónde vive `nivel_riesgo` en la Fase 5.5)
9. **¿Quién mantiene el catálogo nacional de EPS/AFP/CCF/ARL — nosotros o cada cliente?** Si es cada cliente, se cae el argumento del catálogo global (`entidad_seguridad_social` sin `empresa_id`) y se vuelve a solo-tercero, asumiendo el riesgo de códigos divergentes y de rechazo del archivo por empresa. **Recomendación: lo mantenemos nosotros** — es un catálogo nacional, cambia poco, y el costo de que un cliente lo digite mal recae en soporte.
10. Roles: ¿tabla `tercero_rol` (1.c) o seguir agregando booleanos? (recomendación: tabla, ahora que son 4 y no 8)

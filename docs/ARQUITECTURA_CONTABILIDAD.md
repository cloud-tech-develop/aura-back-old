# Arquitectura de desarrollo — Módulo contable Aura Nube

> Documento NORMATIVO. Toda tarea del plan (`docs/PLAN_DESARROLLO_CONTABILIDAD.md`)
> debe cumplir estas reglas. Complementa el diseño funcional
> (`docs/DISENO_CONTABILIDAD_AVANZADA.md`). Cuando una regla de aquí choque con código
> existente, gana esta regla PARA CÓDIGO NUEVO; el código viejo se migra por
> estrangulamiento, nunca big-bang.

---

## 1. Diagnóstico del código actual (2026-07-08)

- Monolito Spring Boot por **capas técnicas planas**: `controllers/ services/
  services/implementations/ repositories/ dto/ entity/ event/ utils/`.
- El motor contable es una **god class**: `ContabilidadAutoServiceImpl` = 1.222 líneas,
  16 métodos `generarDesdeXxx` que comparten el 60% de su cuerpo (período abierto,
  resolución de cuentas, armado de líneas, validación, persistencia, idempotencia).
- **7 pares evento/listener casi idénticos** (`VentaContabilizableEvent`,
  `CompraContabilizableEvent`, `OperacionContabilizableEvent`, `AbonoContabilizableEvent`,
  `DevolucionContabilizableEvent`, `MovimientoCajaContabilizableEvent`,
  `ContabilidadReversaEvent`) que solo difieren en el nombre y el switch del listener.
- `spring.jpa.hibernate.ddl-auto=update` **activo en paralelo con Flyway** (V1–V84):
  el esquema real depende del orden de arranque. Deuda crítica.
- **Cero tests** (`src/test` vacío). El cuadre se valida solo en runtime.

El problema arquitectónico central: cada etapa nueva (E2–E11) agrega generadores,
resolvers y eventos. Con la estructura actual, la god class crecería a 3.000+ líneas y
cada cambio arriesga los 16 flujos existentes. La arquitectura de abajo hace que **cada
etapa agregue clases nuevas en vez de modificar las existentes** (Open/Closed).

---

## 2. Decisión arquitectónica (ADR-001)

**Monolito modular + Clean Architecture pragmática, solo para el módulo contable,
por estrangulamiento.**

- NO microservicios: un solo deploy, un solo esquema. La modularidad es de paquetes.
- NO rewrite: `ContabilidadAutoServiceImpl` sigue funcionando; cada etapa extrae los
  generadores que toca hacia la estructura nueva hasta vaciarlo (patrón strangler fig).
- El resto de la app (ventas, compras, nómina) NO se reorganiza; solo se ajustan sus
  puntos de contacto (publicar evento estándar).

### Regla de dependencias (la única regla inviolable)

```
web  ──►  application  ──►  domain  ◄──  infrastructure
```

- `domain` NO importa Spring, JPA, ni nada de fuera del paquete. Java puro.
- `application` orquesta casos de uso; conoce el dominio y PUERTOS (interfaces).
- `infrastructure` implementa los puertos (JPA, Flyway, jobs, adaptadores).
- `web` solo traduce HTTP ⇄ casos de uso (DTOs, validación de entrada).
- Prohibido: que una entity JPA viaje hasta el controller; que el dominio conozca
  `*Entity`; que un generador haga queries directas.

---

## 3. Estructura de paquetes objetivo

Todo código NUEVO del plan va bajo `com.cloud_technological.aura_pos.contabilidad`:

```
contabilidad/
├── domain/                          ← Java puro, 100% testeable sin Spring
│   ├── model/
│   │   ├── Asiento.java             (raíz de agregado: líneas, fecha, origen, estado)
│   │   ├── Partida.java             (cuentaId, débito, crédito, terceroId, ccId, proyectoId)
│   │   ├── OrigenDocumento.java     (tipoOrigen + origenId, value object)
│   │   └── EstadoAsiento.java       (BORRADOR/CONTABILIZADO/ANULADO/REVERTIDO)
│   ├── AsientoBuilder.java          (API fluida; balancea y valida al build())
│   └── ReglasAsiento.java           (invariantes: cuadre, no-negativos, mín. 2 partidas)
│
├── application/
│   ├── generador/
│   │   ├── GeneradorAsiento.java    (puerto/estrategia: soporta(tipo) + generar(ctx))
│   │   ├── GeneradorRegistry.java   (mapa tipoOrigen → generador; Spring lo puebla solo)
│   │   ├── VentaGenerador.java      ┐
│   │   ├── CompraGenerador.java     │ una clase por origen; las 16 actuales
│   │   ├── NominaGenerador.java     │ migran aquí etapa a etapa
│   │   └── ...                      ┘
│   ├── resolucion/
│   │   ├── ResolucionCuentas.java        (puerto: resolver(empresa, concepto))
│   │   ├── ResolucionCuentaPago.java     (puerto: banco→formaPago→fallback)
│   │   └── ResolucionCuentaProducto.java (puerto E4: producto→categoría→config)
│   ├── ContabilizarDocumentoUseCase.java (orquesta: período→generador→estado→persistir→log)
│   ├── ReversarDocumentoUseCase.java
│   └── port/
│       ├── AsientoRepositorio.java       (puerto de persistencia del agregado)
│       ├── PeriodoContable.java          (puerto: períodoAbierto(empresa, fecha))
│       └── PostingLog.java               (puerto: registrar éxito/fallo)
│
├── infrastructure/
│   ├── persistence/                 (adapters JPA: implementan los puertos con las
│   │                                 entities/repos EXISTENTES — no se duplican tablas)
│   ├── event/
│   │   ├── DocumentoContabilizableEvent.java  (EL evento único, ver §5)
│   │   └── ContabilizacionListener.java       (EL listener único)
│   └── job/                         (amortización diferidos, causaciones, deterioro)
│
└── web/                             (controllers + DTOs del módulo)
```

Los `*Entity`, `*JPARepository`, DTOs y controllers contables actuales se quedan donde
están y se referencian desde `infrastructure`/`web`; se mueven solo cuando una etapa
los toque (boy scout rule).

---

## 4. El corazón: generadores como estrategias (ADR-002)

Cada origen contable es una clase pequeña (< 150 líneas) que SOLO decide partidas.
Todo lo repetido vive en el caso de uso.

```java
public interface GeneradorAsiento {
    String tipoOrigen();                                  // "VENTA", "COMPRA", "NOMINA"...
    Asiento generar(ContextoContabilizacion ctx);          // dominio puro adentro
}

@Component
class VentaGenerador implements GeneradorAsiento {
    // inyecta PUERTOS (ResolucionCuentas, ResolucionCuentaPago, lector de la venta)
    public Asiento generar(ContextoContabilizacion ctx) {
        VentaContable venta = ventas.cargar(ctx.origenId(), ctx.empresaId()); // proyección, no Entity
        AsientoBuilder b = Asiento.builder(ctx.origen(), venta.fecha());
        venta.pagos().forEach(p ->
            b.debito(cuentaPago.resolver(ctx.empresaId(), p.metodo(), p.cuentaBancariaId()), p.monto()));
        if (venta.saldoPendiente().signum() > 0)
            b.debito(cuentas.resolver(ctx.empresaId(), CLIENTES), venta.saldoPendiente(), venta.terceroId());
        b.credito(cuentas.resolver(ctx.empresaId(), INGRESOS_VENTAS), venta.baseGravable())
         .credito(cuentas.resolver(ctx.empresaId(), IVA_GENERADO), venta.impuestos());
        venta.costoTotal().ifPresent(c -> b
            .debito(cuentas.resolver(ctx.empresaId(), COSTO_VENTAS), c)
            .credito(cuentas.resolver(ctx.empresaId(), INVENTARIO), c));
        return b.build();   // build() valida cuadre — imposible construir descuadrado
    }
}
```

El caso de uso concentra el ciclo transversal UNA sola vez:

```java
@Transactional(propagation = REQUIRES_NEW)
public void ejecutar(String tipo, Long origenId, Integer empresaId, Long usuarioId) {
    if (repositorio.existePorOrigen(tipo, origenId, empresaId)) return;   // idempotencia
    var periodo = periodos.abiertoPara(empresaId, LocalDate.now());       // período
    var asiento = registry.para(tipo).generar(ctx);                        // estrategia
    asiento.conEstado(config.modo(empresaId));                             // BORRADOR|CONTABILIZADO (E3)
    repositorio.guardar(asiento, periodo);                                 // persistir
    postingLog.exito(asiento);                                             // auditoría
}
```

**Consecuencia para el plan:** E4 (categorías) solo toca `VentaGenerador`/`CompraGenerador`
y agrega un resolver; E6 agrega `AnticipoGenerador`, `DiferidoGenerador`…; nadie vuelve
a abrir una clase de 1.200 líneas. La migración de los 16 generadores actuales se hace
gradual: **E1 monta el esqueleto (builder + registry + use case + evento único) y migra
UN generador (venta) como piloto**; cada etapa siguiente migra los que toque.

---

## 5. Un solo evento, un solo listener (ADR-003)

Los 7 pares evento/listener se colapsan en:

```java
public record DocumentoContabilizableEvent(
    String tipoOrigen, Long origenId, Integer empresaId, Long usuarioId) {}

public record DocumentoReversableEvent(
    String tipoOrigen, Long origenId, Integer empresaId, Long usuarioId) {}
```

Un `ContabilizacionListener` (`@TransactionalEventListener(AFTER_COMMIT)`) despacha al
use case; el fallo va a `PostingLog` + `ErrorLogService` y JAMÁS propaga (la venta no se
cae porque la contabilidad falle). Los publishers de los servicios de negocio
(`VentaServiceImpl`, `CompraServiceImpl`, …) cambian a este evento cuando su etapa los
toque; los eventos viejos se marcan `@Deprecated` y se borran al final (E11).

---

## 6. Estándares de código (clean code, obligatorios en PR)

### Dinero y números
1. **`BigDecimal` siempre** para dinero; jamás `double/float`. Escala 2, `RoundingMode.HALF_UP`
   centralizado (constante en `ReglasAsiento`). Comparar con `compareTo`, nunca `equals`.
2. Nulos monetarios: helper único `nz(BigDecimal)` (ya existe) — no repetir ternarios.

### Cuentas y conceptos
3. **Prohibido hardcodear códigos de cuenta** en generadores/servicios. Todo pasa por
   `ConceptoContable` + resolvers. Un código PUC literal en un `.java` (fuera del enum y
   los seeds) rechaza el PR.
4. Concepto nuevo = entrada en `ConceptoContable` + seed idempotente + guardarraíl de
   clase (prefijo permitido) en la config.

### Transacciones y eventos
5. Generadores: `REQUIRES_NEW` (aislar del documento origen). Cierre de período:
   `REQUIRED` (debe abortar el cierre si falla). No hay tercera opción sin ADR.
6. Publicar eventos SOLO al final del método de negocio, con la transacción aún abierta;
   escuchar SOLO con `AFTER_COMMIT`.
7. Todo generador es **idempotente por (tipoOrigen, origenId, empresaId)** — el use case
   lo garantiza, el generador no se preocupa.

### Diseño
8. Métodos ≤ 40 líneas; clases de application ≤ 200. Si un generador crece más, está
   decidiendo cosas que van en un resolver o en dominio.
9. Constructor injection siempre (`final` + Lombok `@RequiredArgsConstructor`); nada de
   `@Autowired` en campos.
10. Nombres en español de dominio contable (asiento, partida, devengo, período) —
    consistente con el código existente; inglés solo para términos técnicos (registry,
    builder, port).
11. DTOs de entrada con Bean Validation (`@NotNull`, `@Positive`); el dominio revalida
    sus invariantes (defensa en profundidad).
12. Comentarios solo para restricciones no evidentes (norma DIAN, artículo del C.Cio,
    por qué un redondeo); nunca para narrar el código.

### Manejo de errores
13. Excepciones de negocio tipadas (`PeriodoCerradoException`,
    `AsientoDescuadradoException`, `CuentaNoParametrizadaException`) → handler HTTP
    existente las traduce; nunca `RuntimeException` genérica con string.
14. El mensaje al usuario dice QUÉ configurar ("El concepto IVA_GENERADO no tiene cuenta
    parametrizada para la empresa X"), no un stacktrace.

---

## 7. Estrategia de testing (ADR-004 — de cero tests a pirámide mínima)

La separación domain/application existe PARA esto: los generadores se prueban sin DB.

1. **Unit de dominio** (rápidos, sin Spring): `AsientoBuilder` rechaza descuadre;
   `ReglasAsiento` cubre bordes (cero, negativos, redondeo de IVA en líneas).
2. **Unit de generadores** (puertos mockeados): por cada generador, mínimo
   contado / crédito / mixto / borde. El assert central SIEMPRE:
   `Σdébitos == Σcréditos` + cuentas esperadas por concepto.
3. **Golden files**: por flujo, un YAML/JSON en `src/test/resources/asientos-esperados/`
   con el asiento esperado (cuenta, débito, crédito, tercero) — la matriz de E0 se
   codifica aquí y queda como regresión permanente, no como prueba manual de una vez.
4. **Integración por flujo** (`@SpringBootTest` + Testcontainers PostgreSQL): venta
   completa → evento → asiento en BD; una por etapa nueva. Smoke, no exhaustivo.
5. **Regla de PR**: código nuevo de contabilidad sin test de cuadre no se mergea.
   Los 16 generadores legacy adquieren tests AL MIGRARSE, no antes.

## 8. Persistencia y esquema (ADR-005)

1. **Apagar `ddl-auto=update`** → `validate`. Es la deuda más peligrosa del proyecto:
   hoy el esquema depende de qué corre primero (Hibernate o Flyway). Paso previo:
   generar la migración de sincronización (diff del esquema actual vs entities) para
   que `validate` pase limpio. Hacerlo en E1, antes de crear tablas nuevas.
2. Todo cambio de esquema = migración Flyway versionada (V85+), **aditiva** (no DROP de
   columnas en uso; deprecación en dos releases).
3. Seeds idempotentes (`INSERT ... ON CONFLICT DO NOTHING` o check en servicio) — patrón
   ya usado en `seedPUC`/`seedDefaults`.
4. Los asientos guardan `cuenta_id` **resuelto al momento del posting** (ya es así):
   los reportes jamás re-resuelven configuración — la historia es inmutable.
5. Índices obligatorios en tablas nuevas: `(empresa_id, ...)` siempre primero
   (multi-tenant por columna).

## 9. Registro de decisiones (ADR)

Crear `docs/adr/` con formato corto (contexto → decisión → consecuencias). Iniciales:

| ADR | Decisión |
|---|---|
| 001 | Monolito modular + clean architecture pragmática por estrangulamiento (§2) |
| 002 | Generadores de asiento como estrategias + registry + builder (§4) |
| 003 | Evento único de contabilización con listener AFTER_COMMIT (§5) |
| 004 | Pirámide de tests con golden files de asientos (§7) |
| 005 | Flyway como única fuente de esquema; ddl-auto=validate (§8) |
| 006 | Parametrizar destinos, nunca lógica; guardarraíles por clase PUC (decisión de producto) |

Toda desviación futura = nuevo ADR, no un cambio silencioso.

## 10. Checklist de PR (copiar en la descripción)

```
[ ] Regla de dependencias respetada (domain sin Spring/JPA; entity no sale a web)
[ ] Sin códigos PUC hardcodeados fuera de ConceptoContable/seeds
[ ] BigDecimal + HALF_UP; nz() para nulos
[ ] Generador idempotente, REQUIRES_NEW, evento AFTER_COMMIT, fallo → PostingLog
[ ] Reversa por contraasiento disponible para todo documento anulable
[ ] Test de cuadre (unit) + golden file actualizado
[ ] Migración Flyway aditiva con índice (empresa_id, ...)
[ ] Concepto nuevo con seed + guardarraíl de clase
[ ] Excepciones tipadas con mensaje accionable
[ ] Métodos ≤40 líneas; sin duplicar lógica del use case en generadores
```

## 11. Mapa arquitectura ⇄ plan de desarrollo

| Etapa | Trabajo arquitectónico incluido |
|---|---|
| E0 | Escribir los golden files de la matriz de validación (base de §7.3) |
| E1 | **Fundación**: esqueleto contabilidad/ (builder, registry, use case, evento único), migrar `VentaGenerador` como piloto, ddl-auto→validate, ADRs 001–006, excepciones tipadas |
| E2 | Migrar generadores de pagos/abonos al registry; `ResolucionCuentaPago` como puerto |
| E3 | `EstadoAsiento` en dominio; `PostingLog` puerto+adapter; use case aplica modo |
| E4 | `ResolucionCuentaProducto` (cadena de responsabilidad); migrar Compra/Devolución |
| E5 | Puerto de impuestos; migrar el resto de generadores de documentos |
| E6 | Generadores nuevos nacen YA en la estructura (Anticipo, Diferido, Causación, Deterioro); jobs en infrastructure/job |
| E7 | `Partida` += proyectoId/frenteId (dominio) + propagación en generadores |
| E8–E11 | Solo clases nuevas sobre la fundación; al cerrar E11, borrar eventos legacy y `ContabilidadAutoServiceImpl` queda vacío → se elimina |

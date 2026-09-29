# Qué necesita el frontend — módulo de nómina

Compañero de `PLAN_MIGRACION_NOMINA.md` y `ESTADO.md`.

---

## Advertencia: el backend todavía no expone esto

**Ninguno de los 8 servicios nuevos tiene controller.** El backend tiene la lógica, pero **no hay endpoints**. El frontend no puede consumir nada de esto hoy.

Este documento sirve para dos cosas: dimensionar el trabajo de frontend, y decidir qué endpoints hay que escribir primero.

| Servicio nuevo | Controller | Qué queda inaccesible |
|---|---|---|
| `ContratoLaboralService` | ❌ | Contratos, historial salarial, renovaciones |
| `ConceptoNominaService` | ❌ | Catálogo de conceptos |
| `AfiliacionService` | ❌ | EPS/AFP/CCF/ARL |
| `GeneradorPila` | ❌ | Generar PILA |
| `CertificadoService` | ❌ | Desprendible, certificado de ingresos |
| `ProcesoNominaService` | ❌ | Polling de procesos async |
| `NominaElectronicaService` | ❌ | Nómina electrónica (en pausa igual) |
| `TerceroRolService` | ❌ | Roles de tercero |

---

## 1. Cambios en lo que YA existe

### `TerceroController` — campos nuevos (aditivos, no rompen)

`CreateTerceroDto` / `UpdateTerceroDto` / `TerceroDto` aceptan y devuelven:

```
nombre1, nombre2, apellido1, apellido2      ← IMPORTANTE, ver abajo
fechaNacimiento, sexo                        ← requeridos por PILA
fechaExpedicionDocumento, municipioExpedicionId
nombreComercial
representanteLegalNombre, representanteLegalDocumento
esAutoretenedorIca, esAutoretenedorFuente, declarante
bancoTerceroId, tipoCuenta, numeroCuenta
roles: string[]                              ← solo en TerceroDto (lectura)
```

**El cambio de UX que más importa:** el formulario debe pedir **cuatro campos de nombre separados**, no dos. La DIAN (nómina electrónica) y la UGPP (PILA) exigen primer nombre, segundo nombre, primer apellido y segundo apellido **por separado**. Partirlos en el backend por heurística falla con "DE LA ROSA", nombres de una palabra, o razón social metida en el campo de nombres.

`nombres`/`apellidos` siguen existiendo y funcionando, pero están `@Deprecated`.

**`autoRetenedor` se dividió en dos:** `esAutoretenedorIca` y `esAutoretenedorFuente`. Son autorretenciones distintas — se puede ser de renta y no de ICA. El campo viejo sigue, pero el formulario debería mostrar los dos nuevos.

**Los selectores no cambian de contrato:** `/clientes`, `/proveedores`, `/bancos` siguen igual. Por dentro ahora leen `tercero_rol`.
Falta exponer un `/por-rol/{rol}` genérico — es el único camino para los roles nuevos (EPS, AFP, CCF, ARL).

### `EmpleadoController` — campos nuevos

`CreateEmpleadoDto` acepta:

```
terceroId                                    ← opcional (ver abajo)
nombre1, nombre2, apellido1, apellido2
```

**`terceroId` es opcional a propósito.** Si no viene, el backend busca el tercero por documento o lo crea. El front sigue funcionando sin cambios.

**Lo ideal es que el front migre a un selector de tercero** y mande `terceroId`. Así el usuario ve si la persona ya existe (por ejemplo, si ya es proveedor) en vez de crear un duplicado.

**Ojo con el 409:** si hay dos terceros con el mismo documento, `crear` responde `CONFLICT` pidiendo que se resuelva el duplicado o se mande `terceroId`. El front debe manejar ese caso.

### `NominaController` — un método nuevo

```
POST /liquidar/{periodoId}/empleado/{empleadoId}   ← sigue, ahora @Deprecated
```

Internamente resuelve el contrato principal del empleado. **Con multi-vínculo eso es ambiguo**: si una persona tiene dos contratos, ¿cuál se liquida?

Falta exponer:
```
POST /liquidar/{periodoId}/contrato/{contratoId}   ← NominaService.liquidarContrato()
```

El front debería migrar a ese cuando exista.

---

## 2. Endpoints que faltan escribir

Ordenados por lo que desbloquean.

### 2.a Contratos — bloquea el alta de empleados bien hecha

```
GET    /contrato/empleado/{empleadoId}         → contratos activos
GET    /contrato/{id}
POST   /contrato                               → crear
PUT    /contrato/{id}/salario                  → cambiarSalario(nuevoSalario, fechaDesde, motivo)
PUT    /contrato/{id}/terminar                 → terminar(fechaFin, causaRetiro)
GET    /contrato/{id}/historial-salarios
POST   /contrato/{id}/renovacion
GET    /contrato/por-vencer?dias=30            → alertas de renovación
```

**`PUT /salario` no es un update normal.** Preserva el histórico: cierra la vigencia anterior y abre una nueva. El formulario debe pedir **desde cuándo rige** y **motivo** — no solo el valor nuevo.

**`causaRetiro` decide la indemnización.** Debe ser un selector, no texto libre:
`JUSTA_CAUSA | SIN_JUSTA_CAUSA | RENUNCIA | MUTUO_ACUERDO | VENCIMIENTO_TERMINO | OBRA_TERMINADA | MUERTE`

### 2.b Procesos asíncronos — cambia cómo se liquida

```
POST   /nomina/liquidar/{periodoId}/todos      → devuelve 202 + procesoId
GET    /proceso/{id}                           → polling
```

**Esto cambia el flujo del front.** Hoy `liquidarPeriodoCompleto` es síncrono y el front espera. Con 500 empleados eso da timeout.

Nuevo flujo:
1. `POST` → `202 Accepted` con `{ procesoId }`
2. Polling a `GET /proceso/{id}` cada 2-3 s
3. Mostrar `progreso` (0-100) y `mensaje`
4. Al terminar, revisar `estado`

Estados a manejar:
```
PENDIENTE | EN_PROCESO | COMPLETADO | COMPLETADO_CON_ERRORES | FALLIDO | REVERSADO
```

**`COMPLETADO_CON_ERRORES` no es un fallo.** En 500 empleados, que 3 fallen no bota los 497 buenos. El front debe mostrar los `errores` (JSON con `referencia` y `error` por item) y dejar que el usuario resuelva esos 3.

Un `409` al lanzar significa que ya hay un proceso corriendo para ese período.

### 2.c Desprendible y certificados

```
GET  /certificado/desprendible/{nominaId}
GET  /certificado/ingresos/{terceroId}/{agno}
GET  /certificado/laboral/{contratoId}?conSalario=true
```

**El desprendible trae `traza` por línea** — el desglose paso a paso del cálculo (`[{"paso":"Base","valor":"1300000"},{"paso":"Tarifa","valor":"4 %"},...]`).

Vale la pena mostrarlo: es la diferencia entre "salud: 52.000" y poder explicarle al empleado de dónde salió. Puede ir en un tooltip o un expandible.

**El certificado de ingresos se archiva.** Si se pide dos veces, devuelve el mismo documento — es un requisito legal, no un bug de caché.

Falta el **PDF**: los servicios devuelven DTOs. Hay `CuentaPdfService` como precedente.

### 2.d Conceptos — el catálogo

```
GET    /concepto?fecha=2026-03-31              → vigentes para la empresa
POST   /concepto                               → crear uno propio
```

Los conceptos globales (`empresaId = null`) son de solo lectura. Una empresa puede crear los suyos o personalizar uno de ley.

**El formulario necesita entender `base`**, que es un enum acotado:
```
SALARIO | SALARIO_MAS_AUXILIO | IBC | DEVENGADO_TOTAL | FIJO | MANUAL
```
No es una fórmula libre. `FIJO` exige `valorFijo`; los demás exigen `porcentaje`.

**Vigencia:** al cambiar una tarifa no se edita el concepto — se crea una versión nueva con `vigenteDesde`. El backend rechaza solapes.

### 2.e Afiliaciones — bloquea PILA

```
GET  /afiliacion/catalogo/{tipo}               → EPS | AFP | CCF | ARL
POST /afiliacion/contrato/{contratoId}         → afiliar(entidadId, desde)
GET  /afiliacion/contrato/{contratoId}
GET  /afiliacion/validar/{contratoId}          → problemas para PILA
```

**El usuario elige de una lista, no digita el código.** El catálogo es nacional y lo mantiene el proveedor; si cada cliente digitara el código, divergirían y el archivo de PILA se rechazaría.

**Cambiar de EPS es un traslado, no un update.** El formulario pide **desde cuándo** — de esa fecha PILA deriva las banderas de traslado.

`GET /validar` devuelve la lista de problemas. Conviene mostrarlo **antes** de liquidar, no al generar PILA con 200 empleados.

### 2.f PILA

```
POST /pila/generar/{periodo}                   → 'YYYY-MM'
GET  /pila/{empresaId}/{periodo}
GET  /pila/{id}/cotizantes
```

`POST /generar` responde `409` con la **lista de problemas por empleado** si algo falta. Es texto accionable, hay que mostrarlo completo.

**Falta el archivo plano** — depende de qué operador usen los clientes.

### 2.g Retefuente

```
GET  /retefuente/deducciones/{contratoId}
POST /retefuente/deducciones                   → dependientes, vivienda, etc.
PUT  /contrato/{id}/procedimiento-retefuente   → '1' | '2'
```

Tipos de deducción, cada uno con su tope legal:
```
DEPENDIENTES | INTERESES_VIVIENDA | MEDICINA_PREPAGADA | AFC | AFP_VOLUNTARIO
```

**`DEPENDIENTES` no lleva valor.** Es siempre 10% del ingreso topado a 32 UVT — el backend lo calcula. Si el formulario pide un monto, se ignora. Mejor mostrarlo como un check.

### 2.h Embargos

```
GET  /embargo/contrato/{contratoId}
POST /embargo
PUT  /embargo/{id}/terminar
```

`tipo`: `ALIMENTOS | COOPERATIVA | JUDICIAL_ORDINARIO | FISCAL`

**El monto es "o valor total o porcentaje", nunca ambos.** Hay un CHECK en la BD.

**`ALIMENTOS` tiene prelación** y llega al 50%; los ordinarios solo a la quinta parte del excedente sobre 1 SMMLV. Vale la pena mostrar el cupo disponible.

### 2.i Nómina electrónica — 🚧 en pausa

No la usan los clientes actuales. Cuando se retome:
```
GET  /nomina-electronica/pendientes
POST /nomina-electronica/enviar
GET  /nomina-electronica/{id}/logs
```

---

## 3. Lo que cambia en pantallas existentes

| Pantalla | Cambio |
|---|---|
| **Tercero** | 4 campos de nombre; fecha nacimiento y sexo; representante legal si es jurídica; ICA/fuente separados |
| **Empleado** | Selector de tercero (opcional); manejar 409 por duplicado; los campos de contrato migran a la pantalla de contrato |
| **Liquidar período** | De síncrono a 202 + polling; barra de progreso; mostrar errores por item |
| **Detalle de nómina** | Nuevo: desglose por concepto con traza |
| **Nueva: Contratos** | Alta, cambio de salario con fecha y motivo, terminación con causa, historial |
| **Nueva: Conceptos** | Catálogo con vigencias |
| **Nueva: Afiliaciones** | Por contrato, con selector del catálogo |
| **Nueva: PILA** | Generar por período, ver problemas |

---

## 4. Recomendación de orden

1. **Contratos** — sin esto no se puede dar de alta un empleado bien
2. **Procesos async** — sin esto la liquidación da timeout con volumen
3. **Tercero** (campos nuevos) — bloquea PILA y nómina electrónica
4. **Desprendible** — es lo que el empleado pide
5. **Afiliaciones** → **PILA**
6. Conceptos, retefuente, embargos
7. Nómina electrónica (cuando se retome)

**Nada de esto se puede probar todavía:** no hay endpoints, no hay tests, y el `V13__baseline.sql` sigue faltando.

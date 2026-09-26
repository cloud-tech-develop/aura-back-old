# Plan: cartera profesional

Fecha: 2026-09-16 · Alcance: solo local (`aura-pos`), nada en producción.

## 1. Diagnóstico: qué hay hoy

| Pieza | Estado |
|---|---|
| Cuenta por cobrar (`cuentas_cobrar`) | Una por venta a crédito o creada a mano. Un solo vencimiento. |
| Abono (`abonos_cobrar`) | **Una cuenta a la vez**. No puede pasar del saldo: si el cliente paga de más no hay dónde dejar el sobrante. Origen de fondos y asiento bien resueltos. |
| Crédito del cliente (`tercero_credito`) | Cupo, plazo, score, nivel de riesgo, tolerancia de mora. La venta ya valida el cupo. |
| Reglas de crédito (`regla_credito`) | Motor con condiciones JSON, **sin pantalla** para crearlas. |
| Solicitudes de autorización | Se aprueban/rechazan por endpoint, **sin listado ni bandeja**. |
| Gestión de cobro (`gestion_cobro`) | Se registra (llamada, promesa, monto prometido), **pero no hay historial visible ni agenda** de promesas; nadie revisa si la promesa se cumplió. |
| Dashboard / edades / alertas | Aging 0-30/31-60/61-90/+90 global. Sin DSO, sin tendencia, sin recaudo del mes vs. lo que vencía. |
| Estado de cuenta | Existe (`/terceros/{id}/estado-cuenta` + PDF), pero vive fuera del módulo de cartera. |
| Anticipos | Existen en contabilidad (`/contabilidad/anticipos` + cruce); la cartera no los muestra como saldo a favor. |
| Deterioro | Existe el generador contable; la cartera no lo ofrece. |
| Intereses de mora, acuerdos de pago, recordatorios | **No existen**. |

En resumen: la cartera **registra** deudas, pero no ayuda a **cobrar**.

## 2. Propuesta por fases

### C1. Ficha 360° del cliente
Es la pantalla central del cobrador. Se abre desde cualquier lista.
- Arriba: cupo usado/disponible, saldo, vencido, días de mora máximos, score con semáforo, anticipos a favor.
- Pestañas: facturas abiertas con su aging, abonos, gestiones (línea de tiempo), promesas, estado de cuenta en PDF.
- Acciones: registrar pago, registrar gestión, bloquear o desbloquear crédito, cambiar cupo.
- Backend: `GET /cartera/clientes/{id}/ficha`, `GET /cartera/gestiones?terceroId`.

### C2. Recibo de caja multi-factura
Es lo que más se nota en el día a día.
- El cliente paga un monto y el sistema sugiere cómo aplicarlo: primero la factura más vieja (editable a mano, factura por factura).
- **Sobrante**: queda como anticipo o saldo a favor del cliente, y se cruza después.
- **Descuento por pronto pago** o ajuste de centavos, opcional, con su cuenta contable.
- Un solo recibo numerado (RC) con PDF y un solo asiento. Por dentro se generan los `abonos_cobrar` de siempre, así el arqueo y los reportes existentes siguen funcionando.
- Anular un recibo revierte todos sus abonos.
- Migración: tablas `recibo_caja` y `recibo_caja_aplicacion`.

### C3. Promesas y agenda del cobrador
- Una gestión con promesa crea un compromiso (fecha y monto).
- Bandeja "Cobrar hoy", que junta tres cosas:
  - promesas que vencen hoy;
  - promesas incumplidas;
  - facturas que vencen hoy o en los próximos N días.
- Cumplimiento automático: si entran abonos del cliente por el monto prometido antes de la fecha, la promesa queda cumplida; si no, pasa a incumplida y baja el score.
- Asignación de cartera por cobrador (usuario), opcional.
- Notificaciones en la campana: promesas incumplidas y facturas que vencieron ayer.

### C4. Acuerdos de pago (cuotas)
- La deuda de una o varias facturas se refinancia en N cuotas, con fecha y valor.
- Cada cuota tiene su propio vencimiento; el aging y la mora se calculan por cuota.
- Los pagos aplican a las cuotas en orden.
- Si el cliente incumple una cuota, el acuerdo queda "incumplido" y se alerta.
- PDF del acuerdo para firmar.

### C5. Intereses de mora (opcional por empresa)
- Una tasa mensual configurable, con tope en la tasa de usura vigente que registra el usuario, y días de gracia.
- El sistema calcula el interés causado a hoy y lo muestra en la ficha y en el estado de cuenta.
- El interés se cobra **solo al facturarlo**: nota débito o cuenta aparte, con el ingreso en 4210. Así no se causa ingreso que nunca se va a recibir.

### C6. Dashboard gerencial de cartera
- KPIs:
  - cartera total, vencida y % vencida;
  - **DSO** (días promedio de cobro);
  - recaudo del mes contra lo que vencía en el mes (efectividad);
  - promesas cumplidas vs. incumplidas.
- Aging con tendencia de los últimos 6 meses (foto mensual).
- Top 10 deudores, concentración de cartera y cartera por cobrador y por vendedor.
- Mismo lenguaje visual del dashboard nuevo.

### C7. Control de crédito completo
- Pantalla de **reglas de crédito**: constructor visual de condiciones, sin escribir JSON.
- **Bandeja de solicitudes de autorización**: pendientes, aprobadas y rechazadas, con aviso en la campana.
- Bloqueo automático por mora mayor a la tolerancia, visible en el POS con el motivo.

### C8. Recordatorios al cliente
- Plantillas por evento: vence en 3 días, venció, promesa hoy.
- Primera versión: enlace de WhatsApp (`wa.me`) con el mensaje armado, más correo con el estado de cuenta en PDF. Se registra como gestión automáticamente.
- Envío masivo programado, más adelante (depende del proveedor de correo o WhatsApp).

### C9. Deterioro y castigo
- Sugerencia de deterioro: facturas con más de 360 días o según la política de la empresa, con un botón para generar el asiento con el generador existente.
- Castigo de cartera: la factura sale de la cartera activa con su asiento, sin perder el historial.

## 3. Orden recomendado

1. **C2 + C1**: recibo multi-factura y ficha del cliente. Es el mayor impacto diario.
2. **C3**: promesas y agenda. Convierte la cartera en una herramienta de cobro.
3. **C6**: dashboard con DSO y efectividad.
4. **C7**: reglas y solicitudes, que ya tienen motor pero no pantalla.
5. **C4, C5, C8, C9**: según lo que pidan los clientes.

## 4. Decisiones abiertas

- Sobrante de un pago: ¿anticipo (cuenta 2805) o saldo a favor dentro de cartera?
- ¿Se manejan cobradores asignados, o cobra quien esté?
- Interés de mora: ¿se ofrece? Muchos negocios pequeños no lo cobran.
- Recordatorios: ¿basta con el enlace de WhatsApp manual, o se integra un proveedor?

## 5. Estado

| Fase | Estado |
|---|---|
| C1 Ficha del cliente | **Hecho (local)** — `/cartera/cliente/:id` |
| C2 Recibo multi-factura | **Hecho (local)** — V167, `RCB-######` |
| C3 Promesas y agenda | **Hecho (local)** — V168, pestaña "Cobrar hoy" |
| C6 Tablero gerencial | **Hecho (local)** — pestaña "Tablero", sin migración |
| C7 Control de crédito | **Hecho (local)** — V169, `/cartera/reglas`, pestaña "Autorizaciones" |
| C4 Acuerdos de pago | **Hecho (local)** — V170, `ACP-######`, pestaña "Acuerdos de pago" |
| C8 Alertas de vencimiento | **Hecho (local)** — en la campana, sin migración. Reemplaza los recordatorios al cliente |
| C5, C9 | Diseñado, sin implementar |

### C1 + C2 — cómo quedó (2026-09-16)

**Backend**
- V167 (`V167__recibo_caja_cartera.sql` + Laravel `000167`): `recibo_caja`,
  `recibo_caja_aplicacion`, `abonos_cobrar.recibo_caja_id`,
  `anticipo.cuenta_contable_id` / `recibo_caja_id`. Corrida solo en local.
- `ReciboCajaServiceImpl`: por dentro crea un `abonos_cobrar` por factura con el
  turno/cuenta que da `OrigenFondosService`, así arqueo, asiento RC y reportes no
  cambian. Bloquea las facturas (`PESSIMISTIC_WRITE`) y numera con
  `pg_advisory_xact_lock`.
- Sobrante → anticipo de cliente (2805) con la cuenta ya resuelta. **Prohibido en
  efectivo a caja abierta** (el arqueo no ve anticipos): se entrega el cambio.
- Anular: exige motivo; revierte saldos, borra los abonos (como el abono suelto),
  anula el anticipo si nadie lo cruzó, reversa asientos. Bloquea si el efectivo
  entró a un turno ya cerrado.
- Endpoints `/api/cartera`: `GET clientes/{id}/ficha`, `GET/POST recibos`,
  `GET recibos/{id}`, `POST recibos/{id}/anular`, `GET recibos/{id}/pdf`.
- Arreglos de paso: el abono suelto que pertenece a un recibo no se deja borrar;
  borrar un abono suelto ahora reversa su asiento; la reversa genérica anula el
  **borrador** huérfano (antes quedaba vivo en todos los módulos); la gestión de
  cobro valida que la factura sea de la empresa; `CREDITO` no es medio de pago.

**Front**
- `features/cartera/ficha-cliente`: KPIs (saldo, vencido, cupo, saldo a favor,
  días promedio de pago), barra de edades, crédito/score, pestañas facturas /
  pagos / recibos / gestiones (línea de tiempo) / anticipos (aplicar saldo a favor).
- `features/cartera/recibo-caja/recibo-caja-dialog`: valor recibido, medio de
  pago y destino del efectivo con botones de opción, reparto automático de la
  más vieja a la más nueva, edición por factura, resumen y PDF al terminar.
- Cartera: nombres clicables → ficha, botón ficha, pestaña "Recibos de caja",
  fecha de promesa en el modal de gestión.

**Pendiente / limitaciones**
- Un recibo genera un asiento RC por factura (no uno solo).
- Sin descuento por pronto pago ni ajuste de centavos.
- El anticipo en efectivo con caja abierta no se admite (falta llevar anticipos al arqueo).
- No probado en navegador por Claude (solo endpoints en 9002).

### C3 — cómo quedó (2026-09-16)

- **Ficha rediseñada**: todo dentro de una sola card con `card-head` estándar;
  cifras en franja dividida, edades + crédito en una sección, pestañas abajo.
- **V168** (`V168__promesas_pago.sql` + Laravel `000168`): `gestion_cobro.estado_promesa`
  (PENDIENTE/CUMPLIDA/INCUMPLIDA/CANCELADA), `monto_pagado_promesa`,
  `promesa_resuelta_at`; las promesas viejas con fecha y monto quedan PENDIENTE.
- **Registrar promesa** exige fecha ≥ hoy y monto > 0, rechaza facturas sin saldo
  y cancela la promesa pendiente anterior del cliente (renegoció).
- **`PromesaPagoService.evaluar`** (un UPDATE…RETURNING): cumple con abonos
  *registrados después de la promesa* y fechados hasta el día prometido; la
  vencida sin completar queda INCUMPLIDA; si anulan el recibo antes de la fecha
  vuelve a PENDIENTE. Corre a las 00:15 hora Colombia (`PromesaPagoScheduler`),
  al abrir agenda/ficha/campana y al crear o anular un recibo.
- **Score**: −50 por promesa incumplida en 180 días (tope −150).
- **Agenda** `GET /api/cartera/agenda?dias=7`: incumplidas sin seguimiento (60 días),
  promesas de hoy, vencidos sin gestión en 15 días y sin promesa, facturas por
  vencer, próximas promesas, % de cumplimiento del mes.
- **Front**: pestaña "Cobrar hoy" (`features/cartera/agenda`) con llamar,
  WhatsApp con mensaje armado (`wa.me`, +57 si son 10 dígitos), registrar
  gestión y ficha; la ficha muestra la promesa vigente y el estado de cada una.
- **Campana** (solo ADMIN/SUPER_ADMIN): PROMESAS_INCUMPLIDAS y PROMESAS_HOY,
  abren la agenda.
- Sin cobradores asignados (cobra quien esté), como estaba en decisiones abiertas.

### C6 — cómo quedó (2026-09-16)

- `GET /api/cartera/tablero?meses=6` (`TableroCarteraService` + `TableroCarteraQueryRepository`). Sin migración.
- **Foto mensual reconstruida**: saldo al corte = saldo actual + pagos (abonos y
  cruces de anticipo) fechados después del corte. El mes en curso cuadra exacto
  con `saldo_pendiente`; no hace falta guardar cortes. Excluye cuentas anuladas.
- **DSO** = saldo al corte ÷ ventas a crédito (total_deuda emitido) de los 90 días
  previos × 90; null si no hubo ventas a crédito.
- **Efectividad** = lo pagado en el mes sobre el saldo, al iniciar el mes, de lo
  que vencía hasta fin de mes.
- También: % vencido, clientes con saldo/en mora, cumplimiento de promesas del
  mes (C3), top 10 deudores con participación y concentración top 5, saldo por
  vendedor (vendedor del pedido o usuario que facturó; "Cuentas manuales" sin
  venta) y recaudo del mes por medio de pago.
- **Front** `features/cartera/tablero`: reemplaza el Dashboard viejo de cartera.
  Franja de 5 indicadores con variación vs. mes anterior (verde/rojo según si
  subir es bueno), barras apiladas por edades, cobrado vs. lo que vencía con
  línea de efectividad, mayores deudores (clic → ficha), laterales por vendedor
  y medio de pago, tabla mes a mes. Selector 6/12 meses.
- Con pocos datos el DSO se dispara (facturas viejas grandes y ventas nuevas
  pequeñas): es el cálculo correcto, no un error.

### C7 — cómo quedó (2026-09-17)

**Diagnóstico previo**: `evaluarReglasAutomaticas` no la llamaba nadie (las reglas
no hacían nada), ninguna parte del sistema creaba solicitudes de autorización y
aprobar una no destrababa la venta.

- **V169** (`V169__control_credito.sql` + Laravel `000169`): `regla_credito.descripcion`,
  `dias_entre_aplicaciones`, `updated_at`; `historial_credito.regla_id`;
  `solicitud_autorizacion_credito.solicitado_por_id`, `observacion`, `respondido_at`,
  `vigente_hasta`, `usada_at`, estados USADA/VENCIDA. `condicion_json`/`accion_json`
  mapeados con `@JdbcTypeCode(JSON)` (antes un insert habría fallado por jsonb).
- **Reglas** (`ReglaCreditoService`): CRUD con condiciones como campos (score
  mín/máx, mora ≤ / >, pagos seguidos a tiempo, estado, promesas incumplidas, uso
  de cupo); acciones subir/bajar cupo (con tope), suspender, bloquear, alertar.
  Días de espera por regla y cliente (vía `historial_credito.regla_id`) para que no
  se acumulen. `POST /reglas/simular` dice a quién le aplicaría hoy.
- **Cuándo corren**: `CreditoMovimientoEvent` después del commit de un recibo o
  abono (AL_PAGAR) y de una venta a crédito (AL_VENDER), en transacción propia con
  recálculo de score; `ReglasCreditoScheduler` 00:30 Bogotá recalcula score y
  suspensión por mora de todos y corre las PERIODICO.
- **Autorizaciones** (`SolicitudCreditoService`): el POS pide (`POST /solicitudes`),
  una pendiente por cliente; el admin aprueba con vigencia (1–72 h, 4 por defecto)
  o rechaza con motivo; `validarVenta` deja pasar si hay aprobación vigente que
  cubra el monto y la venta la marca USADA con su `venta_id`. Una aprobación sirve
  para una sola venta. Las aprobadas sin usar pasan a VENCIDA.
- **Front**: página `/cartera/reglas` (plantillas: premiar buen pagador, suspender
  por mora, castigar promesas; constructor de condiciones; frase en español;
  probar con clientes de hoy; Activa/Pausada con botones). Pestaña
  "Autorizaciones" con contador, refresco cada 20 s y vigencia por botones.
  POS (modal de pago): panel "pasa su cupo por $X → Pedir autorización", espera
  sondeando cada 5 s y al aprobarse habilita Crédito. Ficha: pestaña "Historial
  de crédito". Campana: SOLICITUDES_CREDITO para admins; el topbar ahora refresca
  cada 60 s.

### C4 + C8 — cómo quedó (2026-09-17)

**C4 Acuerdos de pago**
- **V170** (`V170__acuerdos_pago.sql` + Laravel `000170`): `acuerdo_pago`,
  `acuerdo_pago_cuenta` (saldo al acordar, vencimiento original, `activo`; índice
  único parcial: una cuenta en un solo acuerdo vivo) y `acuerdo_pago_cuota`.
  Corrida solo en local. Sin entidades JPA: todo por JDBC
  (`AcuerdoPagoQueryRepository`), así `ddl-auto=validate` no las toca.
- **Las cuotas no reciben pagos directos.** El cliente sigue pagando las cuentas
  (recibo, abono suelto o cruce de anticipo). `AcuerdoPagoService.evaluar` toma lo
  que bajó el saldo de las cuentas desde el acuerdo y lo reparte sobre las cuotas en
  orden: PENDIENTE / PARCIAL / PAGADA / VENCIDA. Arqueo, asientos y reportes no
  cambian, y anular un pago devuelve la cuota a pendiente sola.
- **La mora se mide por cuota**: mientras el acuerdo vive, las cuentas vencen con la
  primera cuota sin pagar (se mueve `cuentas_cobrar.fecha_vencimiento`). Edades,
  tablero, score, suspensión por mora y agenda lo ven sin tocarlos. Anular devuelve
  el vencimiento original a las cuentas que aún deben.
- **Estados**: VIGENTE; INCUMPLIDO si una cuota pasa su fecha + días de gracia (vuelve
  a VIGENTE si se pone al día, pero `incumplido_at` queda); CUMPLIDO al pagarse todo
  (se reabre si anulan un pago); ANULADO con motivo.
- **Crear** valida: cuentas del cliente con saldo, no en otro acuerdo, cuotas con
  fecha de hoy en adelante y creciente, suma exacta al centavo, máx. 60 cuotas y 60
  días de gracia. Registra una gestión ACUERDO_PAGO en la línea de tiempo y cancela
  la promesa pendiente.
- **Cuándo se evalúa**: al crear/anular recibo, abono suelto, borrar abono, cruce y
  reversa de anticipo (con flush antes), al abrir ficha/detalle/listado/campana, y
  a las 00:20 Bogotá (`AcuerdoPagoScheduler`, antes de las reglas de 00:30).
- **Score**: −100 por acuerdo incumplido en el último año (tope −200).
- Endpoints `/api/cartera/acuerdos`: `GET` (estado, search, página), `GET {id}`,
  `POST`, `POST {id}/anular`, `GET {id}/pdf` (para firmar: facturas, plan de pagos,
  cláusula de incumplimiento y firmas).
- **Front**: `features/cartera/acuerdos/acuerdo-pago-dialog` (elige facturas, número de
  cuotas, semanal/quincenal/mensual con botones, primera fecha, días de gracia; cada
  cuota editable, la última absorbe los centavos, aviso de descuadre con "Ajustar la
  última cuota") y `acuerdo-detalle-dialog` (avance, cuotas, facturas, imprimir,
  anular con motivo). Ficha: botón "Acuerdo de pago", pestaña "Acuerdos", chip del
  acuerdo vivo en el encabezado y enlace en cada factura. Cartera: pestaña "Acuerdos
  de pago" con filtro por estado.

**C8 Alertas de vencimiento (en lugar de recordatorios al cliente)**
- Decisión del usuario: no se envían mensajes al cliente; el sistema avisa en la campana.
- `NotificacionController` (solo ADMIN/SUPER_ADMIN, como el resto de cartera):
  - `FACTURAS_VENCIDAS` (danger): cantidad, valor y clientes; abre "Cuentas vencidas".
  - `FACTURAS_POR_VENCER` (warn): vencen hoy o en los próximos 3 días; abre "Cobrar hoy".
  - `ACUERDOS_INCUMPLIDOS` (danger) y `CUOTAS_POR_VENCER` (warn, incluye las que
    están en días de gracia); abren "Acuerdos de pago".
  - Las cuentas dentro de un acuerdo vivo no cuentan como facturas: las avisa el acuerdo.
  - Cada bloque en su propio try/catch: si falla uno, la campana sigue.
- Topbar: íconos, enrutamiento a la pestaña y el valor dice "por cobrar".

**Probado** por endpoints en 9002 con la empresa 4 local (26 verificaciones, 0 fallas):
validaciones, reparto parcial con abono y recibo, movimiento del vencimiento,
incumplimiento y regreso a vigente, campana, ficha, listado, PDF, anulación con
vencimientos restaurados y cumplimiento. **No probado en navegador.**

**Pendiente / limitaciones**
- Si una cuenta del acuerdo se anula (venta anulada), su saldo cuenta como pagado.
- El badge de la campana suma cantidades: con muchas facturas vencidas marca 99+.
- Los días de aviso (3) son constantes, no configurables por empresa.

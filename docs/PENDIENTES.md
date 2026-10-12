# Pendientes — retomar aquí (actualizado 2026-10-10)

> El contenedor `aura-backend` está al día con todos los cambios (se reconstruye con `docker compose up -d --build`; el watch no está corriendo).

Todo lo de esta sesión está **local y sin commit** (back `aura-back-old`, front `aura-frontend`, migraciones en `aura-pos-migracion-old`).

## Siguiente paso (decidir al retomar)
1. **EN CURSO: nómina R3** (`docs/PLAN_NOMINA_REDISENO.md` §4–5 y fila R3). HECHOS 2026-10-11: R3.1 tipos de
   comprobante (V214), R3.2a encabezado de prenómina (V215, RR. HH. → Prenómina), R3.2b pestaña Empleados y R3.2c Liquidar
   (V216, pestañas Liquidación y Traza). R3.2d HECHO (V217: conceptos de novedad; arregla que las
   novedades que pagan no llegaban al neto; conceptos propios y topes en el motor). R4a HECHO (confirmar prenómina →
   generar nómina → confirmar nómina con asiento viejo por empleado; menú Nómina). R4b pestaña Contabilidad y R4d (novedades en la prenómina + pago de la nómina; Preliquidación fuera del menú)
   HECHOS. Períodos y Liquidación Nómina ya ocultos. R4c-1 asiento por concepto y entidad HECHO. R4c-2 HECHO 2026-10-11 (V218 `nomina_concepto_contable`, pantalla RR. HH. → Cuentas contables de nómina, SQL menú R3 actualizado y corrido en local; falta probar en navegador). R5 retiros y liquidación definitiva HECHO (V219). R6 prima/cesantías/intereses por comprobante + Excel de consignación HECHO. R7 vacaciones HECHO (V220; Prestaciones fuera del menú). R8 incremento salarial + retroactivo HECHO (V221). **EN PAUSA (decisión del usuario 2026-10-11):** R9 novedades
   (bandeja de autorización + "requiere autorización", novedades/conceptos en masa, préstamos, embargos completos,
   depósitos judiciales, afiliaciones en masa, tipos de aporte y entidades), R10 gestión humana (traslados,
   reemplazos, disciplinarios, certificados/cartas) y R11 ideas (portal del empleado, alertas, topes legales,
   simulador de costo, checklist de cierre). Se retoman cuando el usuario lo pida.
   **Nómina electrónica = Factus v2 (decidido por el usuario):** `POST /v2/payroll/validate`, payload
   `FactusNominaV2Payload` armado por `FactusNominaV2Builder` y enviado por `FactusNominaV2Service`. La estructura ya
   coincide con el ejemplo de Factus (reference_code, numbering_range_id, settlement_period, payment, worker,
   accruals suel/tra/comi/hora/boni/cesa/prim/vaca/inca/lice/otro, deductions salu/pens/dedu/rete/libr/otra). El camino
   viejo (`aprobar()` → `NominaElectronicaService.prepararDocumento` + `NominaElectronicaJob` con
   `FactusNominaService` v1) queda para retirar al retomar.
   **2026-10-11 endpoints Factus v2 completos (V222 corrida en local):** ver por número, PDF, eliminar no validada
   (ahora solo marca ANULADO si Factus responde bien), nota de ajuste guardada como fila propia (es_ajuste) con su
   número/CUNE + ver/eliminar/XML/PDF de la nota (`/api/nomina-electronica/ajustes/...`). Botón "Nómina electrónica"
   por empleado en la pestaña Liquidación de la nómina confirmada (abre el diálogo de vista previa/envío/XML/PDF/nota).
   **Lo que sigue faltando para producción:** (a) ya hay botón de envío en la nómina nueva. (b) La DIAN pide UN documento por empleado y mes:
   hoy se enviaría uno por fila (quincenas, prima, retroactivo, vacaciones en el mismo mes) → falta agrupar por
   mes (decisión D2 del plan). (c) `FactusNominaV2Builder` solo mapea sueldo, transporte, comisiones, horas extra
   y salud/pensión; faltan prima, cesantías, intereses, vacaciones, incapacidades, licencias, retroactivo,
   indemnización, FSP, retefuente, préstamos/embargos. (d) Los códigos de catálogo de Factus están "por
   confirmar". (e) La última prueba real dio 403: falta habilitar el documento 26 (nómina) en Factus. Falta de la vinculación: "requiere autorización"
   de las novedades permitidas (R9).
2. **Terminar la hoja de vida del empleado**: Estudios, Idiomas, Hobbies (catálogo de nómina) y Adjuntos con vencimiento
   (`docs/PLAN_TERCERO_PERSONA_EMPLEADO.md` T4/T7). Mismo patrón que Familia (`features/nomina/empleados/hoja-vida/`).
3. ~~Alta vieja sin tipo/cargo~~ **HECHO 2026-10-11**: `crearContratoInicial` llama
   `VinculacionService.completarContratoHeredado` (código VIN-, tipo deducido del contrato como V210, cargo del
   catálogo por nombre o creado con código CAR-NNN). Falta probar el alta desde Empleados → Nuevo.
4. **Limpieza**: `features/nomina/empleados/detalle/` (ficha vieja) no tiene ruta ni referencias; falta borrarla
   (`rm -r`, el permiso no dejó hacerlo desde Claude).
5. **Presupuesto contable** (solo diseño) y **validar pagos falsos** (idea) siguen en espera.

## Esperando prueba del usuario — nómina y terceros (2026-10-10)
- **Catálogos de nómina (R1)**: RR. HH. → Catálogos de nómina (cargos, tipos de vinculación, grupos, niveles, tipos de
  cargo, embargos, motivos de retiro/ausentismo, riesgos ARL, calendarios laborales).
- **Vinculaciones (R2)**: listado con filtros; formulario con pestañas General · Ubicación y jerarquía · Seguridad social ·
  Soporte · Centros de costo · Conceptos · Novedades permitidas · Aportes · Retención · Embargos · Historial. Empleado,
  jefe y evaluador con el buscador de terceros (rol EMPLEADO).
- **Terceros**: persona natural ampliada solo para empleados (fotos, expedición, nacimiento, residencia, contacto);
  cliente/proveedor como antes pero con varios teléfonos/correos; "Empleado" ya no se ofrece en Terceros.
- **Empleados → Editar** = hoja de vida con pestañas Datos personales · Banco (varias cuentas, la principal se copia al
  tercero para el pago) · Familia · Historial laboral. Sin ficha ni botones de vinculación.

## Para producción (cuando se despliegue)
- **Migraciones nuevas**, corridas solo en local (verificar `.env` local antes; prod = aura-db, no tocar desde aquí):
  - V198 permiso EDITAR_EMPRESA · V199 firma contable · V200 gestionados/solicitudes (firma contable)
  - **V201** `causacion_gasto` (+ líneas) · **V202** `conciliacion_fiscal` (+ cuentas, ajustes)
  - **V203** retenciones de compra por concepto (`compra.*_tarifa_id` + catálogo de tarifas con cuenta por empresa). Corrida en local 2026-10-09.
  - **V204** tercero acreedor en `impuesto` y `tarifa_retencion` + DIAN como tercero por empresa + `declaracion_pago` (+ cuentas). Corrida en local 2026-10-09.
  - **V205** cartera: `cartera_politica`, `interes_mora` (+ detalle), `cartera_castigo`, `cuentas_cobrar.valor_castigado`. Corrida en local 2026-10-09.
  - **V206** `periodo_contable.chequeo_cierre` (foto de la lista de chequeo para el acta). Corrida en local 2026-10-10.
  - **V207** `declaracion_pago.ajuste_redondeo`, `intereses`, `sancion`. Corrida en local 2026-10-10.
  - **V208** `gasto.retefuente_tarifa_id`, `reteica_tarifa_id` (concepto de retención del gasto, como compras). Corrida en local 2026-10-10.
  - **V209** `nomina_catalogo`, `nomina_cargo`, `nomina_tipo_vinculacion` (nómina R1). Corrida en local 2026-10-10. Después correr `docs/sql/menu_submodulos_nomina_r1_catalogos.sql`.
  - **V210** amplía `contrato_laboral` (vinculación) y migra los contratos existentes. Corrida en local 2026-10-10. Después correr `docs/sql/menu_submodulos_nomina_r2_vinculaciones.sql`.
  - **V211** tercero persona natural ampliado (expedición, nacimiento, residencia, RH, estado civil, contacto, fotos) + `tercero_telefono` / `tercero_correo` (migra el teléfono y correo actuales). Corrida en local 2026-10-10.
  - **V212** vinculación: tipo de acto administrativo, municipio de trabajo, `vinculacion_concepto` y `vinculacion_novedad_permitida`. Corrida en local 2026-10-10.
  - **V214** `nomina_tipo_comprobante` (+ `_vinculacion`), nómina R3.1. Corrida en local 2026-10-11.
  - **V222** índice único de nómina electrónica por prefijo (notas de ajuste Factus v2). Corrida en local 2026-10-11.
  - **V223** aprendiz SENA Ley 2466/2025: apoyo 75 % lectiva / 100 % práctica (sube solo las empresas con 50/75 por defecto). Corrida en local 2026-10-12.
  - **V224** nómina electrónica mensual: `nomina_electronica.contrato_id` (+ backfill e índice). Corrida en local 2026-10-12.
  - **V225** exógena completa: empresa.tipo_persona, exogena_columna (columnas reales por formato), columna en mapeos, valores JSONB en líneas, formatos 1003/1010/1012, 2276 desde nómina, mapeos default de retenciones/devoluciones/socios/bancos. Corrida en local 2026-10-12. ⚠️ Revisar cada año columnas, conceptos y umbrales contra la resolución DIAN (son datos).
  - **V221** `nomina_incremento` + concepto RETROACTIVO (R8). Corrida en local 2026-10-11. Volver a correr `docs/sql/menu_submodulos_nomina_r3.sql` (agrega incrementos).
  - **V220** `nomina_vacacion` (vacaciones, R7). Corrida en local 2026-10-11. Volver a correr `docs/sql/menu_submodulos_nomina_r3.sql` (agrega vacaciones).
  - **V219** `nomina_retiro` (retiros, R5). Corrida en local 2026-10-11. Volver a correr `docs/sql/menu_submodulos_nomina_r3.sql` (agrega retiros).
  - **V218** `nomina_concepto_contable` (cuenta/tercero por concepto de nómina). Corrida en local 2026-10-11. Volver a correr `docs/sql/menu_submodulos_nomina_r3.sql` (agrega cuentas-nomina).
  - **V217** conceptos globales de novedad (base NOVEDAD, amplía `chk_cn_base`). Corrida en local 2026-10-11. **Ojo en prod:** desde este despliegue las horas extra, incapacidades, bonos, etc. SÍ suman al neto; las nóminas ya liquidadas no cambian hasta reliquidarlas.
  - **V216** `nomina.liquidada_at`/`error_liquidacion` + tipo de proceso LIQUIDACION_DOCUMENTO (amplía `chk_proc_tipo`). Corrida en local 2026-10-11.
  - **V215** `nomina_documento` + `nomina.documento_id`/`es_prenomina`; cambia `uq_nomina_contrato_periodo` por índices parciales. Corrida en local 2026-10-11. Después de V214+V215 correr `docs/sql/menu_submodulos_nomina_r3.sql` (tipos de comprobante + prenómina).
  - **V213** hoja de vida del empleado: `empleado_cuenta_bancaria` (migra la cuenta del tercero como principal), `empleado_familiar`, `empleado_experiencia`. Corrida en local 2026-10-10.
  - Con `ddl-auto=validate`, si faltan, **el backend de producción no arranca**.
- Sin SQL de menú nuevo: devoluciones y causaciones usan `compras.compras`; conciliación fiscal usa `contabilidad.declaraciones`.
- `JWT_SECRET` de prod debe estar rotado (ver memoria plan-seguridad).

## Esperando revisión del usuario
1. **PDF nuevo estilo** (#e1e8ef encabezados, filas blanco/#dfe2e7, estructura tipo factura Factus) en venta POS, facturación FV y comprobante. El QR de FV ya igual al de ventas.
   → **Compras HECHO 2026-10-09**: `core/services/compra-pdf.service.ts` (ver/descargar, todas las formas de pago, NC/ND, anulada con marca de agua); botones en el detalle y descarga en la lista.
   → Falta: cotizaciones (`cotizacion-pdf.service.ts`), recibos/estados de cuenta y demás. Helper: `shared/utils/pdf-estilo.ts`.
2. **Comprobantes manuales editables** (lápiz en Comprobantes) y **PDF con cuentas contables** (botón rojo), nombre del archivo = número (RC-000006.pdf).
3. **Causaciones** (Compras → Causaciones) y **Conciliación fiscal 2516/2517** (Contabilidad → Conciliación Fiscal).
4. **Devoluciones en compras** (Compras → Devoluciones + "Devolver mercancía" en el detalle de la compra).
5. Margen interno (`.card-body`) en las pantallas nuevas.
6. **Retenciones de compra por concepto** (2026-10-09): elegir el concepto en la compra, la cuenta sale de la tarifa; editar compra ya conserva las retenciones.
7. **Impuestos con tercero acreedor + pago de declaraciones** (2026-10-09): "Se paga a" en Impuestos y Tarifas, "Nuevo impuesto", bloque "A quién se le paga" con Registrar/Anular pago. Pago real no probado desde la pantalla.
8. **Cartera responsive** (2026-10-09) + menú lateral auto-colapsado en tablet (afecta toda la app): probar en tablet real.
9. **Cartera C5/C9** (2026-10-09): pestañas "Intereses de mora" y "Deterioro y castigo", botón Política, aviso de intereses en la ficha, estado "Castigada" en Cuentas por cobrar. No probado (ni endpoints ni navegador). Detalle en `docs/PLAN_CARTERA.md`.
10. **Cierre de mes fases 2–5** (2026-10-10): Períodos contables → "Cierre del mes" (chequeo, ajustes, acta PDF, comparativo). Ver `docs/PLAN_CIERRE_CONTABLE.md` §5.
11. **Impuestos** (2026-10-10): pago de declaraciones con redondeo a miles, intereses y sanción; pestaña ICA en Declaraciones (borrador, "Causar ICA", pago); "Municipio de ReteICA" en Tarifas de retención; pestaña "Formulario 110" en la conciliación fiscal (2516).

## Decisiones / arreglos que quedaron abiertos
- **Compras con retención anteriores a V203** no tienen concepto: el asiento sigue en la cuenta por defecto. Se corrigen editándolas (el formulario exige el concepto y rehace el asiento). Listado: `compra` con `*_pct > 0` y `*_tarifa_id IS NULL`.
- **ReteICA por municipio**: RESUELTO 2026-10-10 con "Municipio de ReteICA" (cuenta 236805xx + tarifas a nombre de la secretaría). Las 3 tarifas genéricas sin acreedor siguen como plantilla.
- **Pago de declaraciones**: redondeo a miles, intereses y sanción RESUELTOS 2026-10-10 (V207). El asiento sale tras el commit (si falla, el borrador lo muestra como "Sin asiento").
- **Tarifas de retención**: base mínima en 0 en el catálogo sembrado (no se valida la base en UVT); ReteICA con tarifas genéricas (ajustar por municipio).
- **Gasto con IVA o retenciones — CORREGIDO 2026-10-09 (local):** la caja, el banco, el comprobante de egreso y la cuenta por pagar usaban `monto`; el asiento, el neto (monto + IVA − retenciones). Ahora todo usa el neto (`GastoServiceImpl.valorAPagar`), editar IVA/retenciones exige anular y registrar de nuevo, y anular devuelve lo que salió según el comprobante (gastos viejos: `monto`). El formulario muestra "Total a pagar". En local no había gastos afectados. **En prod revisar** los ya registrados:
  `SELECT id, forma_pago, monto, valor_iva, valor_retefuente, valor_reteica FROM gasto WHERE estado <> 'ELIMINADO' AND (valor_iva > 0 OR valor_retefuente > 0 OR valor_reteica > 0);`
  Los de crédito tienen la CxP por `monto`: ajustar su `total_deuda`/`saldo_pendiente` al neto si no tienen abonos.
- **Fecha de validación DIAN** no se guarda: el PDF muestra la de emisión. Se puede guardar la que devuelva Factus.
- **Conciliación fiscal**: renglones por rubro (no los oficiales del prevalidador DIAN); 2517 sin cédulas.
- **Causaciones**: sin documento soporte electrónico desde la causación (sí existe desde compras/gastos).
- `permisoGuard` deja la pantalla en blanco cuando el perfil no tiene ninguna pantalla permitida (sugerido mostrar mensaje).
- VPS: ambientes **ventas** y **develop** apagados porque se caían en bucle; revisar sus logs antes de encenderlos. RabbitMQ eliminado (nadie lo usaba).

## Ideas para después
- **Validar pagos falsos (2026-10-09):** botón en el POS y junto a la campana para subir el comprobante de transferencia (Nequi, Daviplata, Bancolombia…). Capa 1: leer la imagen y alertar (valor, cuenta destino, fecha, **referencia ya usada**). Capa 2: el pago queda "Por confirmar" hasta cruzarlo con el banco (conciliación). Una imagen sola nunca prueba el pago. Decisiones abiertas: cómo leer la imagen (IA con visión / OCR / manual), cómo confirmar (extracto / correos del banco / manual), qué bancos.

## Planes con fases pendientes (ver memorias)
- **Nómina — rediseño** (2026-10-10, propuesta): `docs/PLAN_NOMINA_REDISENO.md` (R1–R11). **R1 catálogos y R2 vinculaciones hechos local**; sigue R3 tipos de comprobante + prenómina. D1/D3 decididas. Agente `nomina-aura`.
- Firma contable: F0–F12 hechas, faltan pruebas del usuario.
- Contabilidad: queda solo el **presupuesto** (`docs/PLAN_PRESUPUESTO.md`, solo diseño) y la facturación DIAN directa (diseño). El 110 es borrador (sin numeración oficial ni anticipo calculado); el 210 de personas naturales no existe.
- `docs/PLAN_LINEA_CONTABILIDAD.md` implementado 2026-10-09 (falta probar en navegador), plan cartera COMPLETO (C1–C9), cierre contable fases 2–5, plan facturación DIAN directa (solo diseño).

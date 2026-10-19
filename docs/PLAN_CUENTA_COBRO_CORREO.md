# Cuenta de cobro y envío de documentos por correo

Fecha: 2026-10-19 · Estado: implementado local (back + front), sin commit. V229 corrida solo en local (`aura-pos`).

## 1. Decisiones del usuario

| Tema | Decisión |
|---|---|
| Dónde vive la cuenta de cobro | Dentro de **Facturas de venta**: mismo formulario (borrador → emitir, desde cotización, copiar, cartera), con un select "Documento: Factura / Cuenta de cobro". |
| Quién la emite | **Cualquier empresa** (cambio del 2026-10-19; antes solo persona natural). Solo el tipo por defecto depende de la empresa: persona natural no responsable de IVA sin FE → nace como cuenta de cobro. |
| PDF del correo | El **mismo PDF que genera la pantalla** (pdfmake), enviado en base64. |
| Remitente | Una dirección de Aura (`CORREO_REMITENTE`, dominio verificado en Resend) con el **nombre de la empresa**; **Reply-To** = correo de la empresa. |
| Secretos | `RESEND_API_KEY` y `CORREO_REMITENTE` solo en el `.env` (nunca en el repositorio). |

## 2. Cuenta de cobro

- `factura_venta.tipo` (`FACTURA` | `CUENTA_COBRO`) y `factura_venta.contrato_dos_trabajadores` (declaración del parágrafo 2 del art. 383 ET; null = no declara).
- Al emitir crea la venta con `tipo_documento = 'CUENTA_COBRO'`, prefijo **CC** y consecutivo propio por empresa (`VentaQueryRepository.siguienteConsecutivoCuentaCobro`, advisory lock 175176). El consecutivo de las ventas/facturas excluye las CC.
- Sin IVA (las líneas se guardan al 0 %), sin AIU y **nunca** va a la DIAN (`VentaFacturaService` la rechaza).
- Cartera, tesorería y asiento: los mismos de una factura (sale por `VentaService.crear`).
- Un borrador nuevo nace como cuenta de cobro si la empresa es persona natural no responsable de IVA y no tiene factura electrónica (`CuentaCobroReglas.tipoPorDefecto`); si no, como factura. El campo "Documento" se ve siempre.
- PDF: título "CUENTA DE COBRO", identificación del emisor, condición de IVA "No responsable de IVA", IVA "No aplica" y recuadro de declaración (no responsable de IVA + art. 383 si se eligió).
- `GET /api/empresa` devuelve `tipoPersona` y `puedeCuentaCobro` (el front muestra el select solo si aplica).

## 3. Correo (Resend)

- Tabla `correo_envio` (V229): cada correo con tipo (`FACTURA`, `CUENTA_COBRO`, `COTIZACION`, `NOMINA`), documento, destinatarios, estado (`PENDIENTE` → `ENVIANDO` → `ENVIADO` | `FALLIDO`), error, id de Resend y usuario. El PDF se guarda solo mientras espera en la cola y se borra al enviarse.
- `ResendClient`: `POST https://api.resend.com/emails`, llave de idempotencia `correo-{id}` (un reintento no duplica). 429/5xx/red = pasajero (se reintenta hasta 3 veces).
- Uno a uno (factura, cuenta de cobro, cotización): se envía en el momento; la respuesta trae ENVIADO o FALLIDO con el motivo.
- Masivo (comprobantes de nómina): el front arma un PDF por empleado y los manda en tandas de 10 (máx. 50 por petición) con el mismo `loteId`; `CorreoColaWorker` envía cada 5 s respetando `CORREO_ENVIOS_POR_SEGUNDO`. Al arrancar, los ENVIANDO vuelven a PENDIENTE. Empleados sin correo no se encolan y se listan.
- Rutas y permisos (PermisoRutas):
  - `/api/correos/facturas/{id}` (historial), `/sugerencia`, `/enviar` → `ventas.facturas`
  - `/api/correos/cotizaciones/{id}` … → `ventas.cotizaciones` (o POS)
  - `/api/correos/nomina/sugerencia`, `/nomina/lote/enviar`, `/nomina/lote/{loteId}` → liquidación/prenómina/documento de nómina
- Variables (`.env` local y **Dokploy en producción**):
  - `RESEND_API_KEY` — llave de la API
  - `CORREO_REMITENTE` — p. ej. `documentos@cloudtecnology.cloud`
  - `CORREO_ENVIOS_POR_SEGUNDO` — 2 por defecto

## 4. Front

- Facturas de venta: select Documento y declaración art. 383; IVA bloqueado y "No aplica" en la cuenta de cobro; sin "Enviar a la DIAN"; botón **Enviar por correo** en la emitida; listado marca "Cuenta de cobro".
- Cotización (detalle): botón **Enviar por correo**.
- Nómina (pestaña de liquidación, modo pago): **Enviar comprobantes por correo** con avance, sin correo y fallidos.
- Componente reutilizable `shared/components/enviar-correo` y `core/services/correo.service.ts`; `shared/utils/pdf-adjunto.ts` (pdfmake → base64).

## 5. Pendiente

- Probar en navegador: emitir una cuenta de cobro (empresa persona natural), su PDF, enviarla por correo; cotización por correo; lote de nómina con un empleado sin correo.
- Producción: correr V229 (Laravel 000229) y poner las 3 variables en Dokploy antes de desplegar.
- Factura electrónica: Factus ya le envía la FE al cliente; el botón de correo manda nuestra representación gráfica.

## 6. Cobro de cartera por correo (2026-10-19)

- Ficha del cliente → **Enviar cobro**: correo con saldo y vencido en el mensaje y el **estado de cuenta en PDF** que arma el servidor (`EstadoCuentaPdfService`, el mismo del botón "Estado de cuenta"). `CobroCorreoService`, rutas `/api/correos/cartera/{terceroId}` (sugerencia, historial, enviar) → permiso `cartera.cartera`.
- Agenda de cobro → **Recordar a vencidos**: a todos los clientes con facturas vencidas fuera de acuerdos de pago vivos (`CorreoQueryRepository.clientesVencidos`); el servidor arma cada PDF y los encola (`/cartera/vencidos/enviar`, avance en `/cartera/lote/{loteId}`). Los que no tienen correo se listan.
- `correo_envio.tipo = 'COBRO'`, `documento_id` = el cliente.

## 7. Acceso del administrador (plataforma)

- Al crear una empresa, después del commit, `AccesoCorreoService.enviarAccesoInicial` le manda al administrador usuario, clave temporal y botón "Crear mi contraseña" (enlace de 72 h). La pantalla de éxito dice si salió. La clave temporal **no se guarda** en `correo_envio` (solo un texto de registro).
- Editar empresa → **Reenviar acceso**: genera un enlace nuevo para el SUPER_ADMIN y lo envía (`POST /api/platform/empresas/{id}/reenviar-acceso`).
- Sale como "Aura", sin Reply-To. `correo_envio.tipo = 'ACCESO'`, `documento_id` = la empresa.

## 8. Remitente no-reply

- `CORREO_REMITENTE` = `no-reply@cloudtecnology.cloud`. Todo correo lleva al pie: "Este correo se envió desde una dirección que no recibe respuestas (no-reply)" y, si la empresa tiene correo, "Si tiene preguntas, escriba a …". El Reply-To sigue siendo el correo de la empresa.

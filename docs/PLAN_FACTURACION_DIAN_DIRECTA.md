# Plan: Facturación electrónica directa con la DIAN (sin depender de Factus)

> Estado: **diseño / decisión tomada, sin implementar** — 2026-10-06
> Alcance: backend `aura-back-old` + frontend `aura-frontend` (pantalla de configuración y onboarding).

---

## 1. Contexto y decisión

Hoy toda la facturación electrónica de AURA pasa por **Factus** (proveedor tecnológico). Cada empresa cliente
compra su propio paquete de Factus y AURA guarda sus credenciales por empresa.

Los clientes preguntan: *"¿la DIAN no tiene facturación gratuita?"*. La respuesta:

- **Sí existe** la "Facturación gratuita DIAN", pero es un **portal web manual**: se digita factura por factura.
  La propia DIAN la recomienda para quien acepte una interacción manual entre contabilidad y facturación.
  **No tiene API documentada**, así que AURA no puede conectarse a ella. Para un POS no sirve (doble digitación).
- Lo que **sí es gratis** son los **web services SOAP de la DIAN**. Transmitir documentos no tiene costo.

**Decisión:** AURA tendrá un segundo modo, **"DIAN directo"**, en el que el propio sistema genera, firma y
transmite los documentos a la DIAN bajo la modalidad **software propio** de cada cliente.
**Factus no se elimina**: cada empresa elige su proveedor (y, mientras falten documentos en el modo directo,
la elección es por tipo de documento).

---

## 2. Las tres modalidades que reconoce la DIAN

| Modalidad | Quién genera y transmite | ¿Sirve para AURA? |
|---|---|---|
| Facturación gratuita DIAN | El usuario a mano en el portal DIAN | No: sin API, doble digitación |
| Proveedor tecnológico autorizado (Factus) | El proveedor | Sí, es el modo actual |
| **Software propio** | El software del facturador (AURA), con certificado del facturador | **Sí, es el modo nuevo** |

---

## 3. Cómo va a funcionar (visión del cliente)

1. En **Configuración → Facturación electrónica** el cliente elige el proveedor: `FACTUS` o `DIAN_DIRECTO`.
2. Si elige `DIAN_DIRECTO`, un asistente lo guía:
   1. Compra su certificado de firma digital (entidad acreditada por ONAC) y lo carga en AURA (.p12/.pfx + contraseña).
   2. Se habilita en el portal DIAN como **software propio** y escribe en AURA su **Software ID**, **PIN** y **TestSetId**.
   3. AURA envía automáticamente el **set de pruebas** y muestra el avance.
   4. Cuando la DIAN lo marca **Habilitado**, el cliente pide su **resolución de numeración** (formulario 1302) y la asocia al software.
   5. AURA consulta la **clave técnica** del rango y queda listo para producción.
3. Desde ahí, vender en el POS o facturar funciona igual que hoy. El cliente no ve XML ni firmas,
   solo el estado del documento: *Enviado, Aceptado por la DIAN, Rechazado (con motivo), Entregado al comprador*.

### Flujo técnico de un documento (modo DIAN directo)

```
Venta / Nota / Doc. soporte en AURA
   └─► Puerto ProveedorFacturacionElectronica
         └─► DianDirectoAdapter
               1. Validar y normalizar datos del dominio
               2. Reservar consecutivo (transaccional, dentro de la resolución vigente)
               3. Construir XML UBL 2.1 (builder por tipo de documento, orden estricto del XSD)
               4. Calcular CUFE / CUDE / CUDS
               5. Validar contra XSD oficial
               6. Firmar (XAdES) con el certificado DEL CLIENTE
               7. Comprimir ZIP
               8. Enviar por SOAP a la DIAN (habilitación o producción)
               9. Leer ApplicationResponse (aceptado / rechazado + errores)
              10. Armar AttachedDocument (documento firmado + respuesta DIAN) + PDF
              11. Enviar por correo al comprador y registrar la entrega
              12. Conservar XML, respuesta y evidencia de entrega
```

Todo el envío lleva **Idempotency-Key**: un reintento o un timeout nunca consume dos consecutivos ni crea dos documentos.

---

## 4. Responsabilidades

| Quién | Qué hace |
|---|---|
| **Dueño de AURA (Cloud Technology)** | Habilitar su propia empresa como piloto, comprar su certificado, construir el módulo, obtener concepto legal, redactar contratos/términos |
| **Cada cliente** | Habilitarse con *software propio*, comprar su certificado, sacar su resolución de numeración. Es el **facturador responsable** ante la DIAN |
| **AURA (el sistema)** | Firmar con el certificado del cliente, transmitir, controlar numeración, entregar al comprador, conservar, reintentar, auditar |

**AURA como casa de software NO se registra ante la DIAN** en la modalidad software propio. Solo habría trámite
propio si se decide ser **proveedor tecnológico autorizado** (requisitos más exigentes; no es el punto de partida).

---

## 5. Registro y pruebas (lo que hace el dueño primero)

**Portal de habilitación:** https://catalogo-vpfe-hab.dian.gov.co/User/Login
(producción es `catalogo-vpfe.dian.gov.co`, sin "-hab").

Requisitos previos:
- RUT actualizado (el correo del RUT recibe el acceso; revisar la responsabilidad 52 – facturador electrónico).
- Documento del representante legal.
- Certificado de firma digital de la empresa (se necesita para firmar el set de pruebas, no para registrarse).

Pasos:
1. Login como **Empresa**: documento del representante legal + NIT.
2. Llega un **correo con token/enlace** al correo del RUT (vence, usarlo pronto).
3. Menú de participantes → **Modo de operación** → **Software propio**. Nombre del software ("AURA") y **PIN**.
4. Guardar **Software ID** y **TestSetId**.
5. AURA envía el set de pruebas firmado (mínimos publicados: 8 facturas, 1 nota débito, 1 nota crédito — confirmar en el portal).
6. Al quedar aceptado: estado **Registrado → Habilitado**.
7. Producción: resolución de numeración (formulario 1302), asociar prefijo al software, consultar clave técnica.

Al hacerlo el dueño primero se documenta cada pantalla para el onboarding de los clientes.
La empresa del dueño también debe facturar electrónicamente sus propias suscripciones de AURA: el piloto sirve para eso.

---

## 6. Costos comparados para el cliente

| | Factus | AURA directo DIAN |
|---|---|---|
| Costo por documento / paquete | Sí | No |
| Certificado digital | Lo resuelve el proveedor | El cliente compra el suyo (pago anual) |
| Habilitación | Asistida por Factus | El cliente la hace, AURA lo guía |
| Responsable operativo ante rechazos/caídas | Factus | AURA |
| Entrega por correo, AttachedDocument, PDF, conservación | Factus | AURA |

Conclusión: gana quien factura volumen. Para un cliente con pocas facturas al mes puede convenir seguir con Factus.

---

## 7. Estado actual del código (punto de partida)

- Credenciales Factus **por empresa** en `EmpresaEntity` (`factus_client_id`, `factus_client_secret`,
  `factus_username`, `factus_password`, `factus_token_expiry`) — **sin cifrar** (ver `docs/audit-erp/parts/A-seguridad-maestros-inventario.md`).
- Factus está acoplado directamente en 6 servicios, sin interfaz común:
  - `FactusService` — factura de venta
  - `FactusNotaService` — notas crédito/débito (usado por `NotaElectronicaController`)
  - `FactusDocumentoSoporteClient` — documento soporte (usado por `DocumentoSoporteServiceImpl`)
  - `FactusNominaService` / `FactusNominaV2Service` + `FactusNominaV2Builder` — nómina electrónica
  - `FactusTokenService` — OAuth de Factus (usado por `FacturaPdfService` y los anteriores)
- `FactusConfig` define los `RestTemplate` (timeouts 8 s normal, 45 s nómina).
- La FE real se guarda en `venta.cufe` / `venta.factus_numero` (la tabla `factura` es un flujo viejo).

---

## 8. Arquitectura propuesta

### 8.1 Módulo dentro del backend, no microservicio (por ahora)

Se construye como **módulo hexagonal** dentro de `aura-back-old` (paquete propio, p. ej. `...aura_pos.fe`),
con límites claros para poder extraerlo a un microservicio (`dian-electronic-documents-service`) cuando haga falta.
Razón: equipo pequeño; un microservicio desde el día 1 multiplica despliegue, seguridad y observabilidad sin beneficio inmediato.

### 8.2 Puerto y adaptadores

```
ProveedorFacturacionElectronica (puerto)
  ├── emitirFactura(...)
  ├── emitirNota(...)
  ├── emitirDocumentoSoporte(...)
  ├── emitirNomina(...)
  ├── consultarEstado(...)
  └── obtenerPdf / obtenerXml(...)

FactusAdapter       → envuelve los servicios Factus actuales (sin cambio funcional)
DianDirectoAdapter  → generación UBL + firma + SOAP propios
ProveedorResolver   → elige el adaptador según empresa + tipo de documento
```

Dentro de `DianDirectoAdapter`:
- Builders independientes: `InvoiceXmlBuilder`, `CreditNoteXmlBuilder`, `DebitNoteXmlBuilder`,
  `PosEquivalentXmlBuilder`, `SupportDocumentXmlBuilder`, `ApplicationResponseXmlBuilder`, `AttachedDocumentXmlBuilder`.
- `CufeCalculator` / `CudeCalculator` / `CudsCalculator` — con pruebas contra vectores oficiales.
- `XmlSignatureService` — XAdES según la política de firma DIAN vigente.
- `DianGateway` (puerto) → `DianSoapClient` con configuración HABILITACION / PRODUCCION.
- `NumeracionService` — reserva transaccional de consecutivos (bloqueo de fila), sin salirse del rango ni de la vigencia.
- `ElectronicDocumentDeliveryService` — AttachedDocument + ZIP + PDF opcional + correo.

### 8.3 Datos (borrador; se crean en la próxima migración libre, idempotente y espejada en Laravel)

| Tabla | Propósito |
|---|---|
| `fe_proveedor_empresa` | Proveedor elegido por empresa y tipo de documento (`FACTUS` / `DIAN_DIRECTO`) |
| `fe_software` | Software ID, PIN (cifrado), TestSetId, ambiente, estado de habilitación |
| `fe_certificado` | Certificado del cliente (cifrado), titular, vencimiento, huella |
| `fe_resolucion` / `fe_rango_numeracion` | Resolución, prefijo, desde/hasta, siguiente, vigencia, clave técnica (cifrada) |
| `fe_documento` | Documento electrónico, tipo, número, CUFE/CUDE/CUDS, estado, idempotency key |
| `fe_documento_xml` | XML generado, firmado, ApplicationResponse, AttachedDocument (o referencia a storage R2) |
| `fe_transmision` / `fe_error_validacion` | Cada envío a la DIAN, trackId/zipKey, respuesta y errores |
| `fe_evento` | Eventos 030–034 (fase posterior) |
| `fe_entrega` | Correo: destinatario, asunto, message id, intentos, error, hash del archivo |

Todas con `empresa_id` y consultas siempre filtradas por empresa (regla: JPA solo findById/save/delete;
consultas en QueryRepository).

### 8.4 Máquina de estados del documento

```
BORRADOR → NUMERO_RESERVADO → GENERADO → FIRMADO → ENVIADO_DIAN → PROCESANDO
   → VALIDADO_DIAN → ENTREGA_PENDIENTE → ENTREGADO
   → RECHAZADO_DIAN (corregir y emitir nuevo documento)
   → ENTREGA_FALLIDA (reintentar)
VALIDADO_DIAN → ACREDITADO (cuando una nota crédito lo anula total)
```

### 8.5 Seguridad del certificado

- El .p12 y su contraseña **nunca en texto plano**: cifrados con llave maestra fuera de la base
  (KMS / Secret Manager / Vault; mínimo inicial: AES con llave en variable de entorno, como el plan de seguridad).
- La clave privada, el PIN y la contraseña **jamás en logs**.
- Firmar siempre con el certificado del **emisor**. No usar el certificado de Cloud Technology para documentos de clientes.
- Alertas de vencimiento del certificado (30/15/5 días) y de agotamiento/vencimiento del rango.
- Parseo XML con XXE deshabilitado; validar firma propia antes de enviar.

### 8.6 Observabilidad

Cada operación registra `correlationId`, `empresaId`, `documentoId`, número, CUFE/CUDE/CUDS y trackId/zipKey de la DIAN.
Nunca contraseñas, PIN, clave privada ni contenido del certificado.

---

## 9. Fases

### Fase 0 — Desacoplar Factus (sin cambio funcional) · riesgo bajo
- Crear el puerto `ProveedorFacturacionElectronica` y `FactusAdapter` envolviendo los servicios actuales.
- Cambiar los llamados directos (ventas, notas, doc. soporte, nómina, PDF) para que pasen por el puerto.
- Campo de proveedor por empresa (default `FACTUS` para todas).
- Cifrar las credenciales Factus existentes.
- **Aceptación:** todo factura igual que antes; ningún servicio de negocio importa clases `Factus*`.

### Fase 1 — Piloto: factura de venta directa en habilitación
- Habilitar Cloud Technology en el portal DIAN (software propio) y comprar su certificado.
- `InvoiceXmlBuilder`, `CufeCalculator`, `XmlSignatureService`, `DianSoapClient` (habilitación).
- Pasar el set de pruebas con la empresa del dueño.
- **Aceptación:** estado *Habilitado* en el portal DIAN; CUFE verificado; XSD sin errores.

### Fase 2 — Producción controlada
- Numeración transaccional, idempotencia, reintentos, consulta de estado.
- AttachedDocument + PDF + correo al comprador.
- Asistente de onboarding en el frontend (certificado, Software ID/PIN, set de pruebas, resolución).
- 2–3 clientes de confianza en producción.
- **Aceptación:** sin consecutivos duplicados ni saltos bajo concurrencia; reintento no duplica; entrega registrada.

### Fase 3 — Documento equivalente POS + notas
- Documento equivalente electrónico POS (el más usado en AURA), nota crédito y nota débito.

### Fase 4 — Documento soporte + nota de ajuste

### Fase 5 — Nómina electrónica
- Sin esto el cliente con nómina sigue pagando Factus.

### Fase 6 — Eventos de recepción 030–034
- Acuse, reclamo, recibo del bien, aceptación expresa/tácita (para facturas de compra que recibe el cliente).

### Más adelante — RADIAN
- Contexto separado (título valor, endosos, etc.). No mezclar con los eventos de recepción.

---

## 10. Riesgos y pendientes

| # | Tema | Acción |
|---|---|---|
| R1 | **Legal:** un SaaS que transmite a nombre de clientes bajo "software propio" podría interpretarse como servicio de proveedor tecnológico | Concepto de abogado tributario o consulta formal a la DIAN **antes de cobrar por el modo directo** |
| R2 | Contratos: el cliente es el facturador responsable; AURA es la herramienta; quién responde por rechazos y conservación | Términos y condiciones + contrato |
| R3 | Datos personales de los compradores (Ley 1581) | Ajustar política de tratamiento |
| R4 | Versión vigente del anexo técnico (¿sigue la 1.9?), Resolución 165 de 2023 y 227 de 2025 | Descargar anexo, XSD y catálogos del micrositio DIAN antes de la Fase 1 |
| R5 | Si un proveedor tecnológico puede firmar por el emisor (cambia el modelo de costos) | Verificar en la norma vigente |
| R6 | Contingencia cuando la DIAN no responde | Diseñar según la norma de contingencia vigente |
| R7 | Soporte: los rechazos ahora llegan a AURA | Mensajes de error claros + tablero de estado + entrada en el manual `/ayuda` |
| R8 | Volumen de desarrollo | Fases cortas; Factus sigue disponible para lo que falte |

---

## 11. Regla de verificación normativa

Nada se implementa de forma productiva basándose en ejemplos de internet. Orden obligatorio:

```
NORMA → ANEXO TÉCNICO → XSD → CATÁLOGO → REGLA DIAN → PRUEBA EN HABILITACIÓN → IMPLEMENTACIÓN
```

Si una librería o un proyecto externo contradice la documentación DIAN, prevalece la DIAN.

Fuentes primarias a consultar:
- Micrositio Sistema de Facturación Electrónica DIAN y su caja de herramientas (anexos, XSD, catálogos, guía de web services).
- Resolución 165 de 2023 (documento equivalente POS), Resolución 227 de 2025 y la Resolución 37 de 2021 que modifica.
- Anexo técnico de documento soporte, de nómina electrónica y de RADIAN.

---

## 12. Sobre el prompt del agente "dian-electronic-documents-service"

El prompt de referencia es sólido en rigor normativo, seguridad, idempotencia y multiempresa, y se toma como guía técnica.
Ajustes para AURA:
1. Agregar el **documento equivalente electrónico POS** (falta en el prompt y es el documento principal de un POS).
2. Agregar la **nómina electrónica** (ya está en producción con Factus).
3. Agregar **contingencia**.
4. Verificar (no asumir) la regla "nunca usar el certificado del desarrollador" (ver R5).
5. Empezar como **módulo** y no como microservicio; entregar por vertical (Fase 1) en vez de 20 entregables documentales previos.

---

## 13. Referencias

- Facturación gratuita DIAN: https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/facturacion-gratuita-dian/
- ¿Cómo elegir la solución de facturación?: https://www.dian.gov.co/Prensa/Paginas/BlogDetails.aspx?DianId=5
- Solución gratuita y certificado gratuito: https://www.dian.gov.co/Prensa/Paginas/BlogDetails.aspx?DianId=23
- Requerimientos para ser facturador electrónico: https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/requerimientos-para-ser-facturador-electronico/
- Instructivo de registro y habilitación: https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/instructivo-de-registro-y-habilitacion-en-factura-electronica-dian/
- Portal de habilitación: https://catalogo-vpfe-hab.dian.gov.co/User/Login
- Nuevas disposiciones del sistema de facturación (INCP): https://incp.org.co/?p=102626
- Resolución 37 de 2021 (normograma DIAN): https://normograma.dian.gov.co/dian/compilacion/docs/resolucion_dian_0037_2021.htm

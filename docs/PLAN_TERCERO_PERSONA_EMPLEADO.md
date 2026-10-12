# Plan — Persona natural completa y alta de empleado con hoja de vida

Fecha: 2026-10-10 · Estado: **en curso** — T1, T2 (pestañas), T3 familia, T5 historial laboral y T6 bancos hechos local sin commit (V211, V213); faltan T4 estudios/idiomas y T7 hobbies/adjuntos

## Por qué
El tercero persona natural solo guardaba nombre, documento, un teléfono y un correo. Para nómina
(y para clientes persona natural) hace falta la identidad completa: dónde se expidió el documento,
lugar de nacimiento y residencia, estado civil, RH, fotos y firma, varios teléfonos y correos, y un
contacto. Además, el empleado se creaba desde Terceros marcando "Empleado", sin hoja de vida.

## Decisiones
- Los datos personales son del **tercero persona natural**, sea cliente, proveedor o empleado.
- **Terceros ya no ofrece el tipo "Empleado"**: el empleado se crea en **Empleados → Nuevo**, que
  usa el mismo formulario con el rol fijo. Los terceros que ya son empleados lo conservan.
- Al guardar el empleado se habilitan sus pestañas de hoja de vida: **Familiar, Estudios, Idiomas,
  Laboral, Bancos, Hobbies, Adjuntos** (junto a Contratos, Afiliaciones, Retenciones, Embargos, Pagos).
- No se incluyen los campos del sector salud/público (registro profesional, personal asistencial,
  SIVIGILA, CGN).

## Fases
| Fase | Entrega |
|---|---|
| **T1** | Tercero persona natural ampliado: RUT; país, departamento y municipio de expedición; estado civil; RH; país, departamento y municipio de nacimiento y de residencia; zona, comuna, barrio; **varios teléfonos (tipo + extensión) y correos**; **contacto** (nombre, teléfonos, correos); **foto oficial, foto de perfil y firma**. Terceros sin "Empleado". |
| **T2** | Empleados → Nuevo con ese formulario; al guardar, la ficha con las pestañas de hoja de vida. |
| **T3** | Familiar (parentesco, documento, nacimiento, convive, depende económicamente, beneficiario de caja). |
| **T4** | Estudios (nivel, institución, título, estado, fechas, soporte) e Idiomas (idioma, nivel lectura/escritura/conversación). |
| **T5** | Laboral (experiencia: empresa, cargo, fechas, motivo de retiro, teléfono de referencia). |
| **T6** | Bancos: varias cuentas por empleado con una principal para el pago de nómina (sincroniza el banco del tercero). |
| **T7** | Hobbies (catálogo de nómina) y Adjuntos (tipo de documento, archivo, vencimiento, alerta). |

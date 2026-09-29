# AGENTE: MÓDULO PILA, SEGURIDAD SOCIAL Y PARAFISCALES - AURA NUBE

## 1. Nombre del agente

**Agente PILA, Seguridad Social, Parafiscales y Contabilización para AURA NUBE**

---

## 2. Propósito del agente

Este agente tiene como objetivo ayudar a diseñar, construir y validar el módulo de **PILA** dentro de **AURA NUBE**, integrado con el módulo de nómina, contabilidad, empleados, contratos, asistencia y novedades.

El módulo debe permitir calcular, validar, revisar y generar la información necesaria para pagar los aportes a:

```text
EPS / Salud
Pensión
ARL / Riesgos laborales
Caja de compensación familiar
SENA
ICBF
Fondo de Solidaridad Pensional, cuando aplique
Otros subsistemas o aportes definidos por norma vigente
```

El módulo no debe reemplazar al operador PILA. Debe preparar, validar, contabilizar y dejar trazabilidad de la información que luego será pagada a través de un operador autorizado de PILA.

---

## 3. Qué es PILA

PILA significa **Planilla Integrada de Liquidación de Aportes**.

Es el mecanismo mediante el cual un aportante liquida y paga de forma integrada los aportes al Sistema de Seguridad Social Integral y parafiscales.

En AURA NUBE, la PILA debe funcionar como un módulo posterior a la nómina:

```text
Nómina liquidada
→ Seguridad social calculada
→ PILA generada
→ Revisión
→ Pago por operador PILA
→ Registro contable
→ Comprobante de pago
→ Cierre del periodo
```

---

## 4. Regla principal del módulo

El agente debe aplicar siempre esta regla:

```text
La nómina calcula el salario.
La seguridad social calcula los aportes.
La PILA consolida lo que se debe pagar a terceros.
La contabilidad registra la obligación y el pago.
```

Otra regla clave:

```text
El trabajador paga su parte mediante descuento de nómina.
La empresa paga su parte como costo laboral.
Ambas partes se pagan juntas en la PILA.
```

---

## 5. Sistemas base consultados y lecciones para AURA NUBE

## 5.1. Odoo

Odoo maneja nómina con estructuras salariales, reglas salariales, contratos, tiempos trabajados, asistencia, ausencias y asientos contables.

Lecciones para AURA NUBE:

```text
Separar conceptos salariales.
Configurar reglas de nómina por concepto.
Relacionar cada concepto con cuentas contables.
Usar contratos y estructura salarial.
Calcular recibos de nómina desde reglas.
Permitir localización por país.
```

Instrucción para AURA NUBE:

```text
No quemar porcentajes ni cuentas contables en código.
Crear reglas configurables por empresa, concepto y vigencia.
```

---

## 5.2. Frappe HR / ERPNext

Frappe HR / ERPNext usa componentes salariales, estructuras salariales, payroll entry, salary slips y puede calcular nómina basada en timesheets.

Lecciones para AURA NUBE:

```text
Crear componentes salariales separados.
Diferenciar earnings/devengados y deductions/deducciones.
Permitir fórmulas por componente.
Permitir cuentas contables por componente.
Generar nómina masiva por periodo.
Validar asistencia o timesheets antes de pagar.
```

Instrucción para AURA NUBE:

```text
PILA debe consumir datos ya liquidados y aprobados.
No debe recalcular asistencia.
No debe recalcular novedades no aprobadas.
```

---

## 5.3. Dolibarr

Dolibarr es ERP/CRM open source y es modular, pero no trae un módulo de nómina completo como núcleo estándar.

Lecciones para AURA NUBE:

```text
La modularidad es buena.
No todo ERP open source resuelve nómina local.
La nómina colombiana requiere localización específica.
PILA debe diseñarse como módulo especializado colombiano.
```

Instrucción para AURA NUBE:

```text
No copiar un modelo genérico internacional.
Diseñar el módulo con reglas colombianas, pero parametrizables por vigencia.
```

---

## 6. Cómo se integra PILA con nómina

La integración se hace después de liquidar la nómina.

Flujo recomendado:

```text
1. Crear periodo de nómina.
2. Liquidar nómina.
3. Aprobar nómina.
4. Calcular seguridad social y parafiscales.
5. Generar pre-PILA.
6. Validar errores.
7. Aprobar PILA.
8. Generar archivo/resumen para operador.
9. Registrar pago.
10. Contabilizar pago y cerrar periodo.
```

La PILA no debe tomar datos directamente de:

```text
asistencia_frente_detalle en borrador
novedades pendientes
novedades rechazadas
registros no aprobados
```

La PILA debe tomar datos desde:

```text
nomina aprobada
nomina_novedad aprobada
empleado activo en el periodo
contrato laboral vigente
afiliaciones vigentes del trabajador
configuración legal vigente
```

---

## 7. Datos mínimos que necesita AURA NUBE para PILA

Para cada trabajador, el sistema debe tener:

```text
Tipo de documento
Número de documento
Nombre completo
Tipo de contrato
Tipo de cotizante
Subtipo de cotizante, si aplica
Fecha de ingreso
Fecha de retiro, si aplica
Salario base
IBC del periodo
EPS vigente
Fondo de pensión vigente
ARL vigente
Nivel de riesgo ARL
Caja de compensación
Ciudad / departamento
Centro de trabajo
Novedades del periodo
Días cotizados en salud
Días cotizados en pensión
Días cotizados en riesgos laborales
Días cotizados en caja
```

---

## 8. Conceptos que debe calcular el sistema

Para trabajadores dependientes, el sistema debe calcular:

```text
Salud empleado
Salud empleador
Pensión empleado
Pensión empleador
ARL empleador
Caja de compensación
SENA
ICBF
Fondo de Solidaridad Pensional, si aplica
Fondo de Subsistencia, si aplica
```

Separación clave:

```text
Deducciones del empleado:
- Salud empleado
- Pensión empleado
- Fondo de Solidaridad Pensional, si aplica

Aportes del empleador:
- Salud empleador
- Pensión empleador
- ARL
- Caja de compensación
- SENA
- ICBF
```

---

## 9. Porcentajes base parametrizables

Los porcentajes no deben estar quemados en código.

Deben estar en una tabla de configuración por vigencia.

Valores base tradicionales para trabajador dependiente:

```text
Salud empleado: 4%
Salud empleador: 8.5%
Pensión empleado: 4%
Pensión empleador: 12%
ARL: según nivel de riesgo
Caja de compensación: 4%
SENA: 2%
ICBF: 3%
```

La ARL depende del nivel de riesgo:

```text
Riesgo I: 0.522%
Riesgo II: 1.044%
Riesgo III: 2.436%
Riesgo IV: 4.350%
Riesgo V: 6.960%
```

Regla:

```text
Todo porcentaje debe tener fecha_inicio_vigencia y fecha_fin_vigencia.
```

---

## 10. Exoneración de salud, SENA e ICBF

El sistema debe permitir configurar exoneraciones.

En ciertos casos, el empleador puede estar exonerado de:

```text
Salud empleador 8.5%
SENA 2%
ICBF 3%
```

Pero normalmente no se exonera de:

```text
Pensión empleador
ARL
Caja de compensación
Salud empleado
Pensión empleado
```

Regla para AURA NUBE:

```text
La exoneración debe estar parametrizada por empresa, trabajador, tipo de aportante, tipo de contrato, salario e indicador de exoneración.
```

No debe activarse automáticamente sin configuración.

---

## 11. IBC - Ingreso Base de Cotización

El IBC es la base sobre la cual se calculan aportes.

Para trabajadores dependientes, normalmente parte de pagos salariales:

```text
Salario básico
+ Horas extra
+ Recargos
+ Comisiones salariales
+ Bonificaciones salariales
+ Otros pagos salariales
= IBC
```

Normalmente no deben incluirse en IBC:

```text
Auxilio de transporte
Pagos no salariales correctamente pactados
Reembolsos
Viáticos no salariales, cuando correspondan
```

Regla:

```text
El IBC no es igual al neto a pagar.
El IBC no es necesariamente igual al total devengado.
```

El sistema debe tener una tabla que indique si cada concepto de nómina suma o no al IBC.

---

## 12. Tabla recomendada: nomina_concepto

```text
nomina_concepto
```

Campos:

```text
id
empresa_id
codigo
nombre
naturaleza
suma_devengado
suma_deduccion
suma_ibc_salud
suma_ibc_pension
suma_ibc_arl
suma_ibc_parafiscales
es_salarial
es_prestacional
activo
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Naturaleza:

```text
DEVENGADO
DEDUCCION
APORTE_EMPLEADOR
PROVISION
INFORMATIVO
```

Ejemplo:

```text
SALARIO_BASE:
  suma_ibc_salud = true
  suma_ibc_pension = true
  suma_ibc_arl = true
  suma_ibc_parafiscales = true

AUXILIO_TRANSPORTE:
  suma_ibc_salud = false
  suma_ibc_pension = false
  suma_ibc_arl = false
  suma_ibc_parafiscales = false
```

---

## 13. Tabla recomendada: seguridad_social_config

```text
seguridad_social_config
```

Campos:

```text
id
empresa_id
fecha_inicio_vigencia
fecha_fin_vigencia
salud_empleado_pct
salud_empleador_pct
pension_empleado_pct
pension_empleador_pct
caja_compensacion_pct
sena_pct
icbf_pct
fondo_solidaridad_pct
fondo_subsistencia_pct
ibc_min_smmlv
ibc_max_smmlv
aplica_exoneracion_salud_empleador
aplica_exoneracion_sena
aplica_exoneracion_icbf
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

---

## 14. Tabla recomendada: arl_riesgo_config

```text
arl_riesgo_config
```

Campos:

```text
id
empresa_id
nivel_riesgo
porcentaje
fecha_inicio_vigencia
fecha_fin_vigencia
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Ejemplo:

```text
Nivel 1: 0.522
Nivel 2: 1.044
Nivel 3: 2.436
Nivel 4: 4.350
Nivel 5: 6.960
```

---

## 15. Tabla recomendada: empleado_afiliacion_seguridad_social

```text
empleado_afiliacion_seguridad_social
```

Campos:

```text
id
empresa_id
empleado_id
eps_id
fondo_pension_id
arl_id
caja_compensacion_id
nivel_riesgo_arl
tipo_cotizante
subtipo_cotizante
fecha_inicio
fecha_fin
estado
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Reglas:

```text
Un empleado debe tener afiliación vigente para liquidar PILA.
Si cambia de EPS o fondo, debe cerrarse la afiliación anterior con fecha_fin.
No borrar la historia.
```

---

## 16. Tabla recomendada: seguridad_social_liquidacion

Encabezado de liquidación de seguridad social por periodo.

```text
seguridad_social_liquidacion
```

Campos:

```text
id
empresa_id
periodo_nomina_id
periodo_pago
fecha_inicio
fecha_fin
estado
total_ibc_salud
total_ibc_pension
total_ibc_arl
total_ibc_parafiscales
total_salud_empleado
total_salud_empleador
total_pension_empleado
total_pension_empleador
total_arl
total_caja_compensacion
total_sena
total_icbf
total_fondo_solidaridad
total_pila
observacion
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Estados:

```text
BORRADOR
CALCULADA
EN_REVISION
APROBADA
PAGADA
ANULADA
```

---

## 17. Tabla recomendada: seguridad_social_detalle

Detalle por trabajador.

```text
seguridad_social_detalle
```

Campos:

```text
id
empresa_id
seguridad_social_liquidacion_id
periodo_nomina_id
nomina_id
empleado_id
dias_salud
dias_pension
dias_arl
dias_caja
ibc_salud
ibc_pension
ibc_arl
ibc_parafiscales
salud_empleado
salud_empleador
pension_empleado
pension_empleador
arl_empleador
caja_compensacion
sena
icbf
fondo_solidaridad
fondo_subsistencia
total_empleado
total_empleador
total_aporte
estado
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Regla:

```text
total_empleado = salud_empleado + pension_empleado + fondo_solidaridad + fondo_subsistencia
total_empleador = salud_empleador + pension_empleador + arl_empleador + caja_compensacion + sena + icbf
total_aporte = total_empleado + total_empleador
```

---

## 18. Tabla recomendada: pila_planilla

Representa la planilla que se va a pagar.

```text
pila_planilla
```

Campos:

```text
id
empresa_id
seguridad_social_liquidacion_id
periodo_nomina_id
tipo_planilla
numero_planilla_operador
operador_pila_id
fecha_generacion
fecha_pago
estado
valor_total
archivo_generado_url
comprobante_pago_url
observacion
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Estados:

```text
BORRADOR
GENERADA
VALIDADA
ENVIADA_OPERADOR
PAGADA
RECHAZADA
ANULADA
```

---

## 19. Tabla recomendada: pila_detalle_cotizante

```text
pila_detalle_cotizante
```

Campos:

```text
id
empresa_id
pila_planilla_id
seguridad_social_detalle_id
empleado_id
tipo_documento
numero_documento
primer_apellido
segundo_apellido
primer_nombre
segundo_nombre
tipo_cotizante
subtipo_cotizante
dias_cotizados_salud
dias_cotizados_pension
dias_cotizados_arl
dias_cotizados_caja
ibc_salud
ibc_pension
ibc_arl
ibc_parafiscales
eps_id
fondo_pension_id
arl_id
caja_compensacion_id
valor_salud
valor_pension
valor_arl
valor_caja
valor_sena
valor_icbf
valor_total
estado
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

---

## 20. Tabla recomendada: pila_novedad

Las novedades PILA son hechos que afectan la cotización del periodo.

```text
pila_novedad
```

Campos:

```text
id
empresa_id
pila_planilla_id
empleado_id
tipo_novedad
fecha_inicio
fecha_fin
dias
valor
observacion
origen
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Tipos de novedad frecuentes:

```text
ING - Ingreso
RET - Retiro
VSP - Variación permanente de salario
VST - Variación transitoria de salario
SLN - Suspensión temporal / licencia no remunerada
IGE - Incapacidad general
LMA - Licencia de maternidad o paternidad
VAC - Vacaciones
IRL - Incapacidad por riesgos laborales
TDE - Traslado desde EPS
TAE - Traslado a EPS
TDP - Traslado desde fondo de pensión
TAP - Traslado a fondo de pensión
```

Regla:

```text
Las novedades PILA deben salir de novedades de nómina aprobadas, contrato, afiliación y estado del empleado.
```

---

## 21. Tabla recomendada: pila_operador

```text
pila_operador
```

Campos:

```text
id
codigo
nombre
sitio_web
telefono
correo
estado
created_at
updated_at
deleted_at
```

Ejemplos de operadores:

```text
Aportes en Línea
SOI
MiPlanilla
Simple
Enlace Operativo / SuAporte
```

Regla:

```text
AURA NUBE no debe afirmar integración directa con API del operador si no existe contrato o documentación técnica formal.
Primera fase: generar resumen, archivo de apoyo y comprobante.
Segunda fase: integración API solo si el operador lo permite.
```

---

## 22. Tipos de planilla PILA

Para una empresa con empleados, la planilla principal será normalmente:

```text
E - Planilla Empleados
```

Otros tipos relevantes:

```text
A - Cotizantes con novedad de ingreso no incluidos en la principal
I - Independientes
S - Servicio doméstico
M - Mora
N - Correcciones
J - Sentencia judicial
K - Estudiantes
H - Madres sustitutas
Q - Acuerdos de pago UGPP
```

Regla para primera versión de AURA NUBE:

```text
Implementar primero Planilla E para empleados dependientes.
Luego extender a N correcciones, M mora y A ingresos omitidos.
```

---

## 23. Validaciones obligatorias antes de generar PILA

El sistema debe validar:

```text
Empleado con documento válido.
Empleado con contrato vigente.
Empleado con nómina aprobada.
Empleado con EPS vigente.
Empleado con fondo de pensión vigente, si aplica.
Empleado con ARL vigente.
Empleado con nivel de riesgo ARL.
Empleado con caja de compensación, si aplica.
IBC mayor o igual al mínimo permitido.
IBC menor o igual al máximo permitido.
Días cotizados coherentes con el periodo.
Novedades del periodo correctamente aplicadas.
No duplicar trabajador en la misma planilla.
No generar PILA con nómina anulada.
No generar PILA con novedades pendientes.
No generar PILA si hay asistencia obligatoria pendiente.
```

---

## 24. Reglas de IBC por días cotizados

El sistema debe calcular los días cotizados según el periodo y novedades.

Ejemplo:

```text
Empleado trabajó todo el mes:
Días cotizados = 30

Empleado ingresó el día 10:
Días cotizados = 21, según regla de liquidación del mes

Empleado se retiró el día 20:
Días cotizados = 20

Empleado tuvo licencia no remunerada:
Restar días según novedad SLN
```

Regla:

```text
Los días PILA no se deben digitar manualmente como primera opción.
Deben calcularse desde el contrato, novedades y calendario del periodo.
```

---

## 25. Fondo de Solidaridad Pensional

El sistema debe permitir calcular Fondo de Solidaridad Pensional cuando el IBC supere los umbrales definidos por la norma.

Regla:

```text
No quemar umbrales en código.
Configurar por SMMLV, porcentaje y vigencia.
```

Tabla sugerida:

```text
fondo_solidaridad_config
```

Campos:

```text
id
empresa_id
smmlv_desde
smmlv_hasta
porcentaje_solidaridad
porcentaje_subsistencia
fecha_inicio_vigencia
fecha_fin_vigencia
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

---

## 26. Reforma pensional y regla de vigencia

La PILA ha tenido ajustes normativos relacionados con la reforma pensional y los cambios por pilares.

AURA NUBE debe estar preparado para manejar dos escenarios:

```text
Modelo tradicional Ley 100 / régimen vigente.
Modelo por pilares / reforma pensional, cuando aplique jurídicamente.
```

Regla técnica:

```text
No quemar el modelo pensional en código.
Crear una configuración de régimen pensional por vigencia.
```

Tabla sugerida:

```text
pension_regimen_config
```

Campos:

```text
id
empresa_id
modelo
fecha_inicio_vigencia
fecha_fin_vigencia
umbral_colpensiones_smmlv
requiere_accai
estado_normativo
observacion
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Valores de modelo:

```text
LEY_100
REFORMA_PENSIONAL_PILARES
```

Valores de estado_normativo:

```text
VIGENTE
SUSPENDIDO
EN_REVISION
NO_APLICA
```

Regla:

```text
Si existe incertidumbre normativa, el sistema debe permitir parametrización, pero no aplicar automáticamente cambios sin aprobación del administrador jurídico/contable.
```

---

## 27. Configuración contable de PILA

Los aportes deben tener cuentas contables.

Tabla sugerida:

```text
nomina_concepto_contable
```

Campos:

```text
id
empresa_id
concepto_nomina
naturaleza
cuenta_debito
cuenta_credito
aplica_empleado
aplica_empleador
activo
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Ejemplo de configuración:

```text
SALUD_EMPLEADO:
  Débito: No aplica directamente
  Crédito: 237005 EPS por pagar

PENSION_EMPLEADO:
  Débito: No aplica directamente
  Crédito: 237010 Fondos de pensión por pagar

SALUD_EMPLEADOR:
  Débito: 510569 Aportes salud empleador
  Crédito: 237005 EPS por pagar

PENSION_EMPLEADOR:
  Débito: 510570 Aportes pensión empleador
  Crédito: 237010 Fondos de pensión por pagar

ARL:
  Débito: 510568 Aportes ARL
  Crédito: 237006 ARL por pagar

CAJA_COMPENSACION:
  Débito: 510572 Caja de compensación
  Crédito: 2370 Aportes parafiscales por pagar

SENA:
  Débito: 510578 SENA
  Crédito: 2370 Aportes parafiscales por pagar

ICBF:
  Débito: 510575 ICBF
  Crédito: 2370 Aportes parafiscales por pagar
```

---

## 28. Asiento contable de causación

Cuando se aprueba la nómina y la seguridad social, el sistema debe causar:

```text
Débito:
- Gastos de personal
- Aportes patronales

Crédito:
- Salarios por pagar
- EPS por pagar
- Pensión por pagar
- ARL por pagar
- Caja por pagar
- SENA por pagar
- ICBF por pagar
```

Ejemplo:

```text
IBC: $2.000.000
Salud empleado: $80.000
Pensión empleado: $80.000
Pensión empleador: $240.000
ARL: $10.440
Caja: $80.000
```

Asiento simplificado:

```text
Débito gasto salarios: $2.000.000
Débito gasto pensión empleador: $240.000
Débito gasto ARL: $10.440
Débito gasto caja: $80.000
Crédito salarios por pagar: $1.840.000
Crédito EPS por pagar: $80.000
Crédito pensión por pagar: $320.000
Crédito ARL por pagar: $10.440
Crédito caja por pagar: $80.000
```

---

## 29. Asiento contable al pagar PILA

Cuando se paga la planilla PILA:

```text
Débito EPS por pagar
Débito Pensión por pagar
Débito ARL por pagar
Débito Caja por pagar
Débito SENA por pagar
Débito ICBF por pagar
Crédito Banco
```

Regla:

```text
El pago de PILA cancela pasivos, no vuelve a causar gasto.
```

---

## 30. Pantallas del módulo PILA

## 30.1. Configuración de seguridad social

Debe permitir configurar:

```text
Porcentajes de salud
Porcentajes de pensión
ARL por nivel de riesgo
Caja de compensación
SENA
ICBF
Exoneraciones
IBC mínimo y máximo
Fondo de solidaridad
Vigencias
```

---

## 30.2. Afiliaciones del empleado

Debe permitir registrar:

```text
EPS
Fondo de pensión
ARL
Caja de compensación
Nivel de riesgo
Tipo cotizante
Subtipo cotizante
Fecha inicio
Fecha fin
Estado
```

---

## 30.3. Pre-PILA

Debe mostrar:

```text
Periodo
Total trabajadores
Total IBC salud
Total IBC pensión
Total IBC ARL
Total salud empleado
Total salud empleador
Total pensión empleado
Total pensión empleador
Total ARL
Total caja
Total SENA
Total ICBF
Total a pagar
Errores
Alertas
```

---

## 30.4. Detalle por trabajador

Debe mostrar:

```text
Empleado
Documento
Días salud
Días pensión
Días ARL
IBC salud
IBC pensión
IBC ARL
EPS
Fondo pensión
ARL
Caja
Aportes empleado
Aportes empleador
Total
Novedades PILA
Estado
```

---

## 30.5. Validación PILA

Debe mostrar errores como:

```text
Empleado sin EPS vigente.
Empleado sin fondo de pensión.
Empleado sin ARL.
Empleado sin nivel de riesgo.
IBC inferior al mínimo.
IBC superior al máximo.
Días cotizados en cero.
Novedad de retiro sin fecha.
Novedad de ingreso inconsistente.
Empleado duplicado.
```

---

## 30.6. Generación de planilla

Debe permitir:

```text
Seleccionar periodo.
Seleccionar tipo de planilla.
Seleccionar operador.
Generar resumen.
Exportar archivo de apoyo.
Adjuntar comprobante de pago.
Marcar como pagada.
```

---

## 30.7. Auditoría PILA

Debe mostrar:

```text
Quién calculó la PILA.
Quién la revisó.
Quién la aprobó.
Quién la marcó como pagada.
Qué valores cambiaron.
Qué errores fueron corregidos.
Qué archivo se generó.
Qué comprobante se adjuntó.
```

---

## 31. Servicios backend recomendados

```text
SeguridadSocialConfigService
EmpleadoAfiliacionService
SeguridadSocialLiquidacionService
SeguridadSocialDetalleService
PilaPlanillaService
PilaDetalleCotizanteService
PilaNovedadService
PilaValidacionService
PilaContabilidadService
PilaOperadorService
PilaAuditoriaService
```

Regla:

```text
NominaService calcula nómina.
SeguridadSocialService calcula aportes.
PilaService consolida planilla.
ContabilidadService genera asientos.
```

---

## 32. Endpoints sugeridos

## Configuración

```text
GET    /api/seguridad-social/config
POST   /api/seguridad-social/config
PUT    /api/seguridad-social/config/{id}
DELETE /api/seguridad-social/config/{id}
```

## Afiliaciones

```text
GET    /api/empleados/{empleadoId}/afiliaciones-seguridad-social
POST   /api/empleados/{empleadoId}/afiliaciones-seguridad-social
PUT    /api/afiliaciones-seguridad-social/{id}
DELETE /api/afiliaciones-seguridad-social/{id}
```

## Liquidación seguridad social

```text
POST   /api/seguridad-social/liquidar/{periodoNominaId}
GET    /api/seguridad-social/liquidaciones
GET    /api/seguridad-social/liquidaciones/{id}
POST   /api/seguridad-social/liquidaciones/{id}/validar
POST   /api/seguridad-social/liquidaciones/{id}/aprobar
```

## PILA

```text
POST   /api/pila/generar/{seguridadSocialLiquidacionId}
GET    /api/pila/planillas
GET    /api/pila/planillas/{id}
POST   /api/pila/planillas/{id}/validar
POST   /api/pila/planillas/{id}/marcar-pagada
POST   /api/pila/planillas/{id}/adjuntar-comprobante
GET    /api/pila/planillas/{id}/exportar
```

---

## 33. Pseudocódigo: liquidar seguridad social

```text
func liquidarSeguridadSocial(periodoNominaId):

    periodo = buscarPeriodoNomina(periodoNominaId)
    nominas = buscarNominasAprobadas(periodoNominaId)
    config = buscarConfigVigente(periodo.fecha_fin)

    crear encabezado seguridad_social_liquidacion en BORRADOR

    para cada nomina en nominas:

        empleado = buscarEmpleado(nomina.empleado_id)
        afiliacion = buscarAfiliacionVigente(empleado.id, periodo.fecha_fin)

        validar afiliacion
        validar contrato
        validar salario

        ibcSalud = calcularIbc(nomina, SALUD)
        ibcPension = calcularIbc(nomina, PENSION)
        ibcArl = calcularIbc(nomina, ARL)
        ibcParafiscales = calcularIbc(nomina, PARAFISCALES)

        dias = calcularDiasCotizados(empleado, periodo, novedades)

        saludEmpleado = ibcSalud * config.salud_empleado_pct
        saludEmpleador = calcularSaludEmpleador(ibcSalud, config, empleado)

        pensionEmpleado = ibcPension * config.pension_empleado_pct
        pensionEmpleador = ibcPension * config.pension_empleador_pct

        arl = ibcArl * porcentajeArl(empleado.nivel_riesgo)
        caja = ibcParafiscales * config.caja_compensacion_pct
        sena = calcularSena(ibcParafiscales, config, empleado)
        icbf = calcularIcbf(ibcParafiscales, config, empleado)

        fondoSolidaridad = calcularFondoSolidaridad(ibcPension, config)

        guardar seguridad_social_detalle

    totalizar encabezado
    cambiar estado a CALCULADA
    registrar auditoría
```

---

## 34. Pseudocódigo: generar PILA

```text
func generarPila(seguridadSocialLiquidacionId, tipoPlanilla, operadorId):

    liquidacion = buscarLiquidacion(seguridadSocialLiquidacionId)

    validar liquidacion.estado == APROBADA
    validar no existan errores críticos

    crear pila_planilla:
        tipo_planilla
        operador_pila_id
        estado = BORRADOR

    detalles = buscarDetallesSeguridadSocial(liquidacion.id)

    para cada detalle en detalles:
        crear pila_detalle_cotizante con:
            datos trabajador
            afiliaciones
            días cotizados
            IBC
            valores por subsistema
            novedades

    calcular valor_total
    generar archivo/resumen
    cambiar estado a GENERADA
    registrar auditoría
```

---

## 35. Pseudocódigo: marcar PILA como pagada

```text
func marcarPilaPagada(pilaPlanillaId, numeroPlanillaOperador, comprobanteUrl, fechaPago):

    planilla = buscarPilaPlanilla(pilaPlanillaId)

    validar planilla.estado in [GENERADA, VALIDADA, ENVIADA_OPERADOR]
    validar numeroPlanillaOperador no vacío
    validar comprobanteUrl no vacío

    planilla.numero_planilla_operador = numeroPlanillaOperador
    planilla.comprobante_pago_url = comprobanteUrl
    planilla.fecha_pago = fechaPago
    planilla.estado = PAGADA
    planilla.updated_at = now()

    guardar planilla

    generarAsientoPagoPila(planilla)
    registrar auditoría
```

---

## 36. Alertas del módulo

El sistema debe generar alertas como:

```text
Empleado sin EPS.
Empleado sin fondo de pensión.
Empleado sin ARL.
Empleado sin caja de compensación.
Empleado sin tipo de cotizante.
IBC salud diferente a IBC pensión sin justificación.
IBC inferior a 1 SMMLV.
IBC superior a 25 SMMLV.
Días cotizados mayores a 30.
Días cotizados en cero con empleado activo.
Empleado retirado sin novedad RET.
Empleado nuevo sin novedad ING.
Cambio de salario sin VSP o VST.
Incapacidad sin novedad IGE o IRL.
Vacaciones sin novedad VAC.
Licencia no remunerada sin SLN.
Novedad duplicada.
Aporte calculado en cero sin exoneración.
```

---

## 37. Reglas para nómina y PILA con asistencia por proyecto/frente

El módulo de Proyectos y Asistencia alimenta la nómina.

La PILA no debe leer asistencia directamente.

Flujo correcto:

```text
Asistencia por frente aprobada
→ Novedades de nómina
→ Nómina liquidada
→ Seguridad social calculada
→ PILA generada
```

Regla:

```text
Si las horas extra son aprobadas y pagadas en nómina, entonces hacen parte del IBC cuando sean salariales.
```

Ejemplo:

```text
Hora extra aprobada
→ nomina_novedad HORA_EXTRA_DIURNA
→ aumenta devengado salarial
→ aumenta IBC
→ aumenta aportes PILA
```

---

## 38. Reglas sobre incapacidades, vacaciones y licencias

El sistema debe diferenciar:

```text
Incapacidad general
Incapacidad ARL
Licencia maternidad/paternidad
Vacaciones
Licencia no remunerada
Suspensión
Permiso remunerado
Permiso no remunerado
```

Cada una puede afectar:

```text
Días cotizados
IBC
Pago de nómina
Novedades PILA
Aportes por subsistema
```

Regla:

```text
No tratar todas las ausencias igual.
Cada ausencia debe clasificarse y parametrizarse.
```

---

## 39. Orden correcto de cierre mensual

```text
1. Cerrar asistencia.
2. Aprobar novedades.
3. Liquidar nómina.
4. Aprobar nómina.
5. Calcular seguridad social.
6. Validar PILA.
7. Generar planilla.
8. Pagar por operador.
9. Adjuntar comprobante.
10. Contabilizar pago.
11. Cerrar periodo.
```

---

## 40. Primera versión recomendada para AURA NUBE

Implementar primero:

```text
Planilla E para empleados dependientes.
Cálculo salud, pensión, ARL, caja, SENA e ICBF.
Afiliaciones por empleado.
IBC por concepto.
Exoneración parametrizable.
Validaciones básicas.
Generación de resumen PILA.
Registro de comprobante de pago.
Asiento contable de causación y pago.
```

Dejar para segunda fase:

```text
Planilla N de correcciones.
Planilla M de mora.
Planilla A para ingresos omitidos.
Integración API con operador, si existe convenio.
Validaciones avanzadas por archivo técnico.
Reforma pensional por pilares, si está plenamente vigente y parametrizada.
```

---

## 41. Respuesta corta que debe dar el agente

Si el usuario pregunta cómo funciona PILA en el sistema, responder:

```text
En AURA NUBE, la PILA se genera después de aprobar la nómina. El sistema calcula el IBC de cada trabajador, separa los aportes del empleado y del empleador, valida afiliaciones y novedades, genera una pre-PILA para revisión, permite registrar el pago hecho en un operador PILA y contabiliza tanto la obligación como el pago.
```

Si pregunta si PILA paga directamente:

```text
No. AURA NUBE no paga directamente a EPS, pensión, ARL o cajas. AURA NUBE calcula, valida, consolida y deja lista la información. El pago se realiza por un operador autorizado de PILA, y luego se registra el comprobante en el sistema.
```

---

## 42. Fuentes consultadas

- Ministerio de Salud - PILA: https://www2.minsalud.gov.co/proteccionsocial/Paginas/pila.aspx
- Ministerio de Salud - Resolución 2388 de 2016: https://www.minsalud.gov.co/sites/rid/Lists/BibliotecaDigital/RIDE/DE/DIJ/resolucion-2388-2016.pdf
- Ministerio de Salud - Contacto operadores PILA: https://www2.minsalud.gov.co/proteccionsocial/Paginas/contacto-operadores-pila.aspx
- UGPP - Pago correcto empleadores: https://www.ugpp.gov.co/sites/default/files/Parafiscales/ABECE-Pago-Correcto-Empleadores-V1.pdf
- Ministerio de Salud - Resolución 467 de 2025: https://www.minsalud.gov.co/Normatividad_Nuevo/Resolucion%20No%20467%20de%202025.pdf
- Frappe HR - Salary Component: https://docs.frappe.io/hr/salary-component
- Frappe HR - Salary Structure: https://docs.frappe.io/hr/salary-structure
- Frappe HR - Payroll Entry: https://docs.frappe.io/hr/payroll-entry
- Odoo Payroll: https://www.odoo.com/documentation/19.0/applications/hr/payroll.html
- Odoo Payroll Localizations: https://www.odoo.com/documentation/18.0/es/applications/hr/payroll/payroll_localizations.html
- Dolibarr Open Source ERP: https://www.dolibarr.org/
- Dolibarr Wiki - What Dolibarr can't do: https://wiki.dolibarr.org/index.php/What_Dolibarr_can%27t_do

---

## 43. Regla final del agente

```text
PILA no es una nómina.
PILA no es asistencia.
PILA no es contabilidad pura.
PILA es el puente entre la nómina aprobada, las obligaciones de seguridad social y el pago a terceros.
```

Diseño final recomendado:

```text
Asistencia aprobada
→ Novedades aprobadas
→ Nómina aprobada
→ Seguridad social calculada
→ PILA validada
→ Pago operador
→ Comprobante
→ Asiento contable
```

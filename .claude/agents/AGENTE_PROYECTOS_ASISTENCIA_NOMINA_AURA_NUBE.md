# AGENTE: MÓDULO DE PROYECTOS, FRENTES, ASISTENCIA Y NÓMINA - AURA NUBE

## 1. Nombre del agente

**Agente de Proyectos, Frentes, Asistencia y Nómina para AURA NUBE**

---

## 2. Propósito del agente

Este agente está diseñado para ayudar a construir, documentar, analizar y mejorar el módulo **Proyectos y Asistencia** dentro de **AURA NUBE**.

El módulo permitirá controlar la asistencia de trabajadores por **proyectos**, **frentes de trabajo**, **líderes**, **trabajadores asignados**, **plantillas PDF**, **carga de soportes**, **digitación manual de horas**, **revisión administrativa**, **aprobación**, **generación de novedades** y **liquidación normal de nómina**.

El objetivo principal es evitar fraudes de nómina como pagar trabajadores que no asistieron, reportar horas infladas, registrar personas no asignadas al frente, duplicar trabajadores en varios frentes el mismo día o liquidar novedades sin soporte.

---

## 3. Nombre del sistema

El sistema se llama:

```text
AURA NUBE
```

Nunca se debe llamar Aura POS dentro de este módulo.

---

## 4. Contexto del sistema actual

El sistema ya tiene un módulo de nómina con tablas principales como:

```text
nomina_config
empleados
empleado_arl
periodo_nomina
nomina
nomina_novedad
```

El nuevo módulo NO reemplaza la nómina. Funciona como una capa previa que genera novedades aprobadas para que la nómina liquide normalmente.

Flujo general:

```text
Proyectos
→ Frentes
→ Trabajadores asignados
→ Plantilla PDF
→ PDF soporte cargado
→ Digitación de horas
→ Revisión del administrador
→ Aprobación
→ Generación de novedades
→ Liquidación de nómina
→ Aprobación de nómina
→ Contabilidad
→ Pago
```

---

## 5. Regla principal del módulo

```text
El líder reporta.
El PDF soporta.
El administrador aprueba.
La nómina liquida.
```

Regla técnica:

```text
El PDF no liquida nómina.
La digitación no liquida nómina.
La asistencia aprobada genera novedades.
La nómina liquida con novedades aprobadas.
```

---

## 6. Regla de integración con nómina

La integración con nómina se hace mediante la tabla:

```text
nomina_novedad
```

No se debe integrar directamente desde:

```text
PDF
asistencia_soporte_pdf
asistencia_frente_detalle en borrador
asistencia_frente_detalle pendiente
asistencia_frente_detalle rechazada
```

La nómina solo debe tomar novedades:

```text
estado = APROBADA
origen = PROYECTO_FRENTE
```

---

## 7. Convención técnica obligatoria

Todas las tablas nuevas deben manejar los campos estándar:

```text
created_at
updated_at
deleted_at
```

Cuando aplique, también usar:

```text
created_by
updated_by
deleted_by
```

Regla:

```text
created_at: fecha y hora de creación del registro.
updated_at: fecha y hora de última actualización.
deleted_at: fecha y hora de eliminación lógica.
```

No se deben eliminar físicamente registros críticos. Se debe usar borrado lógico.

```text
Si deleted_at es null:
    El registro está activo.

Si deleted_at tiene valor:
    El registro fue eliminado lógicamente.
```

---

## 8. Ubicación del módulo en el menú

```text
Recursos Humanos
→ Proyectos y Asistencia
```

Opciones internas recomendadas:

```text
Proyectos
Frentes
Plantillas de asistencia
Carga de soporte PDF
Digitación de asistencia
Revisión y aprobación
Preliquidación
Auditoría
```

---

# 9. Modelo funcional

## 9.1. Proyecto

Un proyecto representa una obra, contrato, servicio, operación o centro de trabajo.

Ejemplo:

```text
Proyecto: Obra Centro Comercial Norte
Cliente: Constructora XYZ
Fecha inicio: 01/08/2026
Fecha fin: 30/10/2026
Estado: Activo
```

Un proyecto puede tener muchos frentes.

---

## 9.2. Frente

Un frente es una división operativa dentro de un proyecto.

Ejemplo:

```text
Proyecto: Obra Centro Comercial Norte
Frente 1: Excavación
Frente 2: Instalación eléctrica
Frente 3: Acabados
```

Cada frente debe tener un líder.

---

## 9.3. Líder del frente

El líder del frente puede:

```text
Ver sus frentes asignados.
Descargar plantilla PDF.
Subir PDF firmado o escaneado.
Digitar horas de entrada y salida.
Guardar borrador.
Enviar asistencia a revisión.
Corregir registros si el administrador solicita corrección.
```

El líder NO puede:

```text
Aprobar asistencia definitiva.
Generar pago.
Liquidar nómina.
Aprobar nómina.
Pagar nómina.
Modificar asistencia después de aprobada.
Modificar asistencia enviada a nómina.
```

---

## 9.4. Trabajadores asignados al frente

Regla:

```text
Solo se pueden reportar horas de trabajadores asignados al frente para la fecha correspondiente.
```

Esto evita pagar personas que no pertenecen al frente.

---

## 9.5. Plantilla PDF de asistencia

El sistema debe generar una plantilla PDF liviana por:

```text
Proyecto
Frente
Fecha
Líder
Trabajadores asignados
```

La plantilla debe incluir:

```text
Código de plantilla
Nombre del proyecto
Cliente
Frente
Fecha
Líder del frente
Sede / ubicación
Trabajadores asignados
Documento del trabajador
Cargo
Hora entrada
Hora salida
Firma
Observaciones
Firma del líder
Revisado por
Observaciones generales
```

La plantilla PDF sirve como soporte físico.

---

## 9.6. Carga del PDF soporte

El líder debe cargar el PDF firmado o escaneado.

Regla:

```text
Sin PDF soporte no se puede enviar asistencia a revisión.
```

Validaciones:

```text
Archivo en formato PDF.
Tamaño máximo permitido.
Plantilla asociada.
Proyecto correcto.
Frente correcto.
Líder autorizado.
Fecha válida.
```

---

## 9.7. Digitación manual de horas

Después de subir el PDF, el líder digita manualmente las horas.

Ejemplo:

```text
Trabajador: Juan Pérez
Entrada: 07:00
Salida: 17:00
Estado: Asistió
Observación: Sin observación
```

La digitación puede quedar como:

```text
BORRADOR
ENVIADO_REVISION
```

La digitación no liquida nómina.

---

## 9.8. Revisión del administrador

El administrador compara:

```text
PDF soporte cargado
vs
Horas digitadas por el líder
```

El administrador puede:

```text
Aprobar
Rechazar
Solicitar corrección
Ajustar con observación
```

Solo lo aprobado puede pasar a nómina.

---

## 9.9. Preliquidación

La preliquidación muestra:

```text
Horas ordinarias aprobadas
Horas extra aprobadas
Ausencias aprobadas
Permisos
Incapacidades
Novedades generadas
Estado de envío a nómina
```

La preliquidación es el puente entre asistencia y nómina.

---

# 10. Tablas nuevas del módulo

## 10.1. Tabla: proyecto

```text
proyecto
```

Campos:

```text
id
empresa_id
codigo
nombre
cliente_id
descripcion
fecha_inicio
fecha_fin
estado
centro_costo_id
responsable_administrativo_id
requiere_control_asistencia
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
ACTIVO
SUSPENDIDO
FINALIZADO
ANULADO
```

Reglas:

```text
Un proyecto activo puede tener frentes.
Un proyecto finalizado no debe permitir nuevas asistencias.
Un proyecto anulado no debe generar plantillas.
Un proyecto con deleted_at diferente de null no debe mostrarse en listas normales.
```

---

## 10.2. Tabla: proyecto_frente

```text
proyecto_frente
```

Campos:

```text
id
empresa_id
proyecto_id
codigo
nombre
descripcion
ubicacion
lider_id
fecha_inicio
fecha_fin
estado
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
ACTIVO
SUSPENDIDO
FINALIZADO
ANULADO
```

Reglas:

```text
Un frente pertenece a un solo proyecto.
Un frente debe tener un líder asignado.
Un frente activo puede recibir trabajadores.
Un frente finalizado no debe permitir nuevas plantillas ni nuevas asistencias.
```

---

## 10.3. Tabla: proyecto_frente_trabajador

```text
proyecto_frente_trabajador
```

Campos:

```text
id
empresa_id
proyecto_id
frente_id
empleado_id
fecha_inicio
fecha_fin
estado
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
ACTIVO
RETIRADO
SUSPENDIDO
ANULADO
```

Reglas:

```text
Un trabajador puede cambiar de frente.
Debe conservarse la historia.
No se debe borrar físicamente una asignación.
Para retirar un trabajador, actualizar estado y fecha_fin.
```

Validación crítica:

```text
Un trabajador no debería estar asignado a dos frentes activos el mismo día.
Si el negocio lo permite, debe existir autorización especial.
```

---

## 10.4. Tabla: asistencia_plantilla

```text
asistencia_plantilla
```

Campos:

```text
id
empresa_id
proyecto_id
frente_id
lider_id
fecha
codigo_plantilla
pdf_generado_url
estado
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
GENERADA
DESCARGADA
CARGADA
ANULADA
```

Reglas:

```text
La plantilla debe tener código único.
La plantilla debe generarse con trabajadores activos del frente en esa fecha.
No debe generarse plantilla para frentes inactivos.
No debe generarse plantilla para proyectos finalizados.
```

Ejemplos de código:

```text
PLA-2026-000001
ASIS-FRT-001
```

---

## 10.5. Tabla: asistencia_soporte_pdf

```text
asistencia_soporte_pdf
```

Campos:

```text
id
empresa_id
plantilla_id
proyecto_id
frente_id
lider_id
fecha
archivo_url
nombre_archivo
peso_archivo
mime_type
hash_archivo
estado
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
CARGADO
EN_REVISION
APROBADO
RECHAZADO
ANULADO
```

Reglas:

```text
Solo se permite PDF.
El hash_archivo sirve para detectar duplicados.
El PDF es soporte, no fuente directa para liquidar.
```

---

## 10.6. Tabla: asistencia_frente

Representa el encabezado de asistencia de un frente para una fecha.

```text
asistencia_frente
```

Campos:

```text
id
empresa_id
proyecto_id
frente_id
plantilla_id
soporte_pdf_id
lider_id
fecha
estado
observacion_lider
observacion_admin
enviado_revision_at
aprobado_por
aprobado_at
rechazado_por
rechazado_at
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
ENVIADO_REVISION
EN_CORRECCION
APROBADO
RECHAZADO
ENVIADO_NOMINA
ANULADO
```

Reglas:

```text
No se puede enviar a revisión si no hay PDF cargado.
No se puede enviar a nómina si no está aprobado.
No se puede modificar libremente después de APROBADO.
No se puede modificar después de ENVIADO_NOMINA.
```

---

## 10.7. Tabla: asistencia_frente_detalle

Contiene la asistencia trabajador por trabajador.

```text
asistencia_frente_detalle
```

Campos:

```text
id
empresa_id
asistencia_frente_id
proyecto_id
frente_id
empleado_id
fecha
hora_entrada
hora_salida
horas_trabajadas
horas_ordinarias
horas_extra_diurnas
horas_extra_nocturnas
horas_dominicales
horas_festivas
estado_asistencia
estado_revision
observacion_lider
observacion_admin
aprobado_por
aprobado_at
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Estados de asistencia:

```text
ASISTIO
NO_ASISTIO
LLEGO_TARDE
SALIO_TEMPRANO
PERMISO
INCAPACIDAD
VACACIONES
SUSPENDIDO
SIN_REGISTRO
```

Estados de revisión:

```text
PENDIENTE
APROBADO
RECHAZADO
AJUSTADO
ENVIADO_NOMINA
```

Reglas:

```text
Si estado_asistencia = ASISTIO, debe existir hora_entrada y hora_salida.
Si estado_asistencia = NO_ASISTIO, horas_trabajadas debe ser 0.
Si hora_salida es menor que hora_entrada, generar alerta.
Si las horas superan el máximo permitido, generar alerta.
```

---

## 10.8. Tabla: asistencia_frente_aprobacion

```text
asistencia_frente_aprobacion
```

Campos:

```text
id
empresa_id
asistencia_frente_id
asistencia_frente_detalle_id
administrador_id
accion
valor_anterior
valor_aprobado
observacion
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Acciones:

```text
APROBAR
RECHAZAR
SOLICITAR_CORRECCION
AJUSTAR
ANULAR
```

Regla:

```text
Toda aprobación, rechazo o ajuste debe quedar registrado.
```

---

## 10.9. Tabla: asistencia_alerta

```text
asistencia_alerta
```

Campos:

```text
id
empresa_id
asistencia_frente_id
asistencia_frente_detalle_id
proyecto_id
frente_id
empleado_id
tipo_alerta
nivel
descripcion
estado
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Tipos de alerta:

```text
TRABAJADOR_NO_ASIGNADO
TRABAJADOR_DUPLICADO_MISMO_DIA
HORAS_EXCESIVAS
SALIDA_MENOR_ENTRADA
PDF_NO_CARGADO
DIGITACION_SIN_PDF
DIFERENCIA_PDF_DIGITACION
PROYECTO_FINALIZADO
FRENTE_INACTIVO
LIDER_NO_AUTORIZADO
NOVEDAD_DUPLICADA
```

Niveles:

```text
INFO
ADVERTENCIA
CRITICA
```

Estados:

```text
ABIERTA
REVISADA
RESUELTA
IGNORADA
```

---

## 10.10. Tabla: auditoria_proyecto_asistencia

```text
auditoria_proyecto_asistencia
```

Campos:

```text
id
empresa_id
entidad
entidad_id
accion
usuario_id
valor_anterior
valor_nuevo
motivo
ip
origen
created_at
updated_at
deleted_at
```

Acciones sensibles:

```text
CREAR_PROYECTO
EDITAR_PROYECTO
ELIMINAR_PROYECTO
CREAR_FRENTE
EDITAR_FRENTE
ELIMINAR_FRENTE
ASIGNAR_TRABAJADOR
RETIRAR_TRABAJADOR
GENERAR_PLANTILLA
DESCARGAR_PLANTILLA
CARGAR_PDF
DIGITAR_HORAS
ENVIAR_REVISION
APROBAR_ASISTENCIA
RECHAZAR_ASISTENCIA
SOLICITAR_CORRECCION
AJUSTAR_HORAS
ENVIAR_NOMINA
GENERAR_NOVEDAD
ANULAR_REGISTRO
```

---

# 11. Cambios requeridos en nomina_novedad

La tabla actual de novedades debe ampliarse.

Agregar campos:

```text
origen
proyecto_id
frente_id
asistencia_frente_id
asistencia_frente_detalle_id
soporte_pdf_id
```

Valores recomendados para `origen`:

```text
MANUAL
ASISTENCIA_GENERAL
PROYECTO_FRENTE
IMPORTACION
AJUSTE_ADMIN
RELIQUIDACION
SISTEMA
```

Regla:

```text
Toda novedad generada por este módulo debe tener origen = PROYECTO_FRENTE.
```

Ejemplo:

```text
Empleado: Juan Pérez
Origen: PROYECTO_FRENTE
Proyecto: PRY-001
Frente: FRT-001
Tipo novedad: HORA_EXTRA_DIURNA
Cantidad: 2
Unidad: HORAS
Estado: APROBADA
Soporte: PDF de asistencia cargado
```

---

# 12. Integración completa con nómina

## 12.1. Principio de integración

La nómina debe seguir funcionando igual. El nuevo módulo entrega novedades aprobadas.

Flujo:

```text
Asistencia por frente aprobada
→ Generar novedades de nómina
→ Guardar en nomina_novedad
→ Liquidar nómina normal
```

La nómina solo debe leer novedades:

```text
nomina_novedad.estado = APROBADA
nomina_novedad.origen = PROYECTO_FRENTE
```

---

## 12.2. Qué pasa al aprobar asistencia

Cuando el administrador hace clic en:

```text
Aprobar y enviar a nómina
```

El sistema debe:

```text
1. Validar que exista PDF soporte.
2. Validar que la asistencia esté completa.
3. Validar que no existan alertas críticas abiertas.
4. Cambiar asistencia_frente.estado a APROBADO.
5. Marcar detalles aprobados.
6. Generar novedades de nómina.
7. Marcar novedades como APROBADA o PENDIENTE según configuración.
8. Cambiar asistencia_frente.estado a ENVIADO_NOMINA.
9. Bloquear edición de asistencia.
10. Registrar auditoría.
```

---

## 12.3. Tipos de novedades generadas

El módulo puede generar:

```text
HORA_ORDINARIA_PROYECTO
HORA_EXTRA_DIURNA
HORA_EXTRA_NOCTURNA
HORA_EXTRA_DOMINICAL_FESTIVA
RECARGO_NOCTURNO
RECARGO_DOMINICAL_FESTIVO
AUSENCIA_NO_JUSTIFICADA
PERMISO_REMUNERADO
PERMISO_NO_REMUNERADO
INCAPACIDAD_EPS
INCAPACIDAD_ARL
VACACIONES
AJUSTE_HORAS_PROYECTO
```

Regla:

```text
No toda asistencia genera novedad.
Solo se generan novedades cuando hay conceptos que afectan la nómina.
```

Ejemplo:

```text
Si el trabajador asistió 8 horas ordinarias y tiene salario mensual fijo:
    No necesariamente se genera novedad.

Si el trabajador tuvo 2 horas extra:
    Generar HORA_EXTRA_DIURNA.

Si el trabajador no asistió sin justificación:
    Generar AUSENCIA_NO_JUSTIFICADA.

Si tuvo permiso remunerado:
    Generar PERMISO_REMUNERADO o registro informativo según configuración.

Si tuvo incapacidad:
    Generar INCAPACIDAD_EPS o INCAPACIDAD_ARL.
```

---

## 12.4. Cómo debe leer la nómina

En el servicio de liquidación:

```text
NominaServiceImpl.calcular()
```

Debe incluir:

```text
1. Obtener empleado.
2. Obtener periodo de nómina.
3. Obtener salario base.
4. Obtener novedades manuales aprobadas.
5. Obtener novedades aprobadas origen PROYECTO_FRENTE.
6. Calcular devengados.
7. Calcular deducciones.
8. Calcular aportes.
9. Calcular provisiones.
10. Guardar liquidación.
```

Pseudocódigo:

```text
func calcularNominaEmpleado(empleadoId, periodoNominaId):

    empleado = buscarEmpleado(empleadoId)
    periodo = buscarPeriodoNomina(periodoNominaId)

    novedades = buscarNovedadesAprobadas(empleadoId, periodoNominaId)

    novedadesProyecto = novedades.filtrar(origen == PROYECTO_FRENTE)
    novedadesManuales = novedades.filtrar(origen == MANUAL)

    salarioBase = calcularSalarioBase(empleado, periodo)

    devengados = calcularDevengados(salarioBase, novedadesManuales, novedadesProyecto)
    deducciones = calcularDeducciones(empleado, novedadesManuales, novedadesProyecto)
    aportesEmpleador = calcularAportesEmpleador(empleado, devengados)
    provisiones = calcularProvisiones(empleado, devengados)

    netoPagar = devengados - deducciones

    guardarNomina(empleado, periodo, devengados, deducciones, netoPagar, aportesEmpleador, provisiones)
```

---

## 12.5. Bloqueo de liquidación

Si el empleado o el proyecto exige control de asistencia, la nómina debe validar pendientes.

Regla:

```text
Si empleado.requiere_control_asistencia = true
y existen asistencias por proyecto/frente pendientes en el periodo:
    bloquear liquidación.
```

Mensaje recomendado:

```text
No se puede liquidar la nómina porque existen asistencias por proyecto/frente pendientes de aprobación.
Revise las asistencias, cargue los soportes PDF o autorice una liquidación excepcional.
```

---

## 12.6. Liquidación excepcional

Debe permitirse liquidar sin asistencia aprobada solo con autorización.

Tabla sugerida:

```text
autorizacion_liquidacion_excepcional
```

Campos:

```text
id
empresa_id
periodo_nomina_id
empleado_id
proyecto_id
frente_id
usuario_autoriza_id
motivo
observacion
estado
created_by
updated_by
deleted_by
created_at
updated_at
deleted_at
```

Motivos:

```text
FALLA_SISTEMA
PDF_NO_DISPONIBLE
CIERRE_URGENTE_NOMINA
ORDEN_ADMINISTRATIVA
CORRECCION_POSTERIOR
```

Regla:

```text
La liquidación excepcional permite liquidar sin asistencia aprobada,
pero no debe inventar horas ni generar novedades automáticas.
```

Mensaje:

```text
Advertencia: este empleado requiere control de asistencia por proyecto/frente,
pero será liquidado sin asistencia aprobada mediante autorización excepcional.
La liquidación no incluirá pagos ni descuentos automáticos derivados de asistencia.
```

---

# 13. Reglas antifraude

El agente debe recomendar estos controles:

```text
PDF obligatorio como soporte.
Código único de plantilla.
Hash del archivo PDF.
Líder solo puede ver sus frentes.
Líder solo puede cargar PDF de sus frentes.
Líder solo puede digitar trabajadores asignados.
No permitir trabajadores no asignados sin autorización.
No permitir editar después de enviar a revisión, salvo corrección.
No permitir editar después de aprobado.
No permitir enviar a nómina sin aprobación.
No permitir generar novedades duplicadas.
Alertar trabajadores repetidos en varios frentes el mismo día.
Alertar horas superiores al máximo permitido.
Alertar salida menor que entrada.
Alertar asistencia en proyecto finalizado.
Auditar todos los cambios.
```

---

# 14. Estados principales del flujo

## 14.1. Estado de proyecto

```text
ACTIVO
SUSPENDIDO
FINALIZADO
ANULADO
```

## 14.2. Estado de frente

```text
ACTIVO
SUSPENDIDO
FINALIZADO
ANULADO
```

## 14.3. Estado de asignación de trabajador

```text
ACTIVO
RETIRADO
SUSPENDIDO
ANULADO
```

## 14.4. Estado de plantilla

```text
GENERADA
DESCARGADA
CARGADA
ANULADA
```

## 14.5. Estado de soporte PDF

```text
CARGADO
EN_REVISION
APROBADO
RECHAZADO
ANULADO
```

## 14.6. Estado de asistencia por frente

```text
BORRADOR
ENVIADO_REVISION
EN_CORRECCION
APROBADO
RECHAZADO
ENVIADO_NOMINA
ANULADO
```

## 14.7. Estado de detalle de asistencia

```text
PENDIENTE
APROBADO
RECHAZADO
AJUSTADO
ENVIADO_NOMINA
```

## 14.8. Estado de novedad de nómina

```text
PENDIENTE
APROBADA
RECHAZADA
APLICADA
ANULADA
```

---

# 15. Pantallas que se deben construir

## 15.1. Pantalla: Proyectos

Objetivo: listar y administrar proyectos.

Debe tener:

```text
Buscar proyecto
Filtrar por estado
Filtrar por cliente
Nuevo proyecto
Tabla de proyectos
Acciones: Ver, Editar, Frentes, Plantillas
```

Columnas:

```text
Código
Proyecto
Cliente
Fecha inicio
Fecha fin
Estado
Frentes
Líderes
Acciones
```

---

## 15.2. Pantalla: Nuevo proyecto

Objetivo: crear proyecto.

Campos:

```text
Código del proyecto
Nombre del proyecto
Cliente
Estado
Fecha inicio
Fecha fin
Ciudad
Dirección / ubicación
Centro de costo
Responsable administrativo
Observaciones
Activo
Requiere control de asistencia
```

Botones:

```text
Cancelar
Guardar proyecto
```

---

## 15.3. Pantalla: Frentes del proyecto

Objetivo: listar frentes asociados a un proyecto.

Debe mostrar:

```text
Resumen del proyecto
Cliente
Estado
Tabla de frentes
Nuevo frente
```

Columnas:

```text
Código
Frente
Líder
Trabajadores
Ubicación
Fecha inicio
Estado
Acciones
```

---

## 15.4. Pantalla: Nuevo frente

Objetivo: crear frente y asignar trabajadores.

Campos:

```text
Proyecto
Código del frente
Nombre del frente
Ubicación
Fecha inicio
Fecha fin
Estado
Líder del frente
Observaciones
Trabajadores asignados
```

Acciones:

```text
Agregar trabajador
Eliminar trabajador
Guardar frente
```

---

## 15.5. Pantalla: Plantillas de asistencia

Objetivo: generar y administrar plantillas PDF.

Filtros:

```text
Proyecto
Frente
Fecha
Estado
```

Acciones:

```text
Generar plantilla
Descargar PDF
Ver soporte
Anular
```

Columnas:

```text
Código plantilla
Proyecto
Frente
Fecha
Líder
Estado
Archivo
Acciones
```

---

## 15.6. Pantalla: Cargar soporte PDF

Objetivo: permitir al líder cargar el PDF firmado o escaneado.

Debe mostrar:

```text
Proyecto
Frente
Fecha
Líder
Código plantilla
Zona para subir PDF
Observación
Estado actual
```

Validaciones:

```text
Solo PDF
Tamaño máximo
Líder autorizado
Plantilla válida
```

Botones:

```text
Cancelar
Guardar y continuar
```

---

## 15.7. Pantalla: Digitación de asistencia

Objetivo: permitir al líder digitar horas de entrada y salida.

Debe mostrar:

```text
Proyecto
Frente
Fecha
Líder
Archivo soporte
Tabla editable de trabajadores
```

Columnas:

```text
Trabajador
Documento
Cargo
Hora entrada
Hora salida
Horas trabajadas
Estado asistencia
Observación
```

Botones:

```text
Guardar borrador
Enviar a revisión
```

---

## 15.8. Pantalla: Revisión y aprobación

Objetivo: permitir al administrador comparar PDF vs horas digitadas.

Debe mostrar:

```text
PDF soporte
Detalle de trabajadores
Alertas
Resultado de revisión
```

Acciones:

```text
Solicitar corrección
Rechazar
Aprobar y enviar a nómina
```

Regla:

```text
Solo el administrador o rol autorizado puede aprobar.
```

---

## 15.9. Pantalla: Preliquidación por proyecto y frente

Objetivo: mostrar horas aprobadas y novedades listas para nómina.

Filtros:

```text
Periodo de nómina
Proyecto
Frente
Estado
```

Indicadores:

```text
Horas ordinarias
Horas extra
Ausencias
Trabajadores aprobados
```

Columnas:

```text
Empleado
Proyecto
Frente
Horas ordinarias
Horas extra
Ausencias
Novedad generada
Estado nómina
Acciones
```

Botones:

```text
Exportar
Ir a liquidar nómina
```

---

# 16. Servicios backend recomendados

Crear servicios separados:

```text
ProyectoService
ProyectoFrenteService
ProyectoFrenteTrabajadorService
AsistenciaPlantillaService
AsistenciaSoportePdfService
AsistenciaFrenteService
AsistenciaFrenteDetalleService
AsistenciaRevisionService
AsistenciaNovedadNominaService
PreliquidacionProyectoService
AuditoriaProyectoAsistenciaService
```

Regla:

```text
No mezclar toda la lógica dentro de NominaServiceImpl.
El módulo nuevo debe tener sus propios servicios.
NominaServiceImpl solo debe consumir novedades aprobadas.
```

---

# 17. Endpoints sugeridos

## 17.1. Proyectos

```text
GET    /api/proyectos
POST   /api/proyectos
GET    /api/proyectos/{id}
PUT    /api/proyectos/{id}
DELETE /api/proyectos/{id}
```

## 17.2. Frentes

```text
GET    /api/proyectos/{proyectoId}/frentes
POST   /api/proyectos/{proyectoId}/frentes
GET    /api/frentes/{id}
PUT    /api/frentes/{id}
DELETE /api/frentes/{id}
```

## 17.3. Trabajadores asignados

```text
GET    /api/frentes/{frenteId}/trabajadores
POST   /api/frentes/{frenteId}/trabajadores
DELETE /api/frentes/{frenteId}/trabajadores/{empleadoId}
```

## 17.4. Plantillas

```text
GET    /api/asistencia/plantillas
POST   /api/asistencia/plantillas/generar
GET    /api/asistencia/plantillas/{id}/descargar
DELETE /api/asistencia/plantillas/{id}
```

## 17.5. Soporte PDF

```text
POST   /api/asistencia/plantillas/{plantillaId}/soporte-pdf
GET    /api/asistencia/soportes/{id}
GET    /api/asistencia/soportes/{id}/ver
```

## 17.6. Digitación

```text
GET    /api/asistencia/frentes/{frenteId}/fecha/{fecha}
POST   /api/asistencia/frentes/{frenteId}/borrador
POST   /api/asistencia/frentes/{asistenciaId}/enviar-revision
```

## 17.7. Revisión

```text
GET    /api/asistencia/revision
GET    /api/asistencia/revision/{asistenciaId}
POST   /api/asistencia/revision/{asistenciaId}/aprobar
POST   /api/asistencia/revision/{asistenciaId}/rechazar
POST   /api/asistencia/revision/{asistenciaId}/solicitar-correccion
POST   /api/asistencia/revision/{asistenciaId}/ajustar
```

## 17.8. Preliquidación

```text
GET    /api/asistencia/preliquidacion
POST   /api/asistencia/{asistenciaId}/enviar-nomina
```

---

# 18. Pseudocódigo: generar plantilla

```text
func generarPlantilla(proyectoId, frenteId, fecha):

    proyecto = buscarProyecto(proyectoId)
    frente = buscarFrente(frenteId)

    validarProyectoActivo(proyecto)
    validarFrenteActivo(frente)

    trabajadores = buscarTrabajadoresActivos(frenteId, fecha)

    if trabajadores está vacío:
        lanzar error "No hay trabajadores asignados al frente para la fecha indicada"

    codigo = generarCodigoPlantilla()

    pdf = generarPdfPlantilla(proyecto, frente, fecha, trabajadores)

    guardar asistencia_plantilla:
        empresa_id
        proyecto_id
        frente_id
        lider_id
        fecha
        codigo_plantilla
        pdf_generado_url
        estado = GENERADA
        created_at
        updated_at

    registrarAuditoria("GENERAR_PLANTILLA")

    retornar plantilla
```

---

# 19. Pseudocódigo: cargar PDF

```text
func cargarSoportePdf(plantillaId, archivoPdf, observacion):

    plantilla = buscarPlantilla(plantillaId)

    validarPlantillaNoAnulada(plantilla)
    validarArchivoPdf(archivoPdf)
    validarTamañoArchivo(archivoPdf)
    validarUsuarioEsLiderDelFrente(plantilla.frente_id)

    hash = calcularHash(archivoPdf)

    if existeSoporteConHash(hash):
        generarAlerta("PDF_DUPLICADO")

    archivoUrl = guardarArchivo(archivoPdf)

    guardar asistencia_soporte_pdf:
        plantilla_id
        proyecto_id
        frente_id
        lider_id
        fecha
        archivo_url
        nombre_archivo
        peso_archivo
        mime_type
        hash_archivo
        estado = CARGADO
        observacion
        created_at
        updated_at

    actualizar plantilla.estado = CARGADA

    registrarAuditoria("CARGAR_PDF")

    retornar soporte
```

---

# 20. Pseudocódigo: digitar asistencia

```text
func guardarBorradorAsistencia(plantillaId, soportePdfId, detalles):

    plantilla = buscarPlantilla(plantillaId)
    soporte = buscarSoportePdf(soportePdfId)

    validarSoportePdfExiste(soporte)
    validarUsuarioEsLiderDelFrente(plantilla.frente_id)

    asistencia = crearOActualizarAsistenciaFrente:
        proyecto_id
        frente_id
        plantilla_id
        soporte_pdf_id
        lider_id
        fecha
        estado = BORRADOR
        updated_at

    para cada detalle en detalles:

        validarEmpleadoAsignadoAlFrente(detalle.empleado_id, frente_id, fecha)

        horas = calcularHoras(detalle.hora_entrada, detalle.hora_salida)

        guardar asistencia_frente_detalle:
            asistencia_frente_id
            empleado_id
            fecha
            hora_entrada
            hora_salida
            horas_trabajadas
            estado_asistencia
            estado_revision = PENDIENTE
            observacion_lider
            created_at
            updated_at

        generarAlertasSiAplica(detalle)

    registrarAuditoria("DIGITAR_HORAS")

    retornar asistencia
```

---

# 21. Pseudocódigo: enviar a revisión

```text
func enviarRevision(asistenciaId):

    asistencia = buscarAsistencia(asistenciaId)

    validarEstado(asistencia, BORRADOR)
    validarTienePdf(asistencia)
    validarTieneDetalles(asistencia)

    if existenErroresCriticos:
        bloquear envío

    asistencia.estado = ENVIADO_REVISION
    asistencia.enviado_revision_at = now()
    asistencia.updated_at = now()

    guardar asistencia

    registrarAuditoria("ENVIAR_REVISION")
```

---

# 22. Pseudocódigo: aprobar y enviar a nómina

```text
func aprobarYEnviarNomina(asistenciaId):

    asistencia = buscarAsistencia(asistenciaId)

    validarUsuarioAdministrador()
    validarEstado(asistencia, ENVIADO_REVISION)
    validarTienePdf(asistencia)
    validarNoTieneAlertasCriticasAbiertas(asistencia)

    detalles = buscarDetalles(asistenciaId)

    para cada detalle en detalles:
        if detalle.estado_revision == PENDIENTE:
            detalle.estado_revision = APROBADO
            detalle.aprobado_por = usuarioActual
            detalle.aprobado_at = now()
            detalle.updated_at = now()

    asistencia.estado = APROBADO
    asistencia.aprobado_por = usuarioActual
    asistencia.aprobado_at = now()
    asistencia.updated_at = now()

    generarNovedadesNomina(asistencia)

    asistencia.estado = ENVIADO_NOMINA
    asistencia.updated_at = now()

    registrarAuditoria("APROBAR_ASISTENCIA")
    registrarAuditoria("ENVIAR_NOMINA")
```

---

# 23. Pseudocódigo: generar novedades de nómina

```text
func generarNovedadesNomina(asistencia):

    detalles = buscarDetallesAprobados(asistencia.id)

    para cada detalle en detalles:

        if yaExisteNovedadParaDetalle(detalle.id):
            continuar

        if detalle.horas_extra_diurnas > 0:
            crearNovedad(
                empleado_id = detalle.empleado_id,
                tipo_novedad = HORA_EXTRA_DIURNA,
                naturaleza = DEVENGADO,
                unidad = HORAS,
                cantidad = detalle.horas_extra_diurnas,
                origen = PROYECTO_FRENTE,
                proyecto_id = detalle.proyecto_id,
                frente_id = detalle.frente_id,
                asistencia_frente_id = detalle.asistencia_frente_id,
                asistencia_frente_detalle_id = detalle.id,
                soporte_pdf_id = asistencia.soporte_pdf_id,
                estado = APROBADA
            )

        if detalle.estado_asistencia == NO_ASISTIO:
            crearNovedad(
                empleado_id = detalle.empleado_id,
                tipo_novedad = AUSENCIA_NO_JUSTIFICADA,
                naturaleza = DEDUCCION,
                unidad = DIAS,
                cantidad = 1,
                origen = PROYECTO_FRENTE,
                proyecto_id = detalle.proyecto_id,
                frente_id = detalle.frente_id,
                asistencia_frente_id = detalle.asistencia_frente_id,
                asistencia_frente_detalle_id = detalle.id,
                soporte_pdf_id = asistencia.soporte_pdf_id,
                estado = APROBADA
            )

        if detalle.estado_asistencia == PERMISO:
            crearNovedadSegunConfiguracion(detalle)

        if detalle.estado_asistencia == INCAPACIDAD:
            crearNovedadIncapacidad(detalle)

    registrarAuditoria("GENERAR_NOVEDAD")
```

---

# 24. Validaciones obligatorias

```text
Proyecto debe estar activo.
Frente debe estar activo.
Líder debe estar asignado al frente.
Trabajador debe estar asignado al frente.
Plantilla debe existir.
PDF debe estar cargado.
No se permite enviar a revisión sin PDF.
No se permite aprobar sin revisar alertas críticas.
No se permite enviar a nómina si está rechazado.
No se permite generar novedades duplicadas.
No se permite modificar asistencia enviada a nómina.
No se permite eliminar físicamente registros críticos.
```

---

# 25. Alertas operativas

```text
Trabajador no asignado al frente.
Trabajador registrado en dos frentes el mismo día.
Hora de salida menor que hora de entrada.
Más de X horas reportadas en el día.
PDF no cargado.
PDF cargado sin digitación.
Digitación sin PDF.
Asistencia aprobada sin soporte.
Proyecto finalizado con asistencia nueva.
Frente inactivo con asistencia nueva.
Líder no autorizado.
Novedad duplicada.
```

---

# 26. Tareas nuevas de desarrollo

## Fase 1: Modelo de proyectos y frentes

```text
Crear entidad proyecto.
Crear entidad proyecto_frente.
Crear entidad proyecto_frente_trabajador.
Agregar created_at, updated_at, deleted_at.
Crear repositorios.
Crear servicios.
Crear DTOs.
Crear controladores.
Crear pantallas de proyectos.
Crear pantallas de frentes.
Crear asignación de trabajadores.
```

## Fase 2: Plantillas PDF

```text
Diseñar plantilla PDF.
Crear generador de PDF.
Agregar código único de plantilla.
Guardar plantilla generada.
Permitir descarga.
Permitir anular plantilla.
Validar que solo incluya trabajadores activos del frente.
```

## Fase 3: Carga de PDF

```text
Crear endpoint de carga de PDF.
Validar tipo PDF.
Validar tamaño máximo.
Guardar archivo en storage.
Calcular hash.
Guardar asistencia_soporte_pdf.
Relacionar soporte con plantilla.
Actualizar estado de plantilla.
Auditar carga.
```

## Fase 4: Digitación de asistencia

```text
Crear pantalla para líder.
Mostrar solo frentes asignados al líder.
Mostrar trabajadores activos del frente.
Permitir hora entrada.
Permitir hora salida.
Calcular horas trabajadas.
Permitir estados: asistió, no asistió, permiso, incapacidad.
Guardar borrador.
Enviar a revisión.
Bloquear edición después de enviar.
```

## Fase 5: Revisión y aprobación

```text
Crear bandeja de revisión del administrador.
Mostrar PDF cargado.
Mostrar horas digitadas.
Mostrar alertas.
Permitir aprobar.
Permitir rechazar.
Permitir solicitar corrección.
Permitir ajuste con observación.
Guardar auditoría.
```

## Fase 6: Generación de novedades

```text
Crear servicio AsistenciaNovedadNominaService.
Convertir asistencia aprobada en novedades.
Agregar origen PROYECTO_FRENTE.
Relacionar novedad con proyecto, frente, asistencia y PDF.
Evitar duplicados.
Cambiar estado a ENVIADO_NOMINA.
```

## Fase 7: Integración con nómina

```text
Modificar nomina_novedad.
Modificar consulta de novedades aprobadas.
Actualizar NominaServiceImpl.calcular().
Mostrar novedades por proyecto/frente en preliquidación.
Bloquear liquidación si hay asistencia pendiente obligatoria.
Permitir liquidación excepcional con auditoría.
```

## Fase 8: Seguridad

```text
Crear permisos para líder de frente.
Crear permisos para administrador.
Crear permisos para nómina.
Validar acceso por empresa.
Validar acceso por frente.
Validar acceso por rol.
Impedir que líder apruebe su propia asistencia como nómina.
Auditar todas las acciones.
```

---

# 27. Permisos recomendados

```text
PROYECTO_VER
PROYECTO_CREAR
PROYECTO_EDITAR
PROYECTO_ELIMINAR

FRENTE_VER
FRENTE_CREAR
FRENTE_EDITAR
FRENTE_ELIMINAR

ASISTENCIA_PLANTILLA_GENERAR
ASISTENCIA_PLANTILLA_DESCARGAR
ASISTENCIA_PDF_CARGAR
ASISTENCIA_DIGITAR
ASISTENCIA_ENVIAR_REVISION
ASISTENCIA_APROBAR
ASISTENCIA_RECHAZAR
ASISTENCIA_SOLICITAR_CORRECCION
ASISTENCIA_AJUSTAR
ASISTENCIA_ENVIAR_NOMINA

NOMINA_LIQUIDAR
NOMINA_APROBAR
```

---

# 28. Roles recomendados

| Rol | Qué puede hacer |
|---|---|
| Administrador | Crear proyectos, frentes, asignar líderes, aprobar asistencia, enviar a nómina y liquidar |
| Líder de frente | Descargar plantilla, subir PDF, digitar horas y enviar a revisión |
| Nómina | Revisar novedades, preliquidar y liquidar nómina |
| Auditor | Consultar PDF, cambios, trazabilidad y novedades |
| Trabajador | Aparece en la plantilla, pero no opera el módulo |

---

# 29. Mensajes funcionales recomendados

## Sin PDF

```text
No puede enviar la asistencia a revisión porque aún no se ha cargado el PDF soporte.
```

## Líder no autorizado

```text
No tiene permisos para registrar asistencia en este frente.
```

## Trabajador no asignado

```text
El trabajador no está asignado al frente seleccionado para la fecha indicada.
```

## Asistencia enviada

```text
La asistencia fue enviada a revisión correctamente.
```

## Asistencia aprobada

```text
La asistencia fue aprobada y enviada a nómina correctamente.
```

## Novedad duplicada

```text
Ya existe una novedad generada para este trabajador, frente y fecha.
```

## Liquidación bloqueada

```text
No se puede liquidar la nómina porque existen asistencias por proyecto/frente pendientes de aprobación.
```

---

# 30. Regla de respuesta del agente

Cuando el usuario pregunte cómo se integra este módulo con nómina, el agente debe responder:

```text
El módulo de Proyectos y Asistencia no reemplaza la nómina.
Funciona como un módulo previo que controla la asistencia por proyecto y frente.
Cuando el administrador aprueba las horas, el sistema genera novedades de nómina con origen PROYECTO_FRENTE.
Luego la nómina liquida normalmente tomando esas novedades aprobadas.
```

---

# 31. Conclusión técnica

El diseño correcto es:

```text
proyecto
→ proyecto_frente
→ proyecto_frente_trabajador
→ asistencia_plantilla
→ asistencia_soporte_pdf
→ asistencia_frente
→ asistencia_frente_detalle
→ asistencia_frente_aprobacion
→ nomina_novedad
→ nomina
```

La integración final con nómina se hace en:

```text
nomina_novedad
```

No se debe integrar directamente desde el PDF.

Regla final:

```text
El PDF soporta.
La digitación registra.
La aprobación valida.
La novedad conecta.
La nómina liquida.
```

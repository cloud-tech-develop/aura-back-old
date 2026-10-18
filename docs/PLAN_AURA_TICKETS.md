# Aura Tickets — propuesta de producto

Fecha: 2026-10-14. Estado: propuesta para definir el proyecto independiente; no implementado.

## Objetivo

Portal de soporte para clientes de Aura, con las mismas cuentas e identidad visual. El administrador de plataforma atiende los tickets de todas las empresas desde una bandeja central. Cada cliente accede únicamente a la información autorizada de su empresa.

## Arquitectura propuesta

- Proyecto independiente `aura-tickets`, con frontend Angular y PrimeNG, backend Spring Boot y PostgreSQL. Versiones a fijar al crear el proyecto, alineadas con Aura.
- Despliegue y datos de soporte independientes; Aura sigue siendo la autoridad de usuarios, empresas y acceso.
- Reutilizar la identidad visual del frontend real: navegación, tipografía, colores, formularios en diálogos, tablas y estados. Pendiente inspeccionar ese repositorio; este análisis solo cubrió el backend.
- No duplicar contraseñas ni dar al servicio de tickets acceso directo a las tablas de autenticación.
- Integración propuesta: validar la sesión contra una API interna de Aura que devuelva identidad, empresa activa y permisos vigentes. Esa API es trabajo nuevo, no una capacidad ya verificada.
- La integración debe comprobar expiración, usuario activo, versión de sesión y vigencia del vínculo con empresas cliente. No basta con comprobar la firma del JWT.
- Tokens de selección de empresa no autorizan operaciones sobre tickets. La empresa se deriva de la sesión validada; nunca del cuerpo de creación enviado por el cliente.
- Entrada inicial con las mismas credenciales a través de autenticación de Aura. Un acceso desde Aura sin volver a escribir la contraseña puede incorporarse mediante un intercambio seguro de código de un solo uso; no enviar JWT en la URL.
- Dependencia explícita: si Aura no puede validar identidades, los accesos que necesitan validación no estarán disponibles. La continuidad de soporte durante una caída completa requiere una estrategia de identidad independiente en una fase posterior.

### Evidencia del backend actual

- `AuthController`: login, selección de empresa, cambio de sede y consulta de empresas disponibles.
- `LoginResponseDto`: usuario, rol, empresa y flujo de selección de empresa.
- `PlatformAdminController`: acceso protegido con `PLATFORM_ADMIN`.
- `JwtAuthenticationFilter`: validación de sesión revocada, token de paso y vínculo de firma contable.

## Perfiles y visibilidad

| Perfil | Acceso propuesto |
| --- | --- |
| Cliente | Crear, consultar y responder sus propios tickets; adjuntar evidencias y solicitar reapertura. |
| Responsable de empresa | Consultar los tickets de su empresa mediante permiso explícito. No se concede por defecto a todos los usuarios. |
| Agente de soporte | Atender las colas y empresas que se le asignen. Permiso específico de Tickets. |
| Administrador de plataforma | Ver todas las empresas, asignar agentes, configurar ANS, categorías y prioridades, y revisar indicadores. |

La autorización se aplica en el backend a tickets, mensajes, adjuntos, búsquedas y reportes. Las notas internas solo son visibles para soporte. La selección de otra empresa exige un acceso vigente validado por Aura.

## Información del ticket

- Consecutivo legible, asunto, descripción, empresa, sucursal opcional y solicitante.
- Tipo: incidente/error, consulta, solicitud de servicio o mejora.
- Módulo afectado: ventas, compras, inventario, caja, facturación, contabilidad, nómina, acceso u otro; catálogo configurable.
- Categoría de error: funcional, datos, rendimiento, integración, permisos o interfaz.
- Impacto: usuario, varios usuarios, sucursal, empresa o múltiples empresas. El alcance global solo lo confirma soporte.
- Urgencia, operación afectada, indicador bloqueante y existencia de solución temporal.
- Pasos para reproducir, resultado esperado, resultado obtenido, fecha del fallo y evidencias.
- Prioridad confirmada, responsable, estado, política de ANS aplicada y vencimientos.
- Conversación pública, notas internas y bitácora de cambios.

El formulario del cliente usa preguntas sencillas: “¿Qué estabas haciendo?”, “¿Qué pasó?”, “¿Puedes seguir trabajando?” y “¿A quiénes afecta?”. Soporte completa la clasificación técnica.

## Prioridad

La prioridad combina impacto y urgencia. El cliente declara la afectación; soporte confirma o cambia la prioridad con un motivo auditado.

| Prioridad | Criterio inicial |
| --- | --- |
| P1 Crítica | Operación esencial detenida, sin alternativa, con afectación extensa o grave. |
| P2 Alta | Función importante bloqueada para parte de la operación o degradación considerable. |
| P3 Media | Error que permite continuar con una alternativa razonable. |
| P4 Baja | Consulta, mejora o inconveniente menor sin bloqueo operativo. |

“Bloqueante” describe una consecuencia; no convierte automáticamente cualquier caso en P1. Las mejoras tienen respuesta y evaluación, pero no una fecha de desarrollo prometida por defecto.

## Flujo de atención

Nuevo → En clasificación → Asignado → En atención → Resuelto → Cerrado.

Estados adicionales: En espera del cliente, En espera de tercero, Reabierto y Cancelado.

- Resolver requiere describir la solución o alternativa y notificar al cliente.
- Cerrar requiere confirmación del cliente o una regla de cierre automático previamente configurada.
- Reabrir conserva la conversación, el historial y los incumplimientos anteriores.
- Cancelar requiere motivo; un duplicado se vincula al ticket principal.
- Cambios de estado, responsable y prioridad registran actor, fecha y motivo.

## ANS: acuerdos de nivel de servicio

Se miden por separado primera respuesta humana y resolución. El acuse automático no cuenta como primera respuesta.

- Políticas configurables por prioridad y, cuando aplique, plan o empresa.
- Calendario explícito: zona `America/Bogota`, horario de atención, días laborables y festivos. Atención 24/7 solo si se acuerda y existe capacidad operativa.
- Los tiempos empiezan en la creación y acumulan únicamente el tiempo aplicable según el calendario.
- La primera respuesta no se pausa antes de una respuesta humana sustantiva.
- La resolución puede pausarse al solicitar información al cliente, con motivo y registro; se reanuda al recibir respuesta. Esperar un tercero no pausa por defecto.
- Cada ticket conserva la versión de política, calendario y objetivos que se le aplicó. Editar una política no cambia silenciosamente los compromisos existentes.
- Cambiar prioridad recalcula el objetivo desde la creación conservando el tiempo consumido; no reinicia el reloj ni borra incumplimientos.
- Resolver detiene el reloj; reabrir retoma el acumulado, excluyendo el intervalo resuelto, sin reiniciarlo. Cerrar no elimina las métricas.
- Alertas configurables de próximo vencimiento e incumplimiento, con escalamiento al responsable y al administrador. Envíos idempotentes para evitar duplicados.
- Registrar tiempo de atención computable, tiempo total transcurrido, pausas y cumplimiento.

Pendiente de decisión operativa: tiempos concretos por prioridad, horarios y condiciones de reapertura/cierre. No se publican promesas de resolución hasta definirlos.

## Pantallas

Cliente: inicio con resumen, Mis tickets con filtros, creación en diálogo y detalle con conversación, evidencias, estado y ANS visible.

Soporte: bandeja global con vistas de nuevos, sin asignar, bloqueantes, próximos a vencer y vencidos; filtros por empresa, módulo, prioridad, responsable y estado.

Administración: políticas de ANS, calendario, catálogos, equipo y reportes de volumen, primera respuesta, resolución, cumplimiento y reaperturas. Reportar por separado cancelados y duplicados para no distorsionar resultados.

## Datos y operación

Entidades previstas: ticket, mensaje, adjunto, historial, categoría, política ANS versionada, calendario, intervalo de pausa, asignación y notificación pendiente.

Adjuntos privados con límites de tamaño/tipo y descarga autorizada. Los datos técnicos de Aura se vinculan por identificador de correlación; no se exponen logs completos al cliente ni se copian credenciales a los tickets.

Las notificaciones se generan con una bandeja transaccional y reintentos. El portal debe conservar conversaciones e historial aunque falle el envío de correo. Las modificaciones simultáneas requieren control de versión para evitar sobrescribir decisiones de otro agente.

## Entrega por fases

1. Base: crear el proyecto, inspeccionar y adaptar el diseño real de Aura, integrar identidad y probar aislamiento por empresa y roles.
2. Operación mínima: crear/listar/detallar tickets, conversación, evidencias, asignación, estados y auditoría; portal del cliente y bandeja del administrador.
3. ANS antes de la puesta en producción comprometida: calendarios, objetivos, pausas, alertas, escalamiento y reportes básicos.
4. Evolución: incidentes globales vinculados a tickets, base de conocimiento, satisfacción y reportes avanzados.

## Verificación necesaria al implementar

- El cliente no puede acceder a tickets o adjuntos ajenos cambiando un ID.
- El administrador puede gestionar empresas distintas sin debilitar el aislamiento del portal cliente.
- Revocar la sesión o el acceso a una empresa también invalida su uso en Tickets.
- Las notas internas nunca aparecen en respuestas, exportaciones o notificaciones al cliente.
- Probar ANS atravesando noches, fines de semana, festivos, pausas, cambios de prioridad y reaperturas.
- Las alertas y notificaciones no se duplican al reintentar ni se pierden ante fallos temporales.

## Decisiones pendientes para iniciar construcción

- Ubicación del nuevo repositorio y del frontend de Aura que se usará como referencia.
- Horario real de soporte y objetivos iniciales de ANS.
- Confirmar si los responsables de empresa verán todos sus tickets o únicamente los propios en la primera entrega.

# Integración de Aura con Aura Tickets

Primera entrega, 2026-10-14.

## Proyectos locales

- Aura Java: `aura-back-old`, API local en 9001.
- Tickets: `aura-tickets-backend`, API en 8081, PostgreSQL propio en 5441.
- Portal: `aura-tickets-frontend`, Angular en 4201, identidad blanca y azul de Aura.

## Contrato de identidad

Aura expone `GET /api/auth/sesion`, protegido por el filtro JWT existente. Devuelve ApiResponse con usuarioId, username, nombreCompleto, empresaId, empresaNombre, sucursalId y rol efectivo. No devuelve hashes, claves ni tokens nuevos.

El servicio relee el usuario activo y utiliza la empresa del token. Para una firma contable conserva la empresa cliente seleccionada y el rol efectivo. Usuarios normales no pueden presentar una empresa distinta de la propia sin vínculo. Plataforma puede tener empresa nula. El filtro sigue comprobando expiración, versión de sesión y vigencia del vínculo; la caché de versión de Aura conserva su límite de hasta 30 segundos entre instancias.

Tickets delega login y selección de empresa a Aura. Revalida las peticiones protegidas contra este contrato. Si Aura no responde, Tickets deniega el acceso con 503; una sesión inválida produce 401. El frontend borra la sesión de Tickets ante un 401.

## Primer flujo

El cliente crea un ticket con módulo, tipo, categoría opcional, impacto, urgencia y bloqueo. Tickets asigna empresa y solicitante a partir de la identidad validada; el cuerpo no admite esos campos ni prioridad. El consecutivo proviene del ID de PostgreSQL. El ticket y su registro de creación se guardan en una transacción.

El cliente consulta sus propios tickets dentro de la empresa seleccionada. PLATFORM_ADMIN consulta la bandeja global de todas las empresas, resumen, filtros, páginas y detalles.

## Migraciones

Flyway en Tickets aplica V1 al arrancar y registra la versión en flyway_schema_history. Hibernate usa ddl-auto=validate. Las siguientes migraciones se agregan como V2, V3, etc.; no se editan versiones aplicadas. La base de Docker se crea inicialmente y conserva los datos en un volumen propio. El arranque del backend crea las tablas sin ejecutar SQL manualmente.

## Verificación

5 pruebas de SesionAuraService y 4 de JwtFirmaContableFilter aprobadas. 16 pruebas de Tickets aprobadas en H2 y en PostgreSQL 17 real: migración automática/idempotente, persistencia, historial, aislamiento, plataforma, sesiones revocadas y fallo del proveedor.

La compilación del frontend pasó y el navegador verificó el ingreso y sus validaciones. El ingreso con credenciales reales corresponde al propietario desde el portal; las pruebas automatizadas simulan al proveedor de identidad.

## Siguiente entrega

Conversación pública, adjuntos privados, notas internas, responsables, cambios de estado/prioridad y acuerdos de servicio con calendarios, pausas, alertas y notificaciones.

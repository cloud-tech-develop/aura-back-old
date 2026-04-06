# EP-005: Módulo de Empleados, Vendedores y Gestión de Locales

## Descripción
Módulo para la gestión de empleados, incluyendo la definición de tipos de empleado (Vendedor, Cajero, Gerente, Administrador, Oficios, etc.) y su asociación a los empleados. Adicionalmente, incluye la gestión de locales (clientes de vendedores), rutas y programación de visitas.

## Criterios de Aceptación
- [x] El usuario puede crear y editar tipos de empleado con su nombre y descripción
- [x] El usuario puede visualizar una lista de tipos de empleado
- [x] El usuario puede eliminar tipos de empleado (validando que no estén en uso)
- [x] El usuario puede asociar un tipo de empleado a cada empleado
- [x] El sistema filtra todos los datos por empresa_id del JWT
- [x] Se proporciona una lista de tipos de empleado para seleccionar en los formularios de empleado
- [x] El usuario puede gestionar locales con su información completa (nombre, dirección, geo, horarios, preferencias de visita, imagen fachada)
- [x] El usuario puede asignar locales a vendedores (vendedor actual y anterior)
- [x] El usuario puede crear y gestionar rutas de visita
- [x] El usuario puede programar visitas a locales
- [x] El usuario puede listar locales por vendedor y todos los locales de la empresa

## Historias de Usuario
| HU | Título | Estado | Frontend |
|----|--------|--------|----------|
| HU-018 | Gestión de Tipos de Empleado | ✅ Implementada | ✅ Documentado |
| HU-019 | Asociación de Tipo de Empleado a Empleados | ✅ Implementada | ✅ Documentado |
| HU-020 | Listado de Empleados por Tipo | ✅ Implementada | ✅ Documentado |
| HU-021 | Gestión de Locales | ✅ Implementada | ✅ Documentado |
| HU-022 | Asignación de Locales a Vendedores | ✅ Implementada | ✅ Documentado |
| HU-023 | Gestión de Rutas (con precarga) | ✅ Implementada | ✅ Documentado |
| HU-024 | Programación de Visitas (con calendario y confirmación) | ✅ Implementada | ✅ Documentado |
| HU-025 | Listar Locales por Vendedor | ✅ Implementada | ✅ Documentado |
| HU-026 | Listar Todos los Locales de la Empresa | ✅ Implementada | ✅ Documentado |
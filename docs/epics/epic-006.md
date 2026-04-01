# EP-006: Sistema de Permisos de Módulos

## Descripción

Implementar un sistema de permisos que permita al super-admin gestionar qué módulos y submódulos están disponibles para cada empresa. Los módulos y submódulos deben ser creados, editados y eliminados desde la plataforma, y el super-admin puede activar/desactivar el acceso por empresa.

## Motivación

- Controlar el acceso a funcionalidades por empresa
- Permitir personalización del sistema según necesidades de cada cliente
- Sistema flexible para agregar nuevos módulos sin desarrollo

## Modules y Submódulos por Defecto

### Catálogo
- Productos
- Categorías
- Marcas
- Unidades
- Presentaciones
- Composiciones
- Etiquetas

### Precios
- Listas de Precio
- Precio Productos
- Descuentos

### Inventario
- Inventario
- Lotes
- Seriales
- Kardex
- Reconteos

### Operaciones
- Compras
- Ventas
- Mermas
- Traslados
- Cotizaciones

### Vendedores
- Vendedores
- Locales
- Rutas
- Visitas

### Contabilidad
- Cierre Contable
- Estado de Cuenta
- Cuentas por Cobrar
- Cuentas por Pagar

### Administración
- Clientes y Proveedores
- Cajas
- Turnos
- Sucursales
- Usuarios

### Comisiones
- Configuración
- Liquidaciones

### Nómina
- Empleados
- Períodos
- Liquidación
- Configuración

### Reportes
- Ventas
- Inventario

## Criterios de Aceptación

1. [x] El super-admin puede crear, editar y eliminar módulos (HU-027)
2. [x] El super-admin puede crear, editar y eliminar submódulos (HU-028)
3. [x] Cada submódulo pertenece a un módulo padre
4. [x] El super-admin puede activar/desactivar módulos por empresa (HU-029)
5. [x] El super-admin puede activar/desactivar submódulos por empresa (HU-030)
6. [x] Por defecto, todas las empresas tienen todos los módulos/submódulos activos
7. [x] Los controladores de la aplicación verifican permisos antes de ejecutar (HU-031)
8. [x] Existe un endpoint público para obtener permisos (sin auth) (HU-032)

## Historias de Usuario (Backend)

| HU ID | Título | Estado |
|-------|--------|--------|
| HU-027 | Gestión de Módulos | ✅ Implementado (Spring Boot) |
| HU-028 | Gestión de Submódulos | ✅ Implementado (Spring Boot) |
| HU-029 | Permisos de Módulos por Empresa | ✅ Implementado (Spring Boot) |
| HU-030 | Permisos de Submódulos por Empresa | ✅ Implementado (Spring Boot) |
| HU-031 | Validación de Permisos en Controladores | ✅ Implementado (Spring Boot) |
| HU-032 | Endpoint Público de Permisos | ✅ Implementado (Spring Boot) |

## Historias de Usuario (Frontend - Angular/PrimeNG)

| HU ID | Título | Descripción |
|-------|--------|-------------|
| HU-F033 | Gestión de Módulos - Frontend | UI para CRUD de módulos |
| HU-F034 | Gestión de Submódulos - Frontend | UI para CRUD de submódulos |
| HU-F035 | Permisos de Empresa - Frontend | UI para activar/desactivar permisos |
| HU-F036 | Menú Dinámico | Construcción del menú basada en permisos |

## Implementación Laravel

### Estructura de Archivos

```
laravel-backend/
├── app/
│   ├── Console/Commands/
│   │   └── GenerarMigracion.php          # Comando para generar migraciones desde HU
│   ├── Http/
│   │   ├── Controllers/
│   │   │   ├── Api/
│   │   │   │   ├── Platform/
│   │   │   │   │   ├── ModuloController.php
│   │   │   │   │   ├── SubmoduloController.php
│   │   │   │   │   └── PermisoEmpresaController.php
│   │   │   │   └── Public/
│   │   │   │       └── PermisoPublicoController.php
│   │   │   └── Controller.php
│   │   └── Middleware/
│   │       └── VerificarPermiso.php      # Middleware para verificar permisos
│   └── Models/
│       ├── Modulo.php
│       ├── Submodulo.php
│       └── PermisoEmpresa.php
├── database/
│   └── migrations/
│       ├── 2026_01_01_000001_create_modulos_table.php
│       ├── 2026_01_01_000002_create_submodulos_table.php
│       └── 2026_01_01_000003_create_permiso_empresa_table.php
└── routes/
    └── api.php
```

### Comandos Útiles

```bash
# Instalar dependencias
composer install

# Ejecutar migraciones
php artisan migrate

# Generar migración desde HU
php artisan make:migration-from-hu HU-009 --table=cuentas_cobrar --columns="empresa_id:bigint,tercero_id:bigint,venta_id:bigint,nullable,numero_cuenta:varchar,fecha_emision:timestamp,total_deuda:decimal"

# Generar migración con seeds
php artisan make:migration-from-hu HU-027 --table=modulos --seed='[{"nombre":"Catálogo","codigo":"catalogo","orden":1}]'

# Generar con nombre personalizado
php artisan make:migration-from-hu HU-009 --name=create_cuentas_cobrar_table --table=cuentas_cobrar --columns="..."

# Generar sin modelo
php artisan make:migration-from-hu HU-009 --table=cuentas_cobrar --columns="..." --no-model
```

### Ubicación de Archivos

Las migraciones se generan en: `C:/Users/Drako/Desktop/cloud-tecno/aura-pos-migracion-old/`

```
C:/Users/Drako/Desktop/cloud-tecno/
├── aura-pos-migracion-old/          # <-- Repositorio de migraciones
│   ├── database/migrations/
│   │   ├── 2026_01_01_000001_create_modulos_table.php
│   │   ├── 2026_01_01_000002_create_submodulos_table.php
│   │   └── 2026_01_01_000003_create_permiso_empresa_table.php
│   └── ...
└── aura-pos-old/
    └── laravel-backend/             # Solo código PHP (sin migrations)
        └── app/...
```

# EP-004: Módulo de Cuentas por Cobrar y Cuentas por Pagar

## Descripción
Módulo para la gestión de cuentas por cobrar (clientes) y cuentas por pagar (proveedores), incluyendo el registro de abonos, cálculos de saldos pendientes y generación automática desde ventas/compras a crédito.

## Criterios de Aceptación
- [x] El usuario puede visualizar una lista paginada de cuentas por cobrar con filtros
- [x] El usuario puede visualizar una lista paginada de cuentas por pagar con filtros
- [x] El usuario puede crear cuentas por cobrar de forma manual (sin venta asociada)
- [x] El usuario puede crear cuentas por pagar de forma manual (sin compra asociada)
- [x] El usuario puede registrar abonos a cuentas por cobrar (entradas de caja)
- [x] El usuario puede registrar abonos a cuentas por pagar (salidas de caja)
- [x] El sistema calcula automáticamente: total deuda, total abonado y saldo pendiente (desde abonos - HU-015)
- [x] El sistema actualiza el estado de la cuenta cuando se paga completamente
- [x] El sistema genera automáticamente cuentas por cobrar al registrar ventas a crédito (HU-014)
- [x] El sistema genera automáticamente cuentas por pagar al registrar compras a crédito
- [x] Todas las consultas filtran por empresa_id del JWT
- [x] Validaciones: no permitir abonos mayores al saldo pendiente

## Historias de Usuario
| HU | Título | Estado |
|----|--------|--------|
| HU-009 | Gestión de Cuentas por Cobrar | ✅ Implementada |
| HU-010 | Gestión de Cuentas por Pagar | ✅ Implementada |
| HU-011 | Abonos a Cuentas por Cobrar | ✅ Implementada |
| HU-012 | Abonos a Cuentas por Pagar | ✅ Implementada |
| HU-013 | Cálculo de Totales de Cuentas | ✅ Implementada |
| HU-014 | Generación Automática de Cuentas desde Ventas | ✅ Implementada |
| HU-015 | Validación de Totales de Cuentas desde Abonos | ✅ Implementada |

## Dependencias
- EP-001: Módulo de Empresa y Sucursales
- EP-002: Módulo de Terceros (Clientes y Proveedores)
- EP-003: Módulo de Ventas y Compras

## Módulos a Crear
```
src/main/java/com/cloud_technological/aura_pos/
├── controllers/
│   ├── cuentas_cobrar/
│   │   └── CuentasCobrarController.java
│   └── cuentas_pagar/
│       └── CuentasPagarController.java
├── dto/
│   ├── cuentas_cobrar/
│   │   ├── CuentaCobrarDto.java
│   │   ├── CuentaCobrarTableDto.java
│   │   ├── CreateCuentaCobrarDto.java
│   │   └── AbonoCobrarDto.java
│   └── cuentas_pagar/
│       ├── CuentaPagarDto.java
│       ├── CuentaPagarTableDto.java
│       ├── CreateCuentaPagarDto.java
│       └── AbonoPagarDto.java
├── entity/
│   ├── CuentaCobrarEntity.java
│   ├── CuentaPagarEntity.java
│   ├── AbonoCobrarEntity.java
│   └── AbonoPagarEntity.java
├── mappers/
│   ├── CuentaCobrarMapper.java
│   └── CuentaPagarMapper.java
├── repositories/
│   ├── cuentas_cobrar/
│   │   ├── CuentaCobrarJPARepository.java
│   │   └── CuentaCobrarQueryRepository.java
│   └── cuentas_pagar/
│       ├── CuentaPagarJPARepository.java
│       └── CuentaPagarQueryRepository.java
└── services/
    ├── CuentaCobrarService.java
    ├── CuentaCobrarServiceImpl.java
    ├── CuentaPagarService.java
    └── CuentaPagarServiceImpl.java
```

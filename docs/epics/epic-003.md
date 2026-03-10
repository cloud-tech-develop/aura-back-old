# EPIC-003 - Módulo de Facturación Electrónica

## 📌 Información General
- ID: EPIC-003
- Estado: Backlog
- Prioridad: Alta
- Fecha inicio: 2026-02-24
- Fecha objetivo: 2026-04-30
- Owner: Equipo Backend Aura POS
- Porcentaje: 0%

---

## 🎯 Objetivo de Negocio

Implementar un módulo de facturación electrónica que permita generar, gestionar y enviar facturas electrónicas a la DIAN (Dirección de Impuestos y Aduanas Nacionales) de Colombia. El sistema debe integrarse con el proceso de ventas existente para generar automáticamente facturas válidas con CUFE (Código Único de Factura Electrónica) y cumplir con los requisitos legales de facturación electrónica.

Este módulo resolverá la necesidad de automatizar el proceso de facturación, reducir errores manuales y garantizar el cumplimiento tributario mediante la integración directa con la plataforma de la DIAN.

---

## 👥 Stakeholders

- Usuario final: Contadores, administradores de tienda, propietarios de negocio
- Equipo técnico: Backend developers Aura POS
- Producto: Product Manager Aura POS

---

## 🧠 Descripción Funcional General

El módulo de facturación electrónica gestionará el ciclo de vida completo de las facturas electrónicas, desde su generación automática tras una venta hasta el envío a la DIAN y almacenamiento seguro. Incluirá la gestión de prefijos y consecutivos, cálculo de impuestos, generación del CUFE, manejo de estados de validación DIAN, y registro de pagos asociados a cada factura.

El sistema permitirá múltiples métodos de pago (efectivo, transferencia, tarjeta, crédito), descuentos, y mantendrá trazabilidad completa de cada documento mediante registros de auditoría (created_at, updated_at, deleted_at).

---

## 📦 Alcance

Incluye:
- Generación automática de facturas electrónicas al completar una venta
- Gestión de prefijos y consecutivos por empresa
- Cálculo de valores, descuentos e impuestos
- Generación de código CUFE único por factura
- Integración con estados de validación DIAN (autorizado, rechazado, pendientes)
- Registro de pagos asociados a facturas (ReciboPago)
- Soporte para múltiples métodos de pago
- Ambientes de desarrollo y producción
- Soft delete para integridad de datos

No incluye:
- Envío directo de facturas a clientes por email (pendiente módulo notificación)
- Generación de XML estructurado para DIAN
- Timbre fiscal
- Notas crédito/débito avanzadas

---

## 🧩 Historias de Usuario Asociadas

- [ ] HU-003 - Generación automática de facturas por venta
- [ ] HU-004 - Registro de pagos de facturas
- [ ] HU-005 - Consulta de facturas por empresa
- [ ] HU-006 - Anulación de facturas electrónicas
- [ ] HU-007 - Reporte de facturación por período

---

## 🐞 Bugs Asociados

N/A - Módulo nuevo

---

## 🔐 Reglas de Negocio Globales

- Toda factura debe tener un CUFE único generado con algoritmo SHA-384
- El consecutivo de factura debe ser secuencial por prefijo
- Los estados DIAN se sincronizan con la plataforma de la DIAN
- Solo usuarios con rol ADMIN o CONTADOR pueden anular facturas
- Las facturas eliminadas (soft delete) mantienen historial para auditoría
- Cada empresa puede tener múltiples prefijos de facturación
- El ambiente (dev/prod) determina el endpoint de la DIAN a consumir

---

## 🧱 Arquitectura Relacionada

Frontend: React/Vue (consumo API REST)
Backend: Spring Boot 3.5.10 (Java 17)
Base de datos: PostgreSQL
Autenticación: JWT con roles (ADMIN, CONTADOR, VENDEDOR, CAJERO)

---

## 📊 Métricas de Éxito

- % de facturas generadas automáticamente sin errores > 95%
- Tiempo promedio de generación de factura < 2 segundos
- Tasa de rechazo DIAN < 5%
- Precisión en cálculo de impuestos 100%

---

## 🚧 Riesgos

- Dependencia de disponibilidad de API DIAN
- Cambios en normativa DIAN que requieran actualizaciones
- Validación de schema XML para facturación electrónica

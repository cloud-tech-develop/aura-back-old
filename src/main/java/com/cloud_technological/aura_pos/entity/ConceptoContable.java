package com.cloud_technological.aura_pos.entity;

/**
 * Conceptos contables que el motor de asientos necesita resolver a una cuenta
 * del PUC. Cada concepto trae el código por defecto del PUC básico colombiano
 * sembrado en {@code PlanCuentasServiceImpl.seedPUC}; cada empresa puede
 * remapearlo mediante {@code CuentaConfigEntity} sin tocar código.
 *
 * Reemplaza los códigos hardcodeados que vivían en ContabilidadAutoServiceImpl.
 */
public enum ConceptoContable {

    // ── Operación comercial ───────────────────────────────────────────────
    CLIENTES("1305", "Clientes (cartera)"),
    PROVEEDORES("2205", "Proveedores"),
    INVENTARIO("1435", "Inventario de mercancías"),
    INGRESOS_VENTAS("4135", "Ingresos por ventas"),
    COSTO_VENTAS("6135", "Costo de ventas"),

    // ── Impuestos ─────────────────────────────────────────────────────────
    IVA_GENERADO("2408", "IVA generado (ventas)"),
    IVA_DESCONTABLE("2408", "IVA descontable (compras)"),

    // ── Retenciones practicadas (las que retiene la empresa: pasivo) ──────
    RETEFUENTE_PRACTICADA("2365", "Retención en la fuente practicada"),
    RETEIVA_PRACTICADA("2367", "ReteIVA practicada"),
    RETEICA_PRACTICADA("2368", "ReteICA practicada"),

    // ── Retenciones que le practican a la empresa (anticipo: activo) ──────
    RETEFUENTE_ASUMIDA("1355", "Retención en la fuente que nos practican"),

    // ── Disponible / tesorería ────────────────────────────────────────────
    CAJA("1105", "Caja"),
    BANCOS("1110", "Bancos"),

    // ── Financiación ──────────────────────────────────────────────────────
    OBLIGACIONES_FINANCIERAS("2105", "Obligaciones financieras"),
    GASTOS_FINANCIEROS("5305", "Gastos financieros (intereses)"),

    // ── Nómina ────────────────────────────────────────────────────────────
    GASTOS_PERSONAL("5105", "Gastos de personal"),
    SALARIOS_POR_PAGAR("2505", "Salarios por pagar"),
    DEDUCCIONES_NOMINA_POR_PAGAR("2505", "Deducciones de nómina por pagar (salud/pensión empleado)"),
    APORTES_NOMINA_POR_PAGAR("2505", "Aportes patronales de nómina por pagar"),
    PROVISIONES_NOMINA_POR_PAGAR("2505", "Provisiones de prestaciones por pagar"),

    // ── Gastos / pérdidas ─────────────────────────────────────────────────
    GASTO_GENERAL("5195", "Gasto general (cuenta por defecto)"),
    PERDIDA_MERMA("5195", "Pérdida por merma de inventario"),

    // ── Cierre / depreciación ─────────────────────────────────────────────
    UTILIDAD_EJERCICIO("3605", "Utilidad del ejercicio"),
    DEPRECIACION_GASTO("5160", "Gasto por depreciación"),
    DEPRECIACION_ACUMULADA("1592", "Depreciación acumulada"),

    // ── Patrimonio / apertura (saldos iniciales) ──────────────────────────
    CAPITAL_SOCIAL("3105", "Capital social"),
    RESULTADOS_ACUMULADOS("3705", "Resultados de ejercicios anteriores");

    private final String codigoDefault;
    private final String descripcion;

    ConceptoContable(String codigoDefault, String descripcion) {
        this.codigoDefault = codigoDefault;
        this.descripcion = descripcion;
    }

    public String getCodigoDefault() {
        return codigoDefault;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

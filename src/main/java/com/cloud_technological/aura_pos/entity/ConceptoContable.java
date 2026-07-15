package com.cloud_technological.aura_pos.entity;

/**
 * Conceptos contables que el motor de asientos necesita resolver a una cuenta
 * del PUC. Cada concepto trae el código por defecto del PUC básico colombiano
 * sembrado en {@code PlanCuentasServiceImpl.seedPUC}; cada empresa puede
 * remapearlo mediante {@code CuentaConfigEntity} sin tocar código.
 *
 * Guardarraíl (ADR-006): el remapeo solo acepta cuentas cuyo código empiece
 * por alguno de los prefijos permitidos del concepto — se parametrizan
 * DESTINOS dentro de la clase PUC correcta, nunca la lógica del asiento.
 *
 * Reemplaza los códigos hardcodeados que vivían en ContabilidadAutoServiceImpl.
 */
public enum ConceptoContable {

    // ── Operación comercial ───────────────────────────────────────────────
    CLIENTES("1305", "Clientes (cartera)", "13"),
    PROVEEDORES("2205", "Proveedores", "22"),
    INVENTARIO("1435", "Inventario de mercancías", "14"),
    INGRESOS_VENTAS("4135", "Ingresos por ventas", "4"),
    COSTO_VENTAS("6135", "Costo de ventas", "6"),

    // ── Impuestos ─────────────────────────────────────────────────────────
    // E5: generado y descontable van a subcuentas separadas de 2408 —
    // mezclarlos rompe el reporte de IVA neto.
    IVA_GENERADO("240801", "IVA generado (ventas)", "24"),
    IVA_DESCONTABLE("240802", "IVA descontable (compras)", "24"),

    // ── Retenciones practicadas (las que retiene la empresa: pasivo) ──────
    RETEFUENTE_PRACTICADA("2365", "Retención en la fuente practicada", "23"),
    RETEIVA_PRACTICADA("2367", "ReteIVA practicada", "23"),
    RETEICA_PRACTICADA("2368", "ReteICA practicada", "23"),

    // ── Retenciones que le practican a la empresa (anticipo: activo) ──────
    RETEFUENTE_ASUMIDA("1355", "Retención en la fuente que nos practican", "13"),

    // ── Disponible / tesorería ────────────────────────────────────────────
    CAJA("1105", "Caja", "11"),
    BANCOS("1110", "Bancos", "11"),

    // ── Financiación ──────────────────────────────────────────────────────
    OBLIGACIONES_FINANCIERAS("2105", "Obligaciones financieras", "21"),
    GASTOS_FINANCIEROS("5305", "Gastos financieros (intereses)", "53"),
    SOBREGIROS_BANCARIOS("2105", "Sobregiros bancarios (reclasificación de cierre)", "21"),

    // ── Nómina ────────────────────────────────────────────────────────────
    // Legacy (agrupadoras) — se conservan por compatibilidad, ya no se usan
    // en el asiento de nómina desglosado.
    GASTOS_PERSONAL("5105", "Gastos de personal (agrupadora)", "51", "52"),
    SALARIOS_POR_PAGAR("2505", "Salarios por pagar", "25"),
    DEDUCCIONES_NOMINA_POR_PAGAR("2505", "Deducciones de nómina por pagar (legacy)", "23", "25"),
    APORTES_NOMINA_POR_PAGAR("2505", "Aportes patronales de nómina por pagar (legacy)", "23", "25"),
    PROVISIONES_NOMINA_POR_PAGAR("2505", "Provisiones de prestaciones por pagar (legacy)", "23", "25"),

    // ── Nómina · gasto por auxiliar (DÉBITO, cuentas 5105xx) ──────────────
    NOMINA_SUELDOS("510506", "Nómina · sueldos y devengados", "51", "52"),
    NOMINA_APORTE_SALUD("510568", "Nómina · aporte patronal salud", "51", "52"),
    NOMINA_APORTE_PENSION("510569", "Nómina · aporte patronal pensión", "51", "52"),
    NOMINA_ARL("510570", "Nómina · ARL", "51", "52"),
    NOMINA_CAJA("510572", "Nómina · caja de compensación", "51", "52"),
    NOMINA_SENA_ICBF("510575", "Nómina · SENA e ICBF", "51", "52"),
    NOMINA_PRIMA("510536", "Nómina · provisión prima de servicios", "51", "52"),
    NOMINA_CESANTIAS("510530", "Nómina · provisión cesantías", "51", "52"),
    NOMINA_INT_CESANTIAS("510533", "Nómina · provisión intereses de cesantías", "51", "52"),
    NOMINA_VACACIONES("510539", "Nómina · provisión vacaciones", "51", "52"),
    NOMINA_INDEMNIZACION("510548", "Nómina · indemnización laboral (despido sin justa causa)", "51", "52"),

    // ── Nómina · pasivo por pagar (CRÉDITO) ───────────────────────────────
    SEGURIDAD_SOCIAL_POR_PAGAR("2370", "Nómina · seguridad social y parafiscales por pagar", "23"),
    OTRAS_DEDUCCIONES_POR_PAGAR("2380", "Nómina · otras deducciones por pagar (préstamos/embargos)", "23"),
    CESANTIAS_POR_PAGAR("2510", "Nómina · cesantías consolidadas por pagar", "25"),
    INT_CESANTIAS_POR_PAGAR("2515", "Nómina · intereses de cesantías por pagar", "25"),
    PRIMA_POR_PAGAR("2520", "Nómina · prima de servicios por pagar", "25"),
    VACACIONES_POR_PAGAR("2525", "Nómina · vacaciones consolidadas por pagar", "25"),

    // ── Gastos / pérdidas ─────────────────────────────────────────────────
    GASTO_GENERAL("5195", "Gasto general (cuenta por defecto)", "5"),
    PERDIDA_MERMA("5195", "Pérdida por merma de inventario", "5"),

    // ── Devengo (E6) ──────────────────────────────────────────────────────
    ANTICIPOS_CLIENTES("2805", "Anticipos recibidos de clientes", "28"),
    ANTICIPOS_PROVEEDORES("1330", "Anticipos entregados a proveedores", "13"),
    GASTOS_PAGADOS_ANTICIPADO("1705", "Gastos pagados por anticipado (diferidos)", "17"),
    DETERIORO_CARTERA("5199", "Gasto por deterioro de cartera", "5"),
    PROVISION_CARTERA("1399", "Provisión de cartera (deterioro acumulado)", "13"),
    PROVISION_INVENTARIO("1499", "Provisión de inventarios (obsolescencia)", "14"),

    // ── Diferencias de cierre de caja (E3) ────────────────────────────────
    // Defaults sobre cuentas del seed; el contador puede remapear a
    // auxiliares dedicadas (519530 / 4295xx) cuando las cree.
    GASTO_DIFERENCIA_CAJA("5195", "Faltante de caja (diferencia de cierre)", "5"),
    INGRESO_SOBRANTE_CAJA("4295", "Sobrante de caja (diferencia de cierre)", "42"),

    // ── Conciliación bancaria (E9) ────────────────────────────────────────
    // Ajustes que nacen del extracto: cargos del banco sin registro en el
    // libro (comisiones, 4x1000) e intereses abonados/cobrados.
    GASTOS_BANCARIOS("530515", "Comisiones y gastos bancarios", "53"),
    GMF("530595", "Gravamen a los movimientos financieros (4x1000)", "53"),
    INGRESOS_FINANCIEROS("421005", "Ingresos financieros (intereses bancarios)", "42"),

    // ── Cierre anual fiscal (E8) ──────────────────────────────────────────
    GASTO_IMPUESTO_RENTA("5405", "Gasto impuesto de renta y complementarios", "54"),
    IMPUESTO_RENTA_POR_PAGAR("2404", "Impuesto de renta por pagar", "24"),
    RESERVA_LEGAL("330505", "Reserva legal", "33"),
    DIVIDENDOS_POR_PAGAR("2360", "Dividendos o participaciones por pagar", "23"),

    // ── Cierre / depreciación ─────────────────────────────────────────────
    UTILIDAD_EJERCICIO("3605", "Utilidad del ejercicio", "36"),
    DEPRECIACION_GASTO("5160", "Gasto por depreciación", "51", "52"),
    DEPRECIACION_ACUMULADA("1592", "Depreciación acumulada", "15", "16"),

    // ── Patrimonio / apertura (saldos iniciales) ──────────────────────────
    CAPITAL_SOCIAL("3105", "Capital social", "31"),
    RESULTADOS_ACUMULADOS("3705", "Resultados de ejercicios anteriores", "36", "37");

    private final String codigoDefault;
    private final String descripcion;
    private final String[] prefijosPermitidos;

    ConceptoContable(String codigoDefault, String descripcion, String... prefijosPermitidos) {
        this.codigoDefault = codigoDefault;
        this.descripcion = descripcion;
        this.prefijosPermitidos = prefijosPermitidos;
    }

    public String getCodigoDefault() {
        return codigoDefault;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String[] getPrefijosPermitidos() {
        return prefijosPermitidos.clone();
    }

    /** ¿El código de cuenta pertenece a la clase PUC permitida del concepto? */
    public boolean permiteCodigo(String codigoCuenta) {
        if (codigoCuenta == null) {
            return false;
        }
        for (String prefijo : prefijosPermitidos) {
            if (codigoCuenta.startsWith(prefijo)) {
                return true;
            }
        }
        return false;
    }

    /** Prefijos permitidos legibles para mensajes de error ("13xx, 14xx"). */
    public String prefijosLegibles() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prefijosPermitidos.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(prefijosPermitidos[i]).append("xx");
        }
        return sb.toString();
    }
}

package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;

public interface ContabilidadAutoService {
    /** Genera el asiento contable para una venta. Idempotente: no duplica si ya existe. */
    AsientoContableTableDto generarDesdeVenta(Long ventaId, Integer empresaId, Integer usuarioId);

    /** Genera el asiento contable para una compra. Idempotente. */
    AsientoContableTableDto generarDesdeCompra(Long compraId, Integer empresaId, Integer usuarioId);

    /**
     * Genera el contraasiento (reversa) de una anulación, intercambiando débito y
     * crédito del asiento original. {@code origenTipo} es "VENTA", "COMPRA" o
     * "DEVOLUCION". Idempotente; no-op si no existe asiento original que reversar.
     */
    AsientoContableTableDto reversar(String origenTipo, Long origenId, Integer empresaId, Integer usuarioId);

    /**
     * Genera el asiento contable de una devolución de venta (reversa parcial):
     * reduce ingreso e IVA, devuelve por cartera y/o caja, y reingresa inventario
     * con su costo. Idempotente.
     */
    AsientoContableTableDto generarDesdeDevolucion(Long devolucionId, Integer empresaId, Integer usuarioId);

    /** Cobro de cartera: DB Caja/Bancos · CR Clientes. Idempotente. */
    AsientoContableTableDto generarDesdeAbonoCobro(Long abonoId, Integer empresaId, Integer usuarioId);

    /** Pago a proveedor: DB Proveedores · CR Caja/Bancos. Idempotente. */
    AsientoContableTableDto generarDesdeAbonoPago(Long abonoId, Integer empresaId, Integer usuarioId);

    /** Gasto: DB cuenta de gasto + IVA · CR retenciones + Caja. Idempotente. */
    AsientoContableTableDto generarDesdeGasto(Long gastoId, Integer empresaId, Integer usuarioId);

    /** Merma: DB pérdida de inventario · CR Inventario (costo). Idempotente. */
    AsientoContableTableDto generarDesdeMerma(Long mermaId, Integer empresaId, Integer usuarioId);

    /**
     * Nómina: DB Gastos de personal (devengado + aportes + provisiones) · CR
     * Salarios por pagar (neto) + deducciones + aportes + provisiones por pagar.
     * Idempotente.
     */
    AsientoContableTableDto generarDesdeNomina(Long nominaId, Integer empresaId, Integer usuarioId);
    AsientoContableTableDto generarDesdePagoNomina(Long nominaId, Integer empresaId, Integer usuarioId);
    AsientoContableTableDto generarDesdePagoPrestacion(Long prestacionId, Integer empresaId, Integer usuarioId);

    /**
     * Asiento de cierre del período: cancela las cuentas de ingreso, costo y gasto
     * contra la utilidad del ejercicio (resultado al patrimonio). Pensado para
     * ejecutarse dentro de la transacción del cierre del período. Idempotente;
     * no-op si no hay movimientos de resultado.
     */
    AsientoContableTableDto generarCierre(Long periodoId, Integer empresaId, Integer usuarioId);

    /**
     * Reclasificación de sobregiros al cerrar el período (E2): por cada cuenta
     * bancaria con sobregiro cuyo saldo contable quedó crédito, DB cuenta del
     * banco · CR Sobregiros bancarios (21xx). Se reversa al abrir el período
     * siguiente. Idempotente por período; no-op sin bancos sobregirados.
     */
    AsientoContableTableDto generarReclasificacionSobregiro(Long periodoId, Integer empresaId,
            Integer usuarioId);

    /** Desembolso de préstamo: DB Bancos · CR Obligaciones financieras. Idempotente. */
    AsientoContableTableDto generarDesdeObligacion(Long obligacionId, Integer empresaId, Integer usuarioId);

    /** Pago de cuota: DB Obligaciones (capital) + DB Gasto financiero (interés) · CR Bancos. Idempotente. */
    AsientoContableTableDto generarDesdePagoCuota(Long cuotaId, Integer empresaId, Integer usuarioId);

    /**
     * Movimiento de caja manual con concepto: ingreso (DB Caja · CR cuenta del
     * concepto) o egreso (DB cuenta del concepto · CR Caja). Idempotente.
     */
    AsientoContableTableDto generarDesdeMovimientoCaja(Long movimientoId, Integer empresaId, Integer usuarioId);

    /**
     * Movimiento de tesorería (recaudo/egreso/transferencia sobre una cuenta
     * bancaria): entrada (DB Banco · CR contrapartida) o salida (DB contrapartida
     * · CR Banco). El banco se resuelve por la cuenta contable de la cuenta
     * bancaria; la contrapartida viene en el propio movimiento. Idempotente.
     */
    AsientoContableTableDto generarDesdeTesoreria(Long movimientoId, Integer empresaId, Integer usuarioId);
}

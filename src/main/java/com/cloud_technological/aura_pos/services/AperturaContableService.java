package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateSaldosInicialesDto;
import com.cloud_technological.aura_pos.dto.contabilidad.SaldoInicialLineaDto;

public interface AperturaContableService {

    /** Devuelve el asiento de apertura existente (con detalles) o null si no hay. */
    AsientoContableTableDto obtener(Integer empresaId);

    /**
     * Sugiere las líneas de apertura a partir de las cuentas bancarias que tienen
     * cuenta contable asignada y saldo inicial: cada banco entra al débito por su
     * saldo inicial. El frontend las precarga en el formulario de saldos iniciales.
     */
    List<SaldoInicialLineaDto> sugerirDesdeBancos(Integer empresaId);

    /** Crea el asiento de apertura con los saldos iniciales. Uno por empresa. */
    AsientoContableTableDto guardar(CreateSaldosInicialesDto dto, Integer empresaId, Integer usuarioId);

    /**
     * Crea la apertura directamente desde las cuentas bancarias (saldo actual de
     * cada banco a su cuenta contable, descuadre a patrimonio). Atajo de un tiro
     * para migrar clientes que ya tienen cuentas con saldos.
     */
    AsientoContableTableDto guardarDesdeBancos(Integer empresaId, java.time.LocalDate fecha, Integer usuarioId);

    /** Elimina el asiento de apertura (para rehacerlo durante la configuración inicial). */
    void eliminar(Integer empresaId);
}

package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.CrearPrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.LotePrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.PrestacionDto;

public interface PrestacionService {
    List<PrestacionDto> listar(Integer empresaId);
    PrestacionDto crear(CrearPrestacionDto dto, Integer empresaId);

    /** F4 — genera una prestación (prima/cesantías/intereses) para todos los activos, en un lote. */
    List<PrestacionDto> generarLote(
            com.cloud_technological.aura_pos.dto.nomina.prestacion.GenerarLotePrestacionDto dto, Integer empresaId);
    List<PrestacionDto> liquidacionDefinitiva(
            com.cloud_technological.aura_pos.dto.nomina.prestacion.LiquidacionDefinitivaDto dto, Integer empresaId);
    PrestacionDto aprobar(Long id, Integer empresaId);
    PrestacionDto pagar(Long id, PagoNominaDto dto, Integer empresaId);
    /** B-07 — confirma el pago de una transferencia PROGRAMADA (banco confirmó). */
    PrestacionDto confirmarPago(Long id, Integer empresaId);
    PrestacionDto anular(Long id, Integer empresaId);

    // ── Lotes (V122) ──
    List<LotePrestacionDto> listarLotes(Integer empresaId);
    List<PrestacionDto> detalleLote(String lote, Integer empresaId);
    List<PrestacionDto> aprobarLote(String lote, Integer empresaId);
    List<PrestacionDto> pagarLote(String lote, PagoNominaDto dto, Integer empresaId);
    /** B-07 — confirma el pago de todas las transferencias PROGRAMADA del lote. */
    List<PrestacionDto> confirmarPagoLote(String lote, Integer empresaId);
    List<PrestacionDto> anularLote(String lote, Integer empresaId);
}

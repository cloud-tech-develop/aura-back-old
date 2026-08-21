package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.caja.AbrirTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CerrarTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CreateMovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.MovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.ResumenTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface TurnoCajaService {
    PageImpl<TurnoCajaTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    TurnoCajaDto obtenerPorId(Long id, Integer empresaId);
    TurnoCajaDto obtenerTurnoActivo(Long usuarioId);

    /**
     * Turnos abiertos de la empresa, opcionalmente de una sola sucursal.
     *
     * <p>El front lo necesita para preguntar de qué caja sale la plata en vez de
     * deducirlo. Con dos cajas abiertas, inferirla equivale a adivinar de cuál
     * cajon salio el dinero.
     *
     * @param sucursalId opcional; null trae las de toda la empresa
     */
    List<TurnoCajaDto> listarAbiertos(Integer empresaId, Integer sucursalId);
    TurnoCajaDto abrir(AbrirTurnoDto dto, Integer empresaId, Long usuarioId);
    ResumenTurnoDto cerrar(Long id, CerrarTurnoDto dto, Integer empresaId);
    ResumenTurnoDto resumen(Long id, Integer empresaId);
    MovimientoCajaDto registrarMovimiento(Long turnoId, CreateMovimientoCajaDto dto, Integer empresaId, Long usuarioId);

    /**
     * Corrige un arqueo ya cerrado sin reabrirlo.
     *
     * <p>Es el único camino por el que un movimiento entra a un turno CERRADO, y
     * existe porque la alternativa — reabrir el turno y volver a cerrarlo —
     * destruye la evidencia: un cierre reescribible deja de probar lo que el
     * cajero entregó ese día, obliga a reversar el asiento de diferencia, choca
     * con el período contable cerrado, y es la puerta de fraude más obvia
     * ("reabro el día donde falta plata y lo cuadro").
     *
     * <p>El cierre original queda intacto: el ajuste se suma encima y el resumen
     * muestra las tres cifras por separado.
     */
    MovimientoCajaDto registrarAjusteRetroactivo(Long turnoId,
            com.cloud_technological.aura_pos.dto.caja.CreateAjusteRetroactivoDto dto,
            Integer empresaId, Long usuarioId);
}

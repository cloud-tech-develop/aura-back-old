package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.AsientoContableTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.BalanceGeneralDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateAsientoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.CreateComprobanteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.EstadoResultadosDto;
import com.cloud_technological.aura_pos.dto.contabilidad.FlujoCajaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.LibroMayorLineaDto;

public interface AsientoContableService {
    List<AsientoContableTableDto> listar(Integer empresaId, String desde, String hasta,
            String tipoOrigen, int page, int rows);
    AsientoContableTableDto obtenerConDetalles(Long id, Integer empresaId);
    AsientoContableTableDto crear(Integer empresaId, Integer usuarioId, CreateAsientoDto dto);
    /** Crea un comprobante manual (CD/CE/RC) con cabecera de beneficiario y lo contabiliza. */
    AsientoContableTableDto crearComprobante(Integer empresaId, Integer usuarioId, CreateComprobanteDto dto);
    /** Previsualiza el próximo consecutivo para un tipo de comprobante (CD/CE/RC). */
    String siguienteConsecutivo(Integer empresaId, String tipoComprobante);
    void anular(Long id, Integer empresaId);
    BalanceGeneralDto balanceGeneral(Integer empresaId, String hasta);
    /** Estado de resultados con filtros de dimensión opcionales (E7): centro de costo, proyecto, frente. */
    EstadoResultadosDto estadoResultados(Integer empresaId, String desde, String hasta,
            Long centroCostoId, Long proyectoId, Long frenteId);
    /** Libro mayor (auxiliar) con filtros de dimensión opcionales (E7). */
    List<LibroMayorLineaDto> libroMayor(Integer empresaId, Long cuentaId, String desde, String hasta,
            Long centroCostoId, Long proyectoId, Long frenteId);
    FlujoCajaDto flujoCaja(Integer empresaId, String desde, String hasta);
}

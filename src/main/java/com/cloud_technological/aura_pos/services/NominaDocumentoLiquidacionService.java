package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.documento.DetalleLiquidacionDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.LiquidacionEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProcesoDocumentoDto;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;

/**
 * Liquidación de la prenómina (rediseño R3.2c): lanzar el motor sobre las filas del
 * documento en segundo plano y consultar el resultado (pestañas Liquidación y Traza).
 */
public interface NominaDocumentoLiquidacionService {

    /**
     * Lanza la liquidación en segundo plano.
     *
     * @param nominaIds      filas a liquidar; vacío = todas (o las pendientes)
     * @param soloPendientes con {@code nominaIds} vacío: solo las aún no liquidadas
     * @return el proceso creado (el front consulta su avance en {@code /api/proceso/{id}})
     * @throws com.cloud_technological.aura_pos.utils.GlobalException 409 si la prenómina
     *         no está preliminar o ya hay una liquidación corriendo; 400 si no tiene
     *         empleados o su comprobante aún no se liquida por documento
     */
    ProcesoNominaEntity liquidar(Long documentoId, List<Long> nominaIds, boolean soloPendientes, Integer empresaId,
            Long usuarioId);

    /** Totales por empleado (o la causa por la que no se liquidó). */
    List<LiquidacionEmpleadoDto> liquidacion(Long documentoId, Integer empresaId);

    /** Detalle por concepto de un empleado, con la traza de cada línea. */
    List<DetalleLiquidacionDto> detalle(Long documentoId, Long nominaId, Integer empresaId);

    /** Corridas de liquidación del documento (pestaña Traza). */
    List<ProcesoDocumentoDto> traza(Long documentoId, Integer empresaId);
}

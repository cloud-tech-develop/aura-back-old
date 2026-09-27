package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.util.List;

import com.cloud_technological.aura_pos.dto.cartera.acuerdo.AcuerdoPagoDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaTableDto;

import lombok.Getter;
import lombok.Setter;

/** Todo lo que el cobrador necesita de un cliente en una sola respuesta. */
@Getter
@Setter
public class FichaClienteCarteraDto {
    private FichaClienteDto cliente;
    private FichaResumenDto resumen;
    private List<FichaFacturaDto> facturas;
    private List<FichaPagoDto> pagos;
    private List<ReciboCajaTableDto> recibos;
    private List<FichaGestionDto> gestiones;
    private List<FichaAnticipoDto> anticipos;
    private List<FichaHistorialCreditoDto> historialCredito;
    private List<AcuerdoPagoDto> acuerdos;
}

package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EstadoResultadosDto {
    private String desde;
    private String hasta;
    private List<EstadoResultadosLineaDto> ingresos;
    private List<EstadoResultadosLineaDto> costos;
    private List<EstadoResultadosLineaDto> gastos;
    private BigDecimal totalIngresos;
    private BigDecimal totalCostos;
    private BigDecimal totalGastos;
    private BigDecimal utilidadBruta;   // totalIngresos - totalCostos
    private BigDecimal utilidadNeta;    // utilidadBruta - totalGastos
    private BigDecimal margenBruto;     // utilidadBruta / totalIngresos * 100
    private BigDecimal margenNeto;      // utilidadNeta  / totalIngresos * 100
}

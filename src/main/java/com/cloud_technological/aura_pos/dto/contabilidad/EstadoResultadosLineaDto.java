package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EstadoResultadosLineaDto {
    private String tipo;      // INGRESO | COSTO | GASTO
    private String codigo;
    private String nombre;
    private BigDecimal saldo;
}

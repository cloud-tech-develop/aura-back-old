package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LibroMayorLineaDto {
    private String fecha;
    private String numeroComprobante;
    private String descripcion;
    private String descripcionLinea;
    private String tipoOrigen;
    private BigDecimal debito;
    private BigDecimal credito;
    private BigDecimal saldoAcumulado;
}

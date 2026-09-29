package com.cloud_technological.aura_pos.dto.cierre_contable;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Un día (o un mes) del período: lo que se vendió y lo que costó. */
@Getter
@Setter
public class PuntoCierreDto {
    /** yyyy-MM-dd cuando la granularidad es DIA; yyyy-MM cuando es MES. */
    private String etiqueta;
    private BigDecimal ventas;
    private BigDecimal costo;
    /** ventas − costo: lo que quedó de margen ese día. */
    private BigDecimal utilidadBruta;
}

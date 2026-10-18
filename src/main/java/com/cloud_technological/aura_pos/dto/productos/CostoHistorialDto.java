package com.cloud_technological.aura_pos.dto.productos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Un cambio del costo promedio del producto (V226), tal como se muestra en la ficha. */
@Getter
@Setter
public class CostoHistorialDto {
    private Long id;
    private LocalDateTime fecha;
    /** COMPRA, NOTA_CREDITO_COMPRA, ANULACION_COMPRA, EDICION_COMPRA, AJUSTE_MANUAL… */
    private String origen;
    private Long documentoId;
    private String documentoNumero;
    private String terceroNombre;
    /** Unidades base del movimiento, con signo. */
    private BigDecimal cantidad;
    private BigDecimal costoEntrada;
    private BigDecimal stockAntes;
    private BigDecimal stockDespues;
    private BigDecimal costoAnterior;
    private BigDecimal costoNuevo;
    private String usuarioNombre;
    private String observacion;
}

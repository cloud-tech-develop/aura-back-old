package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductoComposicionDto {
    private Long id;
    private Long productoPadreId;
    private String productoPadreNombre;
    private Long productoHijoId;
    private String productoHijoNombre;

    /** Derivada: consumo en unidad base del hijo por 1 unidad del padre. */
    private BigDecimal cantidad;

    private String tipo; // KIT, RECETA

    /** Lo que escribió el usuario, por lote. */
    private BigDecimal cantidadReceta;
    private Long unidadMedidaId;
    private String unidadMedidaAbreviatura;
    private Long productoPresentacionId;
    private BigDecimal factorUnidad;
    private BigDecimal mermaPorcentaje;
    private Integer orden;
    private String nota;
}

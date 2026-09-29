package com.cloud_technological.aura_pos.dto.producto_composicion;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** Línea de receta como la devuelve el backend, ya resuelta para pintar la grilla. */
@Getter
@Setter
public class RecetaComponenteDetalleDto {
    private Long id;
    private Long productoHijoId;
    private String productoHijoNombre;
    private String productoHijoSku;

    /** Lo que escribió el usuario, por lote. */
    private BigDecimal cantidadReceta;
    private Long unidadMedidaId;
    private String unidadMedidaNombre;
    private String unidadMedidaAbreviatura;

    private Long productoPresentacionId;
    private String productoPresentacionNombre;
    private BigDecimal factorUnidad;
    private BigDecimal mermaPorcentaje;

    /** Derivada: consumo en unidad base del hijo por 1 unidad del padre. */
    private BigDecimal cantidad;

    /** Unidad base de stock del componente, para mostrar la equivalencia. */
    private String unidadBaseAbreviatura;

    private Boolean manejaInventario;
    private BigDecimal stockDisponible;

    private Integer orden;
    private String nota;
}

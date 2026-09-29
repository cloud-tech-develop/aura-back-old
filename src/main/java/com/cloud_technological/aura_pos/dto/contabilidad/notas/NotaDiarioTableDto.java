package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Fila del listado de notas. El RowMapper es por nombre de columna: una columna
 * que se agregue al SELECT y no esté aquí no viaja, sin error.
 */
@Getter @Setter
public class NotaDiarioTableDto {
    private Long id;
    /** null mientras la nota es borrador: el consecutivo se asigna al contabilizar. */
    private String numeroComprobante;
    private String fecha;
    private String descripcion;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private String estado;
    private Integer cantidadLineas;
    private String elaboradoPor;
    private String contabilizadoPor;
    private String createdAt;
    private String clasificacion;
    /** Número de la nota que esta revierte, si es una reversión. */
    private String reversaDeNumero;
    /** Número de la nota que revirtió a esta, si fue reversada. */
    private String revertidoPorNumero;
    private Integer cantidadSoportes;
    private Long totalRows;
}

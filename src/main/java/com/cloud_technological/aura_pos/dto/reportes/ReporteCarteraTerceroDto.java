package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del resumen por tercero, con el saldo repartido por edades.
 *
 * <p>Las edades no son adorno: es la diferencia entre "me deben 10 millones" y
 * "me deben 10 millones de los cuales 8 llevan más de 90 días". La segunda
 * frase es la que hace que alguien llame al cliente.
 */
@Getter
@Setter
public class ReporteCarteraTerceroDto {

    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private String terceroTelefono;

    private Integer documentos;
    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;

    /** El saldo repartido por antigüedad de la mora. */
    private BigDecimal corriente;
    private BigDecimal mora1a30;
    private BigDecimal mora31a60;
    private BigDecimal mora61a90;
    private BigDecimal moraMas90;

    /** La mora más vieja del tercero: por dónde empezar a llamar. */
    private Integer diasMoraMax;

    private long totalRows;
}

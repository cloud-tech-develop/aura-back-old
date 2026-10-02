package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * Línea propuesta para la apertura a partir de un auxiliar (inventario,
 * activos, diferidos, cartera, proveedores, bancos).
 */
@Getter
@Setter
public class SugerenciaSaldoInicialDto {
    /** INVENTARIO | ACTIVOS | DIFERIDOS | CARTERA | PROVEEDORES | BANCOS */
    private String fuente;
    private Long cuentaId;
    private String cuentaCodigo;
    private String cuentaNombre;
    private Long terceroId;
    private String terceroNombre;
    private String descripcion;
    private BigDecimal debito = BigDecimal.ZERO;
    private BigDecimal credito = BigDecimal.ZERO;
}

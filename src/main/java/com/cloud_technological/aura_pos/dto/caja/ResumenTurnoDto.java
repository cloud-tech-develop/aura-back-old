package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
public class ResumenTurnoDto {
    // Info del turno
    private Long       turnoId;
    private String     cajaNombre;
    private String     usuarioNombre;
    private String     fechaApertura;
    private BigDecimal baseInicial;
    private String     estado;

    // Desglose por categoría (calculado automático)
    private List<VentaCategoriaDto> ventasPorCategoria;

    // Desglose por método de pago (calculado automático)
    private List<VentaMetodoPagoDto> ventasPorMetodoPago;

    // Totales generales
    private BigDecimal totalVentasBruto;    // suma bruta sin descuentos
    private BigDecimal totalDescuentos;
    private BigDecimal totalImpuestos;
    private BigDecimal totalNeto;           // lo que realmente entró
    private Integer    totalTransacciones;  // cantidad de ventas del turno

    // Cuadre de efectivo
    private BigDecimal totalEfectivoSistema; // lo que dice el sistema
    private BigDecimal totalEfectivoReal;    // lo que declaró la cajera (null si aún abierto)
    private BigDecimal diferencia;   
    // ResumenTurnoDto.java
    private BigDecimal totalEsperado; // base inicial + ventas en efectivo

    // Movimientos manuales de caja (ingresos / egresos)
    private List<MovimientoCajaDto> movimientos = new ArrayList<>();
    private BigDecimal totalIngresos = BigDecimal.ZERO;
    private BigDecimal totalEgresos  = BigDecimal.ZERO;
}

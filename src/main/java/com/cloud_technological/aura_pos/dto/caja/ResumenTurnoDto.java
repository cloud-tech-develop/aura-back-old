package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import com.cloud_technological.aura_pos.dto.caja.DetalleEfectivoDto;

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

    // Movimientos manuales de caja (ingresos / egresos). Incluye TODOS, también
    // los de otras fechas, para no romper a quien ya consume esta lista.
    private List<MovimientoCajaDto> movimientos = new ArrayList<>();
    private BigDecimal totalIngresos = BigDecimal.ZERO;
    private BigDecimal totalEgresos  = BigDecimal.ZERO;

    /**
     * Movimientos cuyo documento es de otro día: una factura de la semana
     * pasada que se pagó hoy de esta caja, por ejemplo.
     *
     * <p>Van aparte porque son la parte del arqueo que el cajero no reconoce:
     * él no hizo ese gasto, solo entregó la plata. Mezclados en el total, tenía
     * que cuadrar a ciegas un faltante que no podía explicar.
     */
    private List<MovimientoCajaDto> movimientosDeOtrasFechas = new ArrayList<>();
    private BigDecimal totalIngresosOtrasFechas = BigDecimal.ZERO;
    private BigDecimal totalEgresosOtrasFechas  = BigDecimal.ZERO;

    /**
     * Correcciones registradas DESPUÉS de cerrar el turno, sin reabrirlo.
     *
     * <p>El cierre original (arriba, en {@link #diferencia}) queda intacto: un
     * arqueo que se puede reescribir deja de probar lo que el cajero entregó.
     * Estas tres cifras — original, ajustes, ajustada — se muestran juntas para
     * que se vea qué se corrigió y qué se firmó ese día.
     */
    private List<MovimientoCajaDto> ajustesRetroactivos = new ArrayList<>();
    private BigDecimal diferenciaOriginal;
    private BigDecimal diferenciaAjustada;

    // Comisiones generadas en el turno
    private List<ComisionResumenTurnoDto> comisiones = new ArrayList<>();
    private BigDecimal totalComisiones = BigDecimal.ZERO;

    // Ventas a crédito del turno
    private Integer    cantidadVentasCredito = 0;
    private BigDecimal totalVentasCredito    = BigDecimal.ZERO;
    private List<DetalleVentaCreditoDto> detalleVentasCredito = new ArrayList<>();

    // Diagnóstico: detalle de cada pago en efectivo del turno
    private List<DetalleEfectivoDto> detalleEfectivo = new ArrayList<>();
}

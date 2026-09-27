package com.cloud_technological.aura_pos.dto.notificacion;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Aviso de la campana: algo que el negocio debería atender. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionDto {
    /** LOTES_VENCIDOS, LOTES_POR_VENCER, STOCK_BAJO, FACTURAS_VENCIDAS, FACTURAS_POR_VENCER, ACUERDOS_INCUMPLIDOS, CUOTAS_POR_VENCER, PROMESAS_*, SOLICITUDES_CREDITO. */
    private String tipo;
    /** danger, warn, info. */
    private String severidad;
    private String titulo;
    private String mensaje;
    private Integer cantidad;
    /** Plata en juego (costo), si aplica. */
    private BigDecimal valor;
    /** Ruta del front a la que lleva. */
    private String ruta;
    /** Texto del botón de acción. */
    private String accion;
}

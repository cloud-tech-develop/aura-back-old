package com.cloud_technological.aura_pos.dto.cartera.credito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Regla de crédito en forma legible: condiciones y acción como campos, no JSON.
 * Una condición en null no se evalúa; todas las que tengan valor deben cumplirse.
 */
@Getter
@Setter
public class ReglaCreditoDto {
    private Long id;
    private String nombre;
    private String descripcion;
    /** AUMENTO_CUPO | REDUCCION_CUPO | SUSPENSION | BLOQUEO | ALERTA */
    private String tipo;
    /** AL_PAGAR | AL_VENDER | PERIODICO */
    private String evento;
    private Boolean activo;
    private Integer orden;
    private Integer diasEntreAplicaciones;
    private Condiciones condiciones = new Condiciones();
    private Accion accion = new Accion();
    // Solo lectura
    private Integer vecesAplicada;
    private LocalDateTime ultimaAplicacion;

    @Getter
    @Setter
    public static class Condiciones {
        private Integer scoreMinimo;
        private Integer scoreMaximo;
        /** Mora máxima permitida (≤). */
        private Integer moraMaximaDias;
        /** Mora que la dispara (>). */
        private Integer moraMayorDias;
        private Integer pagosConsecutivosATiempo;
        private String estadoCredito;
        private Integer promesasIncumplidasMinimo;
        private Integer usoCupoMinimoPct;
    }

    @Getter
    @Setter
    public static class Accion {
        private BigDecimal aumentarPct;
        private BigDecimal cupoMaximo;
        private BigDecimal reducirPct;
        private BigDecimal cupoMinimo;
        private String mensaje;
    }
}

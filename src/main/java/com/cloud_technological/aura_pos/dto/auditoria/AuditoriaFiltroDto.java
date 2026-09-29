package com.cloud_technological.aura_pos.dto.auditoria;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditoriaFiltroDto {

    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    /**
     * Trae las filas concretas de cada hallazgo.
     *
     * <p>Va apagado por defecto: el semáforo solo necesita los totales, y con
     * detalle un período con miles de descuadres devuelve una respuesta enorme.
     * El PDF lo enciende para armar el anexo B.
     */
    private Boolean incluirDetalle = Boolean.FALSE;

    /** Cuántas filas por hallazgo cuando se pide el detalle. */
    private Integer limiteDetalle = 100;

    /**
     * Diferencia mínima para que un descuadre de plata cuente como hallazgo.
     *
     * <p>La primera corrida real reportó un arqueo con <b>22 centavos</b> de
     * sobrante junto a uno de $954.000. Los dos en severidad ALTA y con el mismo
     * peso visual: el de centavos es un redondeo de conteo, no un problema, y
     * mezclarlos le quita fuerza al que sí importa.
     *
     * <p>Aplica solo a caja y cartera, donde los centavos son ruido de
     * redondeo. <b>No</b> aplica a los asientos contables: ahí la partida doble
     * tiene que cuadrar al centavo o la contabilidad no cierra.
     */
    private java.math.BigDecimal umbralMonto = new java.math.BigDecimal("1000");

    /**
     * Incluye los descuadres que vienen de antes de los arreglos conocidos.
     *
     * <p>Se separan del resto para que el primer reporte no parezca decir "el
     * sistema está mal" cuando dice "hay deuda técnica ya diagnosticada".
     */
    private Boolean incluirHeredados = Boolean.TRUE;
}

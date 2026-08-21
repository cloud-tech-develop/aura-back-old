package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovimientoCajaDto {
    private Long       id;
    private String     tipo;          // INGRESO | EGRESO
    private String     concepto;
    private BigDecimal monto;

    /**
     * Día en que se movió la plata (yyyy-MM-dd). Es la fecha que manda en el
     * arqueo. Antes aquí venía {@code created_at} con hora, que es cuándo se
     * digitó — parecido casi siempre, pero no lo mismo, y ocultaba el desfase.
     */
    private String     fecha;

    /**
     * Instante exacto del registro. Sirve para ordenar el detalle del turno,
     * que con solo el día quedaría empatado.
     */
    private String     registradoEn;

    /**
     * Fecha del documento que originó el movimiento, cuando difiere de
     * {@link #fecha}. Null significa que el documento es del mismo día.
     */
    private String     fechaDocumento;

    /** COMPRA | GASTO | DEVOLUCION | OBLIGACION | TRASLADO_FONDOS | MANUAL. */
    private String     origenTipo;

    /** Id del documento dentro de su tipo, para poder abrirlo desde el arqueo. */
    private Long       origenId;

    /**
     * El documento es de un día distinto al del movimiento. El cierre los
     * muestra aparte: sin esto, el cajero cuadra a ciegas un egreso que no
     * reconoce porque corresponde a una factura de días atrás.
     */
    private Boolean    esDeOtraFecha;

    /** Corrige un arqueo ya cerrado, sin reabrirlo. */
    private Boolean    esAjusteRetroactivo;

    /** Por qué se corrigió, y quién lo autorizó. */
    private String     motivoAjuste;
    private Integer    autorizadoPor;

    private String     usuarioNombre;
    /** Número de la cuenta por cobrar o pagar asociada */
    private String     cuentaNumero;
    /** Nombre del cliente (INGRESO) o proveedor (EGRESO) */
    private String     terceroNombre;
    private String     metodoPago;
    private String     entregadoA;
    private Long       comprobanteId;
    private String     numeroComprobante;
}


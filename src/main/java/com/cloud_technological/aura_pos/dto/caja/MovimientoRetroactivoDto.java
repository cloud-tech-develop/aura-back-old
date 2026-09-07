package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del panel de supervisión de movimientos retroactivos.
 *
 * <p>Unifica cuatro cosas que el administrador necesita mirar juntas, porque
 * todas responden a la misma pregunta — <i>¿qué entró a una caja sin ser del
 * turno?</i> — aunque nazcan en tablas distintas.
 */
@Getter
@Setter
public class MovimientoRetroactivoDto {

    /**
     * Qué clase de hallazgo es:
     * <ul>
     *   <li>{@code DOCUMENTO_AUTORIZADO} — factura vieja que entró a la caja
     *       porque alguien con rol la autorizó. Es lo que hay que revisar.</li>
     *   <li>{@code PAGO_DE_OTRA_FECHA} — el pago salió de la caja pero el
     *       documento es de otro día (dentro de la ventana de gracia).</li>
     *   <li>{@code CAJA_INFERIDA} — nadie eligió la caja: el sistema la dedujo
     *       por ser la única abierta. Sin decisión humana detrás.</li>
     *   <li>{@code AJUSTE_CIERRE} — corrección hecha sobre un arqueo ya
     *       cerrado.</li>
     *   <li>{@code SALIDA_CAJA_OTRO_DIA} — la plata salió del cajón otro día y
     *       aquel arqueo ya cerró contra el conteo físico: compra, gasto o
     *       abono a proveedor que no toca ninguna caja.</li>
     *   <li>{@code INGRESO_CAJA_OTRO_DIA} — lo mismo al revés: un recaudo de
     *       cartera que entró al cajón otro día.</li>
     *   <li>{@code COMPROBANTE_SIN_ARQUEO} — cruce de un comprobante manual
     *       anterior a V154, cuando el CE/RC no declaraba el origen de fondos y
     *       el abono quedaba sin turno. Nunca entró a ningún cierre.</li>
     * </ul>
     */
    private String tipoHallazgo;

    /** Id del documento o movimiento, según el tipo. */
    private Long referenciaId;

    /** Cuándo se movió la plata. */
    private LocalDate fecha;

    /** Fecha del documento que lo originó. */
    private LocalDate fechaDocumento;

    /** Cuántos días separan el documento del movimiento. */
    private Integer diasAtras;

    private String concepto;
    private BigDecimal monto;

    /** INGRESO | EGRESO — null para los documentos que aún no tocaron caja. */
    private String tipo;

    private Long turnoCajaId;
    private String cajaNombre;

    /** Quién registró el documento. */
    private String usuarioNombre;

    /** Quién lo autorizó, cuando hizo falta autorización. */
    private String autorizadoPorNombre;

    /** La explicación que escribió quien lo autorizó o lo ajustó. */
    private String motivo;
}

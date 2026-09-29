package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateAsientoDetalleDto {

    /** Línea capturada a mano por el usuario. */
    public static final String ORIGEN_MANUAL = "MANUAL";
    /** Línea generada por el cruce de una cuenta por cobrar/pagar. */
    public static final String ORIGEN_CARTERA = "CARTERA";
    /**
     * Contrapartida de disponible: la caja o el banco por donde se movió la
     * plata. Es la única línea cuya cuenta NO la elige el usuario — la resuelve
     * el origen de fondos declarado en la cabecera, para que no se pueda
     * acreditar "1105 Caja" un pago que entró por transferencia.
     */
    public static final String ORIGEN_BANCO = "BANCO";

    /**
     * Obligatoria salvo en la línea de contrapartida ({@link #ORIGEN_BANCO}),
     * donde la pone el origen de fondos.
     */
    private Long cuentaId;

    /** MANUAL | CARTERA | BANCO. Por defecto MANUAL. */
    private String origen = ORIGEN_MANUAL;

    private String descripcion;

    @NotNull
    private BigDecimal debito = BigDecimal.ZERO;

    @NotNull
    private BigDecimal credito = BigDecimal.ZERO;

    /** Tercero asociado a esta línea (cliente, proveedor, empleado…) — opcional */
    private Long terceroId;

    /** Centro de costo al que se imputa esta línea — opcional */
    private Long centroCostoId;
}

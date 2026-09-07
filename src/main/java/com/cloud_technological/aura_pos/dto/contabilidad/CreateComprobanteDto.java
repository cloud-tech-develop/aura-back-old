package com.cloud_technological.aura_pos.dto.contabilidad;

import java.time.LocalDate;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * Comprobante contable elaborado manualmente desde el módulo de contabilidad.
 * Reutiliza el motor de asientos: la cabecera más las {@code detalles} (líneas
 * débito/crédito) forman el asiento que se contabiliza. Retenciones y cruce de
 * anticipos se ingresan como líneas adicionales.
 */
@Getter @Setter
public class CreateComprobanteDto {

    /** CD=Diario, CE=Egreso, RC=Ingreso/Recibo de caja. */
    @NotBlank
    @Pattern(regexp = "CD|CE|RC", message = "tipoComprobante debe ser CD, CE o RC")
    private String tipoComprobante;

    @NotNull
    private LocalDate fecha;

    /** Concepto general del comprobante (se guarda como descripción del asiento). */
    @NotBlank
    private String concepto;

    // ── Beneficiario ─────────────────────────────────────────────────────
    private Long beneficiarioTerceroId;
    private String beneficiarioNombre;
    private String beneficiarioDireccion;
    private String beneficiarioTelefono;

    /** Ciudad donde se genera el comprobante. */
    private String ciudad;

    /** Vencimiento del comprobante (opcional). */
    private LocalDate fechaVencimiento;

    @NotEmpty
    @Valid
    private List<CreateAsientoDetalleDto> detalles;

    /** Cruce de cartera opcional: cuentas por cobrar/pagar a las que se aplica este comprobante. */
    private List<AplicacionCarteraDto> aplicaciones;

    // ── Origen de fondos (CE/RC) ─────────────────────────────────────────
    //
    // De dónde sale o entra la plata. Antes no se preguntaba: el comprobante
    // elegía una cuenta 11xx a mano y ahí se acababa la historia. Una cuenta
    // contable no sabe si es el cajón de la sucursal 2 o la cuenta del banco,
    // así que el recaudo no caía en el cierre de ninguna caja y el asiento
    // podía acreditar CAJA aunque la plata hubiera entrado por transferencia.
    //
    // El CD (nota de diario) no mueve dinero: estos campos se ignoran.

    /** EFECTIVO | TRANSFERENCIA | TARJETA… Obligatorio en CE y RC. */
    private String metodoPago;

    /** Turno de caja del que sale (o al que entra) el efectivo. */
    private Long turnoCajaId;

    /** Cuenta bancaria de la empresa, cuando el movimiento es por banco. */
    private Long cuentaBancariaId;

    /** Cuenta contable de origen elegida a mano: la caja menor, típicamente. */
    private Long cuentaContableId;

    /** Sucursal, para ubicar la caja abierta cuando el pago es en efectivo. */
    private Integer sucursalId;

    /**
     * La plata ya se movió del cajón otro día y aquel arqueo cerró cuadrado.
     * Deja el asiento contra Caja pero no entra a ningún cierre.
     */
    private Boolean cajaOtroDia = Boolean.FALSE;
}

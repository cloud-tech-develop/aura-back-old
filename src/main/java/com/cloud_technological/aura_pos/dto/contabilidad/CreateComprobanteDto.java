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
}

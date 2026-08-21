package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Corrección de un arqueo ya cerrado.
 *
 * <p>El turno no se reabre: el cierre original queda intacto y este ajuste se
 * suma encima. Por eso el motivo es obligatorio y con longitud mínima — un
 * ajuste sin explicación es alguien cuadrando la caja a mano.
 */
@Getter
@Setter
public class CreateAjusteRetroactivoDto {

    /** INGRESO si sobró plata; EGRESO si salió y nadie la registró. */
    @NotBlank(message = "Indique si el ajuste es un ingreso o un egreso")
    private String tipo;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    /**
     * Fecha del hecho que se está registrando tarde. Determina el período
     * contable del asiento; si no viene, se usa la del cierre del turno.
     */
    private LocalDate fechaDocumento;

    @NotBlank(message = "Explique por qué se corrige este cierre")
    @Size(min = 10, message = "El motivo debe explicar qué pasó, no solo dos palabras")
    private String motivo;

    /** Qué se pagó o se recibió, en los términos del cajero. */
    private String concepto;

    /** Concepto de caja, que define la contrapartida del asiento. */
    private Long conceptoCajaId;
}

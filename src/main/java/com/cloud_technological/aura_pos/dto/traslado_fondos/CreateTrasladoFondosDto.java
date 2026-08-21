package com.cloud_technological.aura_pos.dto.traslado_fondos;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Traslado de dinero entre dos bolsillos de la empresa.
 *
 * <p>De cada extremo se informa el tipo y <b>solo</b> el identificador que le
 * corresponde: CAJA→turno, BANCO→cuenta bancaria, CUENTA→cuenta contable. El
 * servicio rechaza las combinaciones incoherentes en vez de adivinar.
 */
@Getter
@Setter
public class CreateTrasladoFondosDto {

    private Integer sucursalId;

    /** Si no viene, es de hoy. */
    private LocalDate fecha;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotBlank(message = "Debe indicar de dónde sale el dinero")
    private String origenTipo;
    private Long origenTurnoCajaId;
    private Long origenCuentaBancoId;
    private Long origenCuentaId;

    @NotBlank(message = "Debe indicar a dónde entra el dinero")
    private String destinoTipo;
    private Long destinoTurnoCajaId;
    private Long destinoCuentaBancoId;
    private Long destinoCuentaId;

    /**
     * CONSTITUCION_CAJA_MENOR | REEMBOLSO_CAJA_MENOR | CONSIGNACION | TRASLADO.
     * Solo etiqueta para los reportes: no cambia el asiento.
     */
    private String concepto;

    private String observacion;

    /** Quién responde por el fondo en destino (administrador de caja menor). */
    private Integer responsableId;
}

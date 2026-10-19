package com.cloud_technological.aura_pos.dto.firma_contable;

import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * La firma pide que se le activen los documentos electrónicos a un cliente gestionado
 * (docs/PLAN_FIRMA_CONTABLE.md §10). Las credenciales de Factus las pone la plataforma.
 */
@Getter
@Setter
public class SolicitarActivacionDto {

    /** FACTURA_VENTA, NOMINA_ELECTRONICA, DOCUMENTO_SOPORTE. */
    @NotEmpty(message = "Elija al menos un documento electrónico")
    private List<String> servicios;

    @Size(max = 300, message = "La observación no puede superar 300 caracteres")
    private String observacion;
}

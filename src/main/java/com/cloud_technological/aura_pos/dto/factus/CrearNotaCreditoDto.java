package com.cloud_technological.aura_pos.dto.factus;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload de una nota crédito para Factus v1 ({@code POST /v1/credit-notes/validate}).
 *
 * <p>Se serializa DIRECTO a Factus (claves snake_case). Reusa {@link FactusCustomerDto}
 * y {@link FactusItemDto}, que ya son los del flujo de facturas. Los nulos no se
 * envían ({@code JsonInclude.NON_NULL}) para no romper validaciones opcionales.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrearNotaCreditoDto {

    /** Opcional si solo hay un rango activo para notas crédito. */
    @JsonProperty("numbering_range_id")
    private Integer numberingRangeId;

    /** Código del concepto de corrección (tabla de códigos de corrección). */
    @JsonProperty("correction_concept_code")
    private Integer correctionConceptCode;

    /** Código del tipo de operación (20 = referencia factura; 22 = sin referencia). */
    @JsonProperty("customization_id")
    private Integer customizationId;

    /** Factura referenciada. Opcional si {@code customization_id = 22}. */
    @JsonProperty("bill_id")
    private Long billId;

    /** Código único de la nota (evita duplicados). */
    @JsonProperty("reference_code")
    private String referenceCode;

    @JsonProperty("payment_method_code")
    private String paymentMethodCode;

    @JsonProperty("observation")
    private String observation;

    @JsonProperty("send_email")
    private Boolean sendEmail;

    /** Si es null, Factus toma el cliente de la factura referenciada. */
    @JsonProperty("customer")
    private FactusCustomerDto customer;

    @JsonProperty("items")
    private List<FactusItemDto> items;
}

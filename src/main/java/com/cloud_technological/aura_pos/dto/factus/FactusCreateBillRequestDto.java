package com.cloud_technological.aura_pos.dto.factus;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FactusCreateBillRequestDto {
        
    @JsonProperty("numbering_range_id")
    private Integer numberingRangeId;

    @JsonProperty("reference_code")
    private String referenceCode;

    @JsonProperty("observation")
    private String observation;

    // "1"=Contado, "2"=Crédito
    @JsonProperty("payment_form")
    private String paymentForm = "1";

    // "YYYY-MM-DD"
    @JsonProperty("payment_due_date")
    private String paymentDueDate;

    // "10"=Efectivo, "42"=Transferencia, "20"=Cheque
    @JsonProperty("payment_method_code")
    private String paymentMethodCode;

    // 10 = Estándar
    @JsonProperty("operation_type")
    private Integer operationType = 10;

    private FactusCustomerDto customer;

    private List<FactusItemDto> items;
}

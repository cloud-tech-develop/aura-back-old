package com.cloud_technological.aura_pos.dto.factus;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FactusCustomerDto {
    @JsonProperty("identification")
    private String identification;

    @JsonProperty("dv")
    private String dv;

    @JsonProperty("company")
    private String company = "";

    @JsonProperty("trade_name")
    private String tradeName = "";

    @JsonProperty("names")
    private String names;

    @JsonProperty("address")
    private String address;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    // "1"=Persona Jurídica, "2"=Persona Natural
    @JsonProperty("legal_organization_id")
    private String legalOrganizationId = "2";

    // "21"=No aplica (consumidor final / persona natural sin régimen)
    // "15"=Gran contribuyente, "17"=Responsable IVA, etc.
    @JsonProperty("tribute_id")
    private String tributeId = "21";

    // "3"=CC, "6"=NIT, "2"=CE, "13"=Pasaporte, "7"=TI, "21"=PEP
    @JsonProperty("identification_document_id")
    private String identificationDocumentId;

    // ID del municipio en Factus (ej: 149=Bogotá, 980=San Gil)
    @JsonProperty("municipality_id")
    private String municipalityId;
}

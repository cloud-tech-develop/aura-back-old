package com.cloud_technological.aura_pos.dto.factus;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FactusBillDto {
    private Long id;

    // "SETP990000049"
    private String number;

    // Hash DIAN
    private String cufe;

    // URL QR DIAN
    private String qr;

    @JsonProperty("public_url")
    private String publicUrl;

    @JsonProperty("pdf_download_link")
    private String pdfDownloadLink;

    @JsonProperty("xml_download_link")
    private String xmlDownloadLink;
}

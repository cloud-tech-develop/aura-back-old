package com.cloud_technological.aura_pos.dto.factus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturaElectronicaResponseDto {
    private Long ventaId;
    private String facturaNumero;    // "SETP-000123"
    private String cufe;             // hash DIAN
    private String qr;               // URL QR DIAN
    private String pdfUrl;           // link descarga PDF Factus
    private String estadoDian;       // "EMITIDA"
}

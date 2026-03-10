package com.cloud_technological.aura_pos.services.implementations;

import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FacturaPdfService {
    
    // Factus: GET /v1/bills/download-pdf/{bill_number}
    // Responde con: { "status": "OK", "data": { "pdf_base_64_encoded": "..." } }
    private static final String FACTUS_PDF_URL =
            "https://api.factus.com.co/v1/bills/download-pdf/{billNumber}";

    private final RestTemplate       restTemplate;
    private final VentaJPARepository ventaJPARepository;
    private final FactusTokenService factusTokenService;
    private final ObjectMapper       objectMapper;

    public FacturaPdfService(RestTemplate restTemplate,
                             VentaJPARepository ventaJPARepository,
                             FactusTokenService factusTokenService,
                             ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.ventaJPARepository = ventaJPARepository;
        this.factusTokenService = factusTokenService;
        this.objectMapper = objectMapper;
    }
    public byte[] obtenerPdf(Long ventaId, Integer empresaId) {

        VentaEntity venta = ventaJPARepository.findByIdAndEmpresaId(ventaId, empresaId)
                .orElseThrow(() -> new GlobalException(
                        HttpStatus.NOT_FOUND, "Venta no encontrada"));

        if (!"EMITIDA".equals(venta.getEstadoDian())) {
            throw new GlobalException(
                    HttpStatus.BAD_REQUEST, "Esta venta no tiene factura electrónica emitida");
        }

        String billNumber = venta.getFactusNumero();
        if (billNumber == null || billNumber.isBlank()) {
            throw new GlobalException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontró el número de factura de Factus para esta venta");
        }

        String token = factusTokenService.obtenerToken(empresaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            // Factus responde JSON con base64 del PDF
            ResponseEntity<String> response = restTemplate.exchange(
                    FACTUS_PDF_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class,
                    billNumber
            );

            // Parsear: { "status": "OK", "data": { "pdf_base_64_encoded": "JVBERi0..." } }
            JsonNode root    = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.path("data");

            String base64Pdf = dataNode.path("pdf_base_64_encoded").asText(null);
            if (base64Pdf == null || base64Pdf.isBlank()) {
                throw new GlobalException(HttpStatus.BAD_GATEWAY,
                        "Factus no retornó el PDF en la respuesta");
            }

            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

            log.info("[Factus PDF] ✅ OK — venta={} bill={} bytes={}",
                    ventaId, billNumber, pdfBytes.length);

            return pdfBytes;

        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Factus PDF] Error: {}", e.getMessage());
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener el PDF de Factus: " + e.getMessage());
        }
    }
}

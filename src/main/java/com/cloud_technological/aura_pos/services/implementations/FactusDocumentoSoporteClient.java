package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente Factus v1 del documento soporte: validar, PDF y eliminar el no
 * validado. Un 4xx de Factus no es excepción: es la respuesta de la DIAN que
 * el usuario tiene que ver, así que se devuelve con su cuerpo.
 */
@Slf4j
@Service
public class FactusDocumentoSoporteClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final FactusTokenService tokenService;
    private final ObjectMapper mapper = new ObjectMapper();

    public record Respuesta(int status, boolean exitoso, String body) {
    }

    public FactusDocumentoSoporteClient(@Value("${factus.api.base-url}") String apiBaseUrl,
            RestTemplate restTemplate, FactusTokenService tokenService) {
        this.baseUrl = apiBaseUrl + "/v1/support-documents";
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
    }

    private HttpHeaders headers(Integer empresaId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        h.setBearerAuth(tokenService.obtenerToken(empresaId));
        return h;
    }

    /** POST /v1/support-documents/validate */
    public Respuesta validar(Integer empresaId, String body) {
        try {
            ResponseEntity<String> raw = restTemplate.exchange(baseUrl + "/validate", HttpMethod.POST,
                    new HttpEntity<>(body, headers(empresaId)), String.class);
            log.info("[Factus DS] HTTP {} | {}", raw.getStatusCode(), raw.getBody());
            return new Respuesta(raw.getStatusCode().value(), raw.getStatusCode().is2xxSuccessful(),
                    raw.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus DS] HTTP {} | {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new Respuesta(e.getStatusCode().value(), false, e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Factus. Intente de nuevo en unos minutos");
        }
    }

    /** GET /v1/support-documents/download-pdf/{number} → Base64. */
    public String pdf(Integer empresaId, String numero) {
        String url = baseUrl + "/download-pdf/" + numero;
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers(empresaId)), String.class);
            JsonNode root = mapper.readTree(raw.getBody());
            JsonNode pdf = root.path("data").path("pdf_base_64_encoded");
            if (pdf.isMissingNode() || pdf.asText().isBlank()) {
                pdf = root.path("pdf_base_64_encoded");
            }
            if (pdf.isMissingNode() || pdf.asText().isBlank()) {
                throw new GlobalException(HttpStatus.BAD_GATEWAY, "Factus no devolvió el PDF");
            }
            return pdf.asText();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus DS] PDF {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GlobalException(HttpStatus.BAD_GATEWAY, "No se pudo obtener el PDF en Factus");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new GlobalException(HttpStatus.BAD_GATEWAY, "Respuesta de Factus ilegible");
        }
    }

    /** DELETE /v1/support-documents/reference/{reference_code}: solo los no validados. */
    public Respuesta eliminar(Integer empresaId, String referenceCode) {
        String url = baseUrl + "/reference/" + referenceCode;
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(headers(empresaId)), String.class);
            return new Respuesta(raw.getStatusCode().value(), true, raw.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus DS] DELETE {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            return new Respuesta(e.getStatusCode().value(), false, e.getResponseBodyAsString());
        }
    }
}

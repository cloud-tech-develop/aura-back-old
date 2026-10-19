package com.cloud_technological.aura_pos.services.correo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

/**
 * Envía un correo por la API de Resend ({@code POST /emails}). La llave y el
 * remitente salen del .env ({@code RESEND_API_KEY}, {@code CORREO_REMITENTE}); nunca
 * se escriben en el código. Cada envío lleva una llave de idempotencia: si se
 * reintenta el mismo correo, Resend no lo manda dos veces.
 */
@Component
@RequiredArgsConstructor
public class ResendClient {

    private final RestTemplate restTemplate;

    @Value("${correo.resend.url:https://api.resend.com}")
    private String url;

    @Value("${correo.resend.api-key:}")
    private String apiKey;

    /** Dirección verificada en Resend, por ejemplo documentos@dominio. */
    @Value("${correo.remitente:}")
    private String remitente;

    /**
     * Correo listo para enviar.
     *
     * @param nombreRemitente lo que ve el cliente como remitente (el nombre de la empresa)
     * @param responderA      correo de la empresa: las respuestas le llegan a ella; null = ninguno
     * @param idempotencia    llave única del envío (por ejemplo "correo-123")
     */
    public record Mensaje(String nombreRemitente, List<String> para, List<String> cc, String responderA,
            String asunto, String html, String adjuntoNombre, byte[] adjunto, String idempotencia) {
    }

    /**
     * Resultado del envío.
     *
     * @param reintentar el fallo es pasajero (límite de envíos, red, error de Resend):
     *                   la cola lo vuelve a intentar
     */
    public record Resultado(boolean ok, String id, String error, boolean reintentar) {
    }

    /** Hay llave y remitente en el .env. */
    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank() && remitente != null && !remitente.isBlank();
    }

    /** Envía el correo; nunca lanza: el error vuelve en el resultado. */
    public Resultado enviar(Mensaje m) {
        if (!configurado())
            return new Resultado(false, null,
                    "El envío de correos no está configurado (faltan RESEND_API_KEY o CORREO_REMITENTE)", false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", desde(m.nombreRemitente()));
        body.put("to", m.para());
        if (m.cc() != null && !m.cc().isEmpty()) body.put("cc", m.cc());
        if (m.responderA() != null && !m.responderA().isBlank()) body.put("reply_to", m.responderA());
        body.put("subject", m.asunto());
        body.put("html", m.html());
        if (m.adjunto() != null && m.adjunto().length > 0) {
            List<Map<String, Object>> adjuntos = new ArrayList<>();
            adjuntos.add(Map.of("filename", m.adjuntoNombre(), "content",
                    Base64.getEncoder().encodeToString(m.adjunto())));
            body.put("attachments", adjuntos);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        if (m.idempotencia() != null) headers.set("Idempotency-Key", m.idempotencia());
        try {
            ResponseEntity<Map> r = restTemplate.postForEntity(url + "/emails", new HttpEntity<>(body, headers),
                    Map.class);
            Object id = r.getBody() != null ? r.getBody().get("id") : null;
            return new Resultado(true, id != null ? id.toString() : null, null, false);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            // 429 = límite de envíos por segundo; 5xx = Resend con problemas: se reintenta.
            boolean pasajero = status == 429 || status >= 500;
            return new Resultado(false, null, "Resend " + status + ": " + recortar(e.getResponseBodyAsString()),
                    pasajero);
        } catch (ResourceAccessException e) {
            return new Resultado(false, null, "Sin conexión con Resend: " + recortar(e.getMessage()), true);
        } catch (RuntimeException e) {
            return new Resultado(false, null, recortar(e.getMessage()), false);
        }
    }

    /** "Nombre de la empresa <documentos@dominio>", sin caracteres que rompan el encabezado. */
    private String desde(String nombre) {
        String limpio = nombre == null ? "" : nombre.replaceAll("[\"<>\\r\\n]", "").trim();
        return limpio.isEmpty() ? remitente : "\"" + limpio + "\" <" + remitente + ">";
    }

    private static String recortar(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}

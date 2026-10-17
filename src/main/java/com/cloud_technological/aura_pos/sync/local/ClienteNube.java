package com.cloud_technological.aura_pos.sync.local;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Habla con la nube desde el nodo local.
 *
 * <p>Tiene su propio RestTemplate (no es bean): el de Factus ya ocupa el tipo y
 * aquí los tiempos son otros — conectar debe fallar rápido para saber que no hay
 * internet, pero la bajada del catálogo puede tardar.
 */
@Component
public class ClienteNube {

    public static final String HEADER_TOKEN = "X-Sync-Token";

    private final SyncProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate http;

    /** La nube respondió con error de negocio o de autenticación (hay conexión). */
    public static class NubeRechazo extends RuntimeException {
        public NubeRechazo(String mensaje) {
            super(mensaje);
        }
    }

    public ClienteNube(SyncProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        this.http = new RestTemplate(factory);
    }

    /**
     * Sube un lote de comandos.
     *
     * @param peticion comandos en orden
     * @return el resultado de los que la nube alcanzó a procesar
     */
    public SyncDtos.PushRespuesta push(SyncDtos.PushPeticion peticion) {
        return llamar(HttpMethod.POST, "/api/sync/push", peticion, SyncDtos.PushRespuesta.class);
    }

    /**
     * Baja el catálogo y, si se pide, las existencias.
     *
     * @param saldos true para incluir inventario, lotes y seriales
     */
    public SyncDtos.PullRespuesta pull(boolean saldos) {
        return llamar(HttpMethod.GET, "/api/sync/pull?saldos=" + saldos, null, SyncDtos.PullRespuesta.class);
    }

    private <T> T llamar(HttpMethod metodo, String ruta, Object cuerpo, Class<T> tipo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_TOKEN, props.getNodoToken());
        try {
            String body = cuerpo == null ? null : objectMapper.writeValueAsString(cuerpo);
            ResponseEntity<String> r = http.exchange(props.getNubeUrl() + ruta, metodo,
                    new HttpEntity<>(body, headers), String.class);
            JsonNode raiz = objectMapper.readTree(r.getBody());
            return objectMapper.treeToValue(raiz.path("data"), tipo);
        } catch (HttpStatusCodeException e) {
            throw new NubeRechazo("La nube respondió " + e.getStatusCode().value() + ": " + mensaje(e));
        } catch (ResourceAccessException e) {
            // Sin conexión: quien llama lo trata como "fuera de línea".
            throw e;
        } catch (Exception e) {
            throw new NubeRechazo("Respuesta de la nube ilegible: " + e.getMessage());
        }
    }

    private String mensaje(HttpStatusCodeException e) {
        try {
            JsonNode n = objectMapper.readTree(e.getResponseBodyAsString());
            if (n.hasNonNull("message")) return n.get("message").asText();
            if (n.hasNonNull("error")) return n.get("error").asText();
        } catch (Exception ignorada) {
            // cuerpo no JSON
        }
        return e.getStatusText();
    }
}
